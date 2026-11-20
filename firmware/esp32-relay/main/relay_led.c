#include <stdint.h>

#include "driver/gpio.h"
#include "esp_timer.h"

#include "relay_led.h"
#include "sdkconfig.h"

static bool s_active_high;
static bool s_disabled;
static relay_led_mode_t s_mode = RELAY_LED_SLOW;
static uint32_t s_mode_start_ms;

static uint32_t now_ms(void)
{
    return (uint32_t)(esp_timer_get_time() / 1000ULL);
}

static void drive(int on)
{
    gpio_set_level(CONFIG_RELAY_LED_GPIO, s_active_high ? on : !on);
}

void relay_led_init(void)
{
    if (CONFIG_RELAY_LED_GPIO < 0) {
        s_disabled = true;
        return;
    }
    s_active_high = CONFIG_RELAY_LED_ACTIVE_HIGH;
    gpio_config_t cfg = {
        .pin_bit_mask = 1ULL << CONFIG_RELAY_LED_GPIO,
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    gpio_config(&cfg);
    s_mode_start_ms = now_ms();
    drive(0);
}

void relay_led_set_mode(relay_led_mode_t mode)
{
    if (s_disabled || mode == s_mode) return;
    s_mode = mode;
    /* Restart the pattern so a new state is visible at once. */
    s_mode_start_ms = now_ms();
}

void relay_led_poll(void)
{
    if (s_disabled) return;
    uint32_t t = now_ms() - s_mode_start_ms;
    int on = 0;
    switch (s_mode) {
    case RELAY_LED_ON:
        on = 1;
        break;
    case RELAY_LED_FAST:
        on = (t / 100u) % 2u == 0;
        break;
    case RELAY_LED_SLOW:
        on = (t % 1000u) < 400u;
        break;
    case RELAY_LED_HOLD:
        on = (t / 50u) % 2u == 0;
        break;
    }
    drive(on);
}
