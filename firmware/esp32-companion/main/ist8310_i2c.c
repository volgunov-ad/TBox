#include "ist8310_i2c.h"

#include <string.h>

#include "esp_log.h"

static const char *TAG = "ist8310";

#define REG_WAI   0x00
#define REG_DATAX 0x03
#define REG_STAT1 0x02
#define I2C_TIMEOUT_MS 80
#define I2C_HZ 400000
#define UT_PER_LSB 0.3f

static bool add_dev(i2c_master_bus_handle_t bus, uint8_t addr, i2c_master_dev_handle_t *out)
{
    i2c_device_config_t cfg = {
        .dev_addr_length = I2C_ADDR_BIT_LEN_7,
        .device_address = addr,
        .scl_speed_hz = I2C_HZ,
    };
    return i2c_master_bus_add_device(bus, &cfg, out) == ESP_OK;
}

static bool wr_rd(i2c_master_dev_handle_t dev, uint8_t reg, uint8_t *data, size_t n)
{
    return i2c_master_transmit_receive(dev, &reg, 1, data, n, I2C_TIMEOUT_MS) == ESP_OK;
}

bool ist8310_probe_addr(i2c_master_bus_handle_t bus, uint8_t addr)
{
    if (!bus || addr < IST8310_ADDR_MIN || addr > IST8310_ADDR_MAX) return false;
    if (i2c_master_probe(bus, addr, I2C_TIMEOUT_MS) != ESP_OK) return false;
    i2c_master_dev_handle_t dev = NULL;
    if (!add_dev(bus, addr, &dev)) return false;
    uint8_t id = 0;
    bool ok = wr_rd(dev, REG_WAI, &id, 1) && id == IST8310_WHO_AM_I;
    i2c_master_bus_rm_device(dev);
    return ok;
}

bool ist8310_init(i2c_master_bus_handle_t bus, uint8_t addr, ist8310_t *out)
{
    if (!bus || !out || addr < IST8310_ADDR_MIN || addr > IST8310_ADDR_MAX) return false;
    memset(out, 0, sizeof(*out));
    if (!add_dev(bus, addr, &out->dev)) return false;
    uint8_t id = 0;
    if (!wr_rd(out->dev, REG_WAI, &id, 1) || id != IST8310_WHO_AM_I) {
        i2c_master_bus_rm_device(out->dev);
        out->dev = NULL;
        return false;
    }
    out->addr = addr;
    out->ok = true;
    ESP_LOGI(TAG, "OK at 0x%02x", addr);
    return true;
}

void ist8310_deinit(ist8310_t *dev)
{
    if (!dev) return;
    if (dev->dev) {
        i2c_master_bus_rm_device(dev->dev);
        dev->dev = NULL;
    }
    dev->ok = false;
}

bool ist8310_read_ut(ist8310_t *dev, float *hx, float *hy, float *hz)
{
    if (!dev || !dev->ok || !dev->dev || !hx || !hy || !hz) return false;
    uint8_t raw[6];
    if (!wr_rd(dev->dev, REG_DATAX, raw, sizeof(raw))) return false;
    int16_t x = (int16_t)((raw[1] << 8) | raw[0]);
    int16_t y = (int16_t)((raw[3] << 8) | raw[2]);
    int16_t z = (int16_t)((raw[5] << 8) | raw[4]);
    *hx = (float)x * UT_PER_LSB;
    *hy = (float)y * UT_PER_LSB;
    *hz = (float)z * UT_PER_LSB;
    return true;
}
