#pragma once

#include <stdbool.h>
#include <stdint.h>

void gpio_io_init(void);
uint16_t gpio_io_read_inputs(void);
void gpio_io_set_relays(uint8_t mask);
/**
 * Close the set bits for pulse_ms, then return them to the latched mask.
 * The timer runs on the companion, so a lost USB OFF cannot leave the coil on.
 */
void gpio_io_pulse(uint8_t channel_mask, uint16_t pulse_ms);
uint8_t gpio_io_get_relays(void);
/** True once after the driven mask changes (latch or a pulse edge). */
bool gpio_io_take_relay_dirty(void);
/** Returns true if any input changed after debounce; fills events via callbacks in poll. */
typedef void (*gpio_event_cb_t)(int ch, int level, uint32_t ms);
void gpio_io_poll(uint32_t now_ms, gpio_event_cb_t on_event);
