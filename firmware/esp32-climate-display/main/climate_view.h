#pragma once

#include "lvgl.h"
#include "climate_model.h"

void climate_view_create(lv_obj_t *parent);

void climate_view_refresh(const climate_state_t *state);
