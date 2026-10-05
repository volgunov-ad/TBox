#include "ble_btn.h"

#include <stdio.h>
#include <string.h>

#include "esp_log.h"
#include "esp_timer.h"
#include "nvs.h"
#include "nvs_flash.h"

#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "host/ble_gap.h"
#include "services/gap/ble_svc_gap.h"

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
        char key[8];
        snprintf(key, sizeof(key), "m%d", i);
        if (i < s_mac_count) {
            nvs_set_blob(h, key, s_macs[i], 6);
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
        char key[8];
        snprintf(key, sizeof(key), "m%d", i);
        size_t len = 6;
        if (nvs_get_blob(h, key, s_macs[s_mac_count], &len) == ESP_OK && len == 6) {
            s_mac_count++;
        }
    }
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
    bool encrypted;
    bool have_pid;
    uint8_t pid;
    int battery; /* -1 unknown */
    int button_count;
    uint8_t buttons[4];
} bthome_parse_t;

static bool parse_bthome(const uint8_t *data, uint8_t len, bthome_parse_t *out)
{
    memset(out, 0, sizeof(*out));
    out->battery = -1;
    if (!data || len < 2) return false;
    uint8_t info = data[0];
    out->encrypted = (info & 0x01) != 0;
    if (out->encrypted) return false;
    uint8_t ver = (info >> 5) & 0x07;
    if (ver != 2) {
        /* Still try; some firmwares set version oddly. */
    }
    int i = 1;
    if (info & 0x02) {
        /* MAC included — 6 bytes */
        if (i + 6 > len) return false;
        i += 6;
    }
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

static bool add_mac_locked(const uint8_t *addr)
{
    if (find_mac(addr) >= 0) return true;
    if (s_mac_count >= BLE_BTN_MAX_MACS) return false;
    memcpy(s_macs[s_mac_count], addr, 6);
    s_mac_count++;
    return save_nvs();
}

static int start_scan(void);
static void stop_scan(void);

static void handle_bthome_adv(const uint8_t *addr, int8_t rssi,
                              const uint8_t *svc, uint8_t svc_len)
{
    bthome_parse_t parsed;
    if (!parse_bthome(svc, svc_len, &parsed)) return;
    if (parsed.encrypted) return;

    uint32_t now_ms = (uint32_t)(esp_timer_get_time() / 1000ULL);
    char mac_str[18];
    mac_to_str(addr, mac_str);

    if (parsed.battery >= 0) {
        s_last_bat = parsed.battery;
    }
    s_last_rssi = rssi;
    memcpy(s_last_mac, addr, 6);
    s_have_last_mac = true;

    const bool allowed = find_mac(addr) >= 0;
    const bool has_btn_event = parsed.button_count > 0;

    if (s_learn && has_btn_event && !allowed) {
        protocol_send_ble_seen(mac_str, rssi, now_ms);
        if (add_mac_locked(addr)) {
            ESP_LOGI(TAG, "learned %s", mac_str);
            s_learn = false;
            protocol_send_ble_ack("allow", true, NULL);
            protocol_send_ble_status();
            protocol_send_ble_ack("learnEnd", true, NULL);
        } else {
            protocol_send_ble_ack("allow", false, "full");
        }
    }

    if (find_mac(addr) < 0) return;
    if (dedup_hit(addr, parsed.pid, parsed.have_pid)) return;

    for (int b = 0; b < parsed.button_count; b++) {
        const char *act = btn_act_name(parsed.buttons[b]);
        if (!act) continue; /* None or unknown */
        protocol_send_ble_btn(mac_str, b + 1, act, s_last_bat, rssi, now_ms);
    }
}

static int gap_event(struct ble_gap_event *event, void *arg)
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
    case BLE_GAP_EVENT_DISC_COMPLETE:
        ESP_LOGI(TAG, "scan complete reason=%d", event->disc_complete.reason);
        s_scanning = false;
        if (s_on && s_nimble_ready) {
            start_scan();
        }
        return 0;
    default:
        return 0;
    }
}

static int start_scan(void)
{
    if (!s_nimble_ready || s_scanning) return 0;
    struct ble_gap_disc_params params = {
        .itvl = 0,
        .window = 0,
        .filter_policy = 0,
        .limited = 0,
        .passive = 1,
        .filter_duplicates = 0,
    };
    int rc = ble_gap_disc(BLE_OWN_ADDR_PUBLIC, BLE_HS_FOREVER, &params, gap_event, NULL);
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

static void on_sync(void)
{
    s_nimble_ready = true;
    ESP_LOGI(TAG, "NimBLE sync");
    if (s_on) {
        start_scan();
    }
}

static void on_reset(int reason)
{
    ESP_LOGW(TAG, "NimBLE reset reason=%d", reason);
    s_nimble_ready = false;
    s_scanning = false;
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
    load_nvs();
    s_inited = true;
    ESP_LOGI(TAG, "init on=%d macs=%d", (int)s_on, s_mac_count);

    esp_err_t err = nimble_port_init();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "nimble_port_init: %s", esp_err_to_name(err));
        return;
    }
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
    return s_mac_count;
}

int ble_btn_get_macs(char out[][18], int max_out)
{
    int n = s_mac_count;
    if (n > max_out) n = max_out;
    for (int i = 0; i < n; i++) {
        mac_to_str(s_macs[i], out[i]);
    }
    return n;
}

bool ble_btn_set_on(bool on)
{
    s_on = on;
    save_nvs();
    if (on) {
        if (s_nimble_ready) start_scan();
    } else {
        stop_scan();
        s_learn = false;
    }
    return true;
}

bool ble_btn_learn_begin(uint32_t timeout_ms)
{
    if (!s_on) {
        /* Auto-enable scan for learn. */
        ble_btn_set_on(true);
    }
    if (timeout_ms == 0) timeout_ms = BLE_BTN_LEARN_DEFAULT_MS;
    s_learn = true;
    s_learn_deadline_ms = (uint32_t)(esp_timer_get_time() / 1000ULL) + timeout_ms;
    return true;
}

void ble_btn_learn_end(void)
{
    s_learn = false;
}

bool ble_btn_allow(const char *mac_str)
{
    uint8_t addr[6];
    if (!parse_mac_str(mac_str, addr)) return false;
    if (!add_mac_locked(addr)) return false;
    return true;
}

bool ble_btn_forget(const char *mac_str)
{
    uint8_t addr[6];
    if (!parse_mac_str(mac_str, addr)) return false;
    int idx = find_mac(addr);
    if (idx < 0) return false;
    for (int i = idx; i < s_mac_count - 1; i++) {
        memcpy(s_macs[i], s_macs[i + 1], 6);
    }
    s_mac_count--;
    return save_nvs();
}

bool ble_btn_forget_all(void)
{
    s_mac_count = 0;
    return save_nvs();
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
        s_learn = false;
        protocol_send_ble_ack("learnEnd", false, "timeout");
        protocol_send_ble_status();
    }
}
