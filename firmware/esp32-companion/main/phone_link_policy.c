#include "phone_link_policy.h"

void phone_link_preferred(uint16_t *itvl_min, uint16_t *itvl_max,
                          uint16_t *latency, uint16_t *timeout)
{
    if (itvl_min) *itvl_min = PHONE_ITVL_MIN;
    if (itvl_max) *itvl_max = PHONE_ITVL_MAX;
    if (latency) *latency = PHONE_ITVL_LATENCY;
    if (timeout) *timeout = PHONE_ITVL_TIMEOUT;
}

int phone_link_params_ok(uint16_t peer_itvl_min)
{
    return peer_itvl_min >= PHONE_ITVL_MIN;
}
