#pragma once

#include <stdint.h>

/** Phones linked at once. Shelly scanning keeps a 40 ms window every 80 ms. */
#define PHONE_LINK_MAX 3

/* Connection interval is in 1.25 ms units: 160 = 200 ms, 240 = 300 ms. */
#define PHONE_ITVL_MIN 160
#define PHONE_ITVL_MAX 240
#define PHONE_ITVL_LATENCY 0
/** Supervision timeout in 10 ms units (5 s). Must exceed two max intervals. */
#define PHONE_ITVL_TIMEOUT 500

void phone_link_preferred(uint16_t *itvl_min, uint16_t *itvl_max,
                          uint16_t *latency, uint16_t *timeout);

/** Peer minimum is already in our window, so the request can be accepted. */
int phone_link_params_ok(uint16_t peer_itvl_min);
