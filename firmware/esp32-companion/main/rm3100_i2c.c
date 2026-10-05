#include "rm3100_i2c.h"

#include <string.h>

#include "esp_log.h"

static const char *TAG = "rm3100";

#define REG_POLL   0x00
#define REG_CMM    0x01
#define REG_CCX    0x04
#define REG_MX     0x24
#define REG_STATUS 0x34
#define REG_REVID  0x36

#define CMM_START_XYZ 0x71
#define STATUS_DRDY   0x80
#define I2C_TIMEOUT_MS 80
#define I2C_HZ 400000

/* PNI: gain ≈ 0.375 LSB/µT × cycle count (75 LSB/µT at CC=200). */
#define GAIN_LSB_PER_UT (0.375f * (float)RM3100_CYCLE_COUNT)

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

static int32_t be24_signed(const uint8_t *p)
{
    int32_t v = ((int32_t)p[0] << 16) | ((int32_t)p[1] << 8) | (int32_t)p[2];
    if (v & 0x800000) {
        v |= (int32_t)0xFF000000;
    }
    return v;
}

bool rm3100_probe_addr(i2c_master_bus_handle_t bus, uint8_t addr)
{
    if (!bus || addr < RM3100_ADDR_MIN || addr > RM3100_ADDR_MAX) return false;
    if (i2c_master_probe(bus, addr, I2C_TIMEOUT_MS) != ESP_OK) return false;
    i2c_master_dev_handle_t dev = NULL;
    if (!add_dev(bus, addr, &dev)) return false;
    uint8_t id = 0;
    bool ok = wr_rd(dev, REG_REVID, &id, 1) && id == RM3100_REVID_VALUE;
    i2c_master_bus_rm_device(dev);
    return ok;
}

bool rm3100_init(i2c_master_bus_handle_t bus, uint8_t addr, rm3100_t *out)
{
    if (!bus || !out) return false;
    memset(out, 0, sizeof(*out));
    if (!add_dev(bus, addr, &out->dev)) return false;
    uint8_t id = 0;
    if (!wr_rd(out->dev, REG_REVID, &id, 1) || id != RM3100_REVID_VALUE) {
        ESP_LOGW(TAG, "REVID 0x%02x at 0x%02x", id, addr);
        i2c_master_bus_rm_device(out->dev);
        out->dev = NULL;
        return false;
    }
    uint8_t cc[7] = {
        REG_CCX,
        (uint8_t)(RM3100_CYCLE_COUNT >> 8), (uint8_t)RM3100_CYCLE_COUNT,
        (uint8_t)(RM3100_CYCLE_COUNT >> 8), (uint8_t)RM3100_CYCLE_COUNT,
        (uint8_t)(RM3100_CYCLE_COUNT >> 8), (uint8_t)RM3100_CYCLE_COUNT,
    };
    uint8_t cmm[2] = {REG_CMM, CMM_START_XYZ};
    if (!wr(out->dev, cc, sizeof(cc)) || !wr(out->dev, cmm, sizeof(cmm))) {
        i2c_master_bus_rm_device(out->dev);
        out->dev = NULL;
        return false;
    }
    out->addr = addr;
    out->ok = true;
    ESP_LOGI(TAG, "OK at 0x%02x CC=%d", addr, RM3100_CYCLE_COUNT);
    return true;
}

void rm3100_deinit(rm3100_t *dev)
{
    if (!dev) return;
    if (dev->dev) {
        uint8_t stop[2] = {REG_CMM, 0};
        wr(dev->dev, stop, sizeof(stop));
        i2c_master_bus_rm_device(dev->dev);
        dev->dev = NULL;
    }
    dev->ok = false;
}

bool rm3100_read_ut(rm3100_t *dev, float *hx, float *hy, float *hz)
{
    if (!dev || !dev->ok || !dev->dev || !hx || !hy || !hz) return false;
    uint8_t st = 0;
    if (!wr_rd(dev->dev, REG_STATUS, &st, 1)) return false;
    if ((st & STATUS_DRDY) == 0) {
        /* Kick a single poll if CMM is quiet. */
        uint8_t poll[2] = {REG_POLL, 0x70};
        wr(dev->dev, poll, sizeof(poll));
        return false;
    }
    uint8_t raw[9];
    if (!wr_rd(dev->dev, REG_MX, raw, sizeof(raw))) return false;
    *hx = (float)be24_signed(raw + 0) / GAIN_LSB_PER_UT;
    *hy = (float)be24_signed(raw + 3) / GAIN_LSB_PER_UT;
    *hz = (float)be24_signed(raw + 6) / GAIN_LSB_PER_UT;
    return true;
}
