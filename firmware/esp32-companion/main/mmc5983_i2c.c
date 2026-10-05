#include "mmc5983_i2c.h"

#include <string.h>

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static const char *TAG = "mmc5983";

#define REG_XOUT0     0x00
#define REG_STATUS    0x08
#define REG_CTRL0     0x09
#define REG_CTRL1     0x0A
#define REG_PRODUCT_ID 0x2F

#define STATUS_MEAS_M_DONE 0x01
#define CTRL0_TM_M         0x01
#define CTRL0_AUTO_SR      0x20
#define CTRL1_SW_RST       0x80

#define I2C_TIMEOUT_MS 80
#define I2C_HZ 400000
#define ZERO_18BIT 131072.0f
/* 18-bit, ±8 G, 4096 LSB/G; 1 G = 100 µT. */
#define UT_PER_LSB (100.0f / 4096.0f)

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

bool mmc5983_probe(i2c_master_bus_handle_t bus)
{
    if (!bus) return false;
    if (i2c_master_probe(bus, MMC5983_I2C_ADDR, I2C_TIMEOUT_MS) != ESP_OK) return false;
    i2c_master_dev_handle_t dev = NULL;
    if (!add_dev(bus, MMC5983_I2C_ADDR, &dev)) return false;
    uint8_t id = 0;
    bool ok = wr_rd(dev, REG_PRODUCT_ID, &id, 1) && id == MMC5983_PRODUCT_ID_VALUE;
    i2c_master_bus_rm_device(dev);
    return ok;
}

bool mmc5983_init(i2c_master_bus_handle_t bus, mmc5983_t *out)
{
    if (!bus || !out) return false;
    memset(out, 0, sizeof(*out));
    if (!add_dev(bus, MMC5983_I2C_ADDR, &out->dev)) return false;
    uint8_t id = 0;
    if (!wr_rd(out->dev, REG_PRODUCT_ID, &id, 1) || id != MMC5983_PRODUCT_ID_VALUE) {
        ESP_LOGW(TAG, "product id 0x%02x", id);
        i2c_master_bus_rm_device(out->dev);
        out->dev = NULL;
        return false;
    }
    uint8_t rst[2] = {REG_CTRL1, CTRL1_SW_RST};
    wr(out->dev, rst, sizeof(rst));
    vTaskDelay(pdMS_TO_TICKS(15));
    out->ok = true;
    ESP_LOGI(TAG, "OK at 0x%02x", MMC5983_I2C_ADDR);
    return true;
}

void mmc5983_deinit(mmc5983_t *dev)
{
    if (!dev) return;
    if (dev->dev) {
        i2c_master_bus_rm_device(dev->dev);
        dev->dev = NULL;
    }
    dev->ok = false;
}

bool mmc5983_read_ut(mmc5983_t *dev, float *hx, float *hy, float *hz)
{
    if (!dev || !dev->ok || !dev->dev || !hx || !hy || !hz) return false;
    uint8_t kick[2] = {REG_CTRL0, (uint8_t)(CTRL0_TM_M | CTRL0_AUTO_SR)};
    if (!wr(dev->dev, kick, sizeof(kick))) return false;
    bool done = false;
    for (int i = 0; i < 20; i++) {
        uint8_t st = 0;
        if (!wr_rd(dev->dev, REG_STATUS, &st, 1)) return false;
        if (st & STATUS_MEAS_M_DONE) {
            done = true;
            break;
        }
        vTaskDelay(pdMS_TO_TICKS(1));
    }
    if (!done) return false;
    uint8_t raw[7];
    if (!wr_rd(dev->dev, REG_XOUT0, raw, sizeof(raw))) return false;
    uint32_t x = ((uint32_t)raw[0] << 10) | ((uint32_t)raw[1] << 2) | ((raw[6] >> 6) & 0x03);
    uint32_t y = ((uint32_t)raw[2] << 10) | ((uint32_t)raw[3] << 2) | ((raw[6] >> 4) & 0x03);
    uint32_t z = ((uint32_t)raw[4] << 10) | ((uint32_t)raw[5] << 2) | ((raw[6] >> 2) & 0x03);
    *hx = ((float)x - ZERO_18BIT) * UT_PER_LSB;
    *hy = ((float)y - ZERO_18BIT) * UT_PER_LSB;
    *hz = ((float)z - ZERO_18BIT) * UT_PER_LSB;
    return true;
}
