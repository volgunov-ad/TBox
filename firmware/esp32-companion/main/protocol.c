#include "protocol.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_crc.h"
#include "esp_system.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "ota_update.h"
#include "tinyusb.h"
#include "tusb_cdc_acm.h"

static char s_line[512];
static size_t s_line_len;
static protocol_relay_set_cb_t s_relay_cb;
static protocol_um980_cmd_cb_t s_um980_cb;
static protocol_um980_baud_cb_t s_um980_baud_cb;
static protocol_reboot_cb_t s_reboot_cb;
static int s_hello_baud = ESP_COMPANION_DEFAULT_UM980_BAUD;

/** After otaBegin ACK: parse binary frames until expected size written. */
static bool s_ota_bin_mode;
static uint8_t s_ota_frame[6 + OTA_FRAME_MAX_PAYLOAD + 4];
static size_t s_ota_frame_len;
static uint32_t s_ota_ack_every = 8;
static uint32_t s_ota_chunks_since_ack;
static volatile bool s_ota_restart_pending;

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
    s_hello_baud = ESP_COMPANION_DEFAULT_UM980_BAUD;
    s_ota_bin_mode = false;
    s_ota_frame_len = 0;
    s_ota_chunks_since_ack = 0;
    s_ota_restart_pending = false;
}

bool protocol_ota_active(void)
{
    return ota_is_active() || s_ota_bin_mode;
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

void protocol_send_hello(void)
{
    char buf[224];
    snprintf(buf, sizeof(buf),
             "{\"v\":1,\"t\":\"hello\",\"fw\":\"%s\",\"gpioIn\":%d,\"relays\":%d,\"um980\":true,\"baud\":%d}\n",
             ESP_COMPANION_FW_VERSION,
             ESP_COMPANION_GPIO_IN_COUNT,
             ESP_COMPANION_RELAY_COUNT,
             s_hello_baud);
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
                       int sats_used, int sats_vis, const char *utc_iso)
{
    char buf[320];
    snprintf(buf, sizeof(buf),
             "{\"v\":1,\"t\":\"gps\",\"fix\":%d,\"lat\":%.7f,\"lon\":%.7f,\"alt\":%.2f,"
             "\"speedKmh\":%.2f,\"course\":%.2f,\"satsUsed\":%d,\"satsVis\":%d,\"utc\":\"%s\"}\n",
             fix, lat, lon, alt, speed_kmh, course, sats_used, sats_vis,
             utc_iso ? utc_iso : "");
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
    if (strstr(line, "\"t\":\"reboot\"") || strstr(line, "\"t\": \"reboot\"")) {
        if (s_reboot_cb) {
            s_reboot_cb();
        }
        return;
    }
    if (strstr(line, "\"t\":\"um980Cmd\"") || strstr(line, "\"t\": \"um980Cmd\"")) {
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

void protocol_on_rx_bytes(const uint8_t *data, size_t len)
{
    for (size_t i = 0; i < len; i++) {
        if (s_ota_bin_mode) {
            feed_ota_byte(data[i]);
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
