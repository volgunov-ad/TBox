#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "driver/i2c_master.h"

#define QMC5883L_I2C_ADDR 0x0D
#define QMC5883L_CHIP_ID 0xFF

typedef struct {
    i2c_master_dev_handle_t dev;
    bool ok;
} qmc5883l_t;

bool qmc5883l_probe(i2c_master_bus_handle_t bus);
bool qmc5883l_init(i2c_master_bus_handle_t bus, qmc5883l_t *out);
void qmc5883l_deinit(qmc5883l_t *dev);
bool qmc5883l_read_ut(qmc5883l_t *dev, float *hx, float *hy, float *hz);
