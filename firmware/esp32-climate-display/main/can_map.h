#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

void can_map_apply(uint32_t id, bool ext, const uint8_t *data, uint8_t dlc);

size_t can_map_seen_count(void);

const uint32_t *can_map_seen_ids(void);
