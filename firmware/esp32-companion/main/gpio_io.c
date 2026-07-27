#include "gpio_io.h"

#include "driver/gpio.h"

#define GPIO_IN_COUNT 4
#define RELAY_COUNT 2
#define DEBOUNCE_MS 30

static const int s_in_pins[GPIO_IN_COUNT] = {1, 2, 3, 4};
static const int s_relay_pins[RELAY_COUNT] = {9, 10};

static uint8_t s_relay_mask;
static uint8_t s_stable_mask;
static uint8_t s_raw_mask;
static uint32_t s_last_change_ms[GPIO_IN_COUNT];

void gpio_io_init(void)
{
    for (int i = 0; i < GPIO_IN_COUNT; i++) {
        gpio_config_t io = {
            .pin_bit_mask = 1ULL << s_in_pins[i],
            .mode = GPIO_MODE_INPUT,
            .pull_up_en = GPIO_PULLUP_ENABLE,
            .pull_down_en = GPIO_PULLDOWN_DISABLE,
            .intr_type = GPIO_INTR_DISABLE,
        };
        gpio_config(&io);
        s_last_change_ms[i] = 0;
    }
    for (int i = 0; i < RELAY_COUNT; i++) {
        gpio_config_t io = {
            .pin_bit_mask = 1ULL << s_relay_pins[i],
            .mode = GPIO_MODE_OUTPUT,
            .pull_up_en = GPIO_PULLUP_DISABLE,
            .pull_down_en = GPIO_PULLDOWN_DISABLE,
            .intr_type = GPIO_INTR_DISABLE,
        };
        gpio_config(&io);
        gpio_set_level(s_relay_pins[i], 0);
    }
    s_relay_mask = 0;
    s_stable_mask = 0;
    s_raw_mask = 0;
}

uint16_t gpio_io_read_inputs(void)
{
    return s_stable_mask;
}

void gpio_io_set_relays(uint8_t mask)
{
    s_relay_mask = mask & ((1 << RELAY_COUNT) - 1);
    for (int i = 0; i < RELAY_COUNT; i++) {
        gpio_set_level(s_relay_pins[i], (s_relay_mask >> i) & 1);
    }
}

uint8_t gpio_io_get_relays(void)
{
    return s_relay_mask;
}

void gpio_io_poll(uint32_t now_ms, gpio_event_cb_t on_event)
{
    uint8_t raw = 0;
    for (int i = 0; i < GPIO_IN_COUNT; i++) {
        int level = gpio_get_level(s_in_pins[i]);
        /* Active-low with pull-up: pressed/closed = 0 → report as 1 */
        int logical = level == 0 ? 1 : 0;
        if (logical) {
            raw |= (1 << i);
        }
        int prev_raw = (s_raw_mask >> i) & 1;
        if (logical != prev_raw) {
            s_last_change_ms[i] = now_ms;
        }
        if ((now_ms - s_last_change_ms[i]) >= DEBOUNCE_MS) {
            int prev_stable = (s_stable_mask >> i) & 1;
            if (logical != prev_stable) {
                if (logical) {
                    s_stable_mask |= (1 << i);
                } else {
                    s_stable_mask &= ~(1 << i);
                }
                if (on_event) {
                    on_event(i, logical, now_ms);
                }
            }
        }
    }
    s_raw_mask = raw;
}
