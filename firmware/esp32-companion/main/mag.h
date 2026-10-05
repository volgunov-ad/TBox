#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef enum {
    MAG_CHIP_NONE = -1,
    MAG_CHIP_QMC5883L = 0,
    MAG_CHIP_HMC5883L,
    MAG_CHIP_IST8310,
    MAG_CHIP_HMC5983,
    MAG_CHIP_RM3100,
    MAG_CHIP_MMC5983,
    MAG_CHIP_COUNT,
} mag_chip_t;

#define MAG_SEEN_MAX 6

void mag_init(void);

mag_chip_t mag_get_chip(void);
const char *mag_chip_name(mag_chip_t chip);
/** Parse chip id string. Returns false if unknown. */
bool mag_chip_from_name(const char *name, mag_chip_t *out);

/** Selected chip is answering. */
bool mag_is_present(void);

/** Fill [out] with ids of chips seen on the bus (e.g. "ist8310"). Returns count. */
int mag_get_seen_ids(const char *out[], int max_out);

void mag_refresh_hello(void);

/** Legacy debug hook; autodetect re-runs after ack. */
void mag_request_chip(mag_chip_t chip);
