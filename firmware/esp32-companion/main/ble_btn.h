#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "ble_phone.h"

#define BLE_BTN_MAX_MACS BLE_DEVICE_MAX
#define BLE_BTN_LEARN_DEFAULT_MS 30000u

struct ble_gap_event;
int ble_btn_gap_event(struct ble_gap_event *event, void *arg);

/**
 * Recursive lock for radio and phone state. NimBLE host, TinyUSB RX and the
 * main loop all touch it. NimBLE calls GAP callbacks without its own host lock,
 * so taking this inside a callback cannot deadlock against ble_gap_* calls.
 */
void ble_radio_lock(void);
void ble_radio_unlock(void);

/** Init NVS state and optionally start NimBLE scan if previously enabled. */
void ble_btn_init(void);

bool ble_btn_is_on(void);
bool ble_btn_is_learn(void);
int ble_btn_mac_count(void);
/** Write allowlisted MACs as "aa:bb:…" into out[]; returns count. */
int ble_btn_get_macs(char out[][18], int max_out);

/** Enable/disable passive scan; persists to NVS. */
bool ble_btn_set_on(bool on);

/** key: 16-byte BTHome key the new remote encrypts with, or NULL for a plain remote. */
bool ble_btn_learn_begin(uint32_t timeout_ms, const uint8_t *key);
void ble_btn_learn_end(void);

/** Add MAC string "aa:bb:cc:dd:ee:ff" (any case, ':' or '-' ok). */
bool ble_btn_allow(const char *mac_str);
bool ble_btn_forget(const char *mac_str);
bool ble_btn_forget_all(void);
/** Set (key != NULL) or clear the BTHome key of a known remote; resets its counter. */
bool ble_btn_set_key(const char *mac_str, const uint8_t *key);
/** Remotes that have a key, same format as ble_btn_get_macs. */
int ble_btn_get_keyed(char out[][18], int max_out);

/** Last known battery (-1 if unknown) and RSSI for status. */
int ble_btn_last_bat(void);
int ble_btn_last_rssi(void);
void ble_btn_last_mac(char out[18]);

/** Main-loop tick: expire learn window. */
void ble_btn_poll(uint32_t now_ms);

/** Stop the Shelly scan without waiting for DISC_COMPLETE. */
void ble_btn_suspend_scan(void);
/** Start the scan again when BLE is on. */
void ble_btn_kick_scan(void);
