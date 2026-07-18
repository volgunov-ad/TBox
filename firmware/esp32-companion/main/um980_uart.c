#include "um980_uart.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "driver/gpio.h"
#include "driver/uart.h"

#define UM980_UART_NUM UART_NUM_1
#define UM980_UART_RX_GPIO 18
#define UM980_UART_TX_GPIO 17
#define UM980_UART_BAUD 115200
#define UM980_BUF 512

static char s_line[UM980_BUF];
static size_t s_line_len;
static um980_fix_t s_fix;

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
    // $GNRMC,hhmmss.ss,A,llll.ll,a,yyyyy.yy,a,x.x,x.x,ddmmyy,,,A*hh
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
        s_fix.valid = true;
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
    }
}

void um980_uart_init(void)
{
    memset(&s_fix, 0, sizeof(s_fix));
    s_line_len = 0;
    const uart_config_t cfg = {
        .baud_rate = UM980_UART_BAUD,
        .data_bits = UART_DATA_8_BITS,
        .parity = UART_PARITY_DISABLE,
        .stop_bits = UART_STOP_BITS_1,
        .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
        .source_clk = UART_SCLK_DEFAULT,
    };
    uart_driver_install(UM980_UART_NUM, 2048, 0, 0, NULL, 0);
    uart_param_config(UM980_UART_NUM, &cfg);
    uart_set_pin(UM980_UART_NUM, UM980_UART_TX_GPIO, UM980_UART_RX_GPIO,
                 UART_PIN_NO_CHANGE, UART_PIN_NO_CHANGE);
}

bool um980_uart_poll(um980_fix_t *out)
{
    uint8_t buf[128];
    int n = uart_read_bytes(UM980_UART_NUM, buf, sizeof(buf), 0);
    for (int i = 0; i < n; i++) {
        char c = (char)buf[i];
        if (c == '\n' || c == '\r') {
            if (s_line_len > 0) {
                s_line[s_line_len] = '\0';
                handle_nmea(s_line);
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
    if (!s_fix.valid) {
        return false;
    }
    *out = s_fix;
    s_fix.valid = false;
    return true;
}
