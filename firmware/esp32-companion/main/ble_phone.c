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
#include "host/ble_hs_adv.h"

#include "ble_btn.h"
#include "protocol.h"

static const char *TAG = "ble_phone";

#define NVS_NS "ble_phone"
#define PHONE_REC_LEN 51
#define PHONE_NAME_MAX 26
#define BODY_LEN 11
#define SEALED_LEN 24
#define PAIR_CHUNK 18
#define PAIR_PAGES_MAX 3
#define SNAP_FRESH_MS 2000u
#define SNAP_WAIT_MS 1500u
#define ADV_MS 200
#define QUEUE_MAX 80
#define CMD_OUT_MAX 16
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
/* Denied during this learn session; the phone keeps repeating its pages. */
static bool s_denied_valid;
static uint8_t s_denied_id[4];

static uint8_t s_asm_id[4];
static uint8_t s_asm_mask;
static uint8_t s_asm_count;
static uint8_t s_asm_chunk[PAIR_PAGES_MAX][PAIR_CHUNK];
static uint8_t s_asm_chunk_len[PAIR_PAGES_MAX];
static bool s_asm_active;

static bool s_cache_valid;
static uint32_t s_cache_ms;
static int s_cache_gen;
static uint16_t s_cache_mask;
static int s_cache_vals[12];

static refresh_pending_t s_wait[BLE_DEVICE_MAX];
static int s_wait_n;
static bool s_snap_inflight;
static uint32_t s_snap_req_ms;

static uint8_t s_queue[QUEUE_MAX][SEALED_LEN];
static int s_q_head;
static int s_q_len;

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

static void queue_push(const uint8_t pkt[SEALED_LEN])
{
    if (s_q_len >= QUEUE_MAX) {
        s_q_head = (s_q_head + 1) % QUEUE_MAX;
        s_q_len--;
    }
    int tail = (s_q_head + s_q_len) % QUEUE_MAX;
    memcpy(s_queue[tail], pkt, SEALED_LEN);
    s_q_len++;
    ble_btn_request_phone_airtime();
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
        queue_push(pkt);
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

static void accept_pair_pages(void)
{
    if (!s_learn || s_asm_count == 0) return;
    if (s_asm_mask != (uint8_t)((1u << s_asm_count) - 1u)) return;
    if (s_asm_chunk_len[0] < 16) return;
    s_asm_active = false;
    s_asm_mask = 0;

    const uint8_t *key = s_asm_chunk[0];
    if (s_denied_valid && memcmp(s_denied_id, s_asm_id, 4) == 0) {
        return;
    }
    /* The phone repeats its pairing pages; one dialog per pairing is enough. */
    if (s_pending_pair && memcmp(s_pending.id, s_asm_id, 4) == 0 &&
        memcmp(s_pending.key, key, 16) == 0) {
        return;
    }
    int existing = find_phone(s_asm_id);
    if (existing >= 0 && memcmp(s_phones[existing].key, key, 16) == 0) {
        return;
    }

    memset(&s_pending, 0, sizeof(s_pending));
    memcpy(s_pending.id, s_asm_id, 4);
    memcpy(s_pending.key, key, 16);
    int name_len = s_asm_chunk_len[0] - 16;
    if (name_len > PHONE_NAME_MAX) name_len = PHONE_NAME_MAX;
    if (name_len > 0) memcpy(s_pending.name, s_asm_chunk[0] + 16, (size_t)name_len);
    for (int page = 1; page < s_asm_count && name_len < PHONE_NAME_MAX; page++) {
        int room = PHONE_NAME_MAX - name_len;
        int n = s_asm_chunk_len[page] < room ? s_asm_chunk_len[page] : room;
        if (n > 0) memcpy(s_pending.name + name_len, s_asm_chunk[page], (size_t)n);
        name_len += n;
    }
    s_pending.name_len = (uint8_t)name_len;
    sanitize_name(s_pending.name, s_pending.name_len);
    s_pending_pair = true;

    memcpy(s_out_pair_id, s_pending.id, 4);
    memcpy(s_out_pair_name, s_pending.name, s_pending.name_len);
    s_out_pair_name[s_pending.name_len] = '\0';
    s_out_pair = true;
}

static void on_pair_page(const uint8_t *payload, uint8_t len)
{
    if (!s_learn || len < 6) return;
    const uint8_t *id = payload + 1;
    uint8_t packed = payload[5];
    int index = packed >> 4;
    int count = packed & 0x0F;
    if (count < 1 || count > PAIR_PAGES_MAX || index >= count) return;
    int chunk_len = (int)len - 6;
    if (chunk_len > PAIR_CHUNK) return;
    if (index == 0 && chunk_len < 16) return;
    if (!s_asm_active || memcmp(s_asm_id, id, 4) != 0 || s_asm_count != (uint8_t)count) {
        memset(s_asm_chunk_len, 0, sizeof(s_asm_chunk_len));
        s_asm_mask = 0;
        s_asm_count = (uint8_t)count;
        memcpy(s_asm_id, id, 4);
        s_asm_active = true;
    }
    memcpy(s_asm_chunk[index], payload + 6, (size_t)chunk_len);
    s_asm_chunk_len[index] = (uint8_t)chunk_len;
    s_asm_mask = (uint8_t)(s_asm_mask | (1u << index));
    accept_pair_pages();
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
    ble_radio_lock();
    if (!ble_btn_is_on()) {
        ble_btn_set_on(true);
    }
    if (timeout_ms == 0) timeout_ms = 90000u;
    if (timeout_ms < 60000u) timeout_ms = 60000u;
    if (timeout_ms > 120000u) timeout_ms = 120000u;
    s_learn = true;
    s_pending_pair = false;
    s_denied_valid = false;
    s_asm_active = false;
    s_asm_mask = 0;
    s_learn_deadline_ms = now_ms() + timeout_ms;
    ble_radio_unlock();
    return true;
}

void ble_phone_learn_end(void)
{
    ble_radio_lock();
    s_learn = false;
    s_pending_pair = false;
    s_asm_active = false;
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

void ble_phone_on_adv(const uint8_t *payload, uint8_t len)
{
    if (!payload || len < 1) return;
    ble_radio_lock();
    uint8_t type = payload[0];
    if (type == 1) {
        on_pair_page(payload, len);
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

    ble_radio_lock();
    if (s_learn && (int32_t)(t - s_learn_deadline_ms) >= 0) {
        s_learn = false;
        s_pending_pair = false;
        s_asm_active = false;
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

bool ble_phone_adv_pending(void)
{
    ble_radio_lock();
    bool pending = s_q_len > 0;
    ble_radio_unlock();
    return pending;
}

bool ble_phone_kick_adv(void)
{
    bool started = false;
    ble_radio_lock();
    if (s_q_len > 0 && !ble_gap_adv_active()) {
        uint8_t svc[2 + SEALED_LEN];
        struct ble_hs_adv_fields fields;
        struct ble_gap_adv_params params;
        svc[0] = 0x0E;
        svc[1] = 0x7B;
        memcpy(svc + 2, s_queue[s_q_head], SEALED_LEN);
        memset(&fields, 0, sizeof(fields));
        fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
        fields.svc_data_uuid16 = svc;
        fields.svc_data_uuid16_len = (uint8_t)sizeof(svc);
        int rc = ble_gap_adv_set_fields(&fields);
        if (rc != 0) {
            ESP_LOGW(TAG, "adv fields rc=%d", rc);
        } else {
            memset(&params, 0, sizeof(params));
            params.conn_mode = BLE_GAP_CONN_MODE_NON;
            params.disc_mode = BLE_GAP_DISC_MODE_GEN;
            params.itvl_min = 0x20;
            params.itvl_max = 0x30;
            rc = ble_gap_adv_start(BLE_OWN_ADDR_PUBLIC, NULL, ADV_MS, &params,
                                   ble_btn_gap_event, NULL);
            if (rc != 0) {
                ESP_LOGW(TAG, "adv start rc=%d", rc);
            } else {
                s_q_head = (s_q_head + 1) % QUEUE_MAX;
                s_q_len--;
                started = true;
            }
        }
    }
    ble_radio_unlock();
    return started;
}
