#include <stdio.h>
#include <string.h>

#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "tinyusb.h"
#include "tusb_cdc_acm.h"

#include "gpio_io.h"
#include "protocol.h"
#include "um980_uart.h"

static const char *TAG = "esp32_companion";

static void on_relay_set(uint8_t mask)
{
    gpio_io_set_relays(mask);
}

static void tinyusb_cdc_rx_callback(int itf, cdcacm_event_t *event)
{
    (void)event;
    uint8_t buf[64];
    size_t rx_size = 0;
    esp_err_t ret = tinyusb_cdcacm_read(itf, buf, sizeof(buf), &rx_size);
    if (ret == ESP_OK && rx_size > 0) {
        protocol_on_rx_bytes(buf, rx_size);
    }
}

static void gpio_event(int ch, int level, uint32_t ms)
{
    protocol_send_gpio_event(ch, level, ms);
    protocol_send_gpio(gpio_io_read_inputs(), ms);
}

void app_main(void)
{
    ESP_LOGI(TAG, "ESP32 companion %s starting", ESP_COMPANION_FW_VERSION);

    const tinyusb_config_t tusb_cfg = {
        .device_descriptor = NULL,
        .string_descriptor = NULL,
        .external_phy = false,
#if (TUD_OPT_HIGH_SPEED)
        .fs_configuration_descriptor = NULL,
        .hs_configuration_descriptor = NULL,
        .qualifier_descriptor = NULL,
#else
        .configuration_descriptor = NULL,
#endif
    };
    ESP_ERROR_CHECK(tinyusb_driver_install(&tusb_cfg));

    tinyusb_config_cdcacm_t acm_cfg = {
        .usb_dev = TINYUSB_USBDEV_0,
        .cdc_port = TINYUSB_CDC_ACM_0,
        .callback_rx = &tinyusb_cdc_rx_callback,
        .callback_rx_wanted_char = NULL,
        .callback_line_state_changed = NULL,
        .callback_line_coding_changed = NULL,
    };
    ESP_ERROR_CHECK(tusb_cdc_acm_init(&acm_cfg));

    protocol_init();
    protocol_set_relay_callback(on_relay_set);
    um980_uart_init();
    gpio_io_init();

    uint32_t last_hb_ms = 0;
    uint32_t last_gpio_ms = 0;
    bool sent_hello = false;

    while (1) {
        uint32_t now_ms = (uint32_t)(esp_timer_get_time() / 1000ULL);

        if (tud_cdc_connected()) {
            if (!sent_hello) {
                protocol_send_hello();
                protocol_send_gpio(gpio_io_read_inputs(), now_ms);
                protocol_send_relay(gpio_io_get_relays());
                sent_hello = true;
            }
            if (now_ms - last_hb_ms >= 1000) {
                protocol_send_hb(now_ms);
                last_hb_ms = now_ms;
            }
        } else {
            sent_hello = false;
        }

        um980_fix_t fix;
        if (um980_uart_poll(&fix)) {
            protocol_send_gps(fix.fix, fix.lat, fix.lon, fix.alt,
                              fix.speed_kmh, fix.course,
                              fix.sats_used, fix.sats_vis, fix.utc);
        }

        gpio_io_poll(now_ms, gpio_event);
        if (now_ms - last_gpio_ms >= 1000) {
            protocol_send_gpio(gpio_io_read_inputs(), now_ms);
            last_gpio_ms = now_ms;
        }

        vTaskDelay(pdMS_TO_TICKS(20));
    }
}
