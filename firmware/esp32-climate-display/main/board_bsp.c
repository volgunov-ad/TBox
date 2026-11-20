#include "board_bsp.h"
#include "board_pins.h"

#include "driver/gpio.h"
#include "driver/i2c_master.h"
#include "esp_check.h"
#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_panel_rgb.h"
#include "esp_lcd_touch.h"
#include "esp_lcd_touch_gt911.h"
#include "esp_log.h"
#include "esp_rom_sys.h"

static const char *TAG = "board_bsp";

static i2c_master_bus_handle_t s_i2c_bus = NULL;
static i2c_master_dev_handle_t s_ch422g_mode_dev = NULL;
static i2c_master_dev_handle_t s_ch422g_set_dev = NULL;

static esp_err_t ch422g_add_device(uint16_t address, i2c_master_dev_handle_t *out_dev)
{
    const i2c_device_config_t dev_cfg = {
        .dev_addr_length = I2C_ADDR_BIT_LEN_7,
        .device_address = address,
        .scl_speed_hz = BOARD_I2C_FREQ_HZ,
    };
    return i2c_master_bus_add_device(s_i2c_bus, &dev_cfg, out_dev);
}

static esp_err_t ch422g_write(i2c_master_dev_handle_t dev, uint8_t value)
{
    return i2c_master_transmit(dev, &value, 1, 100);
}

static esp_err_t i2c_bus_init(void)
{
    if (s_i2c_bus != NULL) {
        return ESP_OK;
    }
    const i2c_master_bus_config_t bus_cfg = {
        .i2c_port = BOARD_I2C_PORT,
        .sda_io_num = BOARD_I2C_SDA,
        .scl_io_num = BOARD_I2C_SCL,
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .glitch_ignore_cnt = 7,
        .flags.enable_internal_pullup = true,
    };
    ESP_RETURN_ON_ERROR(i2c_new_master_bus(&bus_cfg, &s_i2c_bus), TAG, "I2C bus init failed");
    ESP_RETURN_ON_ERROR(ch422g_add_device(BOARD_CH422G_MODE_ADDR, &s_ch422g_mode_dev), TAG, "CH422G mode device failed");
    ESP_RETURN_ON_ERROR(ch422g_add_device(BOARD_CH422G_SET_ADDR, &s_ch422g_set_dev), TAG, "CH422G set device failed");
    return ESP_OK;
}

static esp_err_t touch_reset_dance(void)
{
    ESP_RETURN_ON_ERROR(ch422g_write(s_ch422g_mode_dev, BOARD_CH422G_MODE_OUTPUT), TAG, "CH422G mode write failed");
    const gpio_config_t io_conf = {
        .pin_bit_mask = 1ULL << BOARD_TP_DANCE_GPIO,
        .mode = GPIO_MODE_OUTPUT,
    };
    ESP_RETURN_ON_ERROR(gpio_config(&io_conf), TAG, "TP dance GPIO config failed");
    ESP_RETURN_ON_ERROR(ch422g_write(s_ch422g_set_dev, BOARD_CH422G_TP_RESET_PREPARE), TAG, "CH422G set write failed");
    esp_rom_delay_us(100 * 1000);
    gpio_set_level(BOARD_TP_DANCE_GPIO, 0);
    esp_rom_delay_us(100 * 1000);
    ESP_RETURN_ON_ERROR(ch422g_write(s_ch422g_set_dev, BOARD_CH422G_TP_RESET_RELEASE), TAG, "CH422G set write failed");
    esp_rom_delay_us(200 * 1000);
    return ESP_OK;
}

static esp_err_t panel_init(esp_lcd_panel_handle_t *out_panel)
{
    const esp_lcd_rgb_panel_config_t panel_cfg = {
        .clk_src = LCD_CLK_SRC_DEFAULT,
        .dma_burst_size = 64,
        .data_width = BOARD_LCD_RGB_DATA_WIDTH,
        .bits_per_pixel = BOARD_LCD_RGB_BITS_PER_PIXEL,
        .de_gpio_num = BOARD_LCD_DE,
        .pclk_gpio_num = BOARD_LCD_PCLK,
        .vsync_gpio_num = BOARD_LCD_VSYNC,
        .hsync_gpio_num = BOARD_LCD_HSYNC,
        .disp_gpio_num = BOARD_LCD_DISP,
        .data_gpio_nums = {
            BOARD_LCD_DATA0,
            BOARD_LCD_DATA1,
            BOARD_LCD_DATA2,
            BOARD_LCD_DATA3,
            BOARD_LCD_DATA4,
            BOARD_LCD_DATA5,
            BOARD_LCD_DATA6,
            BOARD_LCD_DATA7,
            BOARD_LCD_DATA8,
            BOARD_LCD_DATA9,
            BOARD_LCD_DATA10,
            BOARD_LCD_DATA11,
            BOARD_LCD_DATA12,
            BOARD_LCD_DATA13,
            BOARD_LCD_DATA14,
            BOARD_LCD_DATA15,
        },
        .timings = {
            .pclk_hz = BOARD_LCD_PIXEL_CLOCK_HZ,
            .h_res = BOARD_LCD_H_RES,
            .v_res = BOARD_LCD_V_RES,
            .hsync_pulse_width = 4,
            .hsync_back_porch = 8,
            .hsync_front_porch = 8,
            .vsync_pulse_width = 4,
            .vsync_back_porch = 8,
            .vsync_front_porch = 8,
            .flags.pclk_active_neg = 1,
        },
        .num_fbs = 2,
        .flags.fb_in_psram = 1,
    };
    ESP_RETURN_ON_ERROR(esp_lcd_new_rgb_panel(&panel_cfg, out_panel), TAG, "RGB panel create failed");
    ESP_RETURN_ON_ERROR(esp_lcd_panel_init(*out_panel), TAG, "RGB panel init failed");
    return ESP_OK;
}

static esp_err_t touch_init(esp_lcd_touch_handle_t *out_touch)
{
    esp_lcd_panel_io_handle_t tp_io = NULL;
    const esp_lcd_panel_io_i2c_config_t tp_io_cfg = ESP_LCD_TOUCH_IO_I2C_GT911_CONFIG();
    ESP_RETURN_ON_ERROR(esp_lcd_new_panel_io_i2c(s_i2c_bus, &tp_io_cfg, &tp_io), TAG, "touch panel IO failed");
    const esp_lcd_touch_config_t tp_cfg = {
        .x_max = BOARD_LCD_H_RES,
        .y_max = BOARD_LCD_V_RES,
        .rst_gpio_num = BOARD_TP_RST_GPIO,
        .int_gpio_num = BOARD_TP_INT_GPIO,
        .levels = {
            .reset = 0,
            .interrupt = 0,
        },
        .flags = {
            .swap_xy = 0,
            .mirror_x = 0,
            .mirror_y = 0,
        },
    };
    ESP_RETURN_ON_ERROR(esp_lcd_touch_new_i2c_gt911(tp_io, &tp_cfg, out_touch), TAG, "GT911 init failed");
    return ESP_OK;
}

esp_err_t board_bsp_init(esp_lcd_panel_handle_t *out_panel, esp_lcd_touch_handle_t *out_touch)
{
    ESP_RETURN_ON_ERROR(panel_init(out_panel), TAG, "panel init failed");
    ESP_RETURN_ON_ERROR(i2c_bus_init(), TAG, "I2C bus init failed");
    ESP_RETURN_ON_ERROR(touch_reset_dance(), TAG, "touch reset failed");
    ESP_RETURN_ON_ERROR(touch_init(out_touch), TAG, "touch init failed");
    return ESP_OK;
}

esp_err_t board_bsp_mux_set_can(bool can_mode)
{
    ESP_RETURN_ON_ERROR(i2c_bus_init(), TAG, "I2C bus init failed");
    return ch422g_write(s_ch422g_set_dev, can_mode ? BOARD_CH422G_SET_CAN_MODE : BOARD_CH422G_SET_USB_MODE);
}
