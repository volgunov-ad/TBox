#include <stdio.h>
#include <string.h>

#include "esp_log.h"
#include "esp_random.h"
#include "esp_system.h"
#include "esp_timer.h"
#include "nvs.h"

#include "relay_store.h"
#include "sdkconfig.h"

#define NS "relay"
/*
 * Power is cut without shutdown (car rail), so a saved counter can lag behind
 * the RAM one. TX counters are saved at most this often; the boot margin makes
 * the first packet after a reboot unforgeable against anything sent before it.
 */
#define COUNTER_SAVE_MS 10000u

static const char *TAG = "relay_store";

static uint8_t s_id[4];
static uint8_t s_key[16];
static char s_name[27];
static bool s_paired;
static uint32_t s_tx;
static uint32_t s_rx;
static uint8_t s_mask;
static bool s_tx_dirty;
static bool s_rx_dirty;
static uint32_t s_tx_saved_ms;
static uint32_t s_rx_saved_ms;
static bool s_have_peer;
static uint8_t s_peer_type;
static uint8_t s_peer_addr[6];

static uint32_t now_ms(void)
{
    return (uint32_t)(esp_timer_get_time() / 1000ULL);
}

static void gen_random(uint8_t *out, size_t len)
{
    uint32_t buf = 0;
    for (size_t i = 0; i < len; i++) {
        if ((i & 3) == 0) buf = esp_random();
        out[i] = (uint8_t)(buf >> ((i & 3) * 8));
    }
}

void relay_store_init(void)
{
    nvs_handle_t h;
    if (nvs_open(NS, NVS_READWRITE, &h) != ESP_OK) {
        ESP_LOGE(TAG, "nvs open failed");
        return;
    }
    size_t len = sizeof(s_id);
    if (nvs_get_blob(h, "id", s_id, &len) != ESP_OK || len != sizeof(s_id)) {
        gen_random(s_id, sizeof(s_id));
        nvs_set_blob(h, "id", s_id, sizeof(s_id));
    }
    len = sizeof(s_key);
    if (nvs_get_blob(h, "key", s_key, &len) != ESP_OK || len != sizeof(s_key)) {
        gen_random(s_key, sizeof(s_key));
        nvs_set_blob(h, "key", s_key, sizeof(s_key));
    }
    uint8_t paired = 0;
    nvs_get_u8(h, "paired", &paired);
    s_paired = paired != 0;
    uint32_t v = 0;
    if (nvs_get_u32(h, "tx", &v) == ESP_OK) s_tx = v;
    if (nvs_get_u32(h, "rx", &v) == ESP_OK) s_rx = v;
    uint8_t mask = 0;
    nvs_get_u8(h, "mask", &mask);
    s_mask = mask & 0x01;
    uint8_t peer[7];
    len = sizeof(peer);
    if (nvs_get_blob(h, "peer", peer, &len) == ESP_OK && len == sizeof(peer)) {
        s_have_peer = true;
        s_peer_type = peer[0];
        memcpy(s_peer_addr, peer + 1, 6);
    }

    /* Jump past anything sent before an unclean power cut. */
    s_tx += (uint32_t)CONFIG_RELAY_REBOOT_MARGIN;
    nvs_set_u32(h, "tx", s_tx);
    nvs_commit(h);
    nvs_close(h);

    /* "Relay-XX" -> "Relay-1A2B" from the id; the name is stable across boots. */
    snprintf(s_name, sizeof(s_name), "%s", CONFIG_RELAY_NAME);
    size_t n = strlen(s_name);
    if (n >= 2 && memcmp(s_name + n - 2, "XX", 2) == 0) {
        snprintf(s_name + n - 2, 5, "%02X%02X", s_id[2], s_id[3]);
    }
    ESP_LOGI(TAG, "id=%02x%02x%02x%02x paired=%d mask=%d", s_id[0], s_id[1], s_id[2], s_id[3],
             (int)s_paired, (int)s_mask);
}

const uint8_t *relay_store_id(void) { return s_id; }
const uint8_t *relay_store_key(void) { return s_key; }
const char *relay_store_name(void) { return s_name; }
bool relay_store_paired(void) { return s_paired; }

bool relay_store_key_ok(void)
{
    uint8_t diff = 0;
    for (int i = 0; i < (int)sizeof(s_key); i++) diff |= s_key[i];
    return diff != 0;
}

bool relay_store_has_peer(void) { return s_have_peer; }

bool relay_store_peer_match(uint8_t addr_type, const uint8_t addr[6])
{
    if (!s_have_peer || !addr) return false;
    return s_peer_type == addr_type && memcmp(s_peer_addr, addr, 6) == 0;
}

void relay_store_set_peer(uint8_t addr_type, const uint8_t addr[6])
{
    if (!addr) return;
    if (s_have_peer && s_peer_type == addr_type && memcmp(s_peer_addr, addr, 6) == 0) return;
    s_have_peer = true;
    s_peer_type = addr_type;
    memcpy(s_peer_addr, addr, 6);
    uint8_t peer[7];
    peer[0] = addr_type;
    memcpy(peer + 1, addr, 6);
    nvs_handle_t h;
    if (nvs_open(NS, NVS_READWRITE, &h) != ESP_OK) return;
    nvs_set_blob(h, "peer", peer, sizeof(peer));
    nvs_commit(h);
    nvs_close(h);
}

void relay_store_set_paired(void)
{
    if (s_paired) return;
    s_paired = true;
    nvs_handle_t h;
    if (nvs_open(NS, NVS_READWRITE, &h) != ESP_OK) return;
    nvs_set_u8(h, "paired", 1);
    nvs_commit(h);
    nvs_close(h);
}

uint32_t relay_store_tx_counter(void) { return s_tx; }

uint32_t relay_store_next_tx(void)
{
    s_tx++;
    s_tx_dirty = true;
    return s_tx;
}

bool relay_store_rx_accept(uint32_t counter)
{
    /* Strictly greater: the boot margin guarantees the first real counter is far ahead. */
    return counter > s_rx;
}

void relay_store_commit_rx(uint32_t counter)
{
    if (counter <= s_rx) return;
    s_rx = counter;
    s_rx_dirty = false;
    s_rx_saved_ms = now_ms();
    /* Commands are rare. Persist now so a power cut cannot replay this 0x30. */
    nvs_handle_t h;
    if (nvs_open(NS, NVS_READWRITE, &h) != ESP_OK) return;
    nvs_set_u32(h, "rx", s_rx);
    nvs_commit(h);
    nvs_close(h);
}

uint8_t relay_store_mask(void) { return s_mask; }

void relay_store_set_mask(uint8_t mask)
{
    mask &= 0x01;
    if (mask == s_mask) return;
    s_mask = mask;
    nvs_handle_t h;
    if (nvs_open(NS, NVS_READWRITE, &h) != ESP_OK) return;
    nvs_set_u8(h, "mask", s_mask);
    nvs_commit(h);
    nvs_close(h);
}

void relay_store_tick(void)
{
    uint32_t t = now_ms();
    bool save = false;
    nvs_handle_t h;
    if (nvs_open(NS, NVS_READWRITE, &h) != ESP_OK) return;
    if (s_tx_dirty && (uint32_t)(t - s_tx_saved_ms) >= COUNTER_SAVE_MS) {
        nvs_set_u32(h, "tx", s_tx);
        s_tx_dirty = false;
        s_tx_saved_ms = t;
        save = true;
    }
    if (s_rx_dirty && (uint32_t)(t - s_rx_saved_ms) >= COUNTER_SAVE_MS) {
        nvs_set_u32(h, "rx", s_rx);
        s_rx_dirty = false;
        s_rx_saved_ms = t;
        save = true;
    }
    if (save) nvs_commit(h);
    nvs_close(h);
}

void relay_store_factory_reset(void)
{
    ESP_LOGW(TAG, "factory reset");
    nvs_handle_t h;
    if (nvs_open(NS, NVS_READWRITE, &h) == ESP_OK) {
        nvs_erase_all(h);
        nvs_commit(h);
        nvs_close(h);
    }
    esp_restart();
}
