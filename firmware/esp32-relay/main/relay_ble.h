#pragma once

#include <stdbool.h>
#include <stdint.h>

/**
 * NimBLE central: scan for the companion ("TBox" / UUID16 0x7B0E), connect, enable
 * notifications on 0x7B10, pair with a cleartext 0x11 packet only while the
 * companion advertises an open learn window, then exchange sealed 0x30 / 0x31.
 */
void relay_ble_start(void);

/** Kick a state report (pulse start and pulse end). */
void relay_ble_kick_state(void);

/** Paired and subscribed to companion notifications right now. */
bool relay_ble_online(void);

/**
 * Host task only enqueues. The main loop takes each raw notify and applies it
 * (relay logic, NVS) so the NimBLE host task never writes flash.
 * Returns false when the queue is empty.
 */
bool relay_ble_take_cmd(uint8_t pkt[24]);

/** Main loop: unseal, persist the RX counter, and apply the relay. */
void relay_ble_handle_cmd(const uint8_t pkt[24]);

/** Address of the connected companion, captured on CONNECT. */
bool relay_ble_connected_peer(uint8_t *addr_type, uint8_t addr[6]);
