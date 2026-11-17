#include "can_map.h"

#include "esp_log.h"

#include "climate_model.h"

#define CAN_MAP_SEEN_MAX 64
#define CAN_MAP_ID_CLIMATE_SET 0x52F

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

static void mark_seen(uint32_t id)
{
    if (seen_has(id) || s_seen_count >= CAN_MAP_SEEN_MAX) {
        return;
    }
    s_seen_ids[s_seen_count++] = id;
    ESP_LOGI(TAG, "new CAN id 0x%03lX", (unsigned long)id);
}

static void apply_climate_set(const uint8_t *data, uint8_t dlc)
{
    if (dlc < 6 || data[5] == 0) {
        return;
    }
    int temp_c10 = (int)data[5] * 10 / 4;
    climate_state_t st;
    climate_model_get(&st);
    st.temp_left_c10 = (int16_t)temp_c10;
    st.temp_right_c10 = (int16_t)temp_c10;
    climate_model_set(&st);
}

void can_map_apply(uint32_t id, bool ext, const uint8_t *data, uint8_t dlc)
{
    if (ext) {
        return;
    }
    mark_seen(id);
    if (id == CAN_MAP_ID_CLIMATE_SET) {
        apply_climate_set(data, dlc);
    }
}

size_t can_map_seen_count(void)
{
    return s_seen_count;
}

const uint32_t *can_map_seen_ids(void)
{
    return s_seen_ids;
}
