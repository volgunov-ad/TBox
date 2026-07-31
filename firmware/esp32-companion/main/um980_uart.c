#include "um980_uart.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "driver/uart.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "nvs.h"
#include "nvs_flash.h"

#define UM980_UART_NUM UART_NUM_1
#define UM980_UART_RX_GPIO 18
#define UM980_UART_TX_GPIO 17
#define UM980_UART_BAUD_DEFAULT 115200
#define UM980_BUF 512
#define UM980_NVS_NS "um980"
#define UM980_NVS_KEY_BAUD "baud"

static const char *TAG = "um980_uart";

static char s_line[UM980_BUF];
static size_t s_line_len;
static um980_fix_t s_fix;
static int s_baud = UM980_UART_BAUD_DEFAULT;
static SemaphoreHandle_t s_uart_mu;

static bool baud_allowed(int baud)
{
    switch (baud) {
    case 9600:
    case 19200:
    case 38400:
    case 57600:
    case 115200:
    case 230400:
    case 460800:
        return true;
    default:
        return false;
    }
}

static int load_baud_from_nvs(void)
{
    nvs_handle_t h;
    if (nvs_open(UM980_NVS_NS, NVS_READONLY, &h) != ESP_OK) {
        return UM980_UART_BAUD_DEFAULT;
    }
    int32_t baud = UM980_UART_BAUD_DEFAULT;
    esp_err_t err = nvs_get_i32(h, UM980_NVS_KEY_BAUD, &baud);
    nvs_close(h);
    if (err != ESP_OK || !baud_allowed((int)baud)) {
        return UM980_UART_BAUD_DEFAULT;
    }
    return (int)baud;
}

static bool save_baud_to_nvs(int baud)
{
    nvs_handle_t h;
    if (nvs_open(UM980_NVS_NS, NVS_READWRITE, &h) != ESP_OK) {
        return false;
    }
    esp_err_t err = nvs_set_i32(h, UM980_NVS_KEY_BAUD, baud);
    if (err == ESP_OK) {
        err = nvs_commit(h);
    }
    nvs_close(h);
    return err == ESP_OK;
}

static int hex_nibble(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return -1;
}

static bool nmea_checksum_ok(const char *sentence)
{
    if (sentence[0] != '$') return false;
    const char *star = strrchr(sentence, '*');
    if (!star || strlen(star) < 3) return false;
    unsigned char sum = 0;
    for (const char *p = sentence + 1; p < star; p++) {
        sum ^= (unsigned char)(*p);
    }
    int hi = hex_nibble(star[1]);
    int lo = hex_nibble(star[2]);
    if (hi < 0 || lo < 0) return false;
    return sum == (unsigned char)((hi << 4) | lo);
}

static bool looks_like_nav_nmea(const char *line)
{
    return strstr(line, "RMC") != NULL || strstr(line, "GGA") != NULL ||
           strstr(line, "GSA") != NULL || strstr(line, "GSV") != NULL ||
           strstr(line, "VTG") != NULL || strstr(line, "ZDA") != NULL ||
           strstr(line, "GLL") != NULL || strstr(line, "GST") != NULL;
}

static double nmea_deg_to_decimal(const char *field, char hemi)
{
    if (!field || !*field) return 0.0;
    double v = atof(field);
    int deg = (int)(v / 100.0);
    double minutes = v - deg * 100.0;
    double dec = deg + minutes / 60.0;
    if (hemi == 'S' || hemi == 'W') dec = -dec;
    return dec;
}

static void parse_rmc(char *line)
{
    char *save = NULL;
    char *tok = strtok_r(line, ",", &save);
    int idx = 0;
    char status = 'V';
    char lat_s[24] = {0};
    char lat_h = 0;
    char lon_s[24] = {0};
    char lon_h = 0;
    char time_s[16] = {0};
    char date_s[16] = {0};
    float speed_kn = 0;
    float course = 0;
    while (tok) {
        switch (idx) {
        case 1: strncpy(time_s, tok, sizeof(time_s) - 1); break;
        case 2: status = tok[0]; break;
        case 3: strncpy(lat_s, tok, sizeof(lat_s) - 1); break;
        case 4: lat_h = tok[0]; break;
        case 5: strncpy(lon_s, tok, sizeof(lon_s) - 1); break;
        case 6: lon_h = tok[0]; break;
        case 7: speed_kn = (float)atof(tok); break;
        case 8: course = (float)atof(tok); break;
        case 9: strncpy(date_s, tok, sizeof(date_s) - 1); break;
        default: break;
        }
        tok = strtok_r(NULL, ",", &save);
        idx++;
    }
    s_fix.fix = (status == 'A') ? 1 : 0;
    s_fix.lat = nmea_deg_to_decimal(lat_s, lat_h);
    s_fix.lon = nmea_deg_to_decimal(lon_s, lon_h);
    s_fix.speed_kmh = speed_kn * 1.852f;
    s_fix.course = course;
    if (strlen(time_s) >= 6 && strlen(date_s) >= 6) {
        int hh = (time_s[0] - '0') * 10 + (time_s[1] - '0');
        int mm = (time_s[2] - '0') * 10 + (time_s[3] - '0');
        int ss = (time_s[4] - '0') * 10 + (time_s[5] - '0');
        int dd = (date_s[0] - '0') * 10 + (date_s[1] - '0');
        int mo = (date_s[2] - '0') * 10 + (date_s[3] - '0');
        int yy = (date_s[4] - '0') * 10 + (date_s[5] - '0');
        snprintf(s_fix.utc, sizeof(s_fix.utc),
                 "20%02d-%02d-%02dT%02d:%02d:%02dZ", yy, mo, dd, hh, mm, ss);
    }
    s_fix.valid = true;
}

static void parse_gga(char *line)
{
    char *save = NULL;
    char *tok = strtok_r(line, ",", &save);
    int idx = 0;
    int fix = 0;
    int sats = 0;
    float hdop = 0;
    char lat_s[24] = {0};
    char lat_h = 0;
    char lon_s[24] = {0};
    char lon_h = 0;
    double alt = 0;
    while (tok) {
        switch (idx) {
        case 2: strncpy(lat_s, tok, sizeof(lat_s) - 1); break;
        case 3: lat_h = tok[0]; break;
        case 4: strncpy(lon_s, tok, sizeof(lon_s) - 1); break;
        case 5: lon_h = tok[0]; break;
        case 6: fix = atoi(tok); break;
        case 7: sats = atoi(tok); break;
        case 8: hdop = (float)atof(tok); break;
        case 9: alt = atof(tok); break;
        default: break;
        }
        tok = strtok_r(NULL, ",", &save);
        idx++;
    }
    if (fix > 0) {
        s_fix.fix = fix;
        s_fix.lat = nmea_deg_to_decimal(lat_s, lat_h);
        s_fix.lon = nmea_deg_to_decimal(lon_s, lon_h);
        s_fix.alt = alt;
        s_fix.sats_used = sats;
        s_fix.sats_vis = sats;
        if (hdop > 0.0f) {
            s_fix.hdop = hdop;
        }
        s_fix.valid = true;
    }
}

/**
 * `$--GSA,mode,fixType,sat1..sat12,pdop,hdop,vdop`
 */
static void parse_gsa(char *line)
{
    char *save = NULL;
    char *tok = strtok_r(line, ",", &save);
    int idx = 0;
    int using_sats = 0;
    float pdop = 0;
    float hdop = 0;
    float vdop = 0;
    while (tok) {
        if (idx >= 3 && idx <= 14) {
            if (tok[0] != '\0' && atoi(tok) > 0) {
                using_sats++;
            }
        } else if (idx == 15) {
            pdop = (float)atof(tok);
        } else if (idx == 16) {
            hdop = (float)atof(tok);
        } else if (idx == 17) {
            vdop = (float)atof(tok);
        }
        tok = strtok_r(NULL, ",", &save);
        idx++;
    }
    if (using_sats > 0) {
        s_fix.sats_used = using_sats;
    }
    if (pdop > 0.0f) {
        s_fix.pdop = pdop;
    }
    if (hdop > 0.0f) {
        s_fix.hdop = hdop;
    }
    if (vdop > 0.0f) {
        s_fix.vdop = vdop;
    }
}

static void handle_nmea(char *line)
{
    if (!nmea_checksum_ok(line)) return;
    char copy[UM980_BUF];
    strncpy(copy, line, sizeof(copy) - 1);
    copy[sizeof(copy) - 1] = '\0';
    if (strstr(copy, "RMC")) {
        parse_rmc(copy);
    } else if (strstr(copy, "GGA")) {
        parse_gga(copy);
    } else if (strstr(copy, "GSA")) {
        parse_gsa(copy);
    }
}

static void feed_byte(char c, void (*on_line)(const char *line, void *ctx), void *ctx)
{
    if (c == '\n' || c == '\r') {
        if (s_line_len > 0) {
            s_line[s_line_len] = '\0';
            if (on_line) {
                on_line(s_line, ctx);
            } else {
                handle_nmea(s_line);
            }
            s_line_len = 0;
        }
        return;
    }
    if (s_line_len + 1 < sizeof(s_line)) {
        s_line[s_line_len++] = c;
    } else {
        s_line_len = 0;
    }
}

void um980_uart_init(void)
{
    memset(&s_fix, 0, sizeof(s_fix));
    s_line_len = 0;
    s_uart_mu = xSemaphoreCreateMutex();
    configASSERT(s_uart_mu);

    esp_err_t nvs_err = nvs_flash_init();
    if (nvs_err == ESP_ERR_NVS_NO_FREE_PAGES || nvs_err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        nvs_err = nvs_flash_init();
    }
    ESP_ERROR_CHECK(nvs_err);

    s_baud = load_baud_from_nvs();
    ESP_LOGI(TAG, "UM980 UART baud %d", s_baud);

    const uart_config_t cfg = {
        .baud_rate = s_baud,
        .data_bits = UART_DATA_8_BITS,
        .parity = UART_PARITY_DISABLE,
        .stop_bits = UART_STOP_BITS_1,
        .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
        .source_clk = UART_SCLK_DEFAULT,
    };
    uart_driver_install(UM980_UART_NUM, 2048, 512, 0, NULL, 0);
    uart_param_config(UM980_UART_NUM, &cfg);
    uart_set_pin(UM980_UART_NUM, UM980_UART_TX_GPIO, UM980_UART_RX_GPIO,
                 UART_PIN_NO_CHANGE, UART_PIN_NO_CHANGE);
}

int um980_uart_get_baud(void)
{
    return s_baud;
}

bool um980_uart_set_baud(int baud)
{
    if (!baud_allowed(baud)) {
        ESP_LOGW(TAG, "rejected baud %d", baud);
        return false;
    }
    xSemaphoreTake(s_uart_mu, portMAX_DELAY);
    esp_err_t err = uart_set_baudrate(UM980_UART_NUM, (uint32_t)baud);
    if (err != ESP_OK) {
        xSemaphoreGive(s_uart_mu);
        ESP_LOGE(TAG, "uart_set_baudrate failed: %s", esp_err_to_name(err));
        return false;
    }
    s_baud = baud;
    xSemaphoreGive(s_uart_mu);
    if (!save_baud_to_nvs(baud)) {
        ESP_LOGW(TAG, "NVS save failed for baud %d (runtime applied)", baud);
    }
    ESP_LOGI(TAG, "UM980 UART baud set to %d", baud);
    return true;
}

void um980_uart_write_cmd(const char *cmd)
{
    if (!cmd || !*cmd) return;
    xSemaphoreTake(s_uart_mu, portMAX_DELAY);
    uart_write_bytes(UM980_UART_NUM, cmd, strlen(cmd));
    uart_write_bytes(UM980_UART_NUM, "\r\n", 2);
    xSemaphoreGive(s_uart_mu);
}

typedef struct {
    char (*lines)[UM980_RSP_LINE_LEN];
    int max_lines;
    int count;
} collect_ctx_t;

static void on_collect_line(const char *line, void *ctx)
{
    collect_ctx_t *c = (collect_ctx_t *)ctx;
    if (looks_like_nav_nmea(line) && nmea_checksum_ok(line)) {
        char copy[UM980_BUF];
        strncpy(copy, line, sizeof(copy) - 1);
        copy[sizeof(copy) - 1] = '\0';
        handle_nmea(copy);
        return;
    }
    if (c->count >= c->max_lines) return;
    strncpy(c->lines[c->count], line, UM980_RSP_LINE_LEN - 1);
    c->lines[c->count][UM980_RSP_LINE_LEN - 1] = '\0';
    c->count++;
}

void um980_uart_exec_cmd(const char *cmd, char lines[][UM980_RSP_LINE_LEN], int max_lines,
                         int *out_count, int timeout_ms)
{
    collect_ctx_t ctx = { .lines = lines, .max_lines = max_lines, .count = 0 };
    int64_t deadline = esp_timer_get_time() + (int64_t)timeout_ms * 1000;
    uint8_t buf[128];
    xSemaphoreTake(s_uart_mu, portMAX_DELAY);
    if (cmd && *cmd) {
        uart_write_bytes(UM980_UART_NUM, cmd, strlen(cmd));
        uart_write_bytes(UM980_UART_NUM, "\r\n", 2);
    }
    while (esp_timer_get_time() < deadline) {
        int n = uart_read_bytes(UM980_UART_NUM, buf, sizeof(buf), pdMS_TO_TICKS(20));
        for (int i = 0; i < n; i++) {
            feed_byte((char)buf[i], on_collect_line, &ctx);
        }
        bool saw_ok = false;
        for (int i = 0; i < ctx.count; i++) {
            if (strstr(ctx.lines[i], "OK") || strstr(ctx.lines[i], "ok") ||
                strstr(ctx.lines[i], "PARSING FAILD") || strstr(ctx.lines[i], "GRAMMAR ERROR")) {
                saw_ok = true;
                break;
            }
        }
        if (saw_ok) {
            /* VERSIONA / MODE / CONFIG dumps often emit payload before or right after OK. */
            int64_t grace_end = esp_timer_get_time() + 500LL * 1000;
            while (esp_timer_get_time() < grace_end && esp_timer_get_time() < deadline) {
                n = uart_read_bytes(UM980_UART_NUM, buf, sizeof(buf), pdMS_TO_TICKS(20));
                for (int i = 0; i < n; i++) {
                    feed_byte((char)buf[i], on_collect_line, &ctx);
                }
                if (n <= 0) {
                    vTaskDelay(pdMS_TO_TICKS(10));
                }
            }
            if (out_count) *out_count = ctx.count;
            xSemaphoreGive(s_uart_mu);
            return;
        }
        if (n <= 0) {
            vTaskDelay(pdMS_TO_TICKS(10));
        }
    }
    if (out_count) *out_count = ctx.count;
    xSemaphoreGive(s_uart_mu);
}

void um980_uart_collect_replies(char lines[][UM980_RSP_LINE_LEN], int max_lines,
                                int *out_count, int timeout_ms)
{
    um980_uart_exec_cmd(NULL, lines, max_lines, out_count, timeout_ms);
}

bool um980_uart_poll(um980_fix_t *out)
{
    if (xSemaphoreTake(s_uart_mu, 0) != pdTRUE) {
        return false; // cmd worker holds UART
    }
    uint8_t buf[128];
    int n = uart_read_bytes(UM980_UART_NUM, buf, sizeof(buf), 0);
    for (int i = 0; i < n; i++) {
        feed_byte((char)buf[i], NULL, NULL);
    }
    bool have = s_fix.valid;
    if (have) {
        *out = s_fix;
        s_fix.valid = false;
    }
    xSemaphoreGive(s_uart_mu);
    return have;
}
