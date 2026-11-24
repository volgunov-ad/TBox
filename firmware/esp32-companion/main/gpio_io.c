#include "gpio_io.h"

#include "driver/gpio.h"
#include "esp_timer.h"

#define GPIO_IN_COUNT 4
#define RELAY_COUNT 2
#define DEBOUNCE_MS 30

static const int s_in_pins[GPIO_IN_COUNT] = {1, 2, 3, 4};
static const int s_relay_pins[RELAY_COUNT] = {9, 10};

static uint8_t s_relay_mask;
static uint8_t s_latched_mask;
static uint8_t s_pulse_mask;
static uint32_t s_pulse_until[RELAY_COUNT];
static bool s_relay_dirty;
static uint8_t s_stable_mask;
static uint8_t s_raw_mask;
static uint32_t s_last_change_ms[GPIO_IN_COUNT];

static void drive_relays(void)
{
    s_relay_mask = (uint8_t)((s_latched_mask | s_pulse_mask) & ((1 << RELAY_COUNT) - 1));
    for (int i = 0; i < RELAY_COUNT; i++) {
        gpio_set_level(s_relay_pins[i], (s_relay_mask >> i) & 1);
    }
}

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
    s_latched_mask = 0;
    s_pulse_mask = 0;
    s_relay_dirty = false;
    s_stable_mask = 0;
    s_raw_mask = 0;
}

uint16_t gpio_io_read_inputs(void)
{
    return s_stable_mask;
}

void gpio_io_set_relays(uint8_t mask)
{
    s_latched_mask = mask & ((1 << RELAY_COUNT) - 1);
    s_pulse_mask = 0;
    drive_relays();
    s_relay_dirty = true;
}

void gpio_io_pulse(uint8_t channel_mask, uint16_t pulse_ms)
{
    channel_mask &= (1 << RELAY_COUNT) - 1;
    if (channel_mask == 0 || pulse_ms == 0) return;
    uint32_t until = (uint32_t)(esp_timer_get_time() / 1000ULL) + pulse_ms;
    if (until == 0) until = 1;
    for (int i = 0; i < RELAY_COUNT; i++) {
        if (channel_mask & (1 << i)) {
            s_pulse_mask |= (uint8_t)(1 << i);
            s_pulse_until[i] = until;
        }
    }
    drive_relays();
    s_relay_dirty = true;
}

bool gpio_io_take_relay_dirty(void)
{
    if (!s_relay_dirty) return false;
    s_relay_dirty = false;
    return true;
}

uint8_t gpio_io_get_relays(void)
{
    return s_relay_mask;
}

void gpio_io_poll(uint32_t now_ms, gpio_event_cb_t on_event)
{
    uint8_t pulse = s_pulse_mask;
    for (int i = 0; i < RELAY_COUNT; i++) {
        if ((pulse & (1 << i)) && (int32_t)(now_ms - s_pulse_until[i]) >= 0) {
            s_pulse_mask &= (uint8_t)~(1 << i);
        }
    }
    if (s_pulse_mask != pulse) {
        drive_relays();
        s_relay_dirty = true;
    }
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
