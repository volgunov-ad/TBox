#pragma once

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

#define ESP_COMPANION_FW_VERSION "0.5.0"
#define ESP_COMPANION_GPIO_IN_COUNT 4
#define ESP_COMPANION_RELAY_COUNT 2
#define ESP_COMPANION_PROTO_V 1
#define ESP_COMPANION_DEFAULT_UM980_BAUD 115200

/** OTA / light-bridge binary frame: 0xA5 0x5A | u16be len | payload | u32be crc32(payload) */
#define OTA_FRAME_MAGIC0 0xA5
#define OTA_FRAME_MAGIC1 0x5A
#define OTA_FRAME_MAX_PAYLOAD 1024

/** Compact CAN frame in light mode (device↔host payload record). */
#define CAN_LIGHT_FRAME_LEN 14
#define CAN_LIGHT_FLAG_EXT 0x01
#define CAN_LIGHT_FLAG_RTR 0x02
#define CAN_LIGHT_FLAG_TX  0x04

void protocol_init(void);
void protocol_on_rx_bytes(const uint8_t *data, size_t len);
void protocol_send_hello(void);
void protocol_send_hb(uint32_t uptime_ms);
void protocol_send_gps(int fix, double lat, double lon, double alt,
                       float speed_kmh, float course,
                       int sats_used, int sats_vis, const char *utc_iso,
                       float hdop, float pdop, float vdop,
                       float hrms, float vrms, float diff_age);
void protocol_send_gpio(uint16_t mask, uint32_t ms);
void protocol_send_gpio_event(int ch, int level, uint32_t ms);
void protocol_send_relay(uint8_t mask);
void protocol_send_um980_rsp(const char *cmd, const char *const *lines, int line_count, bool ok);
void protocol_send_um980_baud(int baud, bool ok);
void protocol_send_reboot_ack(void);
void protocol_send_ota_ack(const char *phase, uint32_t offset, bool ok, const char *err);
void protocol_send_ota_done(bool ok, const char *err);

/** UM980 UART bridge (firmware update / raw tunnel). */
void protocol_send_um980_bridge_ack(const char *phase, bool ok, const char *err);
/** Push UART→Host as OTA-style binary frame while bridge active. */
void protocol_bridge_send_uart_bytes(const uint8_t *data, size_t len);
bool protocol_um980_bridge_active(void);
/** Main loop: pump UART→CDC while bridge active. */
void protocol_um980_bridge_poll(void);

/** CAN (MCP2515) control / light stream. */
void protocol_send_can_ack(const char *phase, bool ok, const char *err);
void protocol_send_can_baud(uint32_t baud, bool ok);
void protocol_send_can_filter_ack(bool ok, const char *err);
/** Encode one RX frame into light binary (may batch). */
void protocol_can_light_send_rx(uint32_t id, bool ext, bool rtr, uint8_t dlc, const uint8_t *data);
void protocol_can_light_poll_flush(void);
bool protocol_can_light_active(void);
void protocol_set_can_for_hello(bool present, uint32_t baud);

/** True while OTA / UM980 bridge is active (suppress gps; keep rare hb). CAN light is separate. */
bool protocol_ota_active(void);

/** Set after successful otaEnd ACK — main loop reboots (not from CDC RX). */
bool protocol_ota_restart_pending(void);

/** Current UART baud for hello (set by main before send). */
void protocol_set_um980_baud_for_hello(int baud);

typedef void (*protocol_relay_set_cb_t)(uint8_t mask);
typedef void (*protocol_um980_cmd_cb_t)(const char *cmd);
typedef void (*protocol_um980_baud_cb_t)(int baud);
typedef void (*protocol_reboot_cb_t)(void);
typedef void (*protocol_can_tx_cb_t)(uint32_t id, bool ext, bool rtr, uint8_t dlc, const uint8_t *data);
typedef void (*protocol_can_baud_cb_t)(uint32_t baud);
typedef void (*protocol_can_filter_cb_t)(bool accept_all, const uint32_t *ids, const uint32_t *masks,
                                         const bool *ext, int count);
typedef void (*protocol_can_light_cb_t)(bool enable);

void protocol_set_relay_callback(protocol_relay_set_cb_t cb);
void protocol_set_um980_cmd_callback(protocol_um980_cmd_cb_t cb);
void protocol_set_um980_baud_callback(protocol_um980_baud_cb_t cb);
void protocol_set_reboot_callback(protocol_reboot_cb_t cb);
void protocol_set_can_tx_callback(protocol_can_tx_cb_t cb);
void protocol_set_can_baud_callback(protocol_can_baud_cb_t cb);
void protocol_set_can_filter_callback(protocol_can_filter_cb_t cb);
void protocol_set_can_light_callback(protocol_can_light_cb_t cb);
