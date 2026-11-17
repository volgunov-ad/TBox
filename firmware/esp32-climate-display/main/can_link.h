#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "driver/twai.h"
#include "freertos/FreeRTOS.h"

typedef struct {
    uint32_t frames;
    uint32_t dropped;
    uint32_t errors;
    bool online;
} can_link_stats_t;

esp_err_t can_link_start(void);

bool can_link_pop(twai_message_t *msg, uint32_t timeout_ms);

void can_link_get_stats(can_link_stats_t *out_stats);
