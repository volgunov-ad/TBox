#include "mcp2515.h"

#include <string.h>

#include "driver/gpio.h"
#include "driver/spi_master.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

static const char *TAG = "mcp2515";

/* MCP2515 instructions */
#define MCP_RESET       0xC0
#define MCP_READ        0x03
#define MCP_WRITE       0x02
#define MCP_RTS_TX0     0x81
#define MCP_READ_STATUS 0xA0
#define MCP_BITMOD      0x05

/* Registers */
#define MCP_RXF0SIDH  0x00
#define MCP_RXF1SIDH  0x04
#define MCP_RXF2SIDH  0x08
#define MCP_RXF3SIDH  0x10
#define MCP_RXF4SIDH  0x14
#define MCP_RXF5SIDH  0x18
#define MCP_RXM0SIDH  0x20
#define MCP_RXM1SIDH  0x24
#define MCP_CNF3      0x28
#define MCP_CNF2      0x29
#define MCP_CNF1      0x2A
#define MCP_CANINTE   0x2B
#define MCP_CANINTF   0x2C
#define MCP_CANCTRL   0x0F
#define MCP_CANSTAT   0x0E
#define MCP_TXB0CTRL  0x30
#define MCP_TXB0SIDH  0x31
#define MCP_RXB0CTRL  0x60
#define MCP_RXB0SIDH  0x61
#define MCP_RXB1CTRL  0x70
#define MCP_RXB1SIDH  0x71

#define MCP_RX0IF 0x01
#define MCP_RX1IF 0x02
#define MCP_TX0IF 0x04

#define MCP_MODE_CONFIG 0x80
#define MCP_MODE_NORMAL 0x00
#define MCP_MODE_MASK   0xE0

typedef struct {
    uint8_t cnf1;
    uint8_t cnf2;
    uint8_t cnf3;
} mcp_bitrate_t;

static spi_device_handle_t s_spi;
static SemaphoreHandle_t s_lock;
static bool s_ok;
static uint32_t s_baud = MCP2515_DEFAULT_BAUD;
static uint32_t s_xtal = MCP2515_DEFAULT_XTAL_HZ;

/* CNF tables from common mcp_can / Arduino MCP2515 (TQ layout). */
static bool bitrate_cfg(uint32_t xtal_hz, uint32_t baud, mcp_bitrate_t *out)
{
    if (!out) return false;
    if (xtal_hz == 8000000) {
        switch (baud) {
        case 1000000: *out = (mcp_bitrate_t){0x00, 0x80, 0x00}; return true;
        case 500000:  *out = (mcp_bitrate_t){0x00, 0x90, 0x82}; return true;
        case 250000:  *out = (mcp_bitrate_t){0x00, 0xB1, 0x85}; return true;
        case 125000:  *out = (mcp_bitrate_t){0x01, 0xB1, 0x85}; return true;
        case 100000:  *out = (mcp_bitrate_t){0x01, 0xB4, 0x86}; return true;
        default: return false;
        }
    }
    if (xtal_hz == 16000000) {
        switch (baud) {
        case 1000000: *out = (mcp_bitrate_t){0x00, 0xD0, 0x82}; return true;
        case 500000:  *out = (mcp_bitrate_t){0x00, 0xF0, 0x86}; return true;
        case 250000:  *out = (mcp_bitrate_t){0x41, 0xF1, 0x85}; return true;
        case 125000:  *out = (mcp_bitrate_t){0x03, 0xF0, 0x86}; return true;
        case 100000:  *out = (mcp_bitrate_t){0x03, 0xFA, 0x87}; return true;
        default: return false;
        }
    }
    return false;
}

bool mcp2515_baud_supported(uint32_t baud)
{
    mcp_bitrate_t cfg;
    return bitrate_cfg(s_xtal ? s_xtal : MCP2515_DEFAULT_XTAL_HZ, baud, &cfg);
}

static esp_err_t spi_txrx(const uint8_t *tx, uint8_t *rx, size_t len)
{
    spi_transaction_t t = {
        .length = len * 8,
        .tx_buffer = tx,
        .rx_buffer = rx,
    };
    return spi_device_transmit(s_spi, &t);
}

static uint8_t read_reg(uint8_t addr)
{
    uint8_t tx[3] = {MCP_READ, addr, 0};
    uint8_t rx[3] = {0};
    spi_txrx(tx, rx, 3);
    return rx[2];
}

static void write_reg(uint8_t addr, uint8_t val)
{
    uint8_t tx[3] = {MCP_WRITE, addr, val};
    spi_txrx(tx, NULL, 3);
}

static void write_regs(uint8_t addr, const uint8_t *data, size_t len)
{
    uint8_t tx[2 + 13];
    if (len > 13) len = 13;
    tx[0] = MCP_WRITE;
    tx[1] = addr;
    memcpy(tx + 2, data, len);
    spi_txrx(tx, NULL, 2 + len);
}

static void bit_modify(uint8_t addr, uint8_t mask, uint8_t data)
{
    uint8_t tx[4] = {MCP_BITMOD, addr, mask, data};
    spi_txrx(tx, NULL, 4);
}

static void soft_reset(void)
{
    uint8_t tx = MCP_RESET;
    spi_txrx(&tx, NULL, 1);
    vTaskDelay(pdMS_TO_TICKS(10));
}

static bool set_mode(uint8_t mode)
{
    bit_modify(MCP_CANCTRL, MCP_MODE_MASK, mode);
    for (int i = 0; i < 50; i++) {
        uint8_t st = read_reg(MCP_CANSTAT) & MCP_MODE_MASK;
        if (st == mode) return true;
        vTaskDelay(pdMS_TO_TICKS(1));
    }
    return false;
}

static void write_id_regs(uint8_t start, uint32_t id, bool ext)
{
    uint8_t buf[4];
    if (ext) {
        buf[0] = (uint8_t)((id >> 21) & 0xFF);
        buf[1] = (uint8_t)(((id >> 13) & 0xE0) | 0x08 | ((id >> 16) & 0x03));
        buf[2] = (uint8_t)((id >> 8) & 0xFF);
        buf[3] = (uint8_t)(id & 0xFF);
    } else {
        buf[0] = (uint8_t)((id >> 3) & 0xFF);
        buf[1] = (uint8_t)((id & 0x07) << 5);
        buf[2] = 0;
        buf[3] = 0;
    }
    write_regs(start, buf, 4);
}

static void read_id_regs(uint8_t start, uint32_t *id, bool *ext)
{
    uint8_t tx[6] = {MCP_READ, start, 0, 0, 0, 0};
    uint8_t rx[6] = {0};
    spi_txrx(tx, rx, 6);
    uint8_t sidh = rx[2], sidl = rx[3], eid8 = rx[4], eid0 = rx[5];
    if (sidl & 0x08) {
        *ext = true;
        *id = ((uint32_t)sidh << 21) |
              ((uint32_t)(sidl & 0xE0) << 13) |
              ((uint32_t)(sidl & 0x03) << 16) |
              ((uint32_t)eid8 << 8) |
              (uint32_t)eid0;
    } else {
        *ext = false;
        *id = ((uint32_t)sidh << 3) | ((uint32_t)(sidl >> 5) & 0x07);
    }
}

static bool apply_bitrate(uint32_t baud)
{
    mcp_bitrate_t cfg;
    if (!bitrate_cfg(s_xtal, baud, &cfg)) {
        ESP_LOGE(TAG, "unsupported baud %lu @ xtal %lu", (unsigned long)baud, (unsigned long)s_xtal);
        return false;
    }
    if (!set_mode(MCP_MODE_CONFIG)) {
        ESP_LOGE(TAG, "enter config mode failed");
        return false;
    }
    write_reg(MCP_CNF1, cfg.cnf1);
    write_reg(MCP_CNF2, cfg.cnf2);
    write_reg(MCP_CNF3, cfg.cnf3);
    s_baud = baud;
    return true;
}

bool mcp2515_set_filters(bool accept_all, const mcp2515_filter_t *filters, int count)
{
    if (!s_ok || !s_lock) return false;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    bool ok = set_mode(MCP_MODE_CONFIG);
    if (!ok) {
        xSemaphoreGive(s_lock);
        return false;
    }
    if (accept_all || !filters || count <= 0) {
        /* Masks = 0 → accept all IDs into both RX buffers. */
        uint8_t zero[4] = {0, 0, 0, 0};
        write_regs(MCP_RXM0SIDH, zero, 4);
        write_regs(MCP_RXM1SIDH, zero, 4);
        write_reg(MCP_RXB0CTRL, 0x60); /* receive any, rollover */
        write_reg(MCP_RXB1CTRL, 0x60);
    } else {
        if (count > MCP2515_MAX_FILTERS) count = MCP2515_MAX_FILTERS;
        /* Use filter0/mask0 for first, rest share mask1. Simple exact-ish match. */
        const mcp2515_filter_t *f0 = &filters[0];
        write_id_regs(MCP_RXM0SIDH, f0->mask, f0->ext);
        write_id_regs(MCP_RXF0SIDH, f0->id, f0->ext);
        write_id_regs(MCP_RXF1SIDH, (count > 1) ? filters[1].id : f0->id,
                      (count > 1) ? filters[1].ext : f0->ext);
        uint32_t mask1 = (count > 2) ? filters[2].mask : f0->mask;
        bool ext1 = (count > 2) ? filters[2].ext : f0->ext;
        write_id_regs(MCP_RXM1SIDH, mask1, ext1);
        static const uint8_t faddr[4] = {MCP_RXF2SIDH, MCP_RXF3SIDH, MCP_RXF4SIDH, MCP_RXF5SIDH};
        for (int i = 0; i < 4; i++) {
            int fi = i + 2;
            if (fi < count) {
                write_id_regs(faddr[i], filters[fi].id, filters[fi].ext);
            } else {
                write_id_regs(faddr[i], f0->id, f0->ext);
            }
        }
        write_reg(MCP_RXB0CTRL, 0x00); /* filter match only */
        write_reg(MCP_RXB1CTRL, 0x00);
    }
    ok = set_mode(MCP_MODE_NORMAL);
    xSemaphoreGive(s_lock);
    return ok;
}

bool mcp2515_init(uint32_t baud, uint32_t xtal_hz)
{
    if (s_ok) {
        mcp2515_deinit();
    }
    s_xtal = xtal_hz ? xtal_hz : MCP2515_DEFAULT_XTAL_HZ;
    if (!s_lock) {
        s_lock = xSemaphoreCreateMutex();
    }

    spi_bus_config_t buscfg = {
        .mosi_io_num = MCP2515_PIN_MOSI,
        .miso_io_num = MCP2515_PIN_MISO,
        .sclk_io_num = MCP2515_PIN_SCK,
        .quadwp_io_num = -1,
        .quadhd_io_num = -1,
        .max_transfer_sz = 32,
    };
    esp_err_t err = spi_bus_initialize(SPI2_HOST, &buscfg, SPI_DMA_CH_AUTO);
    if (err != ESP_OK && err != ESP_ERR_INVALID_STATE) {
        ESP_LOGE(TAG, "spi_bus_initialize: %s", esp_err_to_name(err));
        return false;
    }

    spi_device_interface_config_t devcfg = {
        .clock_speed_hz = 8 * 1000 * 1000,
        .mode = 0,
        .spics_io_num = MCP2515_PIN_CS,
        .queue_size = 2,
        .flags = 0,
    };
    err = spi_bus_add_device(SPI2_HOST, &devcfg, &s_spi);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "spi_bus_add_device: %s", esp_err_to_name(err));
        return false;
    }

    soft_reset();
    if (!apply_bitrate(baud ? baud : MCP2515_DEFAULT_BAUD)) {
        return false;
    }
    write_reg(MCP_CANINTE, 0x00); /* poll CANINTF, no INT pin */
    write_reg(MCP_CANINTF, 0x00);
    write_reg(MCP_RXB0CTRL, 0x60);
    write_reg(MCP_RXB1CTRL, 0x60);
    uint8_t zero[4] = {0, 0, 0, 0};
    write_regs(MCP_RXM0SIDH, zero, 4);
    write_regs(MCP_RXM1SIDH, zero, 4);
    if (!set_mode(MCP_MODE_NORMAL)) {
        ESP_LOGE(TAG, "enter normal mode failed");
        return false;
    }
    s_ok = true;
    ESP_LOGI(TAG, "ready baud=%lu xtal=%lu", (unsigned long)s_baud, (unsigned long)s_xtal);
    return true;
}

void mcp2515_deinit(void)
{
    if (s_spi) {
        spi_bus_remove_device(s_spi);
        s_spi = NULL;
    }
    spi_bus_free(SPI2_HOST);
    s_ok = false;
}

bool mcp2515_ok(void)
{
    return s_ok;
}

bool mcp2515_set_baud(uint32_t baud)
{
    if (!s_ok || !s_lock) return false;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    bool ok = apply_bitrate(baud) && set_mode(MCP_MODE_NORMAL);
    xSemaphoreGive(s_lock);
    return ok;
}

uint32_t mcp2515_get_baud(void)
{
    return s_baud;
}

bool mcp2515_send(const mcp2515_frame_t *frame)
{
    if (!s_ok || !frame || !s_lock) return false;
    if (frame->dlc > 8) return false;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    /* Wait TXB0 free briefly */
    bool ready = false;
    for (int i = 0; i < 20; i++) {
        uint8_t ctrl = read_reg(MCP_TXB0CTRL);
        if ((ctrl & 0x08) == 0) {
            ready = true;
            break;
        }
        vTaskDelay(pdMS_TO_TICKS(1));
    }
    if (!ready) {
        xSemaphoreGive(s_lock);
        return false;
    }
    write_id_regs(MCP_TXB0SIDH, frame->id, frame->ext);
    uint8_t dlc = frame->dlc & 0x0F;
    if (frame->rtr) dlc |= 0x40;
    write_reg(MCP_TXB0SIDH + 4, dlc); /* TXB0DLC */
    if (!frame->rtr && frame->dlc > 0) {
        write_regs(MCP_TXB0SIDH + 5, frame->data, frame->dlc);
    }
    uint8_t rts = MCP_RTS_TX0;
    spi_txrx(&rts, NULL, 1);
    xSemaphoreGive(s_lock);
    return true;
}

static bool read_rx_buf(uint8_t sidh_addr, uint8_t if_bit, mcp2515_frame_t *frame)
{
    read_id_regs(sidh_addr, &frame->id, &frame->ext);
    uint8_t dlc_reg = read_reg(sidh_addr + 4);
    frame->rtr = (dlc_reg & 0x40) != 0;
    frame->dlc = dlc_reg & 0x0F;
    if (frame->dlc > 8) frame->dlc = 8;
    memset(frame->data, 0, 8);
    if (!frame->rtr && frame->dlc > 0) {
        uint8_t tx[2 + 8] = {MCP_READ, (uint8_t)(sidh_addr + 5)};
        uint8_t rx[2 + 8] = {0};
        spi_txrx(tx, rx, 2 + frame->dlc);
        memcpy(frame->data, rx + 2, frame->dlc);
    }
    bit_modify(MCP_CANINTF, if_bit, 0);
    return true;
}

bool mcp2515_recv(mcp2515_frame_t *frame)
{
    if (!s_ok || !frame || !s_lock) return false;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    uint8_t intf = read_reg(MCP_CANINTF);
    bool got = false;
    if (intf & MCP_RX0IF) {
        got = read_rx_buf(MCP_RXB0SIDH, MCP_RX0IF, frame);
    } else if (intf & MCP_RX1IF) {
        got = read_rx_buf(MCP_RXB1SIDH, MCP_RX1IF, frame);
    }
    xSemaphoreGive(s_lock);
    return got;
}
