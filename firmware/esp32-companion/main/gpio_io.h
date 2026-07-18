#pragma once

#include <stdint.h>

void gpio_io_init(void);
uint16_t gpio_io_read_inputs(void);
void gpio_io_set_relays(uint8_t mask);
uint8_t gpio_io_get_relays(void);
/** Returns true if any input changed after debounce; fills events via callbacks in poll. */
typedef void (*gpio_event_cb_t)(int ch, int level, uint32_t ms);
void gpio_io_poll(uint32_t now_ms, gpio_event_cb_t on_event);
