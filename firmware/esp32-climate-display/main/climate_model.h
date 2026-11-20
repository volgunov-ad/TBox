#pragma once

#include <stdbool.h>
#include <stdint.h>

#define CLIMATE_TEMP_MIN_C10    (160)
#define CLIMATE_TEMP_MAX_C10    (300)
#define CLIMATE_TEMP_STEP_C10   (5)
#define CLIMATE_FAN_MIN         (0)
#define CLIMATE_FAN_MAX         (7)

typedef enum {
    CLIMATE_MODE_FACE = 1,
    CLIMATE_MODE_FACE_FEET,
    CLIMATE_MODE_FEET,
    CLIMATE_MODE_FEET_DEFROST,
    CLIMATE_MODE_DEFROST,
} climate_mode_t;

typedef struct {
    int16_t temp_left_c10;
    int16_t temp_right_c10;
    uint8_t fan_speed;
    bool ac_on;
    bool sync_on;
    bool front_defrost;
    bool rear_defrost;
    climate_mode_t mode;
} climate_state_t;

typedef void (*climate_change_cb_t)(const climate_state_t *state);

void climate_model_init(void);

void climate_model_get(climate_state_t *out_state);

void climate_model_set(const climate_state_t *state);

void climate_model_set_change_cb(climate_change_cb_t cb);

void climate_model_adjust_temp(bool left, int delta_c10);

void climate_model_set_fan(uint8_t speed);

void climate_model_cycle_fan(int delta);

void climate_model_toggle_ac(void);

void climate_model_toggle_sync(void);

void climate_model_toggle_defrost(bool front);

void climate_model_cycle_mode(void);
