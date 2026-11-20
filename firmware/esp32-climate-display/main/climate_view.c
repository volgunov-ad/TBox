#include "climate_view.h"

#include <stdio.h>

#include "sdkconfig.h"

static const char *s_mode_names[] = {
    "",
    "FACE",
    "FACE/FEET",
    "FEET",
    "FEET/DEF",
    "DEFROST",
};

static lv_obj_t *s_temp_left_label = NULL;
static lv_obj_t *s_temp_right_label = NULL;
static lv_obj_t *s_mode_label = NULL;
static lv_obj_t *s_fan_buttons[CLIMATE_FAN_MAX + 1];
static lv_obj_t *s_ac_button = NULL;
static lv_obj_t *s_sync_button = NULL;
static lv_obj_t *s_front_button = NULL;
static lv_obj_t *s_rear_button = NULL;

static void temp_label_update(lv_obj_t *label, int16_t temp_c10)
{
    char buf[16];
    snprintf(buf, sizeof(buf), "%d.%d", temp_c10 / 10, temp_c10 % 10);
    lv_label_set_text(label, buf);
}

static void set_checked(lv_obj_t *btn, bool checked)
{
    if (checked) {
        lv_obj_add_state(btn, LV_STATE_CHECKED);
    } else {
        lv_obj_clear_state(btn, LV_STATE_CHECKED);
    }
}

static void on_temp_left_minus(lv_event_t *e)
{
    (void)e;
    climate_model_adjust_temp(true, -CLIMATE_TEMP_STEP_C10);
}

static void on_temp_left_plus(lv_event_t *e)
{
    (void)e;
    climate_model_adjust_temp(true, CLIMATE_TEMP_STEP_C10);
}

static void on_temp_right_minus(lv_event_t *e)
{
    (void)e;
    climate_model_adjust_temp(false, -CLIMATE_TEMP_STEP_C10);
}

static void on_temp_right_plus(lv_event_t *e)
{
    (void)e;
    climate_model_adjust_temp(false, CLIMATE_TEMP_STEP_C10);
}

static void on_fan(lv_event_t *e)
{
    climate_model_set_fan((uint8_t)(uintptr_t)lv_event_get_user_data(e));
}

static void on_ac(lv_event_t *e)
{
    (void)e;
    climate_model_toggle_ac();
}

static void on_sync(lv_event_t *e)
{
    (void)e;
    climate_model_toggle_sync();
}

static void on_mode(lv_event_t *e)
{
    (void)e;
    climate_model_cycle_mode();
}

static void on_front(lv_event_t *e)
{
    (void)e;
    climate_model_toggle_defrost(true);
}

static void on_rear(lv_event_t *e)
{
    (void)e;
    climate_model_toggle_defrost(false);
}

static lv_obj_t *make_button(lv_obj_t *parent, const char *text, lv_coord_t x, lv_coord_t y,
                             lv_coord_t w, lv_coord_t h, lv_event_cb_t cb, void *user_data,
                             const lv_font_t *font)
{
    lv_obj_t *btn = lv_button_create(parent);
    lv_obj_set_pos(btn, x, y);
    lv_obj_set_size(btn, w, h);
    lv_obj_add_event_cb(btn, cb, LV_EVENT_CLICKED, user_data);
    lv_obj_set_style_bg_color(btn, lv_palette_main(LV_PALETTE_BLUE), LV_PART_MAIN | LV_STATE_CHECKED);
    lv_obj_t *label = lv_label_create(btn);
    lv_label_set_text(label, text);
    lv_obj_set_style_text_font(label, font, 0);
    lv_obj_center(label);
    return btn;
}

static lv_obj_t *make_label(lv_obj_t *parent, const char *text, lv_coord_t x, lv_coord_t y,
                            const lv_font_t *font)
{
    lv_obj_t *label = lv_label_create(parent);
    lv_label_set_text(label, text);
    lv_obj_set_style_text_font(label, font, 0);
    lv_obj_set_pos(label, x, y);
    return label;
}

void climate_view_create(lv_obj_t *parent)
{
    lv_obj_set_style_bg_color(parent, lv_color_hex(0x0E1116), 0);
    lv_obj_set_style_bg_opa(parent, LV_OPA_COVER, 0);
    lv_obj_clear_flag(parent, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_set_scrollbar_mode(parent, LV_SCROLLBAR_MODE_OFF);

    make_label(parent, "CLIMATE", 12, 10, &lv_font_montserrat_24);
#if CONFIG_CLIMATE_UI_SIM
    make_label(parent, "SIM", 746, 16, &lv_font_montserrat_16);
#else
    make_label(parent, "CAN", 750, 16, &lv_font_montserrat_16);
#endif

    make_label(parent, "DRIVER", 30, 105, &lv_font_montserrat_16);
    s_temp_left_label = make_label(parent, "22.0", 120, 140, &lv_font_montserrat_48);
    make_button(parent, "-", 30, 140, 80, 90, on_temp_left_minus, NULL, &lv_font_montserrat_32);
    make_button(parent, "+", 260, 140, 80, 90, on_temp_left_plus, NULL, &lv_font_montserrat_32);

    make_label(parent, "PASSENGER", 470, 105, &lv_font_montserrat_16);
    s_temp_right_label = make_label(parent, "22.0", 560, 140, &lv_font_montserrat_48);
    make_button(parent, "-", 470, 140, 80, 90, on_temp_right_minus, NULL, &lv_font_montserrat_32);
    make_button(parent, "+", 700, 140, 80, 90, on_temp_right_plus, NULL, &lv_font_montserrat_32);

    make_label(parent, "FAN", 30, 315, &lv_font_montserrat_16);
    for (uint8_t i = 0; i <= CLIMATE_FAN_MAX; i++) {
        char lbl[4];
        snprintf(lbl, sizeof(lbl), "%u", i);
        s_fan_buttons[i] = make_button(parent, lbl, 120 + i * 68, 300, 60, 70,
                                       on_fan, (void *)(uintptr_t)i, &lv_font_montserrat_24);
    }

    s_ac_button = make_button(parent, "A/C", 30, 395, 130, 75, on_ac, NULL, &lv_font_montserrat_20);
    s_sync_button = make_button(parent, "SYNC", 175, 395, 130, 75, on_sync, NULL, &lv_font_montserrat_20);
    lv_obj_t *mode_button = make_button(parent, "FACE", 320, 395, 160, 75, on_mode, NULL, &lv_font_montserrat_20);
    s_mode_label = lv_obj_get_child(mode_button, 0);
    s_front_button = make_button(parent, "FRONT", 495, 395, 130, 75, on_front, NULL, &lv_font_montserrat_20);
    s_rear_button = make_button(parent, "REAR", 640, 395, 130, 75, on_rear, NULL, &lv_font_montserrat_20);
}

void climate_view_refresh(const climate_state_t *state)
{
    if (state == NULL || s_temp_left_label == NULL) {
        return;
    }
    temp_label_update(s_temp_left_label, state->temp_left_c10);
    temp_label_update(s_temp_right_label, state->temp_right_c10);
    for (int i = 0; i <= CLIMATE_FAN_MAX; i++) {
        set_checked(s_fan_buttons[i], state->fan_speed == i);
    }
    set_checked(s_ac_button, state->ac_on);
    set_checked(s_sync_button, state->sync_on);
    set_checked(s_front_button, state->front_defrost);
    set_checked(s_rear_button, state->rear_defrost);
    if (state->mode >= CLIMATE_MODE_FACE && state->mode <= CLIMATE_MODE_DEFROST) {
        lv_label_set_text(s_mode_label, s_mode_names[state->mode]);
    }
}
