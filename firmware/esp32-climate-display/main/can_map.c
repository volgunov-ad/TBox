#include "can_map.h"

#include "esp_log.h"

#define CAN_MAP_SEEN_MAX 64

static const char *TAG = "can_map";

static uint32_t s_seen_ids[CAN_MAP_SEEN_MAX];
static size_t s_seen_count = 0;

static bool seen_has(uint32_t id)
{
    for (size_t i = 0; i < s_seen_count; i++) {
        if (s_seen_ids[i] == id) {
            return true;
        }
    }
    return false;
}

void can_map_apply(uint32_t id, bool ext, const uint8_t *data, uint8_t dlc)
{
    (void)data;
    (void)dlc;
    if (ext) {
        return;
    }
    if (seen_has(id)) {
        return;
    }
    if (s_seen_count >= CAN_MAP_SEEN_MAX) {
        return;
    }
    s_seen_ids[s_seen_count++] = id;
    ESP_LOGI(TAG, "new CAN id 0x%03lX", (unsigned long)id);
}

size_t can_map_seen_count(void)
{
    return s_seen_count;
}

const uint32_t *can_map_seen_ids(void)
{
    return s_seen_ids;
}
