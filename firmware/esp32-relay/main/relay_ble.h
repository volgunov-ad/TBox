#pragma once

/**
 * NimBLE central: scan for the companion ("TBox" / UUID16 0x7B0E), connect, enable
 * notifications on 0x7B10, pair with a cleartext 0x11 packet while the companion
 * learn window is open, then exchange sealed 0x30 commands / 0x31 states.
 */
void relay_ble_start(void);

/** Kick a state report (e.g. after a pulse expiry outside the BLE task). */
void relay_ble_kick_state(void);
