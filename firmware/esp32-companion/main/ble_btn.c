#include "ble_btn.h"

#include <stdio.h>
#include <string.h>

#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "nvs.h"
#include "nvs_flash.h"

#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "host/ble_gap.h"
#include "services/gap/ble_svc_gap.h"

#include "bthome_crypt.h"
#include "protocol.h"

static const char *TAG = "ble_btn";

#define NVS_NS "ble_btn"
#define NVS_KEY_ON "on"
#define NVS_KEY_COUNT "n"
#define NVS_KEY_MAC0 "m0"
#define BTHOME_UUID16 0xFCD2

#define BTH_OBJ_PID 0x00
#define BTH_OBJ_BATTERY 0x01
#define BTH_OBJ_BUTTON 0x3A

#define BTN_EVT_NONE 0x00
#define BTN_EVT_PRESS 0x01
#define BTN_EVT_DOUBLE 0x02
#define BTN_EVT_TRIPLE 0x03
#define BTN_EVT_LONG 0x04
#define BTN_EVT_HOLD 0x80
#define BTN_EVT_HOLD_LEGACY 0xFE

typedef struct {
    uint8_t addr[6];
    uint8_t pid;
    bool have_pid;
} ble_dedup_t;

static bool s_inited;
static bool s_nimble_ready;
static bool s_scanning;
static SemaphoreHandle_t s_radio_lock;
static bool s_on;
static bool s_learn;
static uint32_t s_learn_deadline_ms;
static uint8_t s_macs[BLE_BTN_MAX_MACS][6];
static int s_mac_count;
static int s_last_bat = -1;
static int s_last_rssi;
static uint8_t s_last_mac[6];
static bool s_have_last_mac;
static ble_dedup_t s_dedup[8];
static int s_dedup_count;
/* Per-remote BTHome key. A keyed remote is accepted only encrypted with a rising counter. */
static uint8_t s_keys[BLE_BTN_MAX_MACS][BTHOME_KEY_LEN];
static bool s_has_key[BLE_BTN_MAX_MACS];
static uint32_t s_ctr[BLE_BTN_MAX_MACS];
static bool s_ctr_valid[BLE_BTN_MAX_MACS];
static uint8_t s_learn_key[BTHOME_KEY_LEN];
static bool s_learn_key_valid;

static void mac_to_str(const uint8_t *addr, char out[18])
{
    snprintf(out, 18, "%02x:%02x:%02x:%02x:%02x:%02x",
             addr[5], addr[4], addr[3], addr[2], addr[1], addr[0]);
}

static int hex_nibble(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

static bool parse_mac_str(const char *s, uint8_t out[6])
{
    if (!s) return false;
    uint8_t tmp[6];
    int n = 0;
    const char *p = s;
    while (*p && n < 6) {
        while (*p == ':' || *p == '-' || *p == ' ') p++;
        if (!*p) break;
        int hi = hex_nibble(*p++);
        if (hi < 0 || !*p) return false;
        int lo = hex_nibble(*p++);
        if (lo < 0) return false;
        tmp[n++] = (uint8_t)((hi << 4) | lo);
    }
    if (n != 6) return false;
    /* UI / JSON use human MSB-first; NimBLE addr is little-endian byte order. */
    for (int i = 0; i < 6; i++) {
        out[i] = tmp[5 - i];
    }
    return true;
}

static bool mac_eq(const uint8_t *a, const uint8_t *b)
{
    return memcmp(a, b, 6) == 0;
}

static int find_mac(const uint8_t *addr)
{
    for (int i = 0; i < s_mac_count; i++) {
        if (mac_eq(s_macs[i], addr)) return i;
    }
    return -1;
}

static bool save_nvs(void)
{
    nvs_handle_t h;
    if (nvs_open(NVS_NS, NVS_READWRITE, &h) != ESP_OK) return false;
    nvs_set_u8(h, NVS_KEY_ON, s_on ? 1 : 0);
    nvs_set_u8(h, NVS_KEY_COUNT, (uint8_t)s_mac_count);
    for (int i = 0; i < BLE_BTN_MAX_MACS; i++) {
        char key[12];
        snprintf(key, sizeof(key), "m%d", i);
        if (i < s_mac_count) {
            nvs_set_blob(h, key, s_macs[i], 6);
        } else {
            nvs_erase_key(h, key);
        }
        snprintf(key, sizeof(key), "k%d", i);
        if (i < s_mac_count && s_has_key[i]) {
            nvs_set_blob(h, key, s_keys[i], BTHOME_KEY_LEN);
        } else {
            nvs_erase_key(h, key);
        }
        snprintf(key, sizeof(key), "c%d", i);
        if (i < s_mac_count && s_has_key[i] && s_ctr_valid[i]) {
            nvs_set_u32(h, key, s_ctr[i]);
        } else {
            nvs_erase_key(h, key);
        }
    }
    esp_err_t err = nvs_commit(h);
    nvs_close(h);
    return err == ESP_OK;
}

static void load_nvs(void)
{
    nvs_handle_t h;
    if (nvs_open(NVS_NS, NVS_READONLY, &h) != ESP_OK) {
        s_on = false;
        s_mac_count = 0;
        return;
    }
    uint8_t on = 0;
    uint8_t n = 0;
    nvs_get_u8(h, NVS_KEY_ON, &on);
    nvs_get_u8(h, NVS_KEY_COUNT, &n);
    s_on = on != 0;
    s_mac_count = 0;
    if (n > BLE_BTN_MAX_MACS) n = BLE_BTN_MAX_MACS;
    for (int i = 0; i < n; i++) {
        char key[12];
        snprintf(key, sizeof(key), "m%d", i);
        size_t len = 6;
        if (nvs_get_blob(h, key, s_macs[s_mac_count], &len) != ESP_OK || len != 6) continue;
        int slot = s_mac_count++;
        s_has_key[slot] = false;
        s_ctr_valid[slot] = false;
        snprintf(key, sizeof(key), "k%d", i);
        len = BTHOME_KEY_LEN;
        if (nvs_get_blob(h, key, s_keys[slot], &len) == ESP_OK && len == BTHOME_KEY_LEN) {
            s_has_key[slot] = true;
            snprintf(key, sizeof(key), "c%d", i);
            s_ctr_valid[slot] = nvs_get_u32(h, key, &s_ctr[slot]) == ESP_OK;
        }
    }
    nvs_close(h);
}

/* Button presses are rare, so the counter is written on every accepted press. */
static void save_counter(int idx)
{
    nvs_handle_t h;
    if (nvs_open(NVS_NS, NVS_READWRITE, &h) != ESP_OK) return;
    char key[12];
    snprintf(key, sizeof(key), "c%d", idx);
    nvs_set_u32(h, key, s_ctr[idx]);
    nvs_commit(h);
    nvs_close(h);
}

static bool dedup_hit(const uint8_t *addr, uint8_t pid, bool have_pid)
{
    if (!have_pid) return false;
    for (int i = 0; i < s_dedup_count; i++) {
        if (s_dedup[i].have_pid && mac_eq(s_dedup[i].addr, addr) && s_dedup[i].pid == pid) {
            return true;
        }
    }
    if (s_dedup_count < (int)(sizeof(s_dedup) / sizeof(s_dedup[0]))) {
        memcpy(s_dedup[s_dedup_count].addr, addr, 6);
        s_dedup[s_dedup_count].pid = pid;
        s_dedup[s_dedup_count].have_pid = true;
        s_dedup_count++;
    } else {
        /* Ring: overwrite oldest slot 0. */
        memmove(&s_dedup[0], &s_dedup[1], sizeof(s_dedup) - sizeof(s_dedup[0]));
        memcpy(s_dedup[s_dedup_count - 1].addr, addr, 6);
        s_dedup[s_dedup_count - 1].pid = pid;
        s_dedup[s_dedup_count - 1].have_pid = true;
    }
    return false;
}

static const char *btn_act_name(uint8_t evt)
{
    switch (evt) {
    case BTN_EVT_PRESS: return "press";
    case BTN_EVT_DOUBLE: return "double";
    case BTN_EVT_TRIPLE: return "triple";
    case BTN_EVT_LONG: return "long";
    case BTN_EVT_HOLD:
    case BTN_EVT_HOLD_LEGACY: return "hold";
    default: return NULL;
    }
}

/** BTHome object payload sizes (unencrypted). Unknown ids → fail parse. */
static int bthome_obj_size(uint8_t id)
{
    switch (id) {
    case BTH_OBJ_PID:
    case BTH_OBJ_BATTERY:
    case BTH_OBJ_BUTTON:
        return 1;
    case 0x02: /* temperature int16 */
    case 0x03: /* humidity uint16 */
    case 0x3F: /* rotation int16 */
        return 2;
    default:
        /* Skip unknown single-byte sensors conservatively by failing. */
        return -1;
    }
}

typedef struct {
    bool have_pid;
    uint8_t pid;
    int battery; /* -1 unknown */
    int button_count;
    uint8_t buttons[4];
} bthome_parse_t;

/** Objects start at data[i]; for plain frames after the info byte, for encrypted ones the plaintext. */
static bool parse_objects(const uint8_t *data, int i, int len, bthome_parse_t *out)
{
    while (i < len) {
        uint8_t oid = data[i++];
        int psz = bthome_obj_size(oid);
        if (psz < 0 || i + psz > len) {
            /* Shelly RC4 packets are short; stop on unknown trailing. */
            break;
        }
        if (oid == BTH_OBJ_PID) {
            out->have_pid = true;
            out->pid = data[i];
        } else if (oid == BTH_OBJ_BATTERY) {
            out->battery = data[i];
        } else if (oid == BTH_OBJ_BUTTON) {
            if (out->button_count < 4) {
                out->buttons[out->button_count++] = data[i];
            }
        }
        i += psz;
    }
    return out->button_count > 0 || out->battery >= 0 || out->have_pid;
}

static bool parse_plain(const uint8_t *data, uint8_t len, bthome_parse_t *out)
{
    memset(out, 0, sizeof(*out));
    out->battery = -1;
    if (!data || len < 2) return false;
    int i = 1;
    if (data[0] & 0x02) {
        /* MAC included — 6 bytes */
        if (i + 6 > len) return false;
        i += 6;
    }
    return parse_objects(data, i, len, out);
}

static int start_scan(void);
static void stop_scan(void);

void ble_radio_lock(void)
{
    if (s_radio_lock) xSemaphoreTakeRecursive(s_radio_lock, portMAX_DELAY);
}

void ble_radio_unlock(void)
{
    if (s_radio_lock) xSemaphoreGiveRecursive(s_radio_lock);
}

/* s_macs / s_mac_count are shared with ble_phone (common device limit): always under ble_radio_lock. */
static bool add_mac(const uint8_t *addr, const uint8_t *key, uint32_t counter)
{
    ble_radio_lock();
    bool ok = true;
    if (find_mac(addr) < 0) {
        if (s_mac_count + ble_phone_count() >= BLE_DEVICE_MAX) {
            ok = false;
        } else {
            int slot = s_mac_count++;
            memcpy(s_macs[slot], addr, 6);
            s_has_key[slot] = key != NULL;
            s_ctr_valid[slot] = key != NULL;
            s_ctr[slot] = counter;
            if (key) memcpy(s_keys[slot], key, BTHOME_KEY_LEN);
            ok = save_nvs();
        }
    }
    ble_radio_unlock();
    return ok;
}

static void handle_bthome_adv(const uint8_t *addr, int8_t rssi,
                              const uint8_t *svc, uint8_t svc_len)
{
    if (!svc || svc_len < 2) return;
    const bool encrypted = (svc[0] & 0x01) != 0;
    uint8_t key[BTHOME_KEY_LEN];
    bool have_key = false;
    bool learn_key = false;

    ble_radio_lock();
    int idx = find_mac(addr);
    const bool learn = s_learn;
    if (idx >= 0 && s_has_key[idx]) {
        memcpy(key, s_keys[idx], BTHOME_KEY_LEN);
        have_key = true;
    } else if (idx < 0 && learn && s_learn_key_valid) {
        memcpy(key, s_learn_key, BTHOME_KEY_LEN);
        have_key = true;
        learn_key = true;
    }
    ble_radio_unlock();

    /* A key means plain frames from that address are forgeries. */
    if (have_key != encrypted) return;

    bthome_parse_t parsed;
    uint32_t counter = 0;
    if (encrypted) {
        uint8_t mac_msb[6];
        uint8_t plain[32];
        for (int i = 0; i < 6; i++) mac_msb[i] = addr[5 - i];
        int n = bthome_decrypt(key, mac_msb, svc, svc_len, plain, sizeof(plain), &counter);
        if (n <= 0) return;
        memset(&parsed, 0, sizeof(parsed));
        parsed.battery = -1;
        if (!parse_objects(plain, 0, n, &parsed)) return;
    } else if (!parse_plain(svc, svc_len, &parsed)) {
        return;
    }

    uint32_t now_ms = (uint32_t)(esp_timer_get_time() / 1000ULL);
    char mac_str[18];
    mac_to_str(addr, mac_str);
    const bool has_btn_event = parsed.button_count > 0;

    if (idx < 0) {
        if (!learn || !has_btn_event) return;
        protocol_send_ble_seen(mac_str, rssi, now_ms);
        if (add_mac(addr, learn_key ? key : NULL, counter)) {
            ESP_LOGI(TAG, "learned %s%s", mac_str, learn_key ? " (encrypted)" : "");
            ble_radio_lock();
            s_learn = false;
            s_learn_key_valid = false;
            ble_radio_unlock();
            protocol_send_ble_ack("allow", true, NULL);
            protocol_send_ble_status();
            protocol_send_ble_ack("learnEnd", true, NULL);
        } else {
            protocol_send_ble_ack("allow", false, "full");
            return;
        }
    } else if (encrypted) {
        bool fresh;
        ble_radio_lock();
        /* The slot may have moved while the frame was decrypted. */
        idx = find_mac(addr);
        fresh = idx >= 0 && s_has_key[idx] && (!s_ctr_valid[idx] || counter > s_ctr[idx]);
        if (fresh) {
            s_ctr[idx] = counter;
            s_ctr_valid[idx] = true;
            if (has_btn_event) save_counter(idx);
        }
        ble_radio_unlock();
        /* Repeats of one frame share the counter; older counters are replays. */
        if (!fresh) return;
    }

    if (parsed.battery >= 0) {
        s_last_bat = parsed.battery;
    }
    s_last_rssi = rssi;
    memcpy(s_last_mac, addr, 6);
    s_have_last_mac = true;

    if (dedup_hit(addr, parsed.pid, parsed.have_pid)) return;

    for (int b = 0; b < parsed.button_count; b++) {
        const char *act = btn_act_name(parsed.buttons[b]);
        if (!act) continue; /* None or unknown */
        protocol_send_ble_btn(mac_str, b + 1, act, s_last_bat, rssi, now_ms);
    }
}

int ble_btn_gap_event(struct ble_gap_event *event, void *arg)
{
    (void)arg;
    switch (event->type) {
    case BLE_GAP_EVENT_DISC: {
        const struct ble_gap_disc_desc *disc = &event->disc;
        const uint8_t *addr = disc->addr.val;
        const uint8_t *data = disc->data;
        uint8_t len = disc->length_data;
        int i = 0;
        while (i + 1 < len) {
            uint8_t ad_len = data[i];
            if (ad_len == 0 || i + 1 + ad_len > len) break;
            uint8_t ad_type = data[i + 1];
            const uint8_t *ad_data = &data[i + 2];
            uint8_t ad_dlen = ad_len - 1;
            /* 0x16 = Service Data - 16-bit UUID */
            if (ad_type == 0x16 && ad_dlen >= 3) {
                uint16_t uuid = (uint16_t)ad_data[0] | ((uint16_t)ad_data[1] << 8);
                if (uuid == BTHOME_UUID16) {
                    handle_bthome_adv(addr, disc->rssi, ad_data + 2, ad_dlen - 2);
                }
            }
            i += 1 + ad_len;
        }
        return 0;
    }
    /* A connection preempts the scan. ble_gap_disc_cancel() itself sends nothing. */
    case BLE_GAP_EVENT_DISC_COMPLETE:
        ble_radio_lock();
        ESP_LOGI(TAG, "scan complete reason=%d", event->disc_complete.reason);
        s_scanning = false;
        if (s_on && s_nimble_ready) {
            start_scan();
        }
        ble_radio_unlock();
        return 0;
    case BLE_GAP_EVENT_CONNECT:
    case BLE_GAP_EVENT_DISCONNECT:
    case BLE_GAP_EVENT_SUBSCRIBE:
    case BLE_GAP_EVENT_MTU:
    case BLE_GAP_EVENT_ADV_COMPLETE:
        ble_phone_on_gap(event);
        return 0;
    default:
        return 0;
    }
}

static int start_scan(void)
{
    if (!s_nimble_ready || s_scanning) return 0;
    /* 40 ms window every 80 ms: the connectable phone advert and a phone link share the radio. */
    struct ble_gap_disc_params params = {
        .itvl = 0x80,
        .window = 0x40,
        .filter_policy = 0,
        .limited = 0,
        .passive = 1,
        .filter_duplicates = 0,
    };
    int rc = ble_gap_disc(BLE_OWN_ADDR_PUBLIC, BLE_HS_FOREVER, &params, ble_btn_gap_event, NULL);
    if (rc != 0) {
        ESP_LOGW(TAG, "ble_gap_disc rc=%d", rc);
        return rc;
    }
    s_scanning = true;
    ESP_LOGI(TAG, "BLE scan started");
    return 0;
}

static void stop_scan(void)
{
    if (s_scanning) {
        ble_gap_disc_cancel();
        s_scanning = false;
    }
}

void ble_btn_suspend_scan(void)
{
    ble_radio_lock();
    stop_scan();
    ble_radio_unlock();
}

void ble_btn_kick_scan(void)
{
    ble_radio_lock();
    if (s_on && s_nimble_ready) start_scan();
    ble_radio_unlock();
}

static void on_sync(void)
{
    ble_radio_lock();
    s_nimble_ready = true;
    bool on = s_on;
    ble_radio_unlock();
    ESP_LOGI(TAG, "NimBLE sync");
    if (on) {
        ble_phone_start_adv();
        ble_radio_lock();
        start_scan();
        ble_radio_unlock();
    }
}

static void on_reset(int reason)
{
    ESP_LOGW(TAG, "NimBLE reset reason=%d", reason);
    ble_radio_lock();
    s_nimble_ready = false;
    s_scanning = false;
    ble_radio_unlock();
}

static void nimble_host_task(void *param)
{
    (void)param;
    nimble_port_run();
    nimble_port_freertos_deinit();
}

static void start_nimble(void)
{
    ble_hs_cfg.sync_cb = on_sync;
    ble_hs_cfg.reset_cb = on_reset;
    nimble_port_freertos_init(nimble_host_task);
}

void ble_btn_init(void)
{
    if (s_inited) return;
    s_radio_lock = xSemaphoreCreateRecursiveMutex();
    load_nvs();
    ble_phone_init();
    s_inited = true;
    ESP_LOGI(TAG, "init on=%d macs=%d", (int)s_on, s_mac_count);

    esp_err_t err = nimble_port_init();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "nimble_port_init: %s", esp_err_to_name(err));
        return;
    }
    ble_phone_gatts_register();
    start_nimble();
}

bool ble_btn_is_on(void)
{
    return s_on;
}

bool ble_btn_is_learn(void)
{
    return s_learn;
}

int ble_btn_mac_count(void)
{
    ble_radio_lock();
    int n = s_mac_count;
    ble_radio_unlock();
    return n;
}

int ble_btn_get_macs(char out[][18], int max_out)
{
    ble_radio_lock();
    int n = s_mac_count;
    if (n > max_out) n = max_out;
    for (int i = 0; i < n; i++) {
        mac_to_str(s_macs[i], out[i]);
    }
    ble_radio_unlock();
    return n;
}

bool ble_btn_set_on(bool on)
{
    ble_radio_lock();
    s_on = on;
    save_nvs();
    bool ready = s_nimble_ready;
    if (!on) {
        stop_scan();
        s_learn = false;
        s_learn_key_valid = false;
        ble_phone_learn_end();
    }
    ble_radio_unlock();
    if (!ready) return true;
    if (on) {
        ble_phone_start_adv();
        ble_radio_lock();
        start_scan();
        ble_radio_unlock();
    } else {
        ble_phone_stop_link();
    }
    return true;
}

bool ble_btn_learn_begin(uint32_t timeout_ms, const uint8_t *key)
{
    if (!s_on) {
        /* Auto-enable scan for learn. */
        ble_btn_set_on(true);
    }
    if (timeout_ms == 0) timeout_ms = BLE_BTN_LEARN_DEFAULT_MS;
    ble_radio_lock();
    s_learn_key_valid = key != NULL;
    if (key) memcpy(s_learn_key, key, BTHOME_KEY_LEN);
    s_learn = true;
    s_learn_deadline_ms = (uint32_t)(esp_timer_get_time() / 1000ULL) + timeout_ms;
    ble_radio_unlock();
    return true;
}

void ble_btn_learn_end(void)
{
    ble_radio_lock();
    s_learn = false;
    s_learn_key_valid = false;
    ble_radio_unlock();
}

bool ble_btn_allow(const char *mac_str)
{
    uint8_t addr[6];
    if (!parse_mac_str(mac_str, addr)) return false;
    return add_mac(addr, NULL, 0);
}

bool ble_btn_set_key(const char *mac_str, const uint8_t *key)
{
    uint8_t addr[6];
    if (!parse_mac_str(mac_str, addr)) return false;
    ble_radio_lock();
    int idx = find_mac(addr);
    bool ok = false;
    if (idx >= 0) {
        s_has_key[idx] = key != NULL;
        if (key) memcpy(s_keys[idx], key, BTHOME_KEY_LEN);
        /* A new key (or a remote with a fresh battery) starts its counter again. */
        s_ctr_valid[idx] = false;
        ok = save_nvs();
    }
    ble_radio_unlock();
    return ok;
}

int ble_btn_get_keyed(char out[][18], int max_out)
{
    ble_radio_lock();
    int n = 0;
    for (int i = 0; i < s_mac_count && n < max_out; i++) {
        if (s_has_key[i]) mac_to_str(s_macs[i], out[n++]);
    }
    ble_radio_unlock();
    return n;
}

bool ble_btn_forget(const char *mac_str)
{
    uint8_t addr[6];
    if (!parse_mac_str(mac_str, addr)) return false;
    ble_radio_lock();
    int idx = find_mac(addr);
    bool ok = false;
    if (idx >= 0) {
        for (int i = idx; i < s_mac_count - 1; i++) {
            memcpy(s_macs[i], s_macs[i + 1], 6);
            memcpy(s_keys[i], s_keys[i + 1], BTHOME_KEY_LEN);
            s_has_key[i] = s_has_key[i + 1];
            s_ctr[i] = s_ctr[i + 1];
            s_ctr_valid[i] = s_ctr_valid[i + 1];
        }
        s_mac_count--;
        ok = save_nvs();
    }
    ble_radio_unlock();
    return ok;
}

bool ble_btn_forget_all(void)
{
    ble_radio_lock();
    s_mac_count = 0;
    memset(s_has_key, 0, sizeof(s_has_key));
    memset(s_ctr_valid, 0, sizeof(s_ctr_valid));
    bool ok = save_nvs();
    ble_radio_unlock();
    return ok;
}

int ble_btn_last_bat(void)
{
    return s_last_bat;
}

int ble_btn_last_rssi(void)
{
    return s_last_rssi;
}

void ble_btn_last_mac(char out[18])
{
    if (!s_have_last_mac) {
        out[0] = '\0';
        return;
    }
    mac_to_str(s_last_mac, out);
}

void ble_btn_poll(uint32_t now_ms)
{
    if (s_learn && (int32_t)(now_ms - s_learn_deadline_ms) >= 0) {
        ble_btn_learn_end();
        protocol_send_ble_ack("learnEnd", false, "timeout");
        protocol_send_ble_status();
    }
    ble_phone_poll(now_ms);
}
