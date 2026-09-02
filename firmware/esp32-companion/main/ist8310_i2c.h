#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "driver/i2c_master.h"

#define IST8310_ADDR_MIN 0x0C
#define IST8310_ADDR_MAX 0x0F
#define IST8310_WHO_AM_I 0x10

typedef struct {
    i2c_master_dev_handle_t dev;
    uint8_t addr;
    bool ok;
} ist8310_t;

bool ist8310_probe_addr(i2c_master_bus_handle_t bus, uint8_t addr);
bool ist8310_init(i2c_master_bus_handle_t bus, uint8_t addr, ist8310_t *out);
void ist8310_deinit(ist8310_t *dev);
bool ist8310_read_ut(ist8310_t *dev, float *hx, float *hy, float *hz);
