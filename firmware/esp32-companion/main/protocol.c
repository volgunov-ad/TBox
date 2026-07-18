#include "protocol.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "tinyusb.h"
#include "tusb_cdc_acm.h"

static char s_line[512];
static size_t s_line_len;
static protocol_relay_set_cb_t s_relay_cb;

static void cdc_write_str(const char *s)
{
    if (!tud_cdc_connected()) {
        return;
    }
    tud_cdc_write(s, strlen(s));
    tud_cdc_write_flush();
}

void protocol_init(void)
{
    s_line_len = 0;
    s_relay_cb = NULL;
}

void protocol_set_relay_callback(protocol_relay_set_cb_t cb)
{
    s_relay_cb = cb;
}

void protocol_send_hello(void)
{
    char buf[192];
    snprintf(buf, sizeof(buf),
             "{\"v\":1,\"t\":\"hello\",\"fw\":\"%s\",\"gpioIn\":%d,\"relays\":%d,\"um980\":true}\n",
             ESP_COMPANION_FW_VERSION,
             ESP_COMPANION_GPIO_IN_COUNT,
             ESP_COMPANION_RELAY_COUNT);
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

static void handle_line(const char *line)
{
    if (strstr(line, "\"t\":\"hello\"") || strstr(line, "\"t\": \"hello\"")) {
        protocol_send_hello();
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

void protocol_on_rx_bytes(const uint8_t *data, size_t len)
{
    for (size_t i = 0; i < len; i++) {
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
