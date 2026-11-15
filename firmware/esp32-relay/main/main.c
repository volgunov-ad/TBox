#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "driver/gpio.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "host/ble_hs.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "nvs_flash.h"

#include "relay_ble.h"
#include "relay_led.h"
#include "relay_logic.h"
#include "relay_store.h"
#include "sdkconfig.h"

static const char *TAG = "relay";

static uint32_t now_ms(void)
{
    return (uint32_t)(esp_timer_get_time() / 1000ULL);
}

static void host_task(void *param)
{
    nimble_port_run();
    nimble_port_freertos_deinit();
    vTaskDelete(NULL);
}

static void ble_init(void)
{
    ESP_ERROR_CHECK(nimble_port_init());
    relay_ble_start();
    nimble_port_freertos_init(host_task);
}

/* CONFIG_RELAY_GPIO=-1: drive every candidate for 2 s with a UART log. Listen for
 * the relay click, then set the pin in menuconfig and reflash. */
static void probe_loop(void)
{
    char candidates[64];
    snprintf(candidates, sizeof(candidates), "%s", CONFIG_RELAY_PROBE_GPIOS);
    int gpios[16];
    int n = 0;
    char *save = NULL;
    for (char *tok = strtok_r(candidates, ",", &save);
         tok && n < 16;
         tok = strtok_r(NULL, ",", &save)) {
        gpios[n++] = atoi(tok);
    }
    if (n == 0) {
        ESP_LOGE(TAG, "no probe gpios");
        return;
    }
    gpio_config_t cfg = {
        .pin_bit_mask = 0,
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    for (int i = 0; i < n; i++) cfg.pin_bit_mask |= 1ULL << gpios[i];
    gpio_config(&cfg);
    ESP_LOGW(TAG, "PROBE mode, %d candidates, relay level=%s", n,
             CONFIG_RELAY_ACTIVE_HIGH ? "high" : "low");
    for (;;) {
        for (int i = 0; i < n; i++) {
            gpio_set_level(gpios[i], CONFIG_RELAY_ACTIVE_HIGH ? 1 : 0);
            ESP_LOGW(TAG, "PROBE gpio=%d ON (2 s) - listen for the click", gpios[i]);
            vTaskDelay(pdMS_TO_TICKS(2000));
            gpio_set_level(gpios[i], CONFIG_RELAY_ACTIVE_HIGH ? 0 : 1);
            ESP_LOGI(TAG, "PROBE gpio=%d off", gpios[i]);
            vTaskDelay(pdMS_TO_TICKS(800));
        }
    }
}

static bool reset_held(void)
{
    if (CONFIG_RELAY_BTN_GPIO < 0) return false;
    int level = gpio_get_level(CONFIG_RELAY_BTN_GPIO);
    return CONFIG_RELAY_BTN_ACTIVE_LOW ? level == 0 : level != 0;
}

void app_main(void)
{
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);

    relay_store_init();

    if (CONFIG_RELAY_GPIO < 0) {
        probe_loop();
        return;
    }

    relay_logic_init();
    ESP_LOGI(TAG, "relay on GPIO %d (%s), pulse %d ms", CONFIG_RELAY_GPIO,
             CONFIG_RELAY_ACTIVE_HIGH ? "active-high" : "active-low",
             CONFIG_RELAY_PULSE_MS);

    if (CONFIG_RELAY_BTN_GPIO >= 0) {
        gpio_config_t cfg = {
            .pin_bit_mask = 1ULL << CONFIG_RELAY_BTN_GPIO,
            .mode = GPIO_MODE_INPUT,
            .pull_up_en = CONFIG_RELAY_BTN_ACTIVE_LOW ? GPIO_PULLUP_ENABLE
                                                      : GPIO_PULLUP_DISABLE,
            .pull_down_en = CONFIG_RELAY_BTN_ACTIVE_LOW ? GPIO_PULLDOWN_DISABLE
                                                        : GPIO_PULLDOWN_ENABLE,
            .intr_type = GPIO_INTR_DISABLE,
        };
        gpio_config(&cfg);
    }
    relay_led_init();
    ESP_LOGI(TAG, "btn GPIO %d (%s), led GPIO %d", CONFIG_RELAY_BTN_GPIO,
             CONFIG_RELAY_BTN_ACTIVE_LOW ? "to GND" : "to VCC", CONFIG_RELAY_LED_GPIO);

    ble_init();

    uint32_t hold_start = 0;
    bool state_sent = false;
    for (;;) {
        relay_logic_poll();
        relay_store_tick();
        if (relay_logic_pulse_active() && !state_sent) {
            state_sent = true;
            relay_ble_kick_state();
        }
        if (!relay_logic_pulse_active()) state_sent = false;
        relay_led_mode_t led = RELAY_LED_ON;
        if (reset_held()) {
            if (hold_start == 0) hold_start = now_ms();
            led = RELAY_LED_HOLD;
            if (now_ms() - hold_start >= 5000u) {
                relay_store_factory_reset();
            }
        } else {
            hold_start = 0;
            if (!relay_store_paired()) led = RELAY_LED_FAST;
            else if (!relay_ble_online()) led = RELAY_LED_SLOW;
        }
        relay_led_set_mode(led);
        relay_led_poll();
        vTaskDelay(pdMS_TO_TICKS(50));
    }
}
