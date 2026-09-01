#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "driver/i2c_master.h"

#define MMC5983_I2C_ADDR 0x30
#define MMC5983_PRODUCT_ID_VALUE 0x30

typedef struct {
    i2c_master_dev_handle_t dev;
    bool ok;
} mmc5983_t;

bool mmc5983_probe(i2c_master_bus_handle_t bus);

bool mmc5983_init(i2c_master_bus_handle_t bus, mmc5983_t *out);
void mmc5983_deinit(mmc5983_t *dev);

/** Read XYZ in µT. Returns false on I2C/timeout. */
bool mmc5983_read_ut(mmc5983_t *dev, float *hx, float *hy, float *hz);
