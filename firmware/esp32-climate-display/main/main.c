#include <stdbool.h>

#include "esp_err.h"
#include "esp_log.h"
#include "esp_random.h"

#include "driver/twai.h"
#include "esp_lvgl_port.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "lvgl.h"

#include "board_bsp.h"
#include "board_pins.h"
#include "can_link.h"
#include "can_map.h"
#include "climate_model.h"
#include "climate_view.h"
#include "companion_link.h"

static const char *TAG = "main";

static TaskHandle_t s_ui_task_handle = NULL;

static void on_model_changed(const climate_state_t *state)
{
    (void)state;
    if (s_ui_task_handle != NULL) {
        xTaskNotifyGive(s_ui_task_handle);
    }
}

static void ui_task(void *arg)
{
    (void)arg;
    climate_state_t state;
    for (;;) {
        ulTaskNotifyTake(pdTRUE, portMAX_DELAY);
        lvgl_port_lock(0);
        climate_model_get(&state);
        climate_view_refresh(&state);
        lvgl_port_unlock();
    }
}

static void can_proc_task(void *arg)
{
    (void)arg;
    twai_message_t msg;
    for (;;) {
        if (can_link_pop(&msg, 500)) {
            can_map_apply(msg.identifier, msg.extd, msg.data, msg.data_length_code);
        }
    }
}

static void sim_task(void *arg)
{
    (void)arg;
    uint32_t tick = 0;
    for (;;) {
        vTaskDelay(pdMS_TO_TICKS(2000));
        can_link_stats_t stats;
        can_link_get_stats(&stats);
        if (stats.online) {
            ESP_LOGI(TAG, "CAN online, stopping sim");
            vTaskDelete(NULL);
        }
        tick++;
        if (tick % 5 == 0) {
            int delta = (esp_random() & 1) ? CLIMATE_TEMP_STEP_C10 : -CLIMATE_TEMP_STEP_C10;
            climate_model_adjust_temp((esp_random() & 1) != 0, delta);
        }
        if (tick % 7 == 0) {
            climate_model_cycle_fan(1);
        }
        if (tick % 11 == 0) {
            climate_model_toggle_ac();
        }
        if (tick % 17 == 0) {
            climate_model_cycle_mode();
        }
    }
}

void app_main(void)
{
    climate_model_init();

    esp_lcd_panel_handle_t panel = NULL;
    esp_lcd_touch_handle_t touch = NULL;
    ESP_ERROR_CHECK(board_bsp_init(&panel, &touch));

    bool can_mode = CONFIG_CLIMATE_CAN_MODE;
    ESP_ERROR_CHECK(board_bsp_mux_set_can(can_mode));
    if (can_mode) {
        ESP_ERROR_CHECK(can_link_start());
    }

    const lvgl_port_cfg_t lvgl_cfg = ESP_LVGL_PORT_INIT_CONFIG();
    ESP_ERROR_CHECK(lvgl_port_init(&lvgl_cfg));

    const lvgl_port_display_cfg_t disp_cfg = {
        .panel_handle = panel,
        .buffer_size = BOARD_LCD_H_RES * BOARD_LCD_V_RES,
        .double_buffer = false,
        .hres = BOARD_LCD_H_RES,
        .vres = BOARD_LCD_V_RES,
        .color_format = LV_COLOR_FORMAT_RGB565,
        .flags = {
            .buff_dma = false,
            .buff_spiram = false,
            .direct_mode = true,
        },
    };
    const lvgl_port_display_rgb_cfg_t rgb_cfg = {
        .flags = {
            .bb_mode = false,
            .avoid_tearing = true,
        },
    };
    lv_display_t *disp = lvgl_port_add_disp_rgb(&disp_cfg, &rgb_cfg);
    if (disp == NULL) {
        ESP_LOGE(TAG, "LVGL display registration failed");
        return;
    }

    const lvgl_port_touch_cfg_t touch_cfg = {
        .disp = disp,
        .handle = touch,
    };
    lv_indev_t *indev = lvgl_port_add_touch(&touch_cfg);
    if (indev == NULL) {
        ESP_LOGE(TAG, "LVGL touch registration failed");
        return;
    }

    lvgl_port_lock(0);
    climate_view_create(lv_screen_active());
    climate_state_t state;
    climate_model_get(&state);
    climate_view_refresh(&state);
    lvgl_port_unlock();

    if (xTaskCreate(ui_task, "ui", 4096, NULL, 3, &s_ui_task_handle) != pdPASS) {
        ESP_LOGE(TAG, "ui task create failed");
        return;
    }
    climate_model_set_change_cb(on_model_changed);

    if (can_mode && xTaskCreate(can_proc_task, "canproc", 4096, NULL, 3, NULL) != pdPASS) {
        ESP_LOGE(TAG, "canproc task create failed");
    }
    if (CONFIG_CLIMATE_UI_SIM && xTaskCreate(sim_task, "sim", 4096, NULL, 2, NULL) != pdPASS) {
        ESP_LOGE(TAG, "sim task create failed");
    }

    companion_link_start(can_mode, CONFIG_CLIMATE_CAN_BAUD);
}
