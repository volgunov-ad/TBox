#include "qmc5883l_i2c.h"

#include <string.h>

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static const char *TAG = "qmc5883l";

#define REG_XOUT_LSB 0x00
#define REG_STATUS   0x06
#define REG_CONTROL1 0x09
#define REG_CHIP_ID  0x0D
#define I2C_TIMEOUT_MS 80
#define I2C_HZ 400000
/* 12000 LSB/Gauss, 1 G = 100 uT */
#define UT_PER_LSB (100.0f / 12000.0f)

static bool add_dev(i2c_master_bus_handle_t bus, uint8_t addr, i2c_master_dev_handle_t *out)
{
    i2c_device_config_t cfg = {
        .dev_addr_length = I2C_ADDR_BIT_LEN_7,
        .device_address = addr,
        .scl_speed_hz = I2C_HZ,
    };
    return i2c_master_bus_add_device(bus, &cfg, out) == ESP_OK;
}

static bool wr(i2c_master_dev_handle_t dev, const uint8_t *data, size_t n)
{
    return i2c_master_transmit(dev, data, n, I2C_TIMEOUT_MS) == ESP_OK;
}

static bool wr_rd(i2c_master_dev_handle_t dev, uint8_t reg, uint8_t *data, size_t n)
{
    return i2c_master_transmit_receive(dev, &reg, 1, data, n, I2C_TIMEOUT_MS) == ESP_OK;
}

bool qmc5883l_probe(i2c_master_bus_handle_t bus)
{
    if (!bus) return false;
    if (i2c_master_probe(bus, QMC5883L_I2C_ADDR, I2C_TIMEOUT_MS) != ESP_OK) return false;
    i2c_master_dev_handle_t dev = NULL;
    if (!add_dev(bus, QMC5883L_I2C_ADDR, &dev)) return false;
    uint8_t id = 0;
    bool ok = wr_rd(dev, REG_CHIP_ID, &id, 1) && id == QMC5883L_CHIP_ID;
    i2c_master_bus_rm_device(dev);
    return ok;
}

bool qmc5883l_init(i2c_master_bus_handle_t bus, qmc5883l_t *out)
{
    if (!bus || !out) return false;
    memset(out, 0, sizeof(*out));
    if (!add_dev(bus, QMC5883L_I2C_ADDR, &out->dev)) return false;
    uint8_t id = 0;
    if (!wr_rd(out->dev, REG_CHIP_ID, &id, 1) || id != QMC5883L_CHIP_ID) {
        i2c_master_bus_rm_device(out->dev);
        out->dev = NULL;
        return false;
    }
    uint8_t ctrl[2] = { REG_CONTROL1, 0x1D };
    if (!wr(out->dev, ctrl, sizeof(ctrl))) {
        i2c_master_bus_rm_device(out->dev);
        out->dev = NULL;
        return false;
    }
    out->ok = true;
    ESP_LOGI(TAG, "OK at 0x%02x", QMC5883L_I2C_ADDR);
    return true;
}

void qmc5883l_deinit(qmc5883l_t *dev)
{
    if (!dev) return;
    if (dev->dev) {
        i2c_master_bus_rm_device(dev->dev);
        dev->dev = NULL;
    }
    dev->ok = false;
}

bool qmc5883l_read_ut(qmc5883l_t *dev, float *hx, float *hy, float *hz)
{
    if (!dev || !dev->ok || !dev->dev || !hx || !hy || !hz) return false;
    uint8_t st = 0;
    if (!wr_rd(dev->dev, REG_STATUS, &st, 1)) return false;
    if ((st & 0x04) == 0) return false;
    uint8_t raw[6];
    if (!wr_rd(dev->dev, REG_XOUT_LSB, raw, sizeof(raw))) return false;
    int16_t x = (int16_t)((raw[1] << 8) | raw[0]);
    int16_t y = (int16_t)((raw[3] << 8) | raw[2]);
    int16_t z = (int16_t)((raw[5] << 8) | raw[4]);
    *hx = (float)x * UT_PER_LSB;
    *hy = (float)y * UT_PER_LSB;
    *hz = (float)z * UT_PER_LSB;
    return true;
}
