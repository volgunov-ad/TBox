#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define BLE_DEVICE_MAX 20

void ble_phone_init(void);
int ble_phone_count(void);
bool ble_phone_is_learn(void);

bool ble_phone_learn_begin(uint32_t timeout_ms);
void ble_phone_learn_end(void);
bool ble_phone_allow(const char *id_hex);
/** All BLE_DEVICE_MAX slots (phones + remotes) are taken. */
bool ble_phone_slots_full(void);
void ble_phone_deny(const char *id_hex);
bool ble_phone_forget(const char *id_hex);

/** Service-data payload after the 16-bit UUID. */
void ble_phone_on_adv(const uint8_t *payload, uint8_t len);

/**
 * vals: left, right, fan, mode, auto, blow, sync, seat0..3, vol.
 * Missing fields have the corresponding mask bit clear.
 */
void ble_phone_set_snapshot(int gen, uint16_t mask, const int vals[12]);

void ble_phone_poll(uint32_t now_ms);

/** JSON array of {id,name}. No keys. */
int ble_phone_write_json(char *out, size_t cap);

bool ble_phone_adv_pending(void);
/** Start one legacy advertisement. Call from the NimBLE host context. */
bool ble_phone_kick_adv(void);
