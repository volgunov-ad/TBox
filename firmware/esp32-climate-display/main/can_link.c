#include "can_link.h"
#include "board_pins.h"

#include "driver/twai.h"
#include "esp_check.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/task.h"

static const char *TAG = "can_link";

#define CAN_LINK_ONLINE_WINDOW_US (2 * 1000 * 1000)

static QueueHandle_t s_rx_queue = NULL;
static volatile uint32_t s_frames = 0;
static volatile uint32_t s_dropped = 0;
static volatile uint32_t s_errors = 0;
static volatile int64_t s_last_rx_us = 0;

static const twai_timing_config_t s_timing_250k = TWAI_TIMING_CONFIG_250KBITS();
static const twai_timing_config_t s_timing_500k = TWAI_TIMING_CONFIG_500KBITS();
static const twai_timing_config_t s_timing_800k = TWAI_TIMING_CONFIG_800KBITS();
static const twai_timing_config_t s_timing_1m = TWAI_TIMING_CONFIG_1MBITS();

static const twai_timing_config_t *timing_by_baud(uint32_t baud)
{
    switch (baud) {
    case 250000:
        return &s_timing_250k;
    case 800000:
        return &s_timing_800k;
    case 1000000:
        return &s_timing_1m;
    case 500000:
    default:
        return &s_timing_500k;
    }
}

static void can_rx_task(void *arg)
{
    (void)arg;
    twai_message_t msg;
    for (;;) {
        esp_err_t err = twai_receive(&msg, pdMS_TO_TICKS(200));
        if (err == ESP_OK) {
            s_frames++;
            s_last_rx_us = esp_timer_get_time();
            if (s_rx_queue != NULL && xQueueSend(s_rx_queue, &msg, 0) != pdTRUE) {
                s_dropped++;
            }
        } else if (err != ESP_ERR_TIMEOUT) {
            s_errors++;
        }
    }
}

esp_err_t can_link_start(void)
{
    if (s_rx_queue != NULL) {
        return ESP_OK;
    }
    twai_general_config_t general_cfg = TWAI_GENERAL_CONFIG_DEFAULT(BOARD_CAN_TX, BOARD_CAN_RX, TWAI_MODE_LISTEN_ONLY);
    general_cfg.rx_queue_len = 32;
    const twai_timing_config_t *timing_cfg = timing_by_baud(CONFIG_CLIMATE_CAN_BAUD);
    const twai_filter_config_t filter_cfg = TWAI_FILTER_CONFIG_ACCEPT_ALL();
    ESP_RETURN_ON_ERROR(twai_driver_install(&general_cfg, timing_cfg, &filter_cfg), TAG, "TWAI install failed");
    ESP_RETURN_ON_ERROR(twai_start(), TAG, "TWAI start failed");
    s_rx_queue = xQueueCreate(32, sizeof(twai_message_t));
    if (s_rx_queue == NULL) {
        return ESP_ERR_NO_MEM;
    }
    if (xTaskCreate(can_rx_task, "canrx", 4096, NULL, 4, NULL) != pdPASS) {
        return ESP_ERR_NO_MEM;
    }
    return ESP_OK;
}

bool can_link_pop(twai_message_t *msg, uint32_t timeout_ms)
{
    if (s_rx_queue == NULL) {
        return false;
    }
    return xQueueReceive(s_rx_queue, msg, pdMS_TO_TICKS(timeout_ms)) == pdTRUE;
}

void can_link_get_stats(can_link_stats_t *out_stats)
{
    if (out_stats == NULL) {
        return;
    }
    out_stats->frames = s_frames;
    out_stats->dropped = s_dropped;
    out_stats->errors = s_errors;
    out_stats->online = s_frames > 0 && (esp_timer_get_time() - s_last_rx_us) < CAN_LINK_ONLINE_WINDOW_US;
}
