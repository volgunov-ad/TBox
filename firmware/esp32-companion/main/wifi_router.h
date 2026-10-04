#pragma once

#include <stdbool.h>

/** Queue only. Wi-Fi starts on the first enable. */
void wifi_router_init(void);

/**
 * Host `apCfg`. `on` starts SoftAP + STA to the head-unit AP and NAT.
 * Empty credentials with `on` are rejected.
 */
void wifi_router_request(bool on, const char *hu_ssid, const char *hu_psk, int port);
