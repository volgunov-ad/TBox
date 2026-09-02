#pragma once

#include <stdbool.h>

typedef enum {
    GNSS_CHIP_NONE = 0,
    GNSS_CHIP_UM980,
    GNSS_CHIP_UBLOX,
    GNSS_CHIP_NMEA,
} gnss_chip_t;

/** Probe UART GNSS after [um980_uart_init]. Sets baud in NVS when found. */
void gnss_detect_run(void);

gnss_chip_t gnss_chip_type(void);
/** Protocol id: um980, neo-m8n, ublox, nmea, none */
const char *gnss_chip_id(void);
/** Human label from probe (may be empty). */
const char *gnss_model_label(void);
bool gnss_is_um980(void);
bool gnss_uart_active(void);
