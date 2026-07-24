#include <stdio.h>
#include <string.h>

#include "esp_log.h"
#include "esp_system.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "tinyusb.h"
#include "tusb_cdc_acm.h"

#include "gpio_io.h"
#include "protocol.h"
#include "um980_uart.h"

static const char *TAG = "esp32_companion";

#define UM980_CMD_QUEUE_LEN 8
#define UM980_CMD_MAX 256

typedef enum {
    UM980_JOB_CMD = 0,
    UM980_JOB_BAUD = 1,
} um980_job_type_t;

typedef struct {
    um980_job_type_t type;
    char cmd[UM980_CMD_MAX];
    int baud;
} um980_job_t;

static volatile bool s_pending_reboot = false;
static QueueHandle_t s_um980_job_q;

static void on_relay_set(uint8_t mask)
{
    gpio_io_set_relays(mask);
}

static void on_um980_cmd(const char *cmd)
{
    if (!cmd || !s_um980_job_q) return;
    um980_job_t job = { .type = UM980_JOB_CMD, .baud = 0 };
    strncpy(job.cmd, cmd, sizeof(job.cmd) - 1);
    job.cmd[sizeof(job.cmd) - 1] = '\0';
    if (xQueueSend(s_um980_job_q, &job, 0) != pdTRUE) {
        ESP_LOGW(TAG, "UM980 job queue full, drop cmd: %s", job.cmd);
    }
}

static void on_um980_baud(int baud)
{
    if (!s_um980_job_q) return;
    um980_job_t job = { .type = UM980_JOB_BAUD, .baud = baud };
    job.cmd[0] = '\0';
    if (xQueueSend(s_um980_job_q, &job, 0) != pdTRUE) {
        ESP_LOGW(TAG, "UM980 job queue full, drop baud %d", baud);
        protocol_send_um980_baud(um980_uart_get_baud(), false);
    }
}

static void on_reboot(void)
{
    s_pending_reboot = true;
}

static void tinyusb_cdc_rx_callback(int itf, cdcacm_event_t *event)
{
    (void)event;
    uint8_t buf[512];
    size_t rx_size = 0;
    esp_err_t ret = tinyusb_cdcacm_read(itf, buf, sizeof(buf), &rx_size);
    if (ret == ESP_OK && rx_size > 0) {
        protocol_on_rx_bytes(buf, rx_size);
    }
}

static void gpio_event(int ch, int level, uint32_t ms)
{
    protocol_send_gpio_event(ch, level, ms);
    protocol_send_gpio(gpio_io_read_inputs(), ms);
}

static void process_um980_cmd(const char *cmd)
{
    // Keep large reply buffers off the worker stack (was overflowing 4K and killing USB CDC).
    static char lines[UM980_RSP_MAX_LINES][UM980_RSP_LINE_LEN];
    int count = 0;
    um980_uart_exec_cmd(cmd, lines, UM980_RSP_MAX_LINES, &count, 1200);
    const char *ptrs[UM980_RSP_MAX_LINES];
    for (int i = 0; i < count; i++) {
        ptrs[i] = lines[i];
    }
    bool ok = count > 0;
    for (int i = 0; i < count; i++) {
        if (strstr(lines[i], "OK") || strstr(lines[i], "ok")) {
            ok = true;
            break;
        }
    }
    protocol_send_um980_rsp(cmd, ptrs, count, ok);
}

/** Offloads UART collect + baud so CDC heartbeat / TinyUSB stay alive. */
static void um980_worker_task(void *arg)
{
    (void)arg;
    um980_job_t job;
    while (1) {
        if (xQueueReceive(s_um980_job_q, &job, portMAX_DELAY) != pdTRUE) {
            continue;
        }
        if (job.type == UM980_JOB_BAUD) {
            bool ok = um980_uart_set_baud(job.baud);
            protocol_set_um980_baud_for_hello(um980_uart_get_baud());
            protocol_send_um980_baud(um980_uart_get_baud(), ok);
        } else {
            process_um980_cmd(job.cmd);
        }
    }
}

void app_main(void)
{
    ESP_LOGI(TAG, "ESP32 companion %s starting", ESP_COMPANION_FW_VERSION);

    const tinyusb_config_t tusb_cfg = {
        .device_descriptor = NULL,
        .string_descriptor = NULL,
        .external_phy = false,
#if (TUD_OPT_HIGH_SPEED)
        .fs_configuration_descriptor = NULL,
        .hs_configuration_descriptor = NULL,
        .qualifier_descriptor = NULL,
#else
        .configuration_descriptor = NULL,
#endif
    };
    ESP_ERROR_CHECK(tinyusb_driver_install(&tusb_cfg));

    tinyusb_config_cdcacm_t acm_cfg = {
        .usb_dev = TINYUSB_USBDEV_0,
        .cdc_port = TINYUSB_CDC_ACM_0,
        .callback_rx = &tinyusb_cdc_rx_callback,
        .callback_rx_wanted_char = NULL,
        .callback_line_state_changed = NULL,
        .callback_line_coding_changed = NULL,
    };
    ESP_ERROR_CHECK(tusb_cdc_acm_init(&acm_cfg));

    s_um980_job_q = xQueueCreate(UM980_CMD_QUEUE_LEN, sizeof(um980_job_t));
    configASSERT(s_um980_job_q);

    protocol_init();
    protocol_set_relay_callback(on_relay_set);
    protocol_set_um980_cmd_callback(on_um980_cmd);
    protocol_set_um980_baud_callback(on_um980_baud);
    protocol_set_reboot_callback(on_reboot);
    um980_uart_init();
    protocol_set_um980_baud_for_hello(um980_uart_get_baud());
    gpio_io_init();

    xTaskCreate(um980_worker_task, "um980_cmd", 8192, NULL, 5, NULL);

    uint32_t last_hb_ms = 0;
    uint32_t last_gpio_ms = 0;
    bool sent_hello = false;

    while (1) {
        uint32_t now_ms = (uint32_t)(esp_timer_get_time() / 1000ULL);

        if (protocol_ota_restart_pending()) {
            vTaskDelay(pdMS_TO_TICKS(100));
            esp_restart();
        }

        if (s_pending_reboot) {
            protocol_send_reboot_ack();
            vTaskDelay(pdMS_TO_TICKS(50));
            esp_restart();
        }

        const bool ota_busy = protocol_ota_active();

        if (tud_ready()) {
            if (!sent_hello && !ota_busy) {
                protocol_send_hello();
                protocol_send_gpio(gpio_io_read_inputs(), now_ms);
                protocol_send_relay(gpio_io_get_relays());
                sent_hello = true;
            }
            // During OTA keep a rare heartbeat so HU soft-watchdog stays calm.
            const uint32_t hb_period = ota_busy ? 5000u : 1000u;
            if (now_ms - last_hb_ms >= hb_period) {
                protocol_send_hb(now_ms);
                last_hb_ms = now_ms;
            }
        } else {
            sent_hello = false;
        }

        um980_fix_t fix;
        if (!ota_busy && um980_uart_poll(&fix)) {
            protocol_send_gps(fix.fix, fix.lat, fix.lon, fix.alt,
                              fix.speed_kmh, fix.course,
                              fix.sats_used, fix.sats_vis, fix.utc);
        }

        if (!ota_busy) {
            gpio_io_poll(now_ms, gpio_event);
            if (now_ms - last_gpio_ms >= 1000) {
                protocol_send_gpio(gpio_io_read_inputs(), now_ms);
                last_gpio_ms = now_ms;
            }
        }

        vTaskDelay(pdMS_TO_TICKS(20));
    }
}
