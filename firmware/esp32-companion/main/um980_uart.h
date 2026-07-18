#pragma once

#include <stdbool.h>

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
    char utc[32];
} um980_fix_t;

void um980_uart_init(void);
bool um980_uart_poll(um980_fix_t *out);
