#include <stdbool.h>
#include <stdint.h>

#include "driver/gpio.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

#include "relay_logic.h"
#include "relay_store.h"
#include "sdkconfig.h"

static bool s_active_high;
static uint8_t s_mask;
static bool s_pulse;
static uint32_t s_pulse_start_ms;
static uint32_t s_pulse_len_ms;
static bool s_restored;
static uint32_t s_latched_on_ms;
static SemaphoreHandle_t s_mu;

static uint32_t now_ms(void)
{
    return (uint32_t)(esp_timer_get_time() / 1000ULL);
}

static void drive(void)
{
    int on = (s_mask & 0x01) != 0;
    gpio_set_level(CONFIG_RELAY_GPIO, s_active_high ? on : !on);
}

void relay_state_lock(void)
{
    if (s_mu) xSemaphoreTake(s_mu, portMAX_DELAY);
}

void relay_state_unlock(void)
{
    if (s_mu) xSemaphoreGive(s_mu);
}

void relay_logic_init(void)
{
    if (!s_mu) {
        s_mu = xSemaphoreCreateMutex();
    }
    s_active_high = CONFIG_RELAY_ACTIVE_HIGH;
    s_restored = true;
    gpio_config_t cfg = {
        .pin_bit_mask = 1ULL << CONFIG_RELAY_GPIO,
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    gpio_config(&cfg);
    s_mask = relay_store_mask();
    s_latched_on_ms = (s_mask & 0x01) ? now_ms() : 0;
    drive();
}

uint8_t relay_logic_apply(uint8_t states, uint16_t pulse_ms)
{
    uint8_t next = states & 0x01;
    if (pulse_ms == 0) {
        bool was_pulse = s_pulse;
        s_pulse = false;
        s_pulse_len_ms = 0;
        /* A pulse leaves the closed level in RAM only. A latch must hit NVS. */
        if (next != s_mask || was_pulse) {
            s_mask = next;
            relay_store_set_mask(s_mask);
            drive();
        }
        if (next) {
            if (was_pulse || s_latched_on_ms == 0) s_latched_on_ms = now_ms();
        } else {
            s_latched_on_ms = 0;
        }
        return s_mask;
    }
    /* Pulse: drive now, but do not persist the closed state. */
    s_pulse = true;
    s_pulse_len_ms = pulse_ms;
    s_pulse_start_ms = now_ms();
    if (next != s_mask) {
        s_mask = next;
        drive();
    }
    return s_mask;
}

uint8_t relay_logic_press(void)
{
    return relay_logic_apply(0x01, (uint16_t)CONFIG_RELAY_PULSE_MS);
}

void relay_logic_poll(void)
{
    if (s_pulse) {
        uint32_t elapsed = now_ms() - s_pulse_start_ms;
        if (elapsed >= s_pulse_len_ms) {
            s_pulse = false;
            s_pulse_len_ms = 0;
            if (s_mask != 0) {
                s_mask = 0;
                relay_store_set_mask(s_mask);
                drive();
            }
            s_latched_on_ms = 0;
        }
        return;
    }
    /* 0 disables the limit and keeps today's latched behaviour (TBOX-4). */
    if (CONFIG_RELAY_MAX_ON_S > 0 && (s_mask & 0x01) && s_latched_on_ms != 0) {
        uint32_t limit = (uint32_t)CONFIG_RELAY_MAX_ON_S * 1000u;
        if ((uint32_t)(now_ms() - s_latched_on_ms) >= limit) {
            relay_logic_apply(0, 0);
        }
    }
}

uint8_t relay_logic_mask(void) { return s_mask; }

bool relay_logic_pulse_active(void) { return s_pulse; }

bool relay_logic_take_restored(void)
{
    bool r = s_restored;
    s_restored = false;
    return r;
}
