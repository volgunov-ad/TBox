#pragma once

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

#define ESP_COMPANION_FW_VERSION "0.1.0"
#define ESP_COMPANION_GPIO_IN_COUNT 8
#define ESP_COMPANION_RELAY_COUNT 4
#define ESP_COMPANION_PROTO_V 1

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

typedef void (*protocol_relay_set_cb_t)(uint8_t mask);
void protocol_set_relay_callback(protocol_relay_set_cb_t cb);
