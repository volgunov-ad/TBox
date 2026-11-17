#include "climate_model.h"

#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

static climate_state_t s_state = {
    .temp_left_c10 = 220,
    .temp_right_c10 = 220,
    .fan_speed = 3,
    .ac_on = true,
    .mode = CLIMATE_MODE_FACE_FEET,
};

static SemaphoreHandle_t s_lock = NULL;
static climate_change_cb_t s_cb = NULL;

static int16_t clamp_temp_c10(int v)
{
    if (v < CLIMATE_TEMP_MIN_C10) {
        return (int16_t)CLIMATE_TEMP_MIN_C10;
    }
    if (v > CLIMATE_TEMP_MAX_C10) {
        return (int16_t)CLIMATE_TEMP_MAX_C10;
    }
    return (int16_t)v;
}

static uint8_t clamp_fan(int v)
{
    if (v < CLIMATE_FAN_MIN) {
        return (uint8_t)CLIMATE_FAN_MIN;
    }
    if (v > CLIMATE_FAN_MAX) {
        return (uint8_t)CLIMATE_FAN_MAX;
    }
    return (uint8_t)v;
}

static climate_mode_t clamp_mode(climate_mode_t mode)
{
    if (mode < CLIMATE_MODE_FACE || mode > CLIMATE_MODE_DEFROST) {
        return CLIMATE_MODE_FACE;
    }
    return mode;
}

static void emit_change(void)
{
    if (s_cb != NULL) {
        s_cb(&s_state);
    }
}

void climate_model_init(void)
{
    if (s_lock != NULL) {
        return;
    }
    s_lock = xSemaphoreCreateMutex();
}

void climate_model_get(climate_state_t *out_state)
{
    if (out_state == NULL || s_lock == NULL) {
        return;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    *out_state = s_state;
    xSemaphoreGive(s_lock);
}

void climate_model_set(const climate_state_t *state)
{
    if (state == NULL || s_lock == NULL) {
        return;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_state = *state;
    s_state.temp_left_c10 = clamp_temp_c10(s_state.temp_left_c10);
    s_state.temp_right_c10 = clamp_temp_c10(s_state.temp_right_c10);
    s_state.fan_speed = clamp_fan(s_state.fan_speed);
    s_state.mode = clamp_mode(s_state.mode);
    xSemaphoreGive(s_lock);
    emit_change();
}

void climate_model_set_change_cb(climate_change_cb_t cb)
{
    s_cb = cb;
}

void climate_model_adjust_temp(bool left, int delta_c10)
{
    if (s_lock == NULL) {
        return;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    if (s_state.sync_on) {
        s_state.temp_left_c10 = clamp_temp_c10((int)s_state.temp_left_c10 + delta_c10);
        s_state.temp_right_c10 = s_state.temp_left_c10;
    } else if (left) {
        s_state.temp_left_c10 = clamp_temp_c10((int)s_state.temp_left_c10 + delta_c10);
    } else {
        s_state.temp_right_c10 = clamp_temp_c10((int)s_state.temp_right_c10 + delta_c10);
    }
    xSemaphoreGive(s_lock);
    emit_change();
}

void climate_model_set_fan(uint8_t speed)
{
    if (s_lock == NULL) {
        return;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_state.fan_speed = clamp_fan(speed);
    xSemaphoreGive(s_lock);
    emit_change();
}

void climate_model_cycle_fan(int delta)
{
    if (s_lock == NULL) {
        return;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    int next = ((int)s_state.fan_speed + delta) % (CLIMATE_FAN_MAX + 1);
    if (next < 0) {
        next += CLIMATE_FAN_MAX + 1;
    }
    s_state.fan_speed = clamp_fan(next);
    xSemaphoreGive(s_lock);
    emit_change();
}

void climate_model_toggle_ac(void)
{
    if (s_lock == NULL) {
        return;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_state.ac_on = !s_state.ac_on;
    xSemaphoreGive(s_lock);
    emit_change();
}

void climate_model_toggle_sync(void)
{
    if (s_lock == NULL) {
        return;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_state.sync_on = !s_state.sync_on;
    if (s_state.sync_on) {
        s_state.temp_right_c10 = s_state.temp_left_c10;
    }
    xSemaphoreGive(s_lock);
    emit_change();
}

void climate_model_toggle_defrost(bool front)
{
    if (s_lock == NULL) {
        return;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    if (front) {
        s_state.front_defrost = !s_state.front_defrost;
    } else {
        s_state.rear_defrost = !s_state.rear_defrost;
    }
    xSemaphoreGive(s_lock);
    emit_change();
}

void climate_model_cycle_mode(void)
{
    if (s_lock == NULL) {
        return;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    if (s_state.mode < CLIMATE_MODE_FACE || s_state.mode >= CLIMATE_MODE_DEFROST) {
        s_state.mode = CLIMATE_MODE_FACE;
    } else {
        s_state.mode = (climate_mode_t)(s_state.mode + 1);
    }
    xSemaphoreGive(s_lock);
    emit_change();
}
