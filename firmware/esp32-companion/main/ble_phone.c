#include "ble_phone.h"

#include <stdio.h>
#include <string.h>

#include "esp_log.h"
#include "esp_timer.h"
#include "nvs.h"
#include "nvs_flash.h"

#include "mbedtls/aes.h"
#include "mbedtls/md.h"

#include "host/ble_gap.h"
#include "host/ble_gatt.h"
#include "host/ble_hs.h"
#include "host/ble_hs_adv.h"
#include "host/ble_uuid.h"
#include "host/ble_hs_mbuf.h"
#include "os/os_mbuf.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

#include "ble_btn.h"
#include "protocol.h"

static const char *TAG = "ble_phone";

#define NVS_NS "ble_phone"
#define PHONE_REC_LEN 51
#define PHONE_NAME_MAX 26
#define BODY_LEN 11
#define SEALED_LEN 24
#define SNAP_FRESH_MS 2000u
#define SNAP_WAIT_MS 1500u
#define CMD_OUT_MAX 16
#define TX_MAX 16
/* A connected stranger that never sends a valid packet is dropped. */
#define LINK_IDLE_MS 20000u
#define IDLE_BLOCK_MS 60000u
#define BLE_ERR_REM_USER_CONN_TERM 0x13
/*
 * NVS is 24 KB. A counter write per packet (one refresh every ~2 s per open
 * phone screen) would cycle its pages quickly. Counters are flushed at most this
 * often per phone; after a power cut, packets from the last window may replay once.
 */
#define COUNTER_SAVE_MS 10000u

#define SNAP_LEFT  (1u << 0)
#define SNAP_RIGHT (1u << 1)
#define SNAP_FAN   (1u << 2)
#define SNAP_MODE  (1u << 3)
#define SNAP_AUTO  (1u << 4)
#define SNAP_BLOW  (1u << 5)
#define SNAP_SYNC  (1u << 6)
#define SNAP_S0    (1u << 7)
#define SNAP_S1    (1u << 8)
#define SNAP_S2    (1u << 9)
#define SNAP_S3    (1u << 10)
#define SNAP_VOL   (1u << 11)

typedef struct {
    uint8_t id[4];
    uint8_t key[16];
    uint32_t counter;
    uint8_t name_len;
    char name[PHONE_NAME_MAX];
} phone_rec_t;

typedef struct {
    int index;
    uint32_t counter;
} refresh_pending_t;

typedef struct {
    uint8_t id[4];
    int op;
    int seat;
    int arg;
} cmd_out_t;

static phone_rec_t s_phones[BLE_DEVICE_MAX];
static int s_count;
static uint32_t s_counter_dirty;
static uint32_t s_counter_saved_ms[BLE_DEVICE_MAX];

static bool s_learn;
static uint32_t s_learn_deadline_ms;
static bool s_pending_pair;
static phone_rec_t s_pending;
/* Denied during this learn session; the phone may write the packet again. */
static bool s_denied_valid;
static uint8_t s_denied_id[4];

static bool s_cache_valid;
static uint32_t s_cache_ms;
static int s_cache_gen;
static uint16_t s_cache_mask;
static int s_cache_vals[12];

static refresh_pending_t s_wait[BLE_DEVICE_MAX];
static int s_wait_n;
static bool s_snap_inflight;
static uint32_t s_snap_req_ms;

static uint8_t s_tx[TX_MAX][SEALED_LEN];
static int s_tx_n;

static uint16_t s_conn = BLE_HS_CONN_HANDLE_NONE;
static uint16_t s_snap_handle;
static bool s_notify;
static uint16_t s_mtu = 23;
static uint32_t s_link_ms;
static bool s_link_seen;
static ble_addr_t s_link_addr;
/* A dropped stranger reconnects at once and would hold the only link slot. */
static bool s_idle_valid;
static ble_addr_t s_idle_addr;
static uint32_t s_idle_until_ms;

/* USB messages are sent from ble_phone_poll (main loop), never from the NimBLE host task. */
static bool s_out_pair;
static uint8_t s_out_pair_id[4];
static char s_out_pair_name[PHONE_NAME_MAX + 1];
static cmd_out_t s_out_cmd[CMD_OUT_MAX];
static int s_out_cmd_n;
static bool s_out_snap_req;
static bool s_out_status;

static uint32_t now_ms(void)
{
    return (uint32_t)(esp_timer_get_time() / 1000ULL);
}

static int hex_nibble(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

static bool parse_id(const char *hex, uint8_t out[4])
{
    if (!hex) return false;
    for (int i = 0; i < 4; i++) {
        int hi = hex_nibble(hex[i * 2]);
        if (hi < 0) return false;
        int lo = hex_nibble(hex[i * 2 + 1]);
        if (lo < 0) return false;
        out[i] = (uint8_t)((hi << 4) | lo);
    }
    return hex[8] == '\0';
}

static void id_to_hex(const uint8_t id[4], char out[9])
{
    snprintf(out, 9, "%02x%02x%02x%02x", id[0], id[1], id[2], id[3]);
}

static int find_phone(const uint8_t id[4])
{
    for (int i = 0; i < s_count; i++) {
        if (memcmp(s_phones[i].id, id, 4) == 0) return i;
    }
    return -1;
}

/* The name arrives over the air; control bytes would break the JSON line protocol. */
static void sanitize_name(char *name, uint8_t len)
{
    for (uint8_t i = 0; i < len; i++) {
        unsigned char c = (unsigned char)name[i];
        if (c < 0x20 || c == 0x7F) name[i] = '?';
    }
}

#define COUNTER_KEY_LEN 12

static void counter_key(int index, char key[COUNTER_KEY_LEN])
{
    snprintf(key, COUNTER_KEY_LEN, "c%d", index);
}

static void rec_to_blob(const phone_rec_t *rec, uint8_t blob[PHONE_REC_LEN])
{
    memset(blob, 0, PHONE_REC_LEN);
    memcpy(blob, rec->id, 4);
    memcpy(blob + 4, rec->key, 16);
    blob[20] = (uint8_t)(rec->counter);
    blob[21] = (uint8_t)(rec->counter >> 8);
    blob[22] = (uint8_t)(rec->counter >> 16);
    blob[23] = (uint8_t)(rec->counter >> 24);
    blob[24] = rec->name_len;
    if (rec->name_len > 0) {
        memcpy(blob + 25, rec->name, rec->name_len);
    }
}

static bool blob_to_rec(const uint8_t blob[PHONE_REC_LEN], phone_rec_t *rec)
{
    memset(rec, 0, sizeof(*rec));
    memcpy(rec->id, blob, 4);
    memcpy(rec->key, blob + 4, 16);
    rec->counter = (uint32_t)blob[20] | ((uint32_t)blob[21] << 8) |
                   ((uint32_t)blob[22] << 16) | ((uint32_t)blob[23] << 24);
    rec->name_len = blob[24];
    if (rec->name_len > PHONE_NAME_MAX) return false;
    if (rec->name_len > 0) memcpy(rec->name, blob + 25, rec->name_len);
    sanitize_name(rec->name, rec->name_len);
    return true;
}

static bool save_nvs(void)
{
    nvs_handle_t h;
    if (nvs_open(NVS_NS, NVS_READWRITE, &h) != ESP_OK) return false;
    nvs_set_u8(h, "n", (uint8_t)s_count);
    for (int i = 0; i < BLE_DEVICE_MAX; i++) {
        char key[8];
        char ckey[COUNTER_KEY_LEN];
        snprintf(key, sizeof(key), "p%d", i);
        counter_key(i, ckey);
        if (i < s_count) {
            uint8_t blob[PHONE_REC_LEN];
            rec_to_blob(&s_phones[i], blob);
            nvs_set_blob(h, key, blob, PHONE_REC_LEN);
            nvs_set_u32(h, ckey, s_phones[i].counter);
        } else {
            nvs_erase_key(h, key);
            nvs_erase_key(h, ckey);
        }
    }
    esp_err_t err = nvs_commit(h);
    nvs_close(h);
    if (err == ESP_OK) {
        uint32_t t = now_ms();
        s_counter_dirty = 0;
        for (int i = 0; i < BLE_DEVICE_MAX; i++) s_counter_saved_ms[i] = t;
    }
    return err == ESP_OK;
}

static void save_counter(int index)
{
    nvs_handle_t h;
    if (nvs_open(NVS_NS, NVS_READWRITE, &h) != ESP_OK) return;
    char ckey[COUNTER_KEY_LEN];
    counter_key(index, ckey);
    nvs_set_u32(h, ckey, s_phones[index].counter);
    nvs_commit(h);
    nvs_close(h);
}

static void load_nvs(void)
{
    s_count = 0;
    nvs_handle_t h;
    if (nvs_open(NVS_NS, NVS_READONLY, &h) != ESP_OK) return;
    uint8_t n = 0;
    nvs_get_u8(h, "n", &n);
    if (n > BLE_DEVICE_MAX) n = BLE_DEVICE_MAX;
    for (int i = 0; i < n; i++) {
        char key[8];
        char ckey[COUNTER_KEY_LEN];
        snprintf(key, sizeof(key), "p%d", i);
        counter_key(i, ckey);
        uint8_t blob[PHONE_REC_LEN];
        size_t len = PHONE_REC_LEN;
        if (nvs_get_blob(h, key, blob, &len) != ESP_OK || len != PHONE_REC_LEN ||
            !blob_to_rec(blob, &s_phones[s_count])) {
            continue;
        }
        uint32_t counter = 0;
        if (nvs_get_u32(h, ckey, &counter) == ESP_OK && counter > s_phones[s_count].counter) {
            s_phones[s_count].counter = counter;
        }
        s_count++;
    }
    nvs_close(h);
}

static void aes_ecb(const uint8_t key[16], const uint8_t in[16], uint8_t out[16])
{
    mbedtls_aes_context ctx;
    mbedtls_aes_init(&ctx);
    mbedtls_aes_setkey_enc(&ctx, key, 128);
    mbedtls_aes_crypt_ecb(&ctx, MBEDTLS_AES_ENCRYPT, in, out);
    mbedtls_aes_free(&ctx);
}

static void aes_ctr_xor(const uint8_t key[16], const uint8_t nonce[16],
                        const uint8_t *in, uint8_t *out, size_t len)
{
    mbedtls_aes_context ctx;
    uint8_t stream[16];
    uint8_t nc[16];
    size_t off = 0;
    memcpy(nc, nonce, 16);
    memset(stream, 0, sizeof(stream));
    mbedtls_aes_init(&ctx);
    mbedtls_aes_setkey_enc(&ctx, key, 128);
    mbedtls_aes_crypt_ctr(&ctx, len, &off, nc, stream, in, out);
    mbedtls_aes_free(&ctx);
}

static void put_counter(uint8_t dst[4], uint32_t counter)
{
    dst[0] = (uint8_t)counter;
    dst[1] = (uint8_t)(counter >> 8);
    dst[2] = (uint8_t)(counter >> 16);
    dst[3] = (uint8_t)(counter >> 24);
}

static uint32_t get_counter(const uint8_t src[4])
{
    return (uint32_t)src[0] | ((uint32_t)src[1] << 8) |
           ((uint32_t)src[2] << 16) | ((uint32_t)src[3] << 24);
}

static void make_mask(const uint8_t key[16], uint8_t type, const uint8_t id[4], uint8_t mask[4])
{
    uint8_t block[16];
    uint8_t out[16];
    memset(block, 0, sizeof(block));
    memcpy(block, id, 4);
    block[4] = type;
    block[5] = 0xA5;
    aes_ecb(key, block, out);
    memcpy(mask, out, 4);
}

static void make_nonce(uint8_t type, const uint8_t id[4], uint32_t counter, uint8_t nonce[16])
{
    memset(nonce, 0, 16);
    memcpy(nonce, id, 4);
    nonce[4] = type;
    put_counter(nonce + 5, counter);
    nonce[9] = 0xC7;
}

static void make_tag(const uint8_t key[16], uint8_t type, const uint8_t id[4],
                     uint32_t counter, const uint8_t body[BODY_LEN], uint8_t tag[4])
{
    uint8_t msg[1 + 4 + 4 + BODY_LEN];
    uint8_t full[32];
    msg[0] = type;
    memcpy(msg + 1, id, 4);
    put_counter(msg + 5, counter);
    memcpy(msg + 9, body, BODY_LEN);
    mbedtls_md_hmac(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256),
                    key, 16, msg, sizeof(msg), full);
    memcpy(tag, full, 4);
}

static void seal(const uint8_t key[16], uint8_t type, const uint8_t id[4],
                 uint32_t counter, const uint8_t body[BODY_LEN], uint8_t out[SEALED_LEN])
{
    uint8_t mask[4];
    uint8_t nonce[16];
    uint8_t tag[4];
    uint8_t counter_le[4];
    make_mask(key, type, id, mask);
    put_counter(counter_le, counter);
    out[0] = type;
    memcpy(out + 1, id, 4);
    for (int i = 0; i < 4; i++) {
        out[5 + i] = (uint8_t)(counter_le[i] ^ mask[i]);
    }
    make_nonce(type, id, counter, nonce);
    aes_ctr_xor(key, nonce, body, out + 9, BODY_LEN);
    make_tag(key, type, id, counter, body, tag);
    memcpy(out + 9 + BODY_LEN, tag, 4);
}

static bool unseal(const uint8_t key[16], const uint8_t *payload, uint8_t len,
                   uint8_t *type_out, uint32_t *counter_out, uint8_t body[BODY_LEN])
{
    if (len != SEALED_LEN) return false;
    uint8_t type = payload[0];
    if (type != 2 && type != 3 && type != 4) return false;
    const uint8_t *id = payload + 1;
    uint8_t mask[4];
    uint8_t counter_le[4];
    uint8_t nonce[16];
    uint8_t tag[4];
    make_mask(key, type, id, mask);
    for (int i = 0; i < 4; i++) {
        counter_le[i] = (uint8_t)(payload[5 + i] ^ mask[i]);
    }
    uint32_t counter = get_counter(counter_le);
    make_nonce(type, id, counter, nonce);
    aes_ctr_xor(key, nonce, payload + 9, body, BODY_LEN);
    make_tag(key, type, id, counter, body, tag);
    if (memcmp(tag, payload + 9 + BODY_LEN, 4) != 0) return false;
    *type_out = type;
    *counter_out = counter;
    return true;
}

static void tx_push(const uint8_t pkt[SEALED_LEN])
{
    if (s_tx_n >= TX_MAX) {
        memmove(s_tx[0], s_tx[1], (size_t)(TX_MAX - 1) * SEALED_LEN);
        s_tx_n = TX_MAX - 1;
    }
    memcpy(s_tx[s_tx_n], pkt, SEALED_LEN);
    s_tx_n++;
}

static void enqueue_snap(int phone_index, uint32_t counter)
{
    if (phone_index < 0 || phone_index >= s_count || !s_cache_valid) return;
    const phone_rec_t *phone = &s_phones[phone_index];
    for (int page = 0; page < 4; page++) {
        uint8_t body[BODY_LEN];
        uint8_t pkt[SEALED_LEN];
        memset(body, 0, sizeof(body));
        body[0] = (uint8_t)page;
        body[1] = (uint8_t)(s_cache_gen & 0xFF);
        if (page == 0) {
            int left = (s_cache_mask & SNAP_LEFT) ? s_cache_vals[0] : 0x7FFF;
            int right = (s_cache_mask & SNAP_RIGHT) ? s_cache_vals[1] : 0x7FFF;
            int fan = (s_cache_mask & SNAP_FAN) ? s_cache_vals[2] : 0xFF;
            body[2] = (uint8_t)left;
            body[3] = (uint8_t)(left >> 8);
            body[4] = (uint8_t)right;
            body[5] = (uint8_t)(right >> 8);
            body[6] = (uint8_t)fan;
        } else if (page == 1) {
            body[2] = (uint8_t)((s_cache_mask & SNAP_MODE) ? s_cache_vals[3] : 0xFF);
            body[3] = (uint8_t)((s_cache_mask & SNAP_AUTO) ? s_cache_vals[4] : 0xFF);
            body[4] = (uint8_t)((s_cache_mask & SNAP_BLOW) ? s_cache_vals[5] : 0xFF);
            body[5] = (uint8_t)((s_cache_mask & SNAP_SYNC) ? s_cache_vals[6] : 0xFF);
        } else if (page == 2) {
            for (int s = 0; s < 4; s++) {
                uint16_t bit = (uint16_t)(SNAP_S0 << s);
                body[2 + s] = (uint8_t)((s_cache_mask & bit) ? s_cache_vals[7 + s] : 0xFF);
            }
        } else {
            body[2] = (uint8_t)((s_cache_mask & SNAP_VOL) ? s_cache_vals[11] : 0xFF);
        }
        seal(phone->key, 4, phone->id, counter, body, pkt);
        tx_push(pkt);
    }
}

static int wait_find(int phone_index)
{
    for (int i = 0; i < s_wait_n; i++) {
        if (s_wait[i].index == phone_index) return i;
    }
    return -1;
}

static void request_snap_if_needed(uint32_t t)
{
    if (s_wait_n == 0) return;
    if (s_cache_valid && (uint32_t)(t - s_cache_ms) < SNAP_FRESH_MS) {
        for (int i = 0; i < s_wait_n; i++) {
            enqueue_snap(s_wait[i].index, s_wait[i].counter);
        }
        s_wait_n = 0;
        s_snap_inflight = false;
        return;
    }
    if (!s_snap_inflight) {
        s_out_snap_req = true;
        s_snap_inflight = true;
        s_snap_req_ms = t;
    }
}

static void note_refresh(int phone_index, uint32_t counter, uint32_t t)
{
    int slot = wait_find(phone_index);
    if (slot < 0 && s_wait_n < BLE_DEVICE_MAX) {
        slot = s_wait_n++;
    }
    if (slot < 0) return;
    s_wait[slot].index = phone_index;
    s_wait[slot].counter = counter;
    request_snap_if_needed(t);
}

/** Cleartext write: type, id[4], key[16], name. Name is at most PHONE_NAME_MAX bytes. */
static void on_pair_packet(const uint8_t *payload, uint8_t len)
{
    if (!s_learn || len < 21) return;
    const uint8_t *id = payload + 1;
    const uint8_t *key = payload + 5;
    if (s_denied_valid && memcmp(s_denied_id, id, 4) == 0) return;
    /* One dialog per phone and key, even if the app writes the packet twice. */
    if (s_pending_pair && memcmp(s_pending.id, id, 4) == 0 &&
        memcmp(s_pending.key, key, 16) == 0) {
        s_link_seen = true;
        return;
    }
    int existing = find_phone(id);
    if (existing >= 0 && memcmp(s_phones[existing].key, key, 16) == 0) {
        s_link_seen = true;
        return;
    }

    memset(&s_pending, 0, sizeof(s_pending));
    memcpy(s_pending.id, id, 4);
    memcpy(s_pending.key, key, 16);
    int name_len = (int)len - 21;
    if (name_len > PHONE_NAME_MAX) name_len = PHONE_NAME_MAX;
    if (name_len > 0) memcpy(s_pending.name, payload + 21, (size_t)name_len);
    s_pending.name_len = (uint8_t)name_len;
    sanitize_name(s_pending.name, s_pending.name_len);
    s_pending_pair = true;
    s_link_seen = true;

    memcpy(s_out_pair_id, s_pending.id, 4);
    memcpy(s_out_pair_name, s_pending.name, s_pending.name_len);
    s_out_pair_name[s_pending.name_len] = '\0';
    s_out_pair = true;
}

static void on_sealed(const uint8_t *payload, uint8_t len)
{
    const uint8_t *id = payload + 1;
    int index = find_phone(id);
    if (index < 0) return;
    uint8_t type = 0;
    uint32_t counter = 0;
    uint8_t body[BODY_LEN];
    if (!unseal(s_phones[index].key, payload, len, &type, &counter, body)) return;
    if (counter <= s_phones[index].counter) return;
    s_link_seen = true;
    s_phones[index].counter = counter;
    s_counter_dirty |= (1u << index);
    if (type == 2) {
        if (s_out_cmd_n >= CMD_OUT_MAX) return;
        cmd_out_t *cmd = &s_out_cmd[s_out_cmd_n++];
        memcpy(cmd->id, id, 4);
        cmd->op = body[0];
        cmd->seat = body[1];
        cmd->arg = (int)(int16_t)((uint16_t)body[2] | ((uint16_t)body[3] << 8));
        /* The cached state predates this command. */
        s_cache_valid = false;
    } else if (type == 3) {
        note_refresh(index, counter, now_ms());
    }
}

void ble_phone_init(void)
{
    ble_radio_lock();
    load_nvs();
    ESP_LOGI(TAG, "phones=%d", s_count);
    ble_radio_unlock();
}

int ble_phone_count(void)
{
    ble_radio_lock();
    int n = s_count;
    ble_radio_unlock();
    return n;
}

bool ble_phone_is_learn(void)
{
    return s_learn;
}

bool ble_phone_learn_begin(uint32_t timeout_ms)
{
    if (!ble_btn_is_on()) {
        ble_btn_set_on(true);
    }
    ble_radio_lock();
    if (timeout_ms == 0) timeout_ms = 90000u;
    if (timeout_ms < 60000u) timeout_ms = 60000u;
    if (timeout_ms > 120000u) timeout_ms = 120000u;
    s_learn = true;
    s_pending_pair = false;
    s_denied_valid = false;
    s_learn_deadline_ms = now_ms() + timeout_ms;
    ble_radio_unlock();
    return true;
}

void ble_phone_learn_end(void)
{
    ble_radio_lock();
    s_learn = false;
    s_pending_pair = false;
    s_out_pair = false;
    ble_radio_unlock();
}

bool ble_phone_allow(const char *id_hex)
{
    uint8_t id[4];
    bool ok = false;
    if (!parse_id(id_hex, id)) return false;
    ble_radio_lock();
    if (s_pending_pair && memcmp(s_pending.id, id, 4) == 0) {
        int existing = find_phone(id);
        if (existing >= 0 || s_count + ble_btn_mac_count() < BLE_DEVICE_MAX) {
            phone_rec_t rec = s_pending;
            rec.counter = 0;
            if (existing >= 0) {
                /* A new key starts a new counter; the same key keeps replay protection. */
                if (memcmp(s_phones[existing].key, rec.key, 16) == 0) {
                    rec.counter = s_phones[existing].counter;
                }
                s_phones[existing] = rec;
            } else {
                s_phones[s_count++] = rec;
            }
            s_pending_pair = false;
            ok = save_nvs();
            if (ok) {
                s_learn = false;
                s_out_pair = false;
            }
        }
    }
    ble_radio_unlock();
    return ok;
}

bool ble_phone_slots_full(void)
{
    ble_radio_lock();
    bool full = s_count + ble_btn_mac_count() >= BLE_DEVICE_MAX;
    ble_radio_unlock();
    return full;
}

void ble_phone_deny(const char *id_hex)
{
    uint8_t id[4];
    if (!parse_id(id_hex, id)) return;
    ble_radio_lock();
    if (s_pending_pair && memcmp(s_pending.id, id, 4) == 0) {
        s_pending_pair = false;
    }
    memcpy(s_denied_id, id, 4);
    s_denied_valid = true;
    ble_radio_unlock();
}

bool ble_phone_forget(const char *id_hex)
{
    uint8_t id[4];
    if (!parse_id(id_hex, id)) return false;
    ble_radio_lock();
    int index = find_phone(id);
    bool ok = false;
    if (index >= 0) {
        for (int i = index; i < s_count - 1; i++) {
            s_phones[i] = s_phones[i + 1];
        }
        s_count--;
        for (int i = 0; i < s_wait_n; ) {
            if (s_wait[i].index == index) {
                for (int j = i; j < s_wait_n - 1; j++) s_wait[j] = s_wait[j + 1];
                s_wait_n--;
                continue;
            }
            if (s_wait[i].index > index) s_wait[i].index--;
            i++;
        }
        ok = save_nvs();
    }
    ble_radio_unlock();
    return ok;
}

static void on_incoming(const uint8_t *payload, uint8_t len)
{
    if (!payload || len < 1) return;
    ble_radio_lock();
    uint8_t type = payload[0];
    if (type == 1) {
        on_pair_packet(payload, len);
    } else if ((type == 2 || type == 3) && len == SEALED_LEN) {
        on_sealed(payload, len);
    }
    ble_radio_unlock();
}

void ble_phone_set_snapshot(int gen, uint16_t mask, const int vals[12])
{
    ble_radio_lock();
    s_cache_gen = gen;
    s_cache_mask = mask;
    memcpy(s_cache_vals, vals, sizeof(s_cache_vals));
    s_cache_valid = true;
    s_cache_ms = now_ms();
    s_snap_inflight = false;
    for (int i = 0; i < s_wait_n; i++) {
        enqueue_snap(s_wait[i].index, s_wait[i].counter);
    }
    s_wait_n = 0;
    ble_radio_unlock();
}

void ble_phone_poll(uint32_t t)
{
    bool send_pair = false;
    char pair_hex[9];
    char pair_name[PHONE_NAME_MAX + 1];
    cmd_out_t cmds[CMD_OUT_MAX];
    int cmd_n = 0;
    bool send_snap_req = false;
    bool send_status = false;
    uint8_t note[SEALED_LEN];
    bool send_note = false;
    uint16_t note_conn = BLE_HS_CONN_HANDLE_NONE;
    uint16_t note_handle = 0;
    uint16_t drop_conn = BLE_HS_CONN_HANDLE_NONE;

    ble_radio_lock();
    if (s_learn && (int32_t)(t - s_learn_deadline_ms) >= 0) {
        s_learn = false;
        s_pending_pair = false;
        s_out_pair = false;
        s_out_status = true;
    }
    if (s_snap_inflight && (uint32_t)(t - s_snap_req_ms) > SNAP_WAIT_MS) {
        s_snap_inflight = false;
    }
    if (s_wait_n > 0 && !s_snap_inflight) {
        request_snap_if_needed(t);
    }
    if (s_counter_dirty) {
        for (int i = 0; i < s_count; i++) {
            if ((s_counter_dirty & (1u << i)) &&
                (uint32_t)(t - s_counter_saved_ms[i]) >= COUNTER_SAVE_MS) {
                save_counter(i);
                s_counter_dirty &= ~(1u << i);
                s_counter_saved_ms[i] = t;
                break;
            }
        }
    }
    if (s_out_pair) {
        send_pair = true;
        id_to_hex(s_out_pair_id, pair_hex);
        memcpy(pair_name, s_out_pair_name, sizeof(pair_name));
        s_out_pair = false;
    }
    cmd_n = s_out_cmd_n;
    if (cmd_n > 0) {
        memcpy(cmds, s_out_cmd, sizeof(cmd_out_t) * (size_t)cmd_n);
        s_out_cmd_n = 0;
    }
    send_snap_req = s_out_snap_req;
    s_out_snap_req = false;
    send_status = s_out_status;
    s_out_status = false;
    if (s_conn != BLE_HS_CONN_HANDLE_NONE && !s_link_seen &&
        (uint32_t)(t - s_link_ms) > LINK_IDLE_MS) {
        drop_conn = s_conn;
        s_link_seen = true;
        s_idle_valid = true;
        s_idle_addr = s_link_addr;
        s_idle_until_ms = t + IDLE_BLOCK_MS;
    }
    if (s_tx_n > 0 && s_notify && s_conn != BLE_HS_CONN_HANDLE_NONE &&
        s_mtu >= (uint16_t)(SEALED_LEN + 3)) {
        memcpy(note, s_tx[0], SEALED_LEN);
        if (s_tx_n > 1) {
            memmove(s_tx[0], s_tx[1], (size_t)(s_tx_n - 1) * SEALED_LEN);
        }
        s_tx_n--;
        send_note = true;
        note_conn = s_conn;
        note_handle = s_snap_handle;
    }
    ble_radio_unlock();

    if (send_pair) {
        protocol_send_phone_pair(pair_hex, pair_name);
    }
    for (int i = 0; i < cmd_n; i++) {
        char hex[9];
        id_to_hex(cmds[i].id, hex);
        protocol_send_phone_cmd(hex, cmds[i].op, cmds[i].seat, cmds[i].arg);
    }
    if (send_snap_req) {
        protocol_send_phone_snap_req();
    }
    if (send_status) {
        protocol_send_ble_status();
    }
    if (send_note) {
        struct os_mbuf *om = ble_hs_mbuf_from_flat(note, SEALED_LEN);
        if (om) {
            int rc = ble_gatts_notify_custom(note_conn, note_handle, om);
            if (rc != 0) {
                ESP_LOGW(TAG, "notify rc=%d", rc);
            }
        }
    }
    if (drop_conn != BLE_HS_CONN_HANDLE_NONE) {
        ble_gap_terminate(drop_conn, BLE_ERR_REM_USER_CONN_TERM);
    }
}

int ble_phone_write_json(char *out, size_t cap)
{
    if (!out || cap < 3) return 0;
    ble_radio_lock();
    size_t pos = 0;
    out[pos++] = '[';
    for (int i = 0; i < s_count; i++) {
        /* id + name with every byte escaped fits: 27 + 2 * 26 + 2. */
        char entry[96];
        char hex[9];
        size_t e = 0;
        id_to_hex(s_phones[i].id, hex);
        e += (size_t)snprintf(entry, sizeof(entry), "{\"id\":\"%s\",\"name\":\"", hex);
        for (uint8_t n = 0; n < s_phones[i].name_len; n++) {
            char c = s_phones[i].name[n];
            if (c == '"' || c == '\\') entry[e++] = '\\';
            entry[e++] = c;
        }
        entry[e++] = '"';
        entry[e++] = '}';
        size_t need = e + (i > 0 ? 1 : 0);
        /* Keep room for ']' and '\0'; drop whole entries so the array stays valid JSON. */
        if (pos + need + 2 > cap) break;
        if (i > 0) out[pos++] = ',';
        memcpy(out + pos, entry, e);
        pos += e;
    }
    out[pos++] = ']';
    out[pos] = '\0';
    ble_radio_unlock();
    return (int)pos;
}

static int inbox_access(uint16_t conn_handle, uint16_t attr_handle,
                        struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    (void)conn_handle;
    (void)attr_handle;
    (void)arg;
    if (ctxt->op != BLE_GATT_ACCESS_OP_WRITE_CHR) return BLE_ATT_ERR_UNLIKELY;
    uint16_t len = OS_MBUF_PKTLEN(ctxt->om);
    if (len == 0 || len > 64) return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    uint8_t buf[64];
    uint16_t copied = 0;
    if (ble_hs_mbuf_to_flat(ctxt->om, buf, sizeof(buf), &copied) != 0) {
        return BLE_ATT_ERR_UNLIKELY;
    }
    on_incoming(buf, (uint8_t)copied);
    return 0;
}

static int snap_access(uint16_t conn_handle, uint16_t attr_handle,
                       struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    (void)conn_handle;
    (void)attr_handle;
    (void)ctxt;
    (void)arg;
    return 0;
}

static const struct ble_gatt_svc_def s_svcs[] = {
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = BLE_UUID16_DECLARE(0x7B0E),
        .characteristics = (struct ble_gatt_chr_def[]) {
            {
                .uuid = BLE_UUID16_DECLARE(0x7B0F),
                .access_cb = inbox_access,
                .flags = BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_WRITE_NO_RSP,
            },
            {
                .uuid = BLE_UUID16_DECLARE(0x7B10),
                .access_cb = snap_access,
                .val_handle = &s_snap_handle,
                .flags = BLE_GATT_CHR_F_NOTIFY,
            },
            { 0 },
        },
    },
    { 0 },
};

static ble_uuid16_t s_adv_uuid = BLE_UUID16_INIT(0x7B0E);

void ble_phone_gatts_register(void)
{
    ble_svc_gap_init();
    ble_svc_gatt_init();
    int rc = ble_gatts_count_cfg(s_svcs);
    if (rc != 0) {
        ESP_LOGE(TAG, "gatts count rc=%d", rc);
        return;
    }
    rc = ble_gatts_add_svcs(s_svcs);
    if (rc != 0) {
        ESP_LOGE(TAG, "gatts add rc=%d", rc);
        return;
    }
    ble_svc_gap_device_name_set("TBox");
}

static int start_adv_locked(void)
{
    if (!ble_btn_is_on() || s_conn != BLE_HS_CONN_HANDLE_NONE || ble_gap_adv_active()) return 0;
    struct ble_hs_adv_fields fields;
    struct ble_gap_adv_params params;
    memset(&fields, 0, sizeof(fields));
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    fields.uuids16 = &s_adv_uuid;
    fields.num_uuids16 = 1;
    fields.uuids16_is_complete = 1;
    fields.name = (uint8_t *)"TBox";
    fields.name_len = 4;
    fields.name_is_complete = 1;
    int rc = ble_gap_adv_set_fields(&fields);
    if (rc != 0) return rc;
    memset(&params, 0, sizeof(params));
    params.conn_mode = BLE_GAP_CONN_MODE_UND;
    params.disc_mode = BLE_GAP_DISC_MODE_GEN;
    /* 100–200 ms, so the Shelly scan still gets airtime. */
    params.itvl_min = 0xA0;
    params.itvl_max = 0x140;
    return ble_gap_adv_start(BLE_OWN_ADDR_PUBLIC, NULL, BLE_HS_FOREVER, &params,
                             ble_btn_gap_event, NULL);
}

void ble_phone_start_adv(void)
{
    ble_radio_lock();
    int rc = start_adv_locked();
    ble_radio_unlock();
    if (rc != 0 && rc != BLE_HS_EALREADY) {
        /* Advertising and a running scan do not always start together. */
        ble_btn_suspend_scan();
        ble_radio_lock();
        rc = start_adv_locked();
        ble_radio_unlock();
        ble_btn_kick_scan();
    }
    if (rc != 0 && rc != BLE_HS_EALREADY) {
        ESP_LOGW(TAG, "adv start rc=%d", rc);
    }
}

void ble_phone_stop_link(void)
{
    uint16_t conn;
    ble_radio_lock();
    conn = s_conn;
    s_conn = BLE_HS_CONN_HANDLE_NONE;
    s_notify = false;
    s_tx_n = 0;
    s_link_seen = false;
    s_mtu = 23;
    ble_radio_unlock();
    if (ble_gap_adv_active()) ble_gap_adv_stop();
    if (conn != BLE_HS_CONN_HANDLE_NONE) {
        ble_gap_terminate(conn, BLE_ERR_REM_USER_CONN_TERM);
    }
}

void ble_phone_on_gap(struct ble_gap_event *event)
{
    switch (event->type) {
    case BLE_GAP_EVENT_CONNECT:
        if (event->connect.status != 0) {
            ESP_LOGW(TAG, "connect status=%d", event->connect.status);
            ble_phone_start_adv();
            break;
        }
        {
            struct ble_gap_conn_desc desc;
            bool blocked = false;
            uint32_t t = now_ms();
            memset(&desc, 0, sizeof(desc));
            ble_gap_conn_find(event->connect.conn_handle, &desc);
            ble_radio_lock();
            s_conn = event->connect.conn_handle;
            s_notify = false;
            s_mtu = 23;
            s_link_ms = t;
            s_link_seen = false;
            s_tx_n = 0;
            s_link_addr = desc.peer_id_addr;
            if (s_idle_valid && (int32_t)(t - s_idle_until_ms) >= 0) s_idle_valid = false;
            blocked = s_idle_valid && !s_learn &&
                      ble_addr_cmp(&s_idle_addr, &desc.peer_id_addr) == 0;
            if (blocked) s_link_seen = true;
            ble_radio_unlock();
            if (blocked) {
                ble_gap_terminate(event->connect.conn_handle, BLE_ERR_REM_USER_CONN_TERM);
                break;
            }
        }
        {
            struct ble_gap_upd_params upd;
            memset(&upd, 0, sizeof(upd));
            upd.itvl_min = 80;
            upd.itvl_max = 120;
            upd.latency = 0;
            upd.supervision_timeout = 500;
            ble_gap_update_params(event->connect.conn_handle, &upd);
        }
        ESP_LOGI(TAG, "phone connected");
        break;
    case BLE_GAP_EVENT_DISCONNECT:
        ble_radio_lock();
        if (s_conn == BLE_HS_CONN_HANDLE_NONE ||
            event->disconnect.conn.conn_handle == s_conn) {
            s_conn = BLE_HS_CONN_HANDLE_NONE;
            s_notify = false;
            s_tx_n = 0;
            s_link_seen = false;
            s_mtu = 23;
        }
        ble_radio_unlock();
        ESP_LOGI(TAG, "phone disconnected reason=%d", event->disconnect.reason);
        if (ble_btn_is_on()) ble_phone_start_adv();
        break;
    case BLE_GAP_EVENT_SUBSCRIBE:
        if (event->subscribe.attr_handle == s_snap_handle) {
            ble_radio_lock();
            s_notify = event->subscribe.cur_notify != 0;
            ble_radio_unlock();
        }
        break;
    case BLE_GAP_EVENT_MTU:
        ble_radio_lock();
        if (event->mtu.conn_handle == s_conn) s_mtu = event->mtu.value;
        ble_radio_unlock();
        break;
    case BLE_GAP_EVENT_ADV_COMPLETE:
        /* reason 0 is the advertising stopping because a phone connected. */
        if (event->adv_complete.reason != 0 && ble_btn_is_on()) ble_phone_start_adv();
        break;
    default:
        break;
    }
}
