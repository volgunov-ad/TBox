#pragma once

#include <stdbool.h>
#include <stdint.h>

#define BLE_BTN_MAX_MACS 4
#define BLE_BTN_LEARN_DEFAULT_MS 30000u

/** Init NVS state and optionally start NimBLE scan if previously enabled. */
void ble_btn_init(void);

bool ble_btn_is_on(void);
bool ble_btn_is_learn(void);
int ble_btn_mac_count(void);
/** Write allowlisted MACs as "aa:bb:…" into out[]; returns count. */
int ble_btn_get_macs(char out[][18], int max_out);

/** Enable/disable passive scan; persists to NVS. */
bool ble_btn_set_on(bool on);

bool ble_btn_learn_begin(uint32_t timeout_ms);
void ble_btn_learn_end(void);

/** Add MAC string "aa:bb:cc:dd:ee:ff" (any case, ':' or '-' ok). */
bool ble_btn_allow(const char *mac_str);
bool ble_btn_forget(const char *mac_str);
bool ble_btn_forget_all(void);

/** Last known battery (-1 if unknown) and RSSI for status. */
int ble_btn_last_bat(void);
int ble_btn_last_rssi(void);
void ble_btn_last_mac(char out[18]);

/** Main-loop tick: expire learn window. */
void ble_btn_poll(uint32_t now_ms);
