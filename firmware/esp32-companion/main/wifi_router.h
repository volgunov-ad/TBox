#pragma once

#include <stdbool.h>

/** Queue only. Wi-Fi starts on the first enable. */
void wifi_router_init(void);

/**
 * Host `apCfg`. `on` starts SoftAP + STA to the head-unit AP and NAT.
 * `on == false` stops the SoftAP radio. Empty head-unit credentials with `on` are rejected.
 * Empty `ap_ssid` / `ap_psk` leave the companion AP name and password unchanged.
 */
void wifi_router_request(bool on, const char *hu_ssid, const char *hu_psk, int port,
                         const char *ap_ssid, const char *ap_psk);
