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
/** Connectable advertising so a phone can connect. Keeps going while a slot is free. */
void ble_phone_start_adv(void);
/** Drop every phone link and stop advertising. */
void ble_phone_stop_link(void);
/**
 * CONNECT, DISCONNECT, SUBSCRIBE, MTU, ADV_COMPLETE, and connection-parameter requests.
 * The return value is the GAP result: 0, or an HCI error that rejects a short interval.
 */
int ble_phone_on_gap(struct ble_gap_event *event);

#define PHONE_TITLE_MAX 60
#define PHONE_ARTIST_MAX 30
/* title, '\0', artist */
#define PHONE_TEXT_MAX (PHONE_TITLE_MAX + 1 + PHONE_ARTIST_MAX)

typedef struct {
    int playing;      /* 1, 0, or -1 when unknown */
    int64_t pos_ms;   /* -1 when unknown */
    int64_t dur_ms;   /* -1 when unknown */
    uint8_t text[PHONE_TEXT_MAX];
    size_t text_len;  /* 0 when there is no title and no artist */
} phone_media_t;

#define PHONE_SNAP_VALS 24

/**
 * vals: left, right, fan, mode, auto, blow, sync, seat0..3, vol, window FL/FR/RL/RR
 * (0..100, 0xFE between stops), sunroof (0..100, 102 tilt), sunshade, head unit 9/10,
 * outside and cabin temperature in tenths of °C, recirculation, front climate, A/C
 * (1 on, 0 off). Front climate 1 means the front section is running.
 * Missing fields have the corresponding mask bit clear. media may be NULL.
 */
void ble_phone_set_snapshot(int gen, uint32_t mask, const int vals[PHONE_SNAP_VALS], const phone_media_t *media);

void ble_phone_poll(uint32_t now_ms);

/** JSON array of {id,name}. No keys. */
int ble_phone_write_json(char *out, size_t cap);

