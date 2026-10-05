#include "hmc58xx_i2c.h"

#include <string.h>

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static const char *TAG = "hmc58xx";

#define REG_CONF_A 0x00
#define REG_CONF_B 0x01
#define REG_MODE   0x02
#define REG_DATA   0x03
#define REG_ID_A   0x0A
#define REG_ID_B   0x0B
#define REG_ID_C   0x0C
#define ID_A_VAL   0x48
#define ID_B_VAL   0x34
#define ID_C_5883  0x33
#define ID_C_5983  0x34
#define I2C_TIMEOUT_MS 80
#define I2C_HZ 400000
/* 1090 LSB/Gauss */
#define UT_PER_LSB (100.0f / 1090.0f)

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

hmc58xx_kind_t hmc58xx_probe(i2c_master_bus_handle_t bus)
{
    if (!bus) return HMC58XX_NONE;
    if (i2c_master_probe(bus, HMC58XX_I2C_ADDR, I2C_TIMEOUT_MS) != ESP_OK) return HMC58XX_NONE;
    i2c_master_dev_handle_t dev = NULL;
    if (!add_dev(bus, HMC58XX_I2C_ADDR, &dev)) return HMC58XX_NONE;
    uint8_t id[3] = { 0 };
    bool ok = wr_rd(dev, REG_ID_A, id, sizeof(id)) &&
              id[0] == ID_A_VAL && id[1] == ID_B_VAL;
    hmc58xx_kind_t kind = HMC58XX_NONE;
    if (ok) {
        if (id[2] == ID_C_5883) {
            kind = HMC58XX_5883L;
        } else if (id[2] == ID_C_5983) {
            kind = HMC58XX_5983;
        }
    }
    i2c_master_bus_rm_device(dev);
    return kind;
}

bool hmc58xx_init(i2c_master_bus_handle_t bus, hmc58xx_kind_t kind, hmc58xx_t *out)
{
    if (!bus || !out || kind == HMC58XX_NONE) return false;
    memset(out, 0, sizeof(*out));
    if (!add_dev(bus, HMC58XX_I2C_ADDR, &out->dev)) return false;
    uint8_t id[3] = { 0 };
    if (!wr_rd(out->dev, REG_ID_A, id, sizeof(id))) {
        i2c_master_bus_rm_device(out->dev);
        out->dev = NULL;
        return false;
    }
    uint8_t expected_c = (kind == HMC58XX_5983) ? ID_C_5983 : ID_C_5883;
    if (id[0] != ID_A_VAL || id[1] != ID_B_VAL || id[2] != expected_c) {
        i2c_master_bus_rm_device(out->dev);
        out->dev = NULL;
        return false;
    }
    uint8_t cfg_a[2] = { REG_CONF_A, 0x70 };
    uint8_t cfg_b[2] = { REG_CONF_B, 0xA0 };
    uint8_t mode[2] = { REG_MODE, 0x00 };
    if (!wr(out->dev, cfg_a, sizeof(cfg_a)) || !wr(out->dev, cfg_b, sizeof(cfg_b)) ||
        !wr(out->dev, mode, sizeof(mode))) {
        i2c_master_bus_rm_device(out->dev);
        out->dev = NULL;
        return false;
    }
    vTaskDelay(pdMS_TO_TICKS(10));
    out->kind = kind;
    out->ok = true;
    ESP_LOGI(TAG, "%s OK at 0x%02x", kind == HMC58XX_5983 ? "HMC5983" : "HMC5883L",
             HMC58XX_I2C_ADDR);
    return true;
}

void hmc58xx_deinit(hmc58xx_t *dev)
{
    if (!dev) return;
    if (dev->dev) {
        i2c_master_bus_rm_device(dev->dev);
        dev->dev = NULL;
    }
    dev->ok = false;
}

bool hmc58xx_read_ut(hmc58xx_t *dev, float *hx, float *hy, float *hz)
{
    if (!dev || !dev->ok || !dev->dev || !hx || !hy || !hz) return false;
    uint8_t raw[6];
    if (!wr_rd(dev->dev, REG_DATA, raw, sizeof(raw))) return false;
    int16_t x = (int16_t)((raw[0] << 8) | raw[1]);
    int16_t y = (int16_t)((raw[2] << 8) | raw[3]);
    int16_t z = (int16_t)((raw[4] << 8) | raw[5]);
    *hx = (float)x * UT_PER_LSB;
    *hy = (float)y * UT_PER_LSB;
    *hz = (float)z * UT_PER_LSB;
    return true;
}
