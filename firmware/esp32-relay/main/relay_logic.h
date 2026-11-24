#pragma once

#include <stdbool.h>
#include <stdint.h>

/**
 * Configure the relay GPIO and drive the last latched state from NVS.
 * A pulse is not restored: power loss during a pulse comes back open
 * (or at the previous latch), never stuck closed from the pulse itself.
 */
void relay_logic_init(void);

/**
 * Apply a 0x30 body: states bit for masked channels, pulse_ms 0 = hold.
 * Returns the actual channel mask. NVS is written only for a latch
 * (pulse_ms == 0) and when a pulse ends (forced open).
 */
uint8_t relay_logic_apply(uint8_t states, uint16_t pulse_ms);

/** Expire a running pulse and apply the latched-on limit. Call from the main loop. */
void relay_logic_poll(void);

uint8_t relay_logic_mask(void);
bool relay_logic_pulse_active(void);
/** One-shot: true on the first call after boot (state restored from NVS). */
bool relay_logic_take_restored(void);
/** Remember that max-on or link-loss opened the coil. Not used for a pulse end. */
void relay_logic_mark_self_opened(void);
/** One-shot flag for the next 0x31 (bit 3). */
bool relay_logic_take_self_opened(void);
/** Board button emulation pulse, RELAY_PULSE_MS long. */
uint8_t relay_logic_press(void);

/** Serialize relay_logic and relay_store RAM with the BLE task. NVS stays on the main task. */
void relay_state_lock(void);
void relay_state_unlock(void);
