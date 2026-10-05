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

struct ble_gap_event;

/** Register the phone GATT service. Call after nimble_port_init, before the host task. */
void ble_phone_gatts_register(void);
/** Connectable advertising so a phone can connect. No-op while a phone is linked. */
void ble_phone_start_adv(void);
/** Drop the phone link and stop advertising. */
void ble_phone_stop_link(void);
/** CONNECT, DISCONNECT, SUBSCRIBE, MTU, ADV_COMPLETE. */
void ble_phone_on_gap(struct ble_gap_event *event);

/**
 * vals: left, right, fan, mode, auto, blow, sync, seat0..3, vol.
 * Missing fields have the corresponding mask bit clear.
 */
void ble_phone_set_snapshot(int gen, uint16_t mask, const int vals[12]);

void ble_phone_poll(uint32_t now_ms);

/** JSON array of {id,name}. No keys. */
int ble_phone_write_json(char *out, size_t cap);

