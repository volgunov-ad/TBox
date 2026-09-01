#include "mag.h"

#include <math.h>
#include <string.h>

#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "nvs.h"
#include "nvs_flash.h"

#include "mmc5983_i2c.h"
#include "protocol.h"
#include "rm3100_i2c.h"

static const char *TAG = "mag";

#define MAG_SDA_GPIO 5
#define MAG_SCL_GPIO 6
#define MAG_I2C_HZ 400000
#define MAG_NVS_NS "mag"
#define MAG_NVS_KEY_CHIP "chip"
#define MAG_JSON_PERIOD_MS 100
#define MAG_SAMPLE_PERIOD_MS 50
#define MAG_RESCAN_MS 2000

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

typedef enum {
    MAG_JOB_SET_CHIP = 0,
} mag_job_type_t;

typedef struct {
    mag_job_type_t type;
    mag_chip_t chip;
} mag_job_t;

static i2c_master_bus_handle_t s_bus;
static mag_chip_t s_chip = MAG_CHIP_RM3100;
static bool s_present;
static bool s_seen_rm3100;
static bool s_seen_mmc5983;
static rm3100_t s_rm;
static mmc5983_t s_mmc;
static QueueHandle_t s_job_q;

const char *mag_chip_name(mag_chip_t chip)
{
    return chip == MAG_CHIP_MMC5983 ? "mmc5983" : "rm3100";
}

bool mag_chip_from_name(const char *name, mag_chip_t *out)
{
    if (!name || !out) return false;
    if (strcmp(name, "rm3100") == 0) {
        *out = MAG_CHIP_RM3100;
        return true;
    }
    if (strcmp(name, "mmc5983") == 0) {
        *out = MAG_CHIP_MMC5983;
        return true;
    }
    return false;
}

mag_chip_t mag_get_chip(void)
{
    return s_chip;
}

bool mag_is_present(void)
{
    return s_present;
}

void mag_get_seen(bool *rm3100, bool *mmc5983)
{
    if (rm3100) *rm3100 = s_seen_rm3100;
    if (mmc5983) *mmc5983 = s_seen_mmc5983;
}

static mag_chip_t nvs_load_chip(void)
{
    nvs_handle_t h;
    if (nvs_open(MAG_NVS_NS, NVS_READONLY, &h) != ESP_OK) {
        return MAG_CHIP_RM3100;
    }
    int32_t v = 0;
    esp_err_t err = nvs_get_i32(h, MAG_NVS_KEY_CHIP, &v);
    nvs_close(h);
    if (err != ESP_OK) return MAG_CHIP_RM3100;
    if (v == (int32_t)MAG_CHIP_MMC5983) return MAG_CHIP_MMC5983;
    return MAG_CHIP_RM3100;
}

static bool nvs_save_chip(mag_chip_t chip)
{
    nvs_handle_t h;
    if (nvs_open(MAG_NVS_NS, NVS_READWRITE, &h) != ESP_OK) return false;
    esp_err_t err = nvs_set_i32(h, MAG_NVS_KEY_CHIP, (int32_t)chip);
    if (err == ESP_OK) err = nvs_commit(h);
    nvs_close(h);
    return err == ESP_OK;
}

void mag_refresh_hello(void)
{
    protocol_set_mag_for_hello(s_present, mag_chip_name(s_chip),
                               s_seen_rm3100, s_seen_mmc5983);
}

static void scan_bus(void)
{
    s_seen_rm3100 = false;
    s_seen_mmc5983 = false;
    if (!s_bus) return;
    for (uint8_t a = RM3100_ADDR_MIN; a <= RM3100_ADDR_MAX; a++) {
        if (rm3100_probe_addr(s_bus, a)) {
            s_seen_rm3100 = true;
            break;
        }
    }
    s_seen_mmc5983 = mmc5983_probe(s_bus);
}

static void close_drivers(void)
{
    rm3100_deinit(&s_rm);
    mmc5983_deinit(&s_mmc);
    s_present = false;
}

static bool open_selected(void)
{
    close_drivers();
    if (!s_bus) return false;
    if (s_chip == MAG_CHIP_RM3100) {
        for (uint8_t a = RM3100_ADDR_MIN; a <= RM3100_ADDR_MAX; a++) {
            if (rm3100_init(s_bus, a, &s_rm)) {
                s_present = true;
                return true;
            }
        }
        ESP_LOGW(TAG, "RM3100 not found");
        return false;
    }
    if (mmc5983_init(s_bus, &s_mmc)) {
        s_present = true;
        return true;
    }
    ESP_LOGW(TAG, "MMC5983 not found");
    return false;
}

static float heading_deg(float hx, float hy)
{
    if (!isfinite(hx) || !isfinite(hy) || (hx == 0.f && hy == 0.f)) {
        return 0.f;
    }
    float deg = atan2f(hy, hx) * 180.f / (float)M_PI;
    if (deg < 0.f) deg += 360.f;
    return deg;
}

static bool read_selected(float *hx, float *hy, float *hz)
{
    if (s_chip == MAG_CHIP_RM3100) {
        return rm3100_read_ut(&s_rm, hx, hy, hz);
    }
    return mmc5983_read_ut(&s_mmc, hx, hy, hz);
}

static void apply_chip(mag_chip_t chip, bool from_host)
{
    s_chip = chip;
    if (!nvs_save_chip(chip)) {
        ESP_LOGW(TAG, "NVS save chip failed");
    }
    scan_bus();
    bool ok = open_selected();
    mag_refresh_hello();
    if (from_host) {
        protocol_send_mag_chip(mag_chip_name(s_chip), true, s_present,
                               s_seen_rm3100, s_seen_mmc5983);
        protocol_send_hello();
    }
    (void)ok;
}

static void mag_task(void *arg)
{
    (void)arg;
    uint32_t last_json_ms = 0;
    uint32_t last_rescan_ms = 0;
    mag_job_t job;
    while (1) {
        while (xQueueReceive(s_job_q, &job, 0) == pdTRUE) {
            if (job.type == MAG_JOB_SET_CHIP) {
                apply_chip(job.chip, true);
            }
        }

        uint32_t now_ms = (uint32_t)(esp_timer_get_time() / 1000ULL);
        if (!s_present && now_ms - last_rescan_ms >= MAG_RESCAN_MS) {
            last_rescan_ms = now_ms;
            bool was = s_present;
            scan_bus();
            open_selected();
            mag_refresh_hello();
            if (!was && s_present) {
                protocol_send_hello();
            }
        }

        if (s_present && !protocol_ota_active()) {
            float hx = 0, hy = 0, hz = 0;
            bool ok = read_selected(&hx, &hy, &hz);
            if (ok && now_ms - last_json_ms >= MAG_JSON_PERIOD_MS) {
                last_json_ms = now_ms;
                float fs = sqrtf(hx * hx + hy * hy + hz * hz);
                protocol_send_mag(mag_chip_name(s_chip), hx, hy, hz,
                                  heading_deg(hx, hy), fs, true);
            }
        }

        vTaskDelay(pdMS_TO_TICKS(MAG_SAMPLE_PERIOD_MS));
    }
}

void mag_request_chip(mag_chip_t chip)
{
    if (!s_job_q) {
        protocol_send_mag_chip(mag_chip_name(s_chip), false, s_present,
                               s_seen_rm3100, s_seen_mmc5983);
        return;
    }
    mag_job_t job = { .type = MAG_JOB_SET_CHIP, .chip = chip };
    if (xQueueSend(s_job_q, &job, 0) != pdTRUE) {
        ESP_LOGW(TAG, "job queue full");
        protocol_send_mag_chip(mag_chip_name(s_chip), false, s_present,
                               s_seen_rm3100, s_seen_mmc5983);
    }
}

void mag_init(void)
{
    s_chip = nvs_load_chip();
    s_job_q = xQueueCreate(4, sizeof(mag_job_t));
    configASSERT(s_job_q);

    i2c_master_bus_config_t bus_cfg = {
        .i2c_port = I2C_NUM_0,
        .sda_io_num = MAG_SDA_GPIO,
        .scl_io_num = MAG_SCL_GPIO,
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .glitch_ignore_cnt = 7,
        .flags.enable_internal_pullup = true,
    };
    esp_err_t err = i2c_new_master_bus(&bus_cfg, &s_bus);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "i2c bus failed: %s", esp_err_to_name(err));
        s_bus = NULL;
    } else {
        scan_bus();
        open_selected();
    }
    mag_refresh_hello();
    ESP_LOGI(TAG, "chip=%s present=%d seen_rm=%d seen_mmc=%d",
             mag_chip_name(s_chip), s_present, s_seen_rm3100, s_seen_mmc5983);

    xTaskCreate(mag_task, "mag", 4096, NULL, 4, NULL);
}
