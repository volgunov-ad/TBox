#pragma once

#include <stdbool.h>
#include <stdint.h>

typedef enum {
    MAG_CHIP_RM3100 = 0,
    MAG_CHIP_MMC5983 = 1,
} mag_chip_t;

void mag_init(void);

mag_chip_t mag_get_chip(void);
const char *mag_chip_name(mag_chip_t chip);
/** Parse "rm3100" / "mmc5983". Returns false if unknown. */
bool mag_chip_from_name(const char *name, mag_chip_t *out);

/** Selected chip is answering. */
bool mag_is_present(void);
void mag_get_seen(bool *rm3100, bool *mmc5983);

/**
 * Persist chip, re-probe, ack via protocol. Safe from CDC RX
 * (queued to mag task).
 */
void mag_request_chip(mag_chip_t chip);

void mag_refresh_hello(void);
