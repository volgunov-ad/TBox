#pragma once

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

#define ESP_COMPANION_FW_VERSION "0.4.3"
#define ESP_COMPANION_GPIO_IN_COUNT 8
#define ESP_COMPANION_RELAY_COUNT 4
#define ESP_COMPANION_PROTO_V 1
#define ESP_COMPANION_DEFAULT_UM980_BAUD 115200

/** OTA binary frame: 0xA5 0x5A | u16be len | payload | u32be crc32(payload) */
#define OTA_FRAME_MAGIC0 0xA5
#define OTA_FRAME_MAGIC1 0x5A
#define OTA_FRAME_MAX_PAYLOAD 1024

void protocol_init(void);
void protocol_on_rx_bytes(const uint8_t *data, size_t len);
void protocol_send_hello(void);
void protocol_send_hb(uint32_t uptime_ms);
void protocol_send_gps(int fix, double lat, double lon, double alt,
                       float speed_kmh, float course,
                       int sats_used, int sats_vis, const char *utc_iso);
void protocol_send_gpio(uint16_t mask, uint32_t ms);
void protocol_send_gpio_event(int ch, int level, uint32_t ms);
void protocol_send_relay(uint8_t mask);
void protocol_send_um980_rsp(const char *cmd, const char *const *lines, int line_count, bool ok);
void protocol_send_um980_baud(int baud, bool ok);
void protocol_send_reboot_ack(void);
void protocol_send_ota_ack(const char *phase, uint32_t offset, bool ok, const char *err);
void protocol_send_ota_done(bool ok, const char *err);

/** True while OTA transfer is in progress (suppress gps; hb kept rare). */
bool protocol_ota_active(void);

/** Set after successful otaEnd ACK — main loop reboots (not from CDC RX). */
bool protocol_ota_restart_pending(void);

/** Current UART baud for hello (set by main before send). */
void protocol_set_um980_baud_for_hello(int baud);

typedef void (*protocol_relay_set_cb_t)(uint8_t mask);
typedef void (*protocol_um980_cmd_cb_t)(const char *cmd);
typedef void (*protocol_um980_baud_cb_t)(int baud);
typedef void (*protocol_reboot_cb_t)(void);

void protocol_set_relay_callback(protocol_relay_set_cb_t cb);
void protocol_set_um980_cmd_callback(protocol_um980_cmd_cb_t cb);
void protocol_set_um980_baud_callback(protocol_um980_baud_cb_t cb);
void protocol_set_reboot_callback(protocol_reboot_cb_t cb);
