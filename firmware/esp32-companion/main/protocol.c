#include "protocol.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_crc.h"
#include "esp_system.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "gnss_detect.h"
#include "ota_update.h"
#include "tinyusb.h"
#include "tusb_cdc_acm.h"
#include "um980_uart.h"

static char s_line[512];
static size_t s_line_len;
static protocol_relay_set_cb_t s_relay_cb;
static protocol_um980_cmd_cb_t s_um980_cb;
static protocol_um980_baud_cb_t s_um980_baud_cb;
static protocol_reboot_cb_t s_reboot_cb;
static protocol_can_tx_cb_t s_can_tx_cb;
static protocol_can_baud_cb_t s_can_baud_cb;
static protocol_can_filter_cb_t s_can_filter_cb;
static protocol_can_light_cb_t s_can_light_cb;
static protocol_mag_chip_cb_t s_mag_chip_cb;
static int s_hello_baud = ESP_COMPANION_DEFAULT_UM980_BAUD;
static bool s_hello_can;
static uint32_t s_hello_can_baud;
static bool s_hello_mag;
static char s_hello_mag_chip[16] = "none";
static char s_hello_mag_seen[128];
static bool s_hello_gnss;
static char s_hello_gnss_chip[16] = "none";
static char s_hello_gnss_model[64];

/** After otaBegin ACK: parse binary frames until expected size written. */
static bool s_ota_bin_mode;
static uint8_t s_ota_frame[6 + OTA_FRAME_MAX_PAYLOAD + 4];
static size_t s_ota_frame_len;
static uint32_t s_ota_ack_every = 8;
static uint32_t s_ota_chunks_since_ack;
static volatile bool s_ota_restart_pending;
static bool s_bridge_bin_mode;
static uint8_t s_bridge_frame[6 + OTA_FRAME_MAX_PAYLOAD + 4];
static size_t s_bridge_frame_len;
static bool s_can_light_mode;
static uint8_t s_can_batch[OTA_FRAME_MAX_PAYLOAD];
static size_t s_can_batch_len;

static void cdc_write_str(const char *s)
{
    // Prefer DTR-connected, but some Android USB hosts never assert DTR even
    // after a successful SET_CONTROL_LINE_STATE — still TX when USB is ready.
    if (!tud_ready() || !s) {
        return;
    }
    size_t len = strlen(s);
    size_t off = 0;
    int spins = 0;
    while (off < len && spins < 2000) {
        uint32_t avail = tud_cdc_write_available();
        if (avail == 0) {
            tud_cdc_write_flush();
            vTaskDelay(pdMS_TO_TICKS(1));
            spins++;
            continue;
        }
        size_t n = len - off;
        if (n > avail) {
            n = avail;
        }
        tud_cdc_write(s + off, n);
        off += n;
        spins = 0;
    }
    tud_cdc_write_flush();
}

static void json_escape_append(char *dst, size_t dst_sz, size_t *pos, const char *src)
{
    for (const char *p = src; *p && *pos + 2 < dst_sz; p++) {
        char c = *p;
        if (c == '"' || c == '\\') {
            if (*pos + 3 >= dst_sz) break;
            dst[(*pos)++] = '\\';
            dst[(*pos)++] = c;
        } else if ((unsigned char)c < 0x20) {
            continue;
        } else {
            dst[(*pos)++] = c;
        }
    }
}

void protocol_init(void)
{
    s_line_len = 0;
    s_relay_cb = NULL;
    s_um980_cb = NULL;
    s_um980_baud_cb = NULL;
    s_reboot_cb = NULL;
    s_can_tx_cb = NULL;
    s_can_baud_cb = NULL;
    s_can_filter_cb = NULL;
    s_can_light_cb = NULL;
    s_mag_chip_cb = NULL;
    s_hello_baud = ESP_COMPANION_DEFAULT_UM980_BAUD;
    s_hello_can = false;
    s_hello_can_baud = 0;
    s_hello_mag = false;
    strncpy(s_hello_mag_chip, "none", sizeof(s_hello_mag_chip) - 1);
    s_hello_mag_chip[sizeof(s_hello_mag_chip) - 1] = '\0';
    s_hello_mag_seen[0] = '\0';
    s_hello_gnss = false;
    strncpy(s_hello_gnss_chip, "none", sizeof(s_hello_gnss_chip) - 1);
    s_hello_gnss_chip[sizeof(s_hello_gnss_chip) - 1] = '\0';
    s_hello_gnss_model[0] = '\0';
    s_ota_bin_mode = false;
    s_ota_frame_len = 0;
    s_ota_chunks_since_ack = 0;
    s_ota_restart_pending = false;
    s_bridge_bin_mode = false;
    s_bridge_frame_len = 0;
    s_can_light_mode = false;
    s_can_batch_len = 0;
}

bool protocol_ota_active(void)
{
    return ota_is_active() || s_ota_bin_mode || s_bridge_bin_mode;
}

bool protocol_can_light_active(void)
{
    return s_can_light_mode;
}

bool protocol_um980_bridge_active(void)
{
    return s_bridge_bin_mode;
}

bool protocol_ota_restart_pending(void)
{
    return s_ota_restart_pending;
}

void protocol_set_um980_baud_for_hello(int baud)
{
    s_hello_baud = baud;
}

void protocol_set_relay_callback(protocol_relay_set_cb_t cb)
{
    s_relay_cb = cb;
}

void protocol_set_um980_cmd_callback(protocol_um980_cmd_cb_t cb)
{
    s_um980_cb = cb;
}

void protocol_set_um980_baud_callback(protocol_um980_baud_cb_t cb)
{
    s_um980_baud_cb = cb;
}

void protocol_set_reboot_callback(protocol_reboot_cb_t cb)
{
    s_reboot_cb = cb;
}

void protocol_set_can_tx_callback(protocol_can_tx_cb_t cb)
{
    s_can_tx_cb = cb;
}

void protocol_set_can_baud_callback(protocol_can_baud_cb_t cb)
{
    s_can_baud_cb = cb;
}

void protocol_set_can_filter_callback(protocol_can_filter_cb_t cb)
{
    s_can_filter_cb = cb;
}

void protocol_set_can_light_callback(protocol_can_light_cb_t cb)
{
    s_can_light_cb = cb;
}

void protocol_set_mag_chip_callback(protocol_mag_chip_cb_t cb)
{
    s_mag_chip_cb = cb;
}

void protocol_set_can_for_hello(bool present, uint32_t baud)
{
    s_hello_can = present;
    s_hello_can_baud = baud;
}

void protocol_set_gnss_for_hello(bool present, const char *chip, const char *model, int baud)
{
    s_hello_gnss = present;
    if (chip && chip[0]) {
        strncpy(s_hello_gnss_chip, chip, sizeof(s_hello_gnss_chip) - 1);
        s_hello_gnss_chip[sizeof(s_hello_gnss_chip) - 1] = '\0';
    }
    if (model) {
        strncpy(s_hello_gnss_model, model, sizeof(s_hello_gnss_model) - 1);
        s_hello_gnss_model[sizeof(s_hello_gnss_model) - 1] = '\0';
    }
    s_hello_baud = baud > 0 ? baud : ESP_COMPANION_DEFAULT_UM980_BAUD;
}

void protocol_set_mag_for_hello(bool mag, const char *chip,
                                const char *const *seen, int seen_count)
{
    s_hello_mag = mag;
    if (chip && chip[0]) {
        strncpy(s_hello_mag_chip, chip, sizeof(s_hello_mag_chip) - 1);
        s_hello_mag_chip[sizeof(s_hello_mag_chip) - 1] = '\0';
    }
    size_t pos = 0;
    s_hello_mag_seen[pos++] = '[';
    for (int i = 0; i < seen_count && pos + 24 < sizeof(s_hello_mag_seen); i++) {
        if (!seen[i] || !seen[i][0]) continue;
        if (pos > 1) {
            s_hello_mag_seen[pos++] = ',';
        }
        s_hello_mag_seen[pos++] = '"';
        json_escape_append(s_hello_mag_seen, sizeof(s_hello_mag_seen), &pos, seen[i]);
        s_hello_mag_seen[pos++] = '"';
    }
    s_hello_mag_seen[pos++] = ']';
    s_hello_mag_seen[pos] = '\0';
}

static void mag_seen_json(char *dst, size_t n)
{
    if (n == 0) return;
    strncpy(dst, s_hello_mag_seen, n - 1);
    dst[n - 1] = '\0';
    if (dst[0] == '\0') {
        snprintf(dst, n, "[]");
    }
}

void protocol_send_hello(void)
{
    char seen[128];
    mag_seen_json(seen, sizeof(seen));
    char model_esc[96];
    size_t mpos = 0;
    model_esc[0] = '\0';
    if (s_hello_gnss_model[0]) {
        json_escape_append(model_esc, sizeof(model_esc), &mpos, s_hello_gnss_model);
        model_esc[mpos] = '\0';
    }
    const bool um980_flag = gnss_is_um980();
    char buf[640];
    if (s_hello_can) {
        snprintf(buf, sizeof(buf),
                 "{\"v\":1,\"t\":\"hello\",\"fw\":\"%s\",\"gpioIn\":%d,\"relays\":%d,"
                 "\"gnss\":%s,\"gnssChip\":\"%s\",\"gnssModel\":\"%s\","
                 "\"um980\":%s,\"baud\":%d,\"can\":true,\"canBackend\":\"mcp2515\","
                 "\"canBaud\":%lu,\"canLight\":%s,\"mag\":%s,\"magChip\":\"%s\",\"magSeen\":%s}\n",
                 ESP_COMPANION_FW_VERSION,
                 ESP_COMPANION_GPIO_IN_COUNT,
                 ESP_COMPANION_RELAY_COUNT,
                 s_hello_gnss ? "true" : "false",
                 s_hello_gnss_chip,
                 model_esc,
                 um980_flag ? "true" : "false",
                 s_hello_baud,
                 (unsigned long)s_hello_can_baud,
                 s_can_light_mode ? "true" : "false",
                 s_hello_mag ? "true" : "false",
                 s_hello_mag_chip,
                 seen);
    } else {
        snprintf(buf, sizeof(buf),
                 "{\"v\":1,\"t\":\"hello\",\"fw\":\"%s\",\"gpioIn\":%d,\"relays\":%d,"
                 "\"gnss\":%s,\"gnssChip\":\"%s\",\"gnssModel\":\"%s\","
                 "\"um980\":%s,\"baud\":%d,\"mag\":%s,\"magChip\":\"%s\",\"magSeen\":%s}\n",
                 ESP_COMPANION_FW_VERSION,
                 ESP_COMPANION_GPIO_IN_COUNT,
                 ESP_COMPANION_RELAY_COUNT,
                 s_hello_gnss ? "true" : "false",
                 s_hello_gnss_chip,
                 model_esc,
                 um980_flag ? "true" : "false",
                 s_hello_baud,
                 s_hello_mag ? "true" : "false",
                 s_hello_mag_chip,
                 seen);
    }
    cdc_write_str(buf);
}

void protocol_send_mag(const char *chip, float hx, float hy, float hz,
                       float heading, float fs, bool ok)
{
    if (protocol_ota_active()) return;
    char buf[288];
    snprintf(buf, sizeof(buf),
             "{\"v\":1,\"t\":\"mag\",\"chip\":\"%s\",\"hx\":%.2f,\"hy\":%.2f,\"hz\":%.2f,"
             "\"heading\":%.2f,\"fs\":%.2f,\"ok\":%s}\n",
             chip && chip[0] ? chip : "rm3100",
             hx, hy, hz, heading, fs, ok ? "true" : "false");
    cdc_write_str(buf);
}

void protocol_send_mag_chip(const char *chip, bool ok, bool mag,
                            const char *const *seen, int seen_count)
{
    protocol_set_mag_for_hello(mag, chip, seen, seen_count);
    char seen_json[128];
    mag_seen_json(seen_json, sizeof(seen_json));
    char buf[256];
    snprintf(buf, sizeof(buf),
             "{\"v\":1,\"t\":\"magChip\",\"chip\":\"%s\",\"ok\":%s,\"mag\":%s,\"seen\":%s}\n",
             chip && chip[0] ? chip : s_hello_mag_chip,
             ok ? "true" : "false",
             mag ? "true" : "false",
             seen_json);
    cdc_write_str(buf);
}

void protocol_send_hb(uint32_t uptime_ms)
{
    char buf[96];
    snprintf(buf, sizeof(buf), "{\"v\":1,\"t\":\"hb\",\"uptimeMs\":%lu}\n", (unsigned long)uptime_ms);
    cdc_write_str(buf);
}

void protocol_send_gps(int fix, double lat, double lon, double alt,
                       float speed_kmh, float course,
                       int sats_used, int sats_vis, const char *utc_iso,
                       float hdop, float pdop, float vdop,
                       float hrms, float vrms, float diff_age)
{
    char buf[480];
    snprintf(buf, sizeof(buf),
             "{\"v\":1,\"t\":\"gps\",\"fix\":%d,\"lat\":%.7f,\"lon\":%.7f,\"alt\":%.2f,"
             "\"speedKmh\":%.2f,\"course\":%.2f,\"satsUsed\":%d,\"satsVis\":%d,\"utc\":\"%s\","
             "\"hdop\":%.2f,\"pdop\":%.2f,\"vdop\":%.2f,\"hrms\":%.3f,\"vrms\":%.3f,"
             "\"diffAge\":%.1f}\n",
             fix, lat, lon, alt, speed_kmh, course, sats_used, sats_vis,
             utc_iso ? utc_iso : "", hdop, pdop, vdop, hrms, vrms, diff_age);
    cdc_write_str(buf);
}

void protocol_send_gpio(uint16_t mask, uint32_t ms)
{
    char buf[96];
    snprintf(buf, sizeof(buf), "{\"v\":1,\"t\":\"gpio\",\"mask\":%u,\"ms\":%lu}\n",
             (unsigned)mask, (unsigned long)ms);
    cdc_write_str(buf);
}

void protocol_send_gpio_event(int ch, int level, uint32_t ms)
{
    char buf[112];
    snprintf(buf, sizeof(buf),
             "{\"v\":1,\"t\":\"gpioEvent\",\"ch\":%d,\"level\":%d,\"ms\":%lu}\n",
             ch, level ? 1 : 0, (unsigned long)ms);
    cdc_write_str(buf);
}

void protocol_send_relay(uint8_t mask)
{
    char buf[80];
    snprintf(buf, sizeof(buf), "{\"v\":1,\"t\":\"relay\",\"mask\":%u}\n", (unsigned)mask);
    cdc_write_str(buf);
}

void protocol_send_reboot_ack(void)
{
    cdc_write_str("{\"v\":1,\"t\":\"rebootAck\"}\n");
}

void protocol_send_um980_baud(int baud, bool ok)
{
    char buf[96];
    snprintf(buf, sizeof(buf),
             "{\"v\":1,\"t\":\"um980Baud\",\"baud\":%d,\"ok\":%s}\n",
             baud, ok ? "true" : "false");
    cdc_write_str(buf);
}

void protocol_send_ota_ack(const char *phase, uint32_t offset, bool ok, const char *err)
{
    char buf[192];
    if (err && err[0]) {
        snprintf(buf, sizeof(buf),
                 "{\"v\":1,\"t\":\"otaAck\",\"phase\":\"%s\",\"offset\":%lu,\"ok\":%s,\"err\":\"%s\"}\n",
                 phase ? phase : "", (unsigned long)offset, ok ? "true" : "false", err);
    } else {
        snprintf(buf, sizeof(buf),
                 "{\"v\":1,\"t\":\"otaAck\",\"phase\":\"%s\",\"offset\":%lu,\"ok\":%s}\n",
                 phase ? phase : "", (unsigned long)offset, ok ? "true" : "false");
    }
    cdc_write_str(buf);
}

void protocol_send_ota_done(bool ok, const char *err)
{
    char buf[160];
    if (err && err[0]) {
        snprintf(buf, sizeof(buf),
                 "{\"v\":1,\"t\":\"otaDone\",\"ok\":%s,\"err\":\"%s\"}\n",
                 ok ? "true" : "false", err);
    } else {
        snprintf(buf, sizeof(buf),
                 "{\"v\":1,\"t\":\"otaDone\",\"ok\":%s}\n",
                 ok ? "true" : "false");
    }
    cdc_write_str(buf);
}

void protocol_send_um980_bridge_ack(const char *phase, bool ok, const char *err)
{
    char buf[192];
    if (err && err[0]) {
        snprintf(buf, sizeof(buf),
                 "{\"v\":1,\"t\":\"um980BridgeAck\",\"phase\":\"%s\",\"ok\":%s,\"err\":\"%s\"}\n",
                 phase ? phase : "", ok ? "true" : "false", err);
    } else {
        snprintf(buf, sizeof(buf),
                 "{\"v\":1,\"t\":\"um980BridgeAck\",\"phase\":\"%s\",\"ok\":%s}\n",
                 phase ? phase : "", ok ? "true" : "false");
    }
    cdc_write_str(buf);
}

void protocol_send_can_ack(const char *phase, bool ok, const char *err)
{
    char buf[192];
    if (err && err[0]) {
        snprintf(buf, sizeof(buf),
                 "{\"v\":1,\"t\":\"canAck\",\"phase\":\"%s\",\"ok\":%s,\"err\":\"%s\"}\n",
                 phase ? phase : "", ok ? "true" : "false", err);
    } else {
        snprintf(buf, sizeof(buf),
                 "{\"v\":1,\"t\":\"canAck\",\"phase\":\"%s\",\"ok\":%s}\n",
                 phase ? phase : "", ok ? "true" : "false");
    }
    cdc_write_str(buf);
}

void protocol_send_can_baud(uint32_t baud, bool ok)
{
    char buf[96];
    snprintf(buf, sizeof(buf),
             "{\"v\":1,\"t\":\"canBaud\",\"baud\":%lu,\"ok\":%s}\n",
             (unsigned long)baud, ok ? "true" : "false");
    cdc_write_str(buf);
}

void protocol_send_can_filter_ack(bool ok, const char *err)
{
    protocol_send_can_ack("filter", ok, err);
}

static void cdc_write_bin(const uint8_t *data, size_t len);
static void encode_bridge_frame(const uint8_t *payload, uint16_t plen, uint8_t *out, size_t *out_len);

static void can_light_flush_batch(void)
{
    if (!s_can_light_mode || s_can_batch_len == 0) {
        return;
    }
    uint8_t frame[6 + OTA_FRAME_MAX_PAYLOAD + 4];
    size_t flen = 0;
    encode_bridge_frame(s_can_batch, (uint16_t)s_can_batch_len, frame, &flen);
    cdc_write_bin(frame, flen);
    s_can_batch_len = 0;
}

void protocol_can_light_send_rx(uint32_t id, bool ext, bool rtr, uint8_t dlc, const uint8_t *data)
{
    if (!s_can_light_mode) {
        return;
    }
    if (dlc > 8) dlc = 8;
    if (s_can_batch_len + CAN_LIGHT_FRAME_LEN > OTA_FRAME_MAX_PAYLOAD) {
        can_light_flush_batch();
    }
    uint8_t *p = s_can_batch + s_can_batch_len;
    p[0] = (uint8_t)((ext ? CAN_LIGHT_FLAG_EXT : 0) | (rtr ? CAN_LIGHT_FLAG_RTR : 0));
    p[1] = (uint8_t)((id >> 24) & 0xFF);
    p[2] = (uint8_t)((id >> 16) & 0xFF);
    p[3] = (uint8_t)((id >> 8) & 0xFF);
    p[4] = (uint8_t)(id & 0xFF);
    p[5] = dlc;
    memset(p + 6, 0, 8);
    if (data && dlc > 0) {
        memcpy(p + 6, data, dlc);
    }
    s_can_batch_len += CAN_LIGHT_FRAME_LEN;
    /* Flush often enough for UI latency; batch for throughput. */
    if (s_can_batch_len >= CAN_LIGHT_FRAME_LEN * 8) {
        can_light_flush_batch();
    }
}

void protocol_can_light_poll_flush(void)
{
    can_light_flush_batch();
}

static void cdc_write_bin(const uint8_t *data, size_t len)
{
    if (!tud_ready() || !data || len == 0) {
        return;
    }
    size_t off = 0;
    int spins = 0;
    while (off < len && spins < 4000) {
        uint32_t avail = tud_cdc_write_available();
        if (avail == 0) {
            tud_cdc_write_flush();
            vTaskDelay(pdMS_TO_TICKS(1));
            spins++;
            continue;
        }
        size_t n = len - off;
        if (n > avail) {
            n = avail;
        }
        tud_cdc_write(data + off, n);
        off += n;
        spins = 0;
    }
    tud_cdc_write_flush();
}

static void encode_bridge_frame(const uint8_t *payload, uint16_t plen, uint8_t *out, size_t *out_len)
{
    out[0] = OTA_FRAME_MAGIC0;
    out[1] = OTA_FRAME_MAGIC1;
    out[2] = (uint8_t)((plen >> 8) & 0xFF);
    out[3] = (uint8_t)(plen & 0xFF);
    memcpy(out + 4, payload, plen);
    uint32_t crc = esp_crc32_le(0, payload, plen);
    out[4 + plen] = (uint8_t)((crc >> 24) & 0xFF);
    out[5 + plen] = (uint8_t)((crc >> 16) & 0xFF);
    out[6 + plen] = (uint8_t)((crc >> 8) & 0xFF);
    out[7 + plen] = (uint8_t)(crc & 0xFF);
    *out_len = 4u + (size_t)plen + 4u;
}

void protocol_bridge_send_uart_bytes(const uint8_t *data, size_t len)
{
    if (!s_bridge_bin_mode || !data || len == 0) {
        return;
    }
    size_t off = 0;
    uint8_t frame[6 + OTA_FRAME_MAX_PAYLOAD + 4];
    while (off < len) {
        uint16_t n = (uint16_t)((len - off) > OTA_FRAME_MAX_PAYLOAD ? OTA_FRAME_MAX_PAYLOAD : (len - off));
        size_t flen = 0;
        encode_bridge_frame(data + off, n, frame, &flen);
        cdc_write_bin(frame, flen);
        off += n;
    }
}

void protocol_um980_bridge_poll(void)
{
    if (!s_bridge_bin_mode) {
        return;
    }
    uint8_t buf[256];
    int n = um980_uart_read_raw(buf, sizeof(buf));
    if (n > 0) {
        protocol_bridge_send_uart_bytes(buf, (size_t)n);
    }
}

void protocol_send_um980_rsp(const char *cmd, const char *const *lines, int line_count, bool ok)
{
    /* CONFIG dumps are large; keep room to always close a valid JSON object. */
    static char buf[4096];
    size_t pos = 0;
    const size_t close_room = 4; /* ]}\n\0 */
    int n = snprintf(buf, sizeof(buf), "{\"v\":1,\"t\":\"um980Rsp\",\"ok\":%s,\"cmd\":\"",
                     ok ? "true" : "false");
    if (n < 0) return;
    pos = (size_t)n;
    json_escape_append(buf, sizeof(buf) - 32, &pos, cmd ? cmd : "");
    if (pos + 16 >= sizeof(buf) - close_room) {
        snprintf(buf, sizeof(buf),
                 "{\"v\":1,\"t\":\"um980Rsp\",\"ok\":%s,\"cmd\":\"\",\"lines\":[]}\n",
                 ok ? "true" : "false");
        cdc_write_str(buf);
        return;
    }
    memcpy(buf + pos, "\",\"lines\":[", 11);
    pos += 11;
    for (int i = 0; i < line_count; i++) {
        size_t mark = pos;
        if (i > 0) {
            if (pos + 1 + close_room >= sizeof(buf)) break;
            buf[pos++] = ',';
        }
        if (pos + 2 + close_room >= sizeof(buf)) {
            pos = mark;
            break;
        }
        buf[pos++] = '"';
        json_escape_append(buf, sizeof(buf) - close_room - 1, &pos, lines[i] ? lines[i] : "");
        if (pos + 1 + close_room >= sizeof(buf)) {
            /* Revert incomplete element so JSON stays valid. */
            pos = mark;
            break;
        }
        buf[pos++] = '"';
    }
    buf[pos++] = ']';
    buf[pos++] = '}';
    buf[pos++] = '\n';
    buf[pos] = '\0';
    cdc_write_str(buf);
}

static bool extract_json_string(const char *line, const char *key, char *out, size_t out_sz)
{
    char pattern[48];
    snprintf(pattern, sizeof(pattern), "\"%s\"", key);
    const char *p = strstr(line, pattern);
    if (!p) return false;
    p = strchr(p + strlen(pattern), ':');
    if (!p) return false;
    p++;
    while (*p == ' ') p++;
    if (*p != '"') return false;
    p++;
    size_t i = 0;
    while (*p && *p != '"' && i + 1 < out_sz) {
        if (*p == '\\' && p[1]) {
            p++;
            out[i++] = *p++;
        } else {
            out[i++] = *p++;
        }
    }
    out[i] = '\0';
    return true;
}

static uint32_t extract_json_u32(const char *line, const char *key, bool *found)
{
    char pattern[48];
    snprintf(pattern, sizeof(pattern), "\"%s\"", key);
    const char *p = strstr(line, pattern);
    if (!p) {
        if (found) *found = false;
        return 0;
    }
    p = strchr(p + strlen(pattern), ':');
    if (!p) {
        if (found) *found = false;
        return 0;
    }
    if (found) *found = true;
    return (uint32_t)strtoul(p + 1, NULL, 0);
}

static void handle_ota_begin(const char *line)
{
    bool found_size = false;
    bool found_crc = false;
    uint32_t size = extract_json_u32(line, "size", &found_size);
    uint32_t crc = extract_json_u32(line, "crc32", &found_crc);
    char err[64];
    if (!found_size || !found_crc) {
        protocol_send_ota_ack("begin", 0, false, "missing fields");
        return;
    }
    if (!ota_begin(size, crc, err, sizeof(err))) {
        protocol_send_ota_ack("begin", 0, false, err[0] ? err : "begin failed");
        s_ota_bin_mode = false;
        return;
    }
    s_ota_bin_mode = true;
    s_ota_frame_len = 0;
    s_ota_chunks_since_ack = 0;
    s_line_len = 0;
    protocol_send_ota_ack("begin", 0, true, NULL);
}

static void handle_ota_end(void)
{
    char err[64];
    s_ota_bin_mode = false;
    s_ota_frame_len = 0;
    if (!ota_finish(err, sizeof(err))) {
        protocol_send_ota_ack("end", ota_bytes_written(), false, err[0] ? err : "end failed");
        protocol_send_ota_done(false, err[0] ? err : "end failed");
        return;
    }
    protocol_send_ota_ack("end", ota_bytes_written(), true, NULL);
    protocol_send_ota_done(true, NULL);
    // Reboot from app_main — never block TinyUSB RX callback with vTaskDelay.
    s_ota_restart_pending = true;
}

static bool extract_json_bool(const char *line, const char *key, bool default_val)
{
    char pattern[48];
    snprintf(pattern, sizeof(pattern), "\"%s\"", key);
    const char *p = strstr(line, pattern);
    if (!p) return default_val;
    p = strchr(p + strlen(pattern), ':');
    if (!p) return default_val;
    p++;
    while (*p == ' ') p++;
    if (strncmp(p, "true", 4) == 0) return true;
    if (strncmp(p, "false", 5) == 0) return false;
    return default_val;
}

static int hex_nibble(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

static int parse_hex_bytes(const char *hex, uint8_t *out, int max_out)
{
    int n = 0;
    const char *p = hex;
    while (*p && n < max_out) {
        while (*p == ' ' || *p == ':' || *p == '-') p++;
        if (!*p) break;
        int hi = hex_nibble(*p++);
        if (hi < 0) break;
        while (*p == ' ') p++;
        int lo = hex_nibble(*p++);
        if (lo < 0) break;
        out[n++] = (uint8_t)((hi << 4) | lo);
    }
    return n;
}

static void handle_can_tx_json(const char *line)
{
    char id_s[24];
    char data_s[48];
    if (!extract_json_string(line, "id", id_s, sizeof(id_s))) {
        protocol_send_can_ack("tx", false, "missing id");
        return;
    }
    uint32_t id = (uint32_t)strtoul(id_s, NULL, 0);
    bool ext = extract_json_bool(line, "ext", false);
    bool rtr = extract_json_bool(line, "rtr", false);
    uint8_t data[8] = {0};
    int dlc = 0;
    if (extract_json_string(line, "data", data_s, sizeof(data_s))) {
        dlc = parse_hex_bytes(data_s, data, 8);
    }
    bool found = false;
    uint32_t dlc_u = extract_json_u32(line, "dlc", &found);
    if (found && dlc_u <= 8) {
        dlc = (int)dlc_u;
    }
    if (s_can_tx_cb) {
        s_can_tx_cb(id, ext, rtr, (uint8_t)dlc, data);
    } else {
        protocol_send_can_ack("tx", false, "no can");
    }
}

static void handle_can_filter_json(const char *line)
{
    if (extract_json_bool(line, "acceptAll", false)) {
        if (s_can_filter_cb) {
            s_can_filter_cb(true, NULL, NULL, NULL, 0);
        } else {
            protocol_send_can_filter_ack(false, "no can");
        }
        return;
    }
    /* filters:[{"id":"0x280","mask":"0x7FF","ext":false}, ...] — simple scan */
    const char *arr = strstr(line, "\"filters\"");
    if (!arr) {
        protocol_send_can_filter_ack(false, "missing filters");
        return;
    }
    arr = strchr(arr, '[');
    if (!arr) {
        protocol_send_can_filter_ack(false, "bad filters");
        return;
    }
    uint32_t ids[6] = {0};
    uint32_t masks[6] = {0};
    bool exts[6] = {false};
    int count = 0;
    const char *p = arr;
    while (count < 6 && (p = strstr(p, "\"id\"")) != NULL) {
        char id_s[24];
        char mask_s[24];
        if (!extract_json_string(p, "id", id_s, sizeof(id_s))) break;
        ids[count] = (uint32_t)strtoul(id_s, NULL, 0);
        if (extract_json_string(p, "mask", mask_s, sizeof(mask_s))) {
            masks[count] = (uint32_t)strtoul(mask_s, NULL, 0);
        } else {
            masks[count] = extract_json_bool(p, "ext", false) ? 0x1FFFFFFF : 0x7FF;
        }
        exts[count] = extract_json_bool(p, "ext", false);
        count++;
        p += 4;
    }
    if (s_can_filter_cb) {
        s_can_filter_cb(false, ids, masks, exts, count);
    } else {
        protocol_send_can_filter_ack(false, "no can");
    }
}

static void apply_can_light_host_frame(const uint8_t *payload, uint16_t plen)
{
    if (!s_can_tx_cb || plen < CAN_LIGHT_FRAME_LEN) {
        return;
    }
    for (uint16_t off = 0; off + CAN_LIGHT_FRAME_LEN <= plen; off += CAN_LIGHT_FRAME_LEN) {
        const uint8_t *p = payload + off;
        uint8_t flags = p[0];
        uint32_t id = ((uint32_t)p[1] << 24) | ((uint32_t)p[2] << 16) |
                      ((uint32_t)p[3] << 8) | (uint32_t)p[4];
        uint8_t dlc = p[5];
        if (dlc > 8) dlc = 8;
        s_can_tx_cb(id,
                    (flags & CAN_LIGHT_FLAG_EXT) != 0,
                    (flags & CAN_LIGHT_FLAG_RTR) != 0,
                    dlc,
                    p + 6);
    }
}

static void handle_line(const char *line)
{
    if (strstr(line, "\"t\":\"hello\"") || strstr(line, "\"t\": \"hello\"")) {
        protocol_send_hello();
        return;
    }
    if (strstr(line, "\"t\":\"otaBegin\"") || strstr(line, "\"t\": \"otaBegin\"")) {
        handle_ota_begin(line);
        return;
    }
    if (strstr(line, "\"t\":\"otaEnd\"") || strstr(line, "\"t\": \"otaEnd\"")) {
        handle_ota_end();
        return;
    }
    if (strstr(line, "\"t\":\"um980BridgeBegin\"") || strstr(line, "\"t\": \"um980BridgeBegin\"")) {
        if (s_ota_bin_mode || ota_is_active() || s_can_light_mode) {
            protocol_send_um980_bridge_ack("begin", false, "busy");
            return;
        }
        s_bridge_bin_mode = true;
        s_bridge_frame_len = 0;
        um980_uart_set_bridge_mode(true);
        protocol_send_um980_bridge_ack("begin", true, NULL);
        return;
    }
    if (strstr(line, "\"t\":\"um980BridgeEnd\"") || strstr(line, "\"t\": \"um980BridgeEnd\"")) {
        s_bridge_bin_mode = false;
        s_bridge_frame_len = 0;
        um980_uart_set_bridge_mode(false);
        protocol_send_um980_bridge_ack("end", true, NULL);
        return;
    }
    if (strstr(line, "\"t\":\"canLightBegin\"") || strstr(line, "\"t\": \"canLightBegin\"")) {
        if (s_ota_bin_mode || ota_is_active() || s_bridge_bin_mode) {
            protocol_send_can_ack("lightBegin", false, "busy");
            return;
        }
        s_can_light_mode = true;
        s_can_batch_len = 0;
        if (s_can_light_cb) s_can_light_cb(true);
        protocol_send_can_ack("lightBegin", true, NULL);
        return;
    }
    if (strstr(line, "\"t\":\"canLightEnd\"") || strstr(line, "\"t\": \"canLightEnd\"")) {
        can_light_flush_batch();
        s_can_light_mode = false;
        s_can_batch_len = 0;
        if (s_can_light_cb) s_can_light_cb(false);
        protocol_send_can_ack("lightEnd", true, NULL);
        return;
    }
    if (strstr(line, "\"t\":\"canTx\"") || strstr(line, "\"t\": \"canTx\"")) {
        handle_can_tx_json(line);
        return;
    }
    if (strstr(line, "\"t\":\"canBaud\"") || strstr(line, "\"t\": \"canBaud\"")) {
        bool found = false;
        uint32_t baud = extract_json_u32(line, "baud", &found);
        if (!found || !s_can_baud_cb) {
            protocol_send_can_baud(s_hello_can_baud, false);
            return;
        }
        s_can_baud_cb(baud);
        return;
    }
    if (strstr(line, "\"t\":\"canFilter\"") || strstr(line, "\"t\": \"canFilter\"")) {
        handle_can_filter_json(line);
        return;
    }
    if (strstr(line, "\"t\":\"magChipSet\"") || strstr(line, "\"t\": \"magChipSet\"")) {
        char chip[16];
        const char *seen_ptrs[8];
        int seen_count = 0;
        if (!extract_json_string(line, "chip", chip, sizeof(chip))) {
            protocol_send_mag_chip(s_hello_mag_chip, false, s_hello_mag, seen_ptrs, seen_count);
            return;
        }
        if (s_mag_chip_cb) {
            s_mag_chip_cb(chip);
        } else {
            protocol_send_mag_chip(s_hello_mag_chip, false, s_hello_mag, seen_ptrs, seen_count);
        }
        return;
    }
    if (strstr(line, "\"t\":\"reboot\"") || strstr(line, "\"t\": \"reboot\"")) {
        if (s_reboot_cb) {
            s_reboot_cb();
        }
        return;
    }
    if (strstr(line, "\"t\":\"um980Cmd\"") || strstr(line, "\"t\": \"um980Cmd\"")) {
        if (!gnss_is_um980()) {
            protocol_send_um980_rsp("", NULL, 0, false);
            return;
        }
        char cmd[256];
        if (extract_json_string(line, "cmd", cmd, sizeof(cmd)) && s_um980_cb) {
            s_um980_cb(cmd);
        }
        return;
    }
    if (strstr(line, "\"t\":\"um980Baud\"") || strstr(line, "\"t\": \"um980Baud\"")) {
        const char *p = strstr(line, "\"baud\"");
        if (!p) {
            protocol_send_um980_baud(s_hello_baud, false);
            return;
        }
        p = strchr(p, ':');
        if (!p) {
            protocol_send_um980_baud(s_hello_baud, false);
            return;
        }
        int baud = atoi(p + 1);
        if (s_um980_baud_cb) {
            s_um980_baud_cb(baud);
        } else {
            protocol_send_um980_baud(baud, false);
        }
        return;
    }
    if (strstr(line, "\"t\":\"relaySet\"") || strstr(line, "\"t\": \"relaySet\"")) {
        const char *p = strstr(line, "\"mask\"");
        if (!p) {
            return;
        }
        p = strchr(p, ':');
        if (!p) {
            return;
        }
        int mask = atoi(p + 1);
        if (s_relay_cb) {
            s_relay_cb((uint8_t)(mask & 0xFF));
        }
        protocol_send_relay((uint8_t)(mask & 0xFF));
    }
}

static uint16_t be16(const uint8_t *p)
{
    return (uint16_t)((p[0] << 8) | p[1]);
}

static uint32_t be32(const uint8_t *p)
{
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) |
           ((uint32_t)p[2] << 8) | (uint32_t)p[3];
}

static void process_ota_frame(const uint8_t *payload, uint16_t plen)
{
    char err[64];
    if (!ota_write(payload, plen, err, sizeof(err))) {
        protocol_send_ota_ack("chunk", ota_bytes_written(), false, err[0] ? err : "write failed");
        s_ota_bin_mode = false;
        s_ota_frame_len = 0;
        return;
    }
    s_ota_chunks_since_ack++;
    bool complete = ota_bytes_written() >= ota_expected_size();
    if (complete || s_ota_chunks_since_ack >= s_ota_ack_every) {
        protocol_send_ota_ack("chunk", ota_bytes_written(), true, NULL);
        s_ota_chunks_since_ack = 0;
    }
    if (complete) {
        // Host will send otaEnd as NDJSON; leave binary mode.
        s_ota_bin_mode = false;
        s_ota_frame_len = 0;
    }
}

static void feed_ota_byte(uint8_t b)
{
    if (s_ota_frame_len == 0) {
        if (b != OTA_FRAME_MAGIC0) {
            return; // resync
        }
        s_ota_frame[s_ota_frame_len++] = b;
        return;
    }
    if (s_ota_frame_len == 1) {
        if (b != OTA_FRAME_MAGIC1) {
            s_ota_frame_len = (b == OTA_FRAME_MAGIC0) ? 1 : 0;
            if (s_ota_frame_len) s_ota_frame[0] = b;
            return;
        }
        s_ota_frame[s_ota_frame_len++] = b;
        return;
    }
    s_ota_frame[s_ota_frame_len++] = b;
    if (s_ota_frame_len < 4) {
        return;
    }
    uint16_t plen = be16(&s_ota_frame[2]);
    if (plen == 0 || plen > OTA_FRAME_MAX_PAYLOAD) {
        protocol_send_ota_ack("chunk", ota_bytes_written(), false, "bad len");
        ota_abort();
        s_ota_bin_mode = false;
        s_ota_frame_len = 0;
        return;
    }
    size_t need = 4u + (size_t)plen + 4u;
    if (s_ota_frame_len < need) {
        return;
    }
    const uint8_t *payload = &s_ota_frame[4];
    uint32_t got_crc = be32(&s_ota_frame[4 + plen]);
    uint32_t expect_crc = esp_crc32_le(0, payload, plen);
    if (got_crc != expect_crc) {
        protocol_send_ota_ack("chunk", ota_bytes_written(), false, "chunk crc");
        ota_abort();
        s_ota_bin_mode = false;
        s_ota_frame_len = 0;
        return;
    }
    process_ota_frame(payload, plen);
    s_ota_frame_len = 0;
}

static void process_bridge_frame(const uint8_t *payload, uint16_t plen)
{
    if (!um980_uart_write_raw(payload, plen)) {
        protocol_send_um980_bridge_ack("chunk", false, "uart_write");
    }
}

void protocol_on_rx_bytes(const uint8_t *data, size_t len)
{
    for (size_t i = 0; i < len; i++) {
        if (s_ota_bin_mode) {
            feed_ota_byte(data[i]);
            continue;
        }
        if (s_bridge_bin_mode || s_can_light_mode) {
            uint8_t b = data[i];
            uint8_t *frame = s_bridge_bin_mode ? s_bridge_frame : s_bridge_frame;
            size_t *flen = s_bridge_bin_mode ? &s_bridge_frame_len : &s_bridge_frame_len;
            /* Reuse bridge frame buffer for CAN light host→device frames. */
            if (*flen == 0 && b == '{') {
                s_line_len = 0;
                s_line[s_line_len++] = '{';
                size_t j = i + 1;
                for (; j < len; j++) {
                    char c = (char)data[j];
                    if (c == '\n' || c == '\r') {
                        if (s_line_len > 0) {
                            s_line[s_line_len] = '\0';
                            handle_line(s_line);
                            s_line_len = 0;
                        }
                        i = j;
                        break;
                    }
                    if (s_line_len + 1 < sizeof(s_line)) {
                        s_line[s_line_len++] = c;
                    }
                    if (j + 1 == len) {
                        i = j;
                    }
                }
                continue;
            }
            if (*flen == 0) {
                if (b != OTA_FRAME_MAGIC0) {
                    continue;
                }
                frame[(*flen)++] = b;
                continue;
            }
            if (*flen == 1) {
                if (b != OTA_FRAME_MAGIC1) {
                    *flen = (b == OTA_FRAME_MAGIC0) ? 1 : 0;
                    if (*flen) frame[0] = b;
                    continue;
                }
                frame[(*flen)++] = b;
                continue;
            }
            frame[(*flen)++] = b;
            if (*flen < 4) {
                continue;
            }
            uint16_t plen = be16(&frame[2]);
            if (plen == 0 || plen > OTA_FRAME_MAX_PAYLOAD) {
                *flen = 0;
                continue;
            }
            size_t need = 4u + (size_t)plen + 4u;
            if (*flen < need) {
                continue;
            }
            const uint8_t *payload = &frame[4];
            uint32_t got_crc = be32(&frame[4 + plen]);
            uint32_t expect_crc = esp_crc32_le(0, payload, plen);
            if (got_crc == expect_crc) {
                if (s_bridge_bin_mode) {
                    process_bridge_frame(payload, plen);
                } else {
                    apply_can_light_host_frame(payload, plen);
                }
            }
            *flen = 0;
            continue;
        }
        char c = (char)data[i];
        if (c == '\n' || c == '\r') {
            if (s_line_len > 0) {
                s_line[s_line_len] = '\0';
                handle_line(s_line);
                s_line_len = 0;
            }
            continue;
        }
        if (s_line_len + 1 < sizeof(s_line)) {
            s_line[s_line_len++] = c;
        } else {
            s_line_len = 0;
        }
    }
}
