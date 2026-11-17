#pragma once

#include <stdbool.h>
#include <stdint.h>

#define CLIMATE_FW_VERSION "0.1.0"

void companion_link_start(bool can_mode, uint32_t can_baud);
