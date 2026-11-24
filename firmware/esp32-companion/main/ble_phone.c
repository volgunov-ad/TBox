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
#include "phone_link_policy.h"
#include "protocol.h"

static const char *TAG = "ble_phone";

#define NVS_NS "ble_phone"
#define PHONE_REC_LEN 52
/* Blob of fw <= 0.12: same layout without the trailing kind byte. */
#define PHONE_REC_LEN_V1 51
#define REC_KIND_PHONE 0
#define REC_KIND_RELAY 1
/* Cleartext PAIR from a relay board and sealed 0x30/0x31 relay types (fw 0.13). */
#define RELAY_PAIR_TYPE 0x11
#define RELAY_CMD_TYPE 0x30
#define RELAY_STATE_TYPE 0x31
#define RELAY_OUT_MAX 8
#define PHONE_NAME_MAX 26
#define BODY_LEN 11
#define SEALED_LEN 24
#define SNAP_FRESH_MS 2000u
#define SNAP_WAIT_MS 1500u
#define CMD_OUT_MAX 16
/* One snapshot is up to 7 value pages and 11 text pages. */
#define TX_MAX 24
#define PAGE_MEDIA 4
#define PAGE_BODY 5
#define PAGE_CABIN 6
#define PAGE_TEXT_FIRST 8
#define SNAP_PAGE_TYPE_BASE 0x20
#define TEXT_CHUNK 9
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
#define SNAP_W0    (1u << 12)
#define SNAP_ROOF  (1u << 16)
#define SNAP_SHADE (1u << 17)
#define SNAP_HU    (1u << 18)
#define SNAP_OUT   (1u << 19)
#define SNAP_IN    (1u << 20)
#define SNAP_RECIRC (1u << 21)
#define SNAP_FRONT  (1u << 22)
#define SNAP_AC     (1u << 23)
#define SNAP_LAYOUT (1u << 24)

/* REFRESH body[2]: what the phone screen shows. 0 (older apps) = everything. */
#define GROUP_CLIMATE 0x01u
#define GROUP_SEATS   0x02u
#define GROUP_MEDIA   0x04u
#define GROUP_WINDOWS 0x08u
#define GROUP_ALL     0x0Fu
/* Header temperatures only: page 6, which every answer carries. */
#define GROUP_HEADER  0x10u
#define GROUP_MASK    (GROUP_ALL | GROUP_HEADER)

typedef struct {
    uint8_t id[4];
    uint8_t key[16];
    uint32_t counter;
    uint8_t kind; /* REC_KIND_PHONE or REC_KIND_RELAY */
    uint8_t name_len;
    char name[PHONE_NAME_MAX];
} phone_rec_t;

typedef struct {
    int index;
    uint32_t counter;
    /* Text the phone already shows; its text pages are skipped when this matches. */
    uint16_t text_hash;
    uint8_t groups;
    /* Link that asked. Conn handle 0 is valid; missing is BLE_HS_CONN_HANDLE_NONE. */
    uint16_t conn;
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

/* Relay boards: outbound 0x30 counters (NVS r%d), desired and last known state. */
static uint32_t s_relay_tx[BLE_DEVICE_MAX];
static uint32_t s_relay_tx_dirty;
static uint8_t s_relay_desired[BLE_DEVICE_MAX];
static bool s_relay_desired_valid[BLE_DEVICE_MAX];
static uint8_t s_relay_actual[BLE_DEVICE_MAX];
static bool s_relay_actual_valid[BLE_DEVICE_MAX];
static uint8_t s_relay_gen[BLE_DEVICE_MAX];
static bool s_relay_wait[BLE_DEVICE_MAX];
static uint32_t s_relay_wait_ms[BLE_DEVICE_MAX];
static uint8_t s_relay_wait_tries[BLE_DEVICE_MAX];
static uint8_t s_relay_wait_states[BLE_DEVICE_MAX];
static uint16_t s_relay_wait_pulse[BLE_DEVICE_MAX];
static bool s_relay_need_latch[BLE_DEVICE_MAX];
/** Forget asked for an open; the record stays until 0x31 confirms it. */
static bool s_relay_forgetting[BLE_DEVICE_MAX];
/** Drop from the main loop. on_sealed must not write NVS on the host task. */
static bool s_relay_drop_pending[BLE_DEVICE_MAX];
/**
 * False until the first 0x31 after boot copies the relay's echoed generation.
 * The relay keeps the last generation across a companion reboot.
 */
static bool s_relay_gen_synced[BLE_DEVICE_MAX];
/** Command parked until the first 0x31 adopts the relay's generation. */
static bool s_relay_hold_cmd[BLE_DEVICE_MAX];
static uint8_t s_relay_hold_states[BLE_DEVICE_MAX];
static uint16_t s_relay_hold_pulse[BLE_DEVICE_MAX];
static bool s_adv_refresh;
/** 0x31 body[1]: the relay opened itself (max-on or link loss), not a pulse end. */
#define RELAY_FLAG_SELF_OPEN 0x08

static bool s_learn;
static uint32_t s_learn_deadline_ms;
static bool s_pending_pair;
static phone_rec_t s_pending;
/* Link that sent the pending PAIR: cleartext PAIR binds no link by itself. */
static uint16_t s_pending_conn;
/* Denied during this learn session; the phone may write the packet again. */
static bool s_denied_valid;
static uint8_t s_denied_id[4];

static bool s_cache_valid;
static uint32_t s_cache_ms;
static int s_cache_gen;
static uint32_t s_cache_mask;
static int s_cache_vals[PHONE_SNAP_VALS];
static phone_media_t s_cache_media = { .playing = -1, .pos_ms = -1, .dur_ms = -1 };
static uint16_t s_cache_text_hash;

static refresh_pending_t s_wait[BLE_DEVICE_MAX];
static int s_wait_n;
static bool s_snap_inflight;
static uint32_t s_snap_req_ms;

typedef struct {
    uint16_t handle;
    bool notify;
    bool seen;
    bool kick;
    uint16_t mtu;
    uint32_t link_ms;
    ble_addr_t addr;
    int phone_index;
    uint8_t tx[TX_MAX][SEALED_LEN];
    int tx_n;
    /* Relay 0x30. Snapshot pages must not evict it. */
    uint8_t tx_hi[SEALED_LEN];
    bool tx_hi_valid;
} phone_link_t;

static phone_link_t s_links[PHONE_LINK_MAX];
static bool s_links_ready;
/* Set for the duration of a GATT write so the packet marks that link. */
static phone_link_t *s_rx_link;
static uint16_t s_snap_handle;

/* Dropped strangers reconnect at once and would walk through every slot. */
#define IDLE_BLOCK_MAX 4
typedef struct {
    ble_addr_t addr;
    uint32_t until_ms;
} idle_block_t;
static idle_block_t s_blocked[IDLE_BLOCK_MAX];

/* USB messages are sent from ble_phone_poll (main loop), never from the NimBLE host task. */
static bool s_out_pair;
static uint8_t s_out_pair_id[4];
static char s_out_pair_name[PHONE_NAME_MAX + 1];
static int s_out_pair_kind;
static int s_out_pair_rssi;
static uint16_t s_out_pair_conn;
static cmd_out_t s_out_cmd[CMD_OUT_MAX];
static int s_out_cmd_n;
static bool s_out_snap_req;
static bool s_out_status;

typedef struct {
    uint8_t id[4];
    uint8_t mask;
    uint8_t flags;
    uint16_t uptime_s;
} relay_out_t;
static relay_out_t s_out_relay[RELAY_OUT_MAX];
static int s_out_relay_n;

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
    blob[51] = rec->kind;
}

static bool blob_to_rec(const uint8_t blob[], size_t len, phone_rec_t *rec)
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
    rec->kind = len >= PHONE_REC_LEN ? blob[51] : REC_KIND_PHONE;
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
            char rkey[COUNTER_KEY_LEN];
            rec_to_blob(&s_phones[i], blob);
            nvs_set_blob(h, key, blob, PHONE_REC_LEN);
            nvs_set_u32(h, ckey, s_phones[i].counter);
            snprintf(rkey, COUNTER_KEY_LEN, "r%d", i);
            if (s_phones[i].kind == REC_KIND_RELAY) {
                nvs_set_u32(h, rkey, s_relay_tx[i]);
            } else {
                nvs_erase_key(h, rkey);
            }
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

/* Outbound 0x30 counters are flushed from poll, never from the NimBLE host task. */
static void save_relay_tx(int index)
{
    nvs_handle_t h;
    if (nvs_open(NVS_NS, NVS_READWRITE, &h) != ESP_OK) return;
    char rkey[COUNTER_KEY_LEN];
    snprintf(rkey, COUNTER_KEY_LEN, "r%d", index);
    nvs_set_u32(h, rkey, s_relay_tx[index]);
    nvs_commit(h);
    nvs_close(h);
}

static void load_nvs(void)
{
    s_count = 0;
    memset(s_relay_tx, 0, sizeof(s_relay_tx));
    memset(s_relay_desired, 0, sizeof(s_relay_desired));
    memset(s_relay_desired_valid, 0, sizeof(s_relay_desired_valid));
    memset(s_relay_actual, 0, sizeof(s_relay_actual));
    memset(s_relay_actual_valid, 0, sizeof(s_relay_actual_valid));
    memset(s_relay_gen, 0, sizeof(s_relay_gen));
    memset(s_relay_wait, 0, sizeof(s_relay_wait));
    memset(s_relay_wait_ms, 0, sizeof(s_relay_wait_ms));
    memset(s_relay_wait_tries, 0, sizeof(s_relay_wait_tries));
    memset(s_relay_wait_states, 0, sizeof(s_relay_wait_states));
    memset(s_relay_wait_pulse, 0, sizeof(s_relay_wait_pulse));
    memset(s_relay_need_latch, 0, sizeof(s_relay_need_latch));
    memset(s_relay_forgetting, 0, sizeof(s_relay_forgetting));
    memset(s_relay_drop_pending, 0, sizeof(s_relay_drop_pending));
    memset(s_relay_gen_synced, 0, sizeof(s_relay_gen_synced));
    memset(s_relay_hold_cmd, 0, sizeof(s_relay_hold_cmd));
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
        size_t len = sizeof(blob);
        if (nvs_get_blob(h, key, blob, &len) != ESP_OK ||
            (len != PHONE_REC_LEN && len != PHONE_REC_LEN_V1) ||
            !blob_to_rec(blob, len, &s_phones[s_count])) {
            continue;
        }
        uint32_t counter = 0;
        if (nvs_get_u32(h, ckey, &counter) == ESP_OK && counter > s_phones[s_count].counter) {
            s_phones[s_count].counter = counter;
        }
        if (s_phones[s_count].kind == REC_KIND_RELAY) {
            char rkey[COUNTER_KEY_LEN];
            snprintf(rkey, COUNTER_KEY_LEN, "r%d", i);
            uint32_t tx = 0;
            /* A crash can lose the counter between a send and the NVS flush.
             * Skip ahead so the first command after boot is not a replay. */
            if (nvs_get_u32(h, rkey, &tx) == ESP_OK) {
                if (tx > 0xFFFFFFFFu - 32u) tx = 0xFFFFFFFFu - 32u;
                s_relay_tx[s_count] = tx + 32u;
            }
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
    if (type != 2 && type != 3 && type != 4 && type != RELAY_STATE_TYPE) return false;
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
    uint8_t diff = 0;
    for (int i = 0; i < 4; i++) diff |= (uint8_t)(tag[i] ^ payload[9 + BODY_LEN + i]);
    if (diff != 0) return false;
    *type_out = type;
    *counter_out = counter;
    return true;
}

/* BSS leaves handle 0, and 0 is a live NimBLE conn handle. */
static void links_ensure(void)
{
    if (s_links_ready) return;
    for (int i = 0; i < PHONE_LINK_MAX; i++) {
        s_links[i].handle = BLE_HS_CONN_HANDLE_NONE;
        s_links[i].phone_index = -1;
    }
    s_links_ready = true;
}

static phone_link_t *link_by_conn(uint16_t conn)
{
    links_ensure();
    if (conn == BLE_HS_CONN_HANDLE_NONE) return NULL;
    for (int i = 0; i < PHONE_LINK_MAX; i++) {
        if (s_links[i].handle == conn) return &s_links[i];
    }
    return NULL;
}

static phone_link_t *link_alloc_new(uint16_t conn)
{
    links_ensure();
    for (int i = 0; i < PHONE_LINK_MAX; i++) {
        if (s_links[i].handle == BLE_HS_CONN_HANDLE_NONE) {
            memset(&s_links[i], 0, sizeof(s_links[i]));
            s_links[i].handle = conn;
            s_links[i].phone_index = -1;
            return &s_links[i];
        }
    }
    return NULL;
}

static int link_count(void)
{
    links_ensure();
    int n = 0;
    for (int i = 0; i < PHONE_LINK_MAX; i++) {
        if (s_links[i].handle != BLE_HS_CONN_HANDLE_NONE) n++;
    }
    return n;
}

static void clear_link(phone_link_t *link)
{
    uint16_t conn = link->handle;
    memset(link, 0, sizeof(*link));
    link->handle = BLE_HS_CONN_HANDLE_NONE;
    link->phone_index = -1;
    for (int i = 0; i < s_wait_n; ) {
        if (s_wait[i].conn == conn) {
            for (int j = i; j < s_wait_n - 1; j++) s_wait[j] = s_wait[j + 1];
            s_wait_n--;
            continue;
        }
        i++;
    }
}

static void tx_push(phone_link_t *link, const uint8_t pkt[SEALED_LEN])
{
    if (!link) return;
    if (link->tx_n >= TX_MAX) {
        memmove(link->tx[0], link->tx[1], (size_t)(TX_MAX - 1) * SEALED_LEN);
        link->tx_n = TX_MAX - 1;
    }
    memcpy(link->tx[link->tx_n], pkt, SEALED_LEN);
    link->tx_n++;
}

static void mark_link_seen(void)
{
    if (s_rx_link) s_rx_link->seen = true;
}

/* The same phone on a new link: snapshots and the counter follow the new one. */
static void bind_phone(int phone_index)
{
    if (!s_rx_link) return;
    s_rx_link->phone_index = phone_index;
    s_rx_link->seen = true;
    s_rx_link->kick = false;
    for (int i = 0; i < PHONE_LINK_MAX; i++) {
        phone_link_t *other = &s_links[i];
        if (other == s_rx_link) continue;
        if (other->handle == BLE_HS_CONN_HANDLE_NONE) continue;
        if (other->phone_index == phone_index) other->kick = true;
    }
}

static void block_addr(const ble_addr_t *addr, uint32_t until_ms)
{
    if (until_ms == 0) until_ms = 1;
    int free_slot = -1;
    int same = -1;
    int oldest = 0;
    for (int i = 0; i < IDLE_BLOCK_MAX; i++) {
        if (s_blocked[i].until_ms == 0) {
            if (free_slot < 0) free_slot = i;
            continue;
        }
        if (ble_addr_cmp(&s_blocked[i].addr, addr) == 0) same = i;
        if (s_blocked[i].until_ms < s_blocked[oldest].until_ms) oldest = i;
    }
    int slot = same >= 0 ? same : (free_slot >= 0 ? free_slot : oldest);
    s_blocked[slot].addr = *addr;
    s_blocked[slot].until_ms = until_ms;
}

static bool addr_blocked(const ble_addr_t *addr, uint32_t t)
{
    if (s_learn) return false;
    for (int i = 0; i < IDLE_BLOCK_MAX; i++) {
        if (s_blocked[i].until_ms == 0) continue;
        if ((int32_t)(t - s_blocked[i].until_ms) >= 0) {
            s_blocked[i].until_ms = 0;
            continue;
        }
        if (ble_addr_cmp(&s_blocked[i].addr, addr) == 0) return true;
    }
    return false;
}

/* FNV-1a 32 folded to 16 bits, never 0 for non-empty text. Same as PhoneBleCodec.textHash. */
static uint16_t text_hash(const uint8_t *text, size_t len)
{
    if (len == 0) return 0;
    uint32_t h = 0x811C9DC5u;
    for (size_t i = 0; i < len; i++) {
        h ^= text[i];
        h *= 0x01000193u;
    }
    uint16_t folded = (uint16_t)((h ^ (h >> 16)) & 0xFFFFu);
    return folded == 0 ? 1 : folded;
}

static void put_u16(uint8_t *dst, uint32_t v)
{
    dst[0] = (uint8_t)v;
    dst[1] = (uint8_t)(v >> 8);
}

static uint32_t seconds_u16(int64_t ms)
{
    if (ms < 0) return 0xFFFFu;
    int64_t s = ms / 1000;
    return s >= 0xFFFF ? 0xFFFEu : (uint32_t)s;
}

static int cache_val(uint32_t bit, int index, int missing)
{
    return (s_cache_mask & bit) ? s_cache_vals[index] : missing;
}

static void put_i16(uint8_t *dst, int v)
{
    dst[0] = (uint8_t)v;
    dst[1] = (uint8_t)((uint16_t)v >> 8);
}

static void enqueue_snap(int phone_index, uint32_t counter, uint16_t phone_text_hash, uint8_t groups,
                          uint16_t conn)
{
    phone_link_t *link = link_by_conn(conn);
    if (phone_index < 0 || phone_index >= s_count || !s_cache_valid || !link) return;
    const phone_rec_t *phone = &s_phones[phone_index];
    const phone_media_t *m = &s_cache_media;
    int pages[7 + (PHONE_TEXT_MAX + TEXT_CHUNK - 1) / TEXT_CHUNK];
    int page_n = 0;
    uint8_t want = (groups & GROUP_MASK) ? (uint8_t)(groups & GROUP_MASK) : GROUP_ALL;
    if (want & GROUP_CLIMATE) {
        pages[page_n++] = 0;
        pages[page_n++] = 1;
    }
    if (want & GROUP_SEATS) pages[page_n++] = 2;
    if (want & GROUP_MEDIA) {
        pages[page_n++] = 3;
        pages[page_n++] = PAGE_MEDIA;
    }
    if (want & GROUP_WINDOWS) pages[page_n++] = PAGE_BODY;
    pages[page_n++] = PAGE_CABIN;
    if ((want & GROUP_MEDIA) && m->text_len > 0 && phone_text_hash != s_cache_text_hash) {
        int chunks = (int)((m->text_len + TEXT_CHUNK - 1) / TEXT_CHUNK);
        for (int c = 0; c < chunks; c++) pages[page_n++] = PAGE_TEXT_FIRST + c;
    }
    for (int pi = 0; pi < page_n; pi++) {
        int page = pages[pi];
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
            body[6] = (uint8_t)((s_cache_mask & SNAP_RECIRC) ? s_cache_vals[21] : 0xFF);
            body[7] = (uint8_t)((s_cache_mask & SNAP_FRONT) ? s_cache_vals[22] : 0xFF);
            body[8] = (uint8_t)((s_cache_mask & SNAP_AC) ? s_cache_vals[23] : 0xFF);
            /* 0 ordinary / 1 single / 2 dual; missing 0xFF (phone defaults to dual). */
            body[9] = (uint8_t)((s_cache_mask & SNAP_LAYOUT) ? s_cache_vals[24] : 0xFF);
        } else if (page == 2) {
            for (int s = 0; s < 4; s++) {
                uint16_t bit = (uint16_t)(SNAP_S0 << s);
                body[2 + s] = (uint8_t)((s_cache_mask & bit) ? s_cache_vals[7 + s] : 0xFF);
            }
        } else if (page == 3) {
            body[2] = (uint8_t)((s_cache_mask & SNAP_VOL) ? s_cache_vals[11] : 0xFF);
        } else if (page == PAGE_MEDIA) {
            int64_t pos = m->pos_ms;
            /* The cache may be up to SNAP_FRESH_MS old. */
            if (m->playing == 1 && pos >= 0) {
                pos += (int64_t)(uint32_t)(now_ms() - s_cache_ms);
                if (m->dur_ms > 0 && pos > m->dur_ms) pos = m->dur_ms;
            }
            body[2] = (uint8_t)(m->playing < 0 ? 0xFF : m->playing);
            put_u16(body + 3, seconds_u16(pos));
            put_u16(body + 5, seconds_u16(m->dur_ms));
            put_u16(body + 7, s_cache_text_hash);
            body[9] = (uint8_t)m->text_len;
        } else if (page == PAGE_BODY) {
            for (int w = 0; w < 4; w++) {
                body[2 + w] = (uint8_t)cache_val(SNAP_W0 << w, 12 + w, 0xFF);
            }
            body[6] = (uint8_t)cache_val(SNAP_ROOF, 16, 0xFF);
            body[7] = (uint8_t)cache_val(SNAP_SHADE, 17, 0xFF);
            body[8] = (uint8_t)cache_val(SNAP_HU, 18, 0xFF);
        } else if (page == PAGE_CABIN) {
            put_i16(body + 2, cache_val(SNAP_OUT, 19, 0x7FFF));
            put_i16(body + 4, cache_val(SNAP_IN, 20, 0x7FFF));
        } else {
            size_t from = (size_t)(page - PAGE_TEXT_FIRST) * TEXT_CHUNK;
            size_t n = m->text_len - from;
            if (n > TEXT_CHUNK) n = TEXT_CHUNK;
            memcpy(body + 2, m->text + from, n);
        }
        /*
         * Pages 0..3 keep type 4 for older phone apps, so they share one keystream.
         * Later pages carry their own type, hence their own nonce: track names must not
         * be recoverable by XOR with a page of known content.
         */
        uint8_t type = page >= PAGE_MEDIA ? (uint8_t)(SNAP_PAGE_TYPE_BASE + page) : 4;
        seal(phone->key, type, phone->id, counter, body, pkt);
        tx_push(link, pkt);
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
            enqueue_snap(s_wait[i].index, s_wait[i].counter, s_wait[i].text_hash, s_wait[i].groups,
                         s_wait[i].conn);
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

static void note_refresh(int phone_index, uint32_t counter, uint16_t text_hash, uint8_t groups, uint32_t t)
{
    if (!s_rx_link) return;
    int slot = wait_find(phone_index);
    if (slot < 0 && s_wait_n < BLE_DEVICE_MAX) {
        slot = s_wait_n++;
    }
    if (slot < 0) return;
    s_wait[slot].index = phone_index;
    s_wait[slot].counter = counter;
    s_wait[slot].text_hash = text_hash;
    s_wait[slot].groups = groups;
    s_wait[slot].conn = s_rx_link->handle;
    request_snap_if_needed(t);
}

static void relay_report_locked(int index, uint8_t mask, uint8_t flags, uint16_t uptime_s)
{
    if (s_out_relay_n >= RELAY_OUT_MAX) return;
    relay_out_t *out = &s_out_relay[s_out_relay_n++];
    memcpy(out->id, s_phones[index].id, 4);
    out->mask = mask;
    out->flags = flags;
    out->uptime_s = uptime_s;
}

/* The live link bound to this record, if any. */
static phone_link_t *link_by_phone_locked(int index)
{
    links_ensure();
    for (int i = 0; i < PHONE_LINK_MAX; i++) {
        if (s_links[i].handle != BLE_HS_CONN_HANDLE_NONE && s_links[i].phone_index == index) {
            return &s_links[i];
        }
    }
    return NULL;
}

static bool drop_record_locked(int index);

/**
 * Seal 0x30 and queue it for the relay. The counter bump stays in RAM; NVS r%d is
 * flushed from poll, so this is safe inside GATT callbacks. A retry passes
 * fresh=false and reuses the generation so the relay does not pulse again.
 * Returns false when the relay is offline (the desired state still applies
 * on its next report).
 */
static bool relay_send_cmd_locked(int index, uint8_t states, uint16_t pulse_ms, bool fresh)
{
    phone_link_t *link = link_by_phone_locked(index);
    if (!link) return false;
    /* A command before the first 0x31 would reuse generation 1 and the
     * powered relay would echo it without applying. Park it. */
    if (!s_relay_gen_synced[index]) {
        s_relay_hold_cmd[index] = true;
        s_relay_hold_states[index] = states;
        s_relay_hold_pulse[index] = pulse_ms;
        return true;
    }
    s_relay_hold_cmd[index] = false;
    uint8_t body[BODY_LEN];
    uint8_t pkt[SEALED_LEN];
    memset(body, 0, sizeof(body));
    body[0] = 0x01; /* channel mask: the X1 board has one channel */
    body[1] = states;
    body[2] = (uint8_t)pulse_ms;
    body[3] = (uint8_t)(pulse_ms >> 8);
    if (fresh) s_relay_gen[index]++;
    body[4] = s_relay_gen[index];
    uint32_t counter = ++s_relay_tx[index];
    seal(s_phones[index].key, RELAY_CMD_TYPE, s_phones[index].id, counter, body, pkt);
    memcpy(link->tx_hi, pkt, SEALED_LEN);
    link->tx_hi_valid = true;
    s_relay_tx_dirty |= (1u << index);
    s_relay_wait[index] = true;
    s_relay_wait_ms[index] = now_ms();
    s_relay_wait_states[index] = states;
    s_relay_wait_pulse[index] = pulse_ms;
    return true;
}

/** Cleartext write: type, id[4], key[16], name. Name is at most PHONE_NAME_MAX bytes. */
static void on_pair_packet(const uint8_t *payload, uint8_t len, uint8_t kind)
{
    if (len < 21) return;
    const uint8_t *id = payload + 1;
    const uint8_t *key = payload + 5;
    if (s_denied_valid && memcmp(s_denied_id, id, 4) == 0) return;
    /* One dialog per phone and key, even if the app writes the packet twice. */
    if (s_pending_pair && memcmp(s_pending.id, id, 4) == 0 &&
        memcmp(s_pending.key, key, 16) == 0) {
        mark_link_seen();
        return;
    }
    /* Keep the first pending request until the HU allows or denies it. */
    if (s_pending_pair) return;
    int existing = find_phone(id);
    if (existing >= 0 && memcmp(s_phones[existing].key, key, 16) == 0) {
        /* Cleartext PAIR must not move an existing binding. A relay that
         * still sends PAIR has not latched, so the 0x30 goes out now. */
        if (s_phones[existing].kind != REC_KIND_RELAY) {
            mark_link_seen();
            return;
        }
        /* A matching key and a cleartext PAIR means the relay is not
         * latched. paired is cleared only by a factory reset, which
         * reboots the board, so it has no generation to adopt. */
        if (s_rx_link && link_by_phone_locked(existing) == NULL) {
            s_rx_link->phone_index = existing;
            s_rx_link->seen = true;
        }
        s_relay_need_latch[existing] = true;
        s_adv_refresh = true;
        s_relay_gen_synced[existing] = true;
        if (!s_relay_wait[existing]) {
            bool held = s_relay_hold_cmd[existing];
            uint8_t st = held ? s_relay_hold_states[existing] : 0;
            uint16_t pulse = held ? s_relay_hold_pulse[existing] : 0;
            s_relay_wait_tries[existing] = 0;
            if (!relay_send_cmd_locked(existing, st, pulse, true)) {
                s_relay_gen_synced[existing] = false;
            }
        }
        return;
    }
    /* A new phone or relay is only accepted while the learn window is open.
     * An already allowed relay may still PAIR so its latch can go out. */
    if (!s_learn) return;

    memset(&s_pending, 0, sizeof(s_pending));
    memcpy(s_pending.id, id, 4);
    memcpy(s_pending.key, key, 16);
    s_pending.kind = kind;
    int name_len = (int)len - 21;
    if (name_len > PHONE_NAME_MAX) name_len = PHONE_NAME_MAX;
    if (name_len > 0) memcpy(s_pending.name, payload + 21, (size_t)name_len);
    s_pending.name_len = (uint8_t)name_len;
    sanitize_name(s_pending.name, s_pending.name_len);
    s_pending_pair = true;
    s_pending_conn = s_rx_link ? s_rx_link->handle : BLE_HS_CONN_HANDLE_NONE;
    mark_link_seen();

    memcpy(s_out_pair_id, s_pending.id, 4);
    memcpy(s_out_pair_name, s_pending.name, s_pending.name_len);
    s_out_pair_name[s_pending.name_len] = '\0';
    s_out_pair_kind = s_pending.kind == REC_KIND_RELAY ? 1 : 0;
    s_out_pair_conn = s_pending_conn;
    s_out_pair_rssi = 0;
    s_out_pair = true;
}

static void on_sealed(const uint8_t *payload, uint8_t len)
{
    const uint8_t *id = payload + 1;
    int index = find_phone(id);
    if (index < 0) return;
    uint8_t wire = payload[0];
    uint8_t kind = s_phones[index].kind;
    if (wire == 2 || wire == 3) {
        if (kind != REC_KIND_PHONE) return;
    } else if (wire == RELAY_STATE_TYPE) {
        if (kind != REC_KIND_RELAY) return;
    } else {
        return;
    }
    uint8_t type = 0;
    uint32_t counter = 0;
    uint8_t body[BODY_LEN];
    if (!unseal(s_phones[index].key, payload, len, &type, &counter, body)) return;
    if (counter <= s_phones[index].counter) return;
    bind_phone(index);
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
        note_refresh(index, counter, (uint16_t)(body[0] | (body[1] << 8)), body[2], now_ms());
    } else if (type == RELAY_STATE_TYPE) {
        uint8_t actual = body[0] & 0x01;
        uint8_t echoed = body[4];
        s_relay_actual[index] = actual;
        s_relay_actual_valid[index] = true;
        /* The relay remembers the last generation. Copy it before any new command. */
        if (!s_relay_gen_synced[index] && !s_relay_wait[index]) {
            s_relay_gen[index] = echoed;
        }
        s_relay_gen_synced[index] = true;
        bool sent_now = false;
        if (s_relay_need_latch[index] && !s_relay_wait[index] && !s_relay_hold_cmd[index]) {
            s_relay_wait_tries[index] = 0;
            sent_now = relay_send_cmd_locked(index, 0, 0, true);
        } else if (s_relay_need_latch[index] && !s_relay_hold_cmd[index] &&
                   echoed == s_relay_gen[index]) {
            s_relay_need_latch[index] = false;
            s_adv_refresh = true;
        }
        if (echoed == s_relay_gen[index] && !sent_now) {
            /* While forgetting, keep retrying until the coil is actually open. */
            if (!(s_relay_forgetting[index] && actual != 0)) {
                s_relay_wait[index] = false;
            }
        }
        /* The relay opened itself. A keepalive that left before our latest
         * command must not cancel that command. A forget still waits. */
        if ((body[1] & RELAY_FLAG_SELF_OPEN) && echoed == s_relay_gen[index]) {
            s_relay_desired_valid[index] = false;
            if (!s_relay_forgetting[index]) {
                s_relay_wait[index] = false;
            }
        }
        if (s_relay_hold_cmd[index] && !s_relay_wait[index]) {
            uint8_t st = s_relay_hold_states[index];
            uint16_t pulse = s_relay_hold_pulse[index];
            s_relay_wait_tries[index] = 0;
            /* need_latch stays set until this generation is echoed. */
            relay_send_cmd_locked(index, st, pulse, true);
        }
        if (s_relay_forgetting[index] && actual == 0 && echoed == s_relay_gen[index]) {
            s_relay_drop_pending[index] = true;
            s_relay_wait[index] = false;
            return;
        }
        relay_report_locked(index, actual, body[1],
                            (uint16_t)(body[2] | ((uint16_t)body[3] << 8)));
        /* Repeat the latched state when this link is new or the relay disagrees. */
        if (s_relay_desired_valid[index] &&
            actual != (s_relay_desired[index] & 0x01) && !s_relay_wait[index]) {
            s_relay_wait_tries[index] = 0;
            relay_send_cmd_locked(index, s_relay_desired[index], 0, true);
        }
    }
}

void ble_phone_init(void)
{
    ble_radio_lock();
    links_ensure();
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
    s_adv_refresh = true;
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
    s_adv_refresh = true;
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
            int index;
            if (existing >= 0) {
                /* A new key starts a new counter; the same key keeps replay protection. */
                if (memcmp(s_phones[existing].key, rec.key, 16) == 0) {
                    rec.counter = s_phones[existing].counter;
                }
                s_phones[existing] = rec;
                index = existing;
            } else {
                index = s_count;
                s_phones[s_count++] = rec;
            }
            s_pending_pair = false;
            ok = save_nvs();
            if (ok) {
                s_learn = false;
                s_out_pair = false;
                s_adv_refresh = true;
                if (rec.kind == REC_KIND_RELAY) {
                    /* The relay latches "paired" on the first 0x30. The link
                     * that wrote PAIR is the one the user just allowed. */
                    s_relay_desired[index] = 0;
                    s_relay_desired_valid[index] = false;
                    s_relay_forgetting[index] = false;
                    s_relay_need_latch[index] = true;
                    /* Unpaired: no generation to adopt. Send on this link now. */
                    s_relay_gen_synced[index] = true;
                    s_relay_wait_tries[index] = 0;
                    links_ensure();
                    phone_link_t *link = link_by_conn(s_pending_conn);
                    if (link && link->phone_index != index) {
                        phone_link_t *rx = s_rx_link;
                        s_rx_link = link;
                        bind_phone(index);
                        s_rx_link = rx;
                    }
                    if (relay_send_cmd_locked(index, 0, 0, true)) {
                        ESP_LOGI(TAG, "relay %d latch queued", index);
                    } else {
                        s_relay_gen_synced[index] = false;
                        ESP_LOGI(TAG, "relay %d latch armed", index);
                    }
                }
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

/** Remove the record. A live link is kicked after the caller has finished with it. */
static bool drop_record_locked(int index)
{
    if (index < 0 || index >= s_count) return false;
    links_ensure();
    bool relay = s_phones[index].kind == REC_KIND_RELAY;
    for (int i = 0; i < PHONE_LINK_MAX; i++) {
        if (relay && s_links[i].phone_index == index) s_links[i].kick = true;
    }
    for (int i = index; i < s_count - 1; i++) {
        s_phones[i] = s_phones[i + 1];
    }
    s_count--;
    for (int i = index; i < BLE_DEVICE_MAX - 1; i++) {
        s_relay_tx[i] = s_relay_tx[i + 1];
        s_relay_desired[i] = s_relay_desired[i + 1];
        s_relay_desired_valid[i] = s_relay_desired_valid[i + 1];
        s_relay_actual[i] = s_relay_actual[i + 1];
        s_relay_actual_valid[i] = s_relay_actual_valid[i + 1];
        s_relay_gen[i] = s_relay_gen[i + 1];
        s_relay_wait[i] = s_relay_wait[i + 1];
        s_relay_wait_ms[i] = s_relay_wait_ms[i + 1];
        s_relay_wait_tries[i] = s_relay_wait_tries[i + 1];
        s_relay_wait_states[i] = s_relay_wait_states[i + 1];
        s_relay_wait_pulse[i] = s_relay_wait_pulse[i + 1];
        s_relay_need_latch[i] = s_relay_need_latch[i + 1];
        s_relay_forgetting[i] = s_relay_forgetting[i + 1];
        s_relay_drop_pending[i] = s_relay_drop_pending[i + 1];
        s_relay_gen_synced[i] = s_relay_gen_synced[i + 1];
        s_relay_hold_cmd[i] = s_relay_hold_cmd[i + 1];
        s_relay_hold_states[i] = s_relay_hold_states[i + 1];
        s_relay_hold_pulse[i] = s_relay_hold_pulse[i + 1];
    }
    s_relay_forgetting[s_count] = false;
    s_relay_drop_pending[s_count] = false;
    s_relay_gen_synced[s_count] = false;
    s_relay_hold_cmd[s_count] = false;
    for (int i = 0; i < s_wait_n; ) {
        if (s_wait[i].index == index) {
            for (int j = i; j < s_wait_n - 1; j++) s_wait[j] = s_wait[j + 1];
            s_wait_n--;
            continue;
        }
        if (s_wait[i].index > index) s_wait[i].index--;
        i++;
    }
    for (int i = 0; i < PHONE_LINK_MAX; i++) {
        if (s_links[i].phone_index == index) s_links[i].phone_index = -1;
        else if (s_links[i].phone_index > index) s_links[i].phone_index--;
    }
    s_out_status = true;
    return save_nvs();
}

bool ble_phone_forget(const char *id_hex)
{
    uint8_t id[4];
    if (!parse_id(id_hex, id)) return false;
    ble_radio_lock();
    int index = find_phone(id);
    bool ok = false;
    if (index >= 0) {
        if (s_phones[index].kind == REC_KIND_RELAY && link_by_phone_locked(index)) {
            /* Keep the record until the relay confirms the coil is open.
             * Deleting it now would drop the link before 0x30 is applied. */
            if (!s_relay_forgetting[index] || !s_relay_wait[index]) {
                s_relay_forgetting[index] = true;
                s_relay_desired[index] = 0;
                s_relay_desired_valid[index] = false;
                s_relay_need_latch[index] = false;
                s_relay_wait_tries[index] = 0;
                s_relay_wait_states[index] = 0;
                s_relay_wait_pulse[index] = 0;
                relay_send_cmd_locked(index, 0, 0, true);
            }
            ok = true;
        } else {
            ok = drop_record_locked(index);
        }
    }
    ble_radio_unlock();
    return ok;
}

static void on_incoming(uint16_t conn, const uint8_t *payload, uint8_t len)
{
    if (!payload || len < 1) return;
    ble_radio_lock();
    s_rx_link = link_by_conn(conn);
    uint8_t type = payload[0];
    if (type == 1 || type == RELAY_PAIR_TYPE) {
        on_pair_packet(payload, len, type == 1 ? REC_KIND_PHONE : REC_KIND_RELAY);
    } else if ((type == 2 || type == 3 || type == RELAY_STATE_TYPE) && len == SEALED_LEN) {
        on_sealed(payload, len);
    }
    s_rx_link = NULL;
    ble_radio_unlock();
}

void ble_phone_set_snapshot(int gen, uint32_t mask, const int vals[PHONE_SNAP_VALS], const phone_media_t *media)
{
    ble_radio_lock();
    s_cache_gen = gen;
    s_cache_mask = mask;
    memcpy(s_cache_vals, vals, sizeof(s_cache_vals));
    if (media) {
        s_cache_media = *media;
        if (s_cache_media.text_len > PHONE_TEXT_MAX) s_cache_media.text_len = PHONE_TEXT_MAX;
    } else {
        memset(&s_cache_media, 0, sizeof(s_cache_media));
        s_cache_media.playing = -1;
        s_cache_media.pos_ms = -1;
        s_cache_media.dur_ms = -1;
    }
    s_cache_text_hash = text_hash(s_cache_media.text, s_cache_media.text_len);
    s_cache_valid = true;
    s_cache_ms = now_ms();
    s_snap_inflight = false;
    for (int i = 0; i < s_wait_n; i++) {
        enqueue_snap(s_wait[i].index, s_wait[i].counter, s_wait[i].text_hash, s_wait[i].groups,
                     s_wait[i].conn);
    }
    s_wait_n = 0;
    ble_radio_unlock();
}

bool ble_phone_adv_dead(void)
{
    ble_radio_lock();
    bool dead = ble_btn_is_on() && link_count() < PHONE_LINK_MAX &&
                !ble_gap_adv_active();
    ble_radio_unlock();
    return dead;
}

void ble_phone_poll(uint32_t t)
{
    bool send_pair = false;
    char pair_hex[9];
    char pair_name[PHONE_NAME_MAX + 1];
    int pair_kind = 0;
    uint16_t pair_conn = BLE_HS_CONN_HANDLE_NONE;
    bool adv_refresh = false;
    cmd_out_t cmds[CMD_OUT_MAX];
    int cmd_n = 0;
    bool send_snap_req = false;
    bool send_status = false;
    uint8_t notes[PHONE_LINK_MAX][SEALED_LEN];
    uint16_t note_conn[PHONE_LINK_MAX];
    bool note_hi[PHONE_LINK_MAX];
    int note_n = 0;
    uint16_t note_handle = 0;
    uint16_t drop_conn[PHONE_LINK_MAX];
    int drop_n = 0;
    relay_out_t relays[RELAY_OUT_MAX];
    int relay_n = 0;

    ble_radio_lock();
    if (s_learn && (int32_t)(t - s_learn_deadline_ms) >= 0) {
        s_learn = false;
        s_pending_pair = false;
        s_out_pair = false;
        s_out_status = true;
        s_adv_refresh = true;
    }
    for (int i = 0; i < s_count; ) {
        if (!s_relay_drop_pending[i]) {
            i++;
            continue;
        }
        drop_record_locked(i);
    }
    for (int i = 0; i < s_count; i++) {
        if (s_phones[i].kind != REC_KIND_RELAY) continue;
        if (s_relay_forgetting[i] && !s_relay_wait[i]) {
            if (!link_by_phone_locked(i)) {
                drop_record_locked(i);
                i--;
                continue;
            }
            s_relay_wait_tries[i] = 0;
            s_relay_wait_states[i] = 0;
            s_relay_wait_pulse[i] = 0;
            relay_send_cmd_locked(i, 0, 0, true);
            continue;
        }
        if (!s_relay_wait[i]) continue;
        if (!link_by_phone_locked(i)) {
            s_relay_wait[i] = false;
            if (s_relay_forgetting[i]) {
                drop_record_locked(i);
                i--;
            }
            continue;
        }
        if ((uint32_t)(t - s_relay_wait_ms[i]) < 1000u) continue;
        int cap = s_relay_wait_pulse[i] != 0 ? 3 : 8;
        if (s_relay_wait_tries[i] >= cap) {
            s_relay_wait[i] = false;
            if (s_relay_forgetting[i]) {
                drop_record_locked(i);
                i--;
            }
            continue;
        }
        s_relay_wait_tries[i]++;
        relay_send_cmd_locked(i, s_relay_wait_states[i], s_relay_wait_pulse[i], false);
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
    if (s_relay_tx_dirty) {
        for (int i = 0; i < s_count; i++) {
            if (s_relay_tx_dirty & (1u << i)) {
                save_relay_tx(i);
            }
        }
        s_relay_tx_dirty = 0;
    }
    relay_n = s_out_relay_n;
    if (relay_n > 0) {
        memcpy(relays, s_out_relay, sizeof(relay_out_t) * (size_t)relay_n);
        s_out_relay_n = 0;
    }
    if (s_out_pair) {
        send_pair = true;
        id_to_hex(s_out_pair_id, pair_hex);
        memcpy(pair_name, s_out_pair_name, sizeof(pair_name));
        pair_kind = s_out_pair_kind;
        pair_conn = s_out_pair_conn;
        s_out_pair = false;
    }
    adv_refresh = s_adv_refresh;
    s_adv_refresh = false;
    cmd_n = s_out_cmd_n;
    if (cmd_n > 0) {
        memcpy(cmds, s_out_cmd, sizeof(cmd_out_t) * (size_t)cmd_n);
        s_out_cmd_n = 0;
    }
    send_snap_req = s_out_snap_req;
    s_out_snap_req = false;
    send_status = s_out_status;
    s_out_status = false;
    links_ensure();
    note_handle = s_snap_handle;
    for (int i = 0; i < PHONE_LINK_MAX; i++) {
        phone_link_t *link = &s_links[i];
        if (link->handle == BLE_HS_CONN_HANDLE_NONE) continue;
        if (link->kick || (!link->seen && (uint32_t)(t - link->link_ms) > LINK_IDLE_MS)) {
            /* A duplicate of a live phone shares its address; do not block that address. */
            if (!link->kick) block_addr(&link->addr, t + IDLE_BLOCK_MS);
            if (drop_n < PHONE_LINK_MAX) drop_conn[drop_n++] = link->handle;
            link->seen = true;
            link->kick = false;
        }
        if (link->notify && link->mtu >= (uint16_t)(SEALED_LEN + 3) &&
            note_n < PHONE_LINK_MAX &&
            (link->tx_hi_valid || link->tx_n > 0)) {
            if (link->tx_hi_valid) {
                memcpy(notes[note_n], link->tx_hi, SEALED_LEN);
                link->tx_hi_valid = false;
                note_hi[note_n] = true;
            } else {
                memcpy(notes[note_n], link->tx[0], SEALED_LEN);
                if (link->tx_n > 1) {
                    memmove(link->tx[0], link->tx[1], (size_t)(link->tx_n - 1) * SEALED_LEN);
                }
                link->tx_n--;
                note_hi[note_n] = false;
            }
            note_conn[note_n++] = link->handle;
        }
    }
    ble_radio_unlock();

    if (adv_refresh) {
        if (ble_gap_adv_active()) ble_gap_adv_stop();
        ble_phone_start_adv();
    }
    if (send_pair) {
        int8_t rssi = 0;
        if (pair_conn != BLE_HS_CONN_HANDLE_NONE) {
            ble_gap_conn_rssi(pair_conn, &rssi);
        }
        protocol_send_phone_pair(pair_hex, pair_name, pair_kind, rssi);
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
    for (int i = 0; i < relay_n; i++) {
        char hex[9];
        id_to_hex(relays[i].id, hex);
        protocol_send_relay_dev(hex, relays[i].mask, relays[i].flags, relays[i].uptime_s);
    }
    for (int i = 0; i < note_n; i++) {
        struct os_mbuf *om = ble_hs_mbuf_from_flat(notes[i], SEALED_LEN);
        if (!om) continue;
        int rc = ble_gatts_notify_custom(note_conn[i], note_handle, om);
        if (rc != 0) {
            ESP_LOGW(TAG, "notify rc=%d", rc);
            ble_radio_lock();
            phone_link_t *link = link_by_conn(note_conn[i]);
            if (link) {
                if (note_hi[i]) {
                    if (!link->tx_hi_valid) {
                        memcpy(link->tx_hi, notes[i], SEALED_LEN);
                        link->tx_hi_valid = true;
                    }
                } else if (link->tx_n < TX_MAX) {
                    memmove(link->tx[1], link->tx[0], (size_t)link->tx_n * SEALED_LEN);
                    memcpy(link->tx[0], notes[i], SEALED_LEN);
                    link->tx_n++;
                }
            }
            ble_radio_unlock();
        }
    }
    for (int i = 0; i < drop_n; i++) {
        ble_gap_terminate(drop_conn[i], BLE_ERR_REM_USER_CONN_TERM);
    }
}

int ble_phone_write_json(char *out, size_t cap)
{
    if (!out || cap < 3) return 0;
    ble_radio_lock();
    size_t pos = 0;
    out[pos++] = '[';
    for (int i = 0; i < s_count; i++) {
        /* id + name with every byte escaped plus the relay flag fits in entry[]. */
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
        if (s_phones[i].kind == REC_KIND_RELAY) {
            memcpy(entry + e, ",\"r\":1", 6);
            e += 6;
        }
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

int ble_phone_relay_count(void)
{
    ble_radio_lock();
    int n = 0;
    for (int i = 0; i < s_count; i++) {
        if (s_phones[i].kind == REC_KIND_RELAY) n++;
    }
    ble_radio_unlock();
    return n;
}

int ble_phone_relay_set(const char *id_hex, uint8_t mask, uint16_t pulse_ms)
{
    uint8_t id[4];
    bool has_id = false;
    if (id_hex && id_hex[0]) {
        if (!parse_id(id_hex, id)) return -1;
        has_id = true;
    }
    ble_radio_lock();
    int matched = 0;
    int queued = 0;
    for (int i = 0; i < s_count; i++) {
        if (s_phones[i].kind != REC_KIND_RELAY) continue;
        if (has_id && memcmp(s_phones[i].id, id, 4) != 0) continue;
        if (s_relay_forgetting[i]) {
            matched++;
            continue;
        }
        s_relay_desired[i] = mask;
        /* A pulse returns to open on its own, so it is not a state to restore. */
        s_relay_desired_valid[i] = pulse_ms == 0;
        s_relay_wait_tries[i] = 0;
        /* A parked command has not gone on the air. Do not report it queued. */
        if (relay_send_cmd_locked(i, mask, pulse_ms, true) && !s_relay_hold_cmd[i]) {
            queued++;
        }
        matched++;
    }
    ble_radio_unlock();
    if (matched == 0) return 0;
    /* -2: the record exists but no link accepted the command. */
    if (queued == 0) return -2;
    return queued;
}

int ble_phone_relay_json(char *out, size_t cap)
{
    if (!out || cap < 3) return 0;
    ble_radio_lock();
    size_t pos = 0;
    out[pos++] = '[';
    for (int i = 0; i < s_count; i++) {
        if (s_phones[i].kind != REC_KIND_RELAY) continue;
        char entry[128];
        char hex[9];
        size_t e = 0;
        bool online = link_by_phone_locked(i) != NULL;
        id_to_hex(s_phones[i].id, hex);
        e += (size_t)snprintf(entry, sizeof(entry), "{\"id\":\"%s\",\"name\":\"", hex);
        for (uint8_t n = 0; n < s_phones[i].name_len; n++) {
            char c = s_phones[i].name[n];
            if (c == '"' || c == '\\') entry[e++] = '\\';
            entry[e++] = c;
        }
        e += (size_t)snprintf(entry + e, sizeof(entry) - e,
                              "\",\"online\":%s,\"mask\":%d}",
                              online ? "true" : "false",
                              s_relay_actual_valid[i] ? (int)s_relay_actual[i] : 0);
        size_t need = e + (pos > 1 ? 1 : 0);
        /* Keep room for ']' and '\0'; drop whole entries so the array stays valid JSON. */
        if (pos + need + 2 > cap) break;
        if (pos > 1) out[pos++] = ',';
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
    on_incoming(conn_handle, buf, (uint8_t)copied);
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
    if (!ble_btn_is_on() || link_count() >= PHONE_LINK_MAX || ble_gap_adv_active()) return 0;
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
    /* Company id 0xFFFF. Bit0 stays set for the learn window and while a
     * relay still needs its first latch: the board only reconnects then. */
    bool learn_bit = s_learn;
    if (!learn_bit) {
        for (int i = 0; i < s_count; i++) {
            if (s_phones[i].kind == REC_KIND_RELAY && s_relay_need_latch[i]) {
                learn_bit = true;
                break;
            }
        }
    }
    uint8_t mfg[3] = { 0xFF, 0xFF, learn_bit ? 0x01 : 0x00 };
    fields.mfg_data = mfg;
    fields.mfg_data_len = sizeof(mfg);
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
    uint16_t conns[PHONE_LINK_MAX];
    int n = 0;
    ble_radio_lock();
    links_ensure();
    for (int i = 0; i < PHONE_LINK_MAX; i++) {
        if (s_links[i].handle == BLE_HS_CONN_HANDLE_NONE) continue;
        conns[n++] = s_links[i].handle;
        clear_link(&s_links[i]);
    }
    ble_radio_unlock();
    if (ble_gap_adv_active()) ble_gap_adv_stop();
    for (int i = 0; i < n; i++) {
        ble_gap_terminate(conns[i], BLE_ERR_REM_USER_CONN_TERM);
    }
}

/* HCI 0x3B: unacceptable connection interval. L2CAP can only accept or reject. */
#define BLE_ERR_UNACCEPTABLE_CONN_INTERVAL 0x3B

static void prefer_interval(uint16_t conn)
{
    struct ble_gap_upd_params upd;
    memset(&upd, 0, sizeof(upd));
    phone_link_preferred(&upd.itvl_min, &upd.itvl_max, &upd.latency, &upd.supervision_timeout);
    ble_gap_update_params(conn, &upd);
}

static int on_param_req(struct ble_gap_event *event)
{
    const struct ble_gap_upd_params *peer = event->conn_update_req.peer_params;
    struct ble_gap_upd_params *self = event->conn_update_req.self_params;
    if (!peer || phone_link_params_ok(peer->itvl_min)) return 0;
    /* L2CAP can only accept or reject. A short interval is refused. */
    if (event->type == BLE_GAP_EVENT_L2CAP_UPDATE_REQ || !self) {
        return BLE_ERR_UNACCEPTABLE_CONN_INTERVAL;
    }
    phone_link_preferred(&self->itvl_min, &self->itvl_max, &self->latency,
                         &self->supervision_timeout);
    return 0;
}

int ble_phone_on_gap(struct ble_gap_event *event)
{
    switch (event->type) {
    case BLE_GAP_EVENT_CONNECT:
        if (event->connect.status != 0) {
            ESP_LOGW(TAG, "connect status=%d", event->connect.status);
            ble_phone_start_adv();
            return 0;
        }
        {
            struct ble_gap_conn_desc desc;
            bool reject = false;
            int n = 0;
            uint32_t t = now_ms();
            memset(&desc, 0, sizeof(desc));
            ble_gap_conn_find(event->connect.conn_handle, &desc);
            ble_radio_lock();
            phone_link_t *link = link_by_conn(event->connect.conn_handle);
            bool blocked = addr_blocked(&desc.peer_id_addr, t);
            /* A blocked address must not take a slot: poll would idle-drop link_ms 0. */
            if (!blocked && !link) {
                link = link_alloc_new(event->connect.conn_handle);
            }
            reject = !link || blocked;
            if (!reject && link->link_ms == 0) {
                link->notify = false;
                link->seen = false;
                link->kick = false;
                link->mtu = 23;
                link->tx_n = 0;
                link->link_ms = t ? t : 1;
                link->addr = desc.peer_id_addr;
                link->phone_index = -1;
            }
            n = link_count();
            ble_radio_unlock();
            if (reject) {
                ble_gap_terminate(event->connect.conn_handle, BLE_ERR_REM_USER_CONN_TERM);
                return 0;
            }
            prefer_interval(event->connect.conn_handle);
            if (n >= PHONE_LINK_MAX && ble_gap_adv_active()) {
                ble_gap_adv_stop();
            } else {
                ble_phone_start_adv();
            }
            ESP_LOGI(TAG, "phone connected (%d)", n);
        }
        return 0;
    case BLE_GAP_EVENT_DISCONNECT:
        ble_radio_lock();
        {
            phone_link_t *link = link_by_conn(event->disconnect.conn.conn_handle);
            if (link) clear_link(link);
        }
        ble_radio_unlock();
        ESP_LOGI(TAG, "phone disconnected reason=%d", event->disconnect.reason);
        if (ble_btn_is_on()) ble_phone_start_adv();
        return 0;
    case BLE_GAP_EVENT_SUBSCRIBE:
        if (event->subscribe.attr_handle == s_snap_handle) {
            ble_radio_lock();
            phone_link_t *link = link_by_conn(event->subscribe.conn_handle);
            if (link) link->notify = event->subscribe.cur_notify != 0;
            ble_radio_unlock();
        }
        return 0;
    case BLE_GAP_EVENT_MTU:
        ble_radio_lock();
        {
            phone_link_t *link = link_by_conn(event->mtu.conn_handle);
            if (link) link->mtu = event->mtu.value;
        }
        ble_radio_unlock();
        return 0;
    case BLE_GAP_EVENT_ADV_COMPLETE:
        /* reason 0 is advertising stopping because a phone connected. Resume if a slot is free. */
        if (ble_btn_is_on()) ble_phone_start_adv();
        return 0;
    case BLE_GAP_EVENT_CONN_UPDATE_REQ:
    case BLE_GAP_EVENT_L2CAP_UPDATE_REQ:
        return on_param_req(event);
    default:
        return 0;
    }
}
