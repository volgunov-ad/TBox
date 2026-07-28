#pragma once

#include <stdbool.h>
#include <stddef.h>

typedef struct {
    bool valid;
    int fix;
    double lat;
    double lon;
    double alt;
    float speed_kmh;
    float course;
    int sats_used;
    int sats_vis;
    char utc[40];
} um980_fix_t;

#define UM980_RSP_MAX_LINES 16
#define UM980_RSP_LINE_LEN 192

void um980_uart_init(void);
bool um980_uart_poll(um980_fix_t *out);

/** Current ESP↔UM980 UART baud (from NVS or default). */
int um980_uart_get_baud(void);

/**
 * Set ESP↔UM980 UART baud, persist to NVS, apply immediately.
 * Returns false if baud is not in the allowed list.
 */
bool um980_uart_set_baud(int baud);

/**
 * Send command and collect non-NMEA replies under one UART lock
 * (avoids racing with um980_uart_poll).
 */
void um980_uart_exec_cmd(const char *cmd, char lines[][UM980_RSP_LINE_LEN], int max_lines,
                         int *out_count, int timeout_ms);
