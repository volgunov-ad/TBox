#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "driver/i2c_master.h"

#define HMC58XX_I2C_ADDR 0x1E

typedef enum {
    HMC58XX_NONE = 0,
    HMC58XX_5883L,
    HMC58XX_5983,
} hmc58xx_kind_t;

typedef struct {
    i2c_master_dev_handle_t dev;
    hmc58xx_kind_t kind;
    bool ok;
} hmc58xx_t;

/** Returns chip kind when ID registers match HMC5883L or HMC5983. */
hmc58xx_kind_t hmc58xx_probe(i2c_master_bus_handle_t bus);
bool hmc58xx_init(i2c_master_bus_handle_t bus, hmc58xx_kind_t kind, hmc58xx_t *out);
void hmc58xx_deinit(hmc58xx_t *dev);
bool hmc58xx_read_ut(hmc58xx_t *dev, float *hx, float *hy, float *hz);
