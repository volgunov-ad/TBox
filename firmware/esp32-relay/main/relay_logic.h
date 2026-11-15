#pragma once

#include <stdbool.h>
#include <stdint.h>

/** Drive RELAY_GPIO low (open). Read relay_store_mask for the restored state. */
void relay_logic_init(void);

/**
 * Apply a 0x30 body: states bit for masked channels, pulse_ms 0 = hold.
 * Returns the actual channel mask. NVS mask is written on change.
 */
uint8_t relay_logic_apply(uint8_t states, uint16_t pulse_ms);

/** Expire a running pulse. Call from the main loop. */
void relay_logic_poll(void);

uint8_t relay_logic_mask(void);
bool relay_logic_pulse_active(void);
/** One-shot: true on the first call after boot (state restored from NVS). */
bool relay_logic_take_restored(void);
/** Board button emulation pulse, RELAY_PULSE_MS long. */
uint8_t relay_logic_press(void);
