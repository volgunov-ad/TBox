#include "gnss_detect.h"

#include <string.h>

#include "driver/uart.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "nvs.h"

#include "um980_uart.h"

static const char *TAG = "gnss_detect";

#define GNSS_UART_NUM UART_NUM_1
#define GNSS_NVS_NS "gnss"
#define GNSS_NVS_KEY_CHIP "chip"
#define GNSS_NVS_KEY_MODEL "model"

static gnss_chip_t s_chip = GNSS_CHIP_NONE;
static char s_model[64];
static char s_chip_id[16] = "none";

static bool looks_like_nav_nmea(const char *line)
{
    return strstr(line, "RMC") != NULL || strstr(line, "GGA") != NULL ||
           strstr(line, "GSA") != NULL || strstr(line, "GSV") != NULL;
}

static bool nmea_checksum_ok(const char *sentence)
{
    if (!sentence || sentence[0] != '$') return false;
    const char *star = strrchr(sentence, '*');
    if (!star || strlen(star) < 3) return false;
    unsigned char sum = 0;
    for (const char *p = sentence + 1; p < star; p++) {
        sum ^= (unsigned char)(*p);
    }
    int hi = (star[1] >= 'A') ? (star[1] - 'A' + 10) : (star[1] - '0');
    int lo = (star[2] >= 'A') ? (star[2] - 'A' + 10) : (star[2] - '0');
    if (hi < 0 || hi > 15 || lo < 0 || lo > 15) return false;
    return sum == (unsigned char)((hi << 4) | lo);
}

static void flush_uart(void)
{
    uint8_t buf[128];
    while (uart_read_bytes(GNSS_UART_NUM, buf, sizeof(buf), pdMS_TO_TICKS(10)) > 0) {
    }
}

static bool apply_baud(int baud)
{
    if (!um980_uart_set_baud(baud)) {
        return false;
    }
    flush_uart();
    vTaskDelay(pdMS_TO_TICKS(50));
    return true;
}

static uint8_t ubx_ck_sum_a, ubx_ck_sum_b;

static void ubx_ck_reset(void)
{
    ubx_ck_sum_a = 0;
    ubx_ck_sum_b = 0;
}

static void ubx_ck_feed(uint8_t b)
{
    ubx_ck_sum_a = (uint8_t)(ubx_ck_sum_a + b);
    ubx_ck_sum_b = (uint8_t)(ubx_ck_sum_b + ubx_ck_sum_a);
}

static void ubx_mon_ver_poll(void)
{
    static const uint8_t frame[] = {
        0xB5, 0x62, 0x0A, 0x04, 0x00, 0x00, 0x0E, 0x34,
    };
    um980_uart_write_raw(frame, sizeof(frame));
}

typedef struct {
    bool got_versiona;
    bool got_ubx_mon_ver;
    bool got_nmea;
    char version_line[256];
    char ublox_model[64];
} probe_state_t;

static void feed_probe_byte(char c, char *line, size_t *line_len, size_t line_cap,
                            probe_state_t *st)
{
    if (c == '\n' || c == '\r') {
        if (*line_len == 0) return;
        line[*line_len] = '\0';
        if (strstr(line, "#VERSIONA") != NULL || strstr(line, "VERSIONA,") != NULL) {
            st->got_versiona = true;
            strncpy(st->version_line, line, sizeof(st->version_line) - 1);
            st->version_line[sizeof(st->version_line) - 1] = '\0';
        } else if (looks_like_nav_nmea(line) && nmea_checksum_ok(line)) {
            st->got_nmea = true;
        }
        *line_len = 0;
        return;
    }
    if (*line_len + 1 < line_cap) {
        line[(*line_len)++] = c;
    } else {
        *line_len = 0;
    }
}

static void feed_ubx_byte(uint8_t b, probe_state_t *st, int *ubx_state, int *payload_len,
                          int *payload_idx, uint8_t *payload, size_t payload_cap)
{
    switch (*ubx_state) {
    case 0:
        if (b == 0xB5) *ubx_state = 1;
        break;
    case 1:
        *ubx_state = (b == 0x62) ? 2 : 0;
        break;
    case 2:
        ubx_ck_reset();
        ubx_ck_feed(b);
        *ubx_state = 3;
        break;
    case 3:
        ubx_ck_feed(b);
        if (b == 0x0A) {
            *ubx_state = 4;
        } else {
            *ubx_state = 0;
        }
        break;
    case 4:
        ubx_ck_feed(b);
        if (b == 0x04) {
            *ubx_state = 5;
        } else {
            *ubx_state = 0;
        }
        break;
    case 5:
        ubx_ck_feed(b);
        *payload_len = b;
        *payload_idx = 0;
        *ubx_state = 6;
        break;
    case 6:
        ubx_ck_feed(b);
        *payload_len |= ((int)b << 8);
        *payload_idx = 0;
        if (*payload_len <= 0 || *payload_len > (int)payload_cap) {
            *ubx_state = 0;
        } else {
            *ubx_state = 7;
        }
        break;
    case 7:
        ubx_ck_feed(b);
        if (*payload_idx < *payload_len && *payload_idx < (int)payload_cap) {
            payload[(*payload_idx)++] = b;
        }
        if (*payload_idx >= *payload_len) {
            *ubx_state = 8;
        }
        break;
    case 8:
        ubx_ck_feed(b);
        *ubx_state = 9;
        break;
    case 9:
        ubx_ck_feed(b);
        if (b == ubx_ck_sum_a) {
            *ubx_state = 10;
        } else {
            *ubx_state = 0;
        }
        break;
    case 10:
        *ubx_state = 0;
        if (b == ubx_ck_sum_b && *payload_len > 0) {
            st->got_ubx_mon_ver = true;
            size_t n = (size_t)(*payload_len < 63 ? *payload_len : 63);
            memcpy(st->ublox_model, payload, n);
            st->ublox_model[n] = '\0';
            for (size_t i = 0; i < n; i++) {
                if (st->ublox_model[i] == '\0') {
                    st->ublox_model[i] = ' ';
                }
            }
        }
        break;
    default:
        *ubx_state = 0;
        break;
    }
}

static bool probe_at_baud(int baud, probe_state_t *st)
{
    memset(st, 0, sizeof(*st));
    if (!apply_baud(baud)) return false;

    um980_uart_write_cmd("VERSIONA");
    vTaskDelay(pdMS_TO_TICKS(100));
    ubx_mon_ver_poll();

    char line[256];
    size_t line_len = 0;
    uint8_t ubx_payload[128];
    int ubx_state = 0;
    int payload_len = 0;
    int payload_idx = 0;

    int64_t deadline = esp_timer_get_time() + 1800LL * 1000;
    while (esp_timer_get_time() < deadline) {
        uint8_t buf[128];
        int n = um980_uart_read_raw(buf, sizeof(buf));
        for (int i = 0; i < n; i++) {
            feed_probe_byte((char)buf[i], line, &line_len, sizeof(line), st);
            feed_ubx_byte(buf[i], st, &ubx_state, &payload_len, &payload_idx,
                          ubx_payload, sizeof(ubx_payload));
        }
        if (st->got_versiona || st->got_ubx_mon_ver) {
            break;
        }
        if (st->got_nmea) {
            break;
        }
        if (n <= 0) {
            vTaskDelay(pdMS_TO_TICKS(10));
        }
    }
    return st->got_versiona || st->got_ubx_mon_ver || st->got_nmea;
}

static void set_chip(gnss_chip_t chip, const char *chip_id, const char *model)
{
    s_chip = chip;
    strncpy(s_chip_id, chip_id, sizeof(s_chip_id) - 1);
    s_chip_id[sizeof(s_chip_id) - 1] = '\0';
    strncpy(s_model, model ? model : "", sizeof(s_model) - 1);
    s_model[sizeof(s_model) - 1] = '\0';

    nvs_handle_t h;
    if (nvs_open(GNSS_NVS_NS, NVS_READWRITE, &h) == ESP_OK) {
        nvs_set_i32(h, GNSS_NVS_KEY_CHIP, (int32_t)chip);
        nvs_set_str(h, GNSS_NVS_KEY_MODEL, s_model);
        nvs_commit(h);
        nvs_close(h);
    }
}

static void classify_probe(const probe_state_t *st)
{
    if (st->got_versiona) {
        const char *model = "UM980";
        if (strstr(st->version_line, "UM982") != NULL) {
            model = "UM982";
        } else if (strstr(st->version_line, "UM980") != NULL) {
            model = "UM980";
        }
        set_chip(GNSS_CHIP_UM980, "um980", model);
        ESP_LOGI(TAG, "detected UM980 (%s) @ %d", model, um980_uart_get_baud());
        return;
    }
    if (st->got_ubx_mon_ver) {
        const char *chip_id = "ublox";
        if (strstr(st->ublox_model, "M8") != NULL || strstr(st->ublox_model, "m8") != NULL) {
            chip_id = "neo-m8n";
        }
        set_chip(GNSS_CHIP_UBLOX, chip_id, st->ublox_model);
        ESP_LOGI(TAG, "detected u-blox %s (%s) @ %d", chip_id, st->ublox_model,
                 um980_uart_get_baud());
        return;
    }
    if (st->got_nmea) {
        set_chip(GNSS_CHIP_NMEA, "nmea", "NMEA");
        ESP_LOGI(TAG, "detected generic NMEA @ %d", um980_uart_get_baud());
        return;
    }
    set_chip(GNSS_CHIP_NONE, "none", "");
    ESP_LOGW(TAG, "no GNSS detected");
}

void gnss_detect_run(void)
{
    static const int bauds[] = { 115200, 9600, 38400, 57600, 230400, 460800 };
    int first = um980_uart_get_baud();
    probe_state_t st;

    for (size_t round = 0; round < 2; round++) {
        for (size_t i = 0; i < sizeof(bauds) / sizeof(bauds[0]); i++) {
            int baud = (round == 0 && i == 0) ? first : bauds[i];
            if (round == 0 && i > 0 && baud == first) continue;
            if (probe_at_baud(baud, &st)) {
                classify_probe(&st);
                return;
            }
        }
    }
    set_chip(GNSS_CHIP_NONE, "none", "");
}

gnss_chip_t gnss_chip_type(void)
{
    return s_chip;
}

const char *gnss_chip_id(void)
{
    return s_chip_id;
}

const char *gnss_model_label(void)
{
    return s_model;
}

bool gnss_is_um980(void)
{
    return s_chip == GNSS_CHIP_UM980;
}

bool gnss_uart_active(void)
{
    return s_chip != GNSS_CHIP_NONE;
}
