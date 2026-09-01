#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "driver/i2c_master.h"

#define RM3100_ADDR_MIN 0x20
#define RM3100_ADDR_MAX 0x23
#define RM3100_REVID_VALUE 0x22
#define RM3100_CYCLE_COUNT 200

typedef struct {
    i2c_master_dev_handle_t dev;
    uint8_t addr;
    bool ok;
} rm3100_t;

/** True if REVID at [addr] is MagI2C. */
bool rm3100_probe_addr(i2c_master_bus_handle_t bus, uint8_t addr);

bool rm3100_init(i2c_master_bus_handle_t bus, uint8_t addr, rm3100_t *out);
void rm3100_deinit(rm3100_t *dev);

/** Read XYZ in µT. Returns false on I2C/timeout. */
bool rm3100_read_ut(rm3100_t *dev, float *hx, float *hy, float *hz);
