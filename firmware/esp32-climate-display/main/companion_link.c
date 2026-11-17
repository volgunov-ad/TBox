#include "companion_link.h"
#include "can_link.h"

#include "cJSON.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "stdio.h"

static const char *TAG = "companion_link";

static bool s_can_mode = false;
static uint32_t s_can_baud = 0;

static void send_json(cJSON *root)
{
    char *line = cJSON_PrintUnformatted(root);
    if (line != NULL) {
        printf("%s\n", line);
        cJSON_free(line);
    }
    cJSON_Delete(root);
}

static void link_task(void *arg)
{
    (void)arg;
    cJSON *hello = cJSON_CreateObject();
    if (hello != NULL) {
        cJSON_AddNumberToObject(hello, "v", 1);
        cJSON_AddStringToObject(hello, "t", "hello");
        cJSON_AddStringToObject(hello, "kind", "display");
        cJSON_AddStringToObject(hello, "fw", CLIMATE_FW_VERSION);
        cJSON_AddBoolToObject(hello, "can", s_can_mode);
        cJSON_AddNumberToObject(hello, "canBaud", (double)s_can_baud);
        send_json(hello);
    }
    int64_t start_us = esp_timer_get_time();
    for (;;) {
        vTaskDelay(pdMS_TO_TICKS(1000));
        can_link_stats_t stats;
        can_link_get_stats(&stats);
        cJSON *hb = cJSON_CreateObject();
        if (hb == NULL) {
            continue;
        }
        cJSON_AddNumberToObject(hb, "v", 1);
        cJSON_AddStringToObject(hb, "t", "hb");
        cJSON_AddNumberToObject(hb, "up", (double)((esp_timer_get_time() - start_us) / 1000000));
        cJSON_AddBoolToObject(hb, "can", stats.online);
        cJSON_AddNumberToObject(hb, "frames", (double)stats.frames);
        cJSON_AddNumberToObject(hb, "err", (double)stats.errors);
        send_json(hb);
    }
}

void companion_link_start(bool can_mode, uint32_t can_baud)
{
    s_can_mode = can_mode;
    s_can_baud = can_baud;
    if (xTaskCreate(link_task, "companion", 6144, NULL, 2, NULL) != pdPASS) {
        ESP_LOGE(TAG, "companion task create failed");
    }
}
