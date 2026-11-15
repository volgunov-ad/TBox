#include <stdbool.h>
#include <stdint.h>

#include "driver/gpio.h"
#include "esp_timer.h"

#include "relay_logic.h"
#include "relay_store.h"
#include "sdkconfig.h"

static bool s_active_high;
static uint8_t s_mask;
static bool s_pulse;
static uint32_t s_pulse_start_ms;
static uint32_t s_pulse_len_ms;
static bool s_restored;

static uint32_t now_ms(void)
{
    return (uint32_t)(esp_timer_get_time() / 1000ULL);
}

static void drive(void)
{
    int on = (s_mask & 0x01) != 0;
    gpio_set_level(CONFIG_RELAY_GPIO, s_active_high ? on : !on);
}

void relay_logic_init(void)
{
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
    drive();
}

uint8_t relay_logic_apply(uint8_t states, uint16_t pulse_ms)
{
    uint8_t next = states & 0x01;
    if (pulse_ms == 0) {
        s_pulse = false;
        s_pulse_len_ms = 0;
    } else {
        s_pulse = true;
        s_pulse_len_ms = pulse_ms;
        s_pulse_start_ms = now_ms();
    }
    if (next != s_mask) {
        s_mask = next;
        relay_store_set_mask(s_mask);
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
    if (!s_pulse) return;
    uint32_t elapsed = now_ms() - s_pulse_start_ms;
    if (elapsed >= s_pulse_len_ms) {
        s_pulse = false;
        s_pulse_len_ms = 0;
        if (s_mask != 0) {
            s_mask = 0;
            relay_store_set_mask(s_mask);
            drive();
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
