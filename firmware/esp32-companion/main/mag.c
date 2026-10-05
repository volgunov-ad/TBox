#include "mag.h"

#include <math.h>
#include <string.h>

#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"

#include "hmc58xx_i2c.h"
#include "ist8310_i2c.h"
#include "mmc5983_i2c.h"
#include "protocol.h"
#include "qmc5883l_i2c.h"
#include "rm3100_i2c.h"

static const char *TAG = "mag";

#define MAG_SDA_GPIO 5
#define MAG_SCL_GPIO 6
#define MAG_I2C_HZ 400000
#define MAG_JSON_PERIOD_MS 100
#define MAG_SAMPLE_PERIOD_MS 50
#define MAG_RESCAN_MS 2000

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

typedef enum {
    MAG_JOB_RESCAN = 0,
    MAG_JOB_SET_CHIP = 1,
} mag_job_type_t;

typedef struct {
    mag_job_type_t type;
    mag_chip_t chip;
} mag_job_t;

typedef struct {
    mag_chip_t chip;
    int priority;
} mag_priority_t;

static const mag_priority_t k_priority[] = {
    { MAG_CHIP_RM3100, 60 },
    { MAG_CHIP_MMC5983, 50 },
    { MAG_CHIP_IST8310, 40 },
    { MAG_CHIP_HMC5983, 30 },
    { MAG_CHIP_HMC5883L, 20 },
    { MAG_CHIP_QMC5883L, 10 },
};

static i2c_master_bus_handle_t s_bus;
static mag_chip_t s_chip = MAG_CHIP_NONE;
static bool s_present;
static bool s_seen[MAG_CHIP_COUNT];
static uint8_t s_ist_addr;
static hmc58xx_kind_t s_hmc_kind;
static rm3100_t s_rm;
static mmc5983_t s_mmc;
static ist8310_t s_ist;
static qmc5883l_t s_qmc;
static hmc58xx_t s_hmc;
static QueueHandle_t s_job_q;

const char *mag_chip_name(mag_chip_t chip)
{
    switch (chip) {
    case MAG_CHIP_QMC5883L: return "qmc5883l";
    case MAG_CHIP_HMC5883L: return "hmc5883l";
    case MAG_CHIP_IST8310: return "ist8310";
    case MAG_CHIP_HMC5983: return "hmc5983";
    case MAG_CHIP_RM3100: return "rm3100";
    case MAG_CHIP_MMC5983: return "mmc5983";
    default: return "none";
    }
}

bool mag_chip_from_name(const char *name, mag_chip_t *out)
{
    if (!name || !out) return false;
    for (mag_chip_t c = 0; c < MAG_CHIP_COUNT; c++) {
        if (strcmp(name, mag_chip_name(c)) == 0) {
            *out = c;
            return true;
        }
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

int mag_get_seen_ids(const char *out[], int max_out)
{
    int n = 0;
    for (mag_chip_t c = 0; c < MAG_CHIP_COUNT && n < max_out; c++) {
        if (s_seen[c]) {
            out[n++] = mag_chip_name(c);
        }
    }
    return n;
}

static void update_hello(void)
{
    const char *seen[MAG_SEEN_MAX];
    int count = mag_get_seen_ids(seen, MAG_SEEN_MAX);
    protocol_set_mag_for_hello(s_present, mag_chip_name(s_chip), seen, count);
}

void mag_refresh_hello(void)
{
    update_hello();
}

static void clear_seen(void)
{
    memset(s_seen, 0, sizeof(s_seen));
    s_ist_addr = 0;
    s_hmc_kind = HMC58XX_NONE;
}

static void scan_bus(void)
{
    clear_seen();
    if (!s_bus) return;

    for (uint8_t a = RM3100_ADDR_MIN; a <= RM3100_ADDR_MAX; a++) {
        if (rm3100_probe_addr(s_bus, a)) {
            s_seen[MAG_CHIP_RM3100] = true;
            break;
        }
    }
    if (mmc5983_probe(s_bus)) {
        s_seen[MAG_CHIP_MMC5983] = true;
    }
    for (uint8_t a = IST8310_ADDR_MIN; a <= IST8310_ADDR_MAX; a++) {
        if (ist8310_probe_addr(s_bus, a)) {
            s_seen[MAG_CHIP_IST8310] = true;
            s_ist_addr = a;
            break;
        }
    }
    s_hmc_kind = hmc58xx_probe(s_bus);
    if (s_hmc_kind == HMC58XX_5883L) {
        s_seen[MAG_CHIP_HMC5883L] = true;
    } else if (s_hmc_kind == HMC58XX_5983) {
        s_seen[MAG_CHIP_HMC5983] = true;
    }
    if (qmc5883l_probe(s_bus)) {
        s_seen[MAG_CHIP_QMC5883L] = true;
    }
}

static mag_chip_t pick_autodetect(void)
{
    mag_chip_t best = MAG_CHIP_NONE;
    int best_pri = -1;
    for (size_t i = 0; i < sizeof(k_priority) / sizeof(k_priority[0]); i++) {
        mag_chip_t c = k_priority[i].chip;
        if (s_seen[c] && k_priority[i].priority > best_pri) {
            best = c;
            best_pri = k_priority[i].priority;
        }
    }
    return best;
}

static void close_drivers(void)
{
    rm3100_deinit(&s_rm);
    mmc5983_deinit(&s_mmc);
    ist8310_deinit(&s_ist);
    qmc5883l_deinit(&s_qmc);
    hmc58xx_deinit(&s_hmc);
    s_present = false;
}

static bool open_chip(mag_chip_t chip)
{
    close_drivers();
    if (!s_bus || chip == MAG_CHIP_NONE) return false;

    switch (chip) {
    case MAG_CHIP_RM3100:
        for (uint8_t a = RM3100_ADDR_MIN; a <= RM3100_ADDR_MAX; a++) {
            if (rm3100_init(s_bus, a, &s_rm)) {
                s_present = true;
                return true;
            }
        }
        break;
    case MAG_CHIP_MMC5983:
        if (mmc5983_init(s_bus, &s_mmc)) {
            s_present = true;
            return true;
        }
        break;
    case MAG_CHIP_IST8310:
        if (s_ist_addr != 0 && ist8310_init(s_bus, s_ist_addr, &s_ist)) {
            s_present = true;
            return true;
        }
        for (uint8_t a = IST8310_ADDR_MIN; a <= IST8310_ADDR_MAX; a++) {
            if (ist8310_init(s_bus, a, &s_ist)) {
                s_ist_addr = a;
                s_present = true;
                return true;
            }
        }
        break;
    case MAG_CHIP_HMC5883L:
        if (hmc58xx_init(s_bus, HMC58XX_5883L, &s_hmc)) {
            s_present = true;
            return true;
        }
        break;
    case MAG_CHIP_HMC5983:
        if (hmc58xx_init(s_bus, HMC58XX_5983, &s_hmc)) {
            s_present = true;
            return true;
        }
        break;
    case MAG_CHIP_QMC5883L:
        if (qmc5883l_init(s_bus, &s_qmc)) {
            s_present = true;
            return true;
        }
        break;
    default:
        break;
    }
    ESP_LOGW(TAG, "%s not found", mag_chip_name(chip));
    return false;
}

static void apply_selection(mag_chip_t chip, bool from_host)
{
    scan_bus();
    if (chip == MAG_CHIP_NONE || !s_seen[chip]) {
        chip = pick_autodetect();
    }
    s_chip = chip;
    bool ok = open_chip(s_chip);
    if (!ok) {
        s_chip = MAG_CHIP_NONE;
    }
    update_hello();
    if (from_host) {
        const char *seen[MAG_SEEN_MAX];
        int count = mag_get_seen_ids(seen, MAG_SEEN_MAX);
        protocol_send_mag_chip(mag_chip_name(s_chip), ok, s_present, seen, count);
        protocol_send_hello();
    }
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

static bool read_active(float *hx, float *hy, float *hz)
{
    switch (s_chip) {
    case MAG_CHIP_RM3100:
        return rm3100_read_ut(&s_rm, hx, hy, hz);
    case MAG_CHIP_MMC5983:
        return mmc5983_read_ut(&s_mmc, hx, hy, hz);
    case MAG_CHIP_IST8310:
        return ist8310_read_ut(&s_ist, hx, hy, hz);
    case MAG_CHIP_QMC5883L:
        return qmc5883l_read_ut(&s_qmc, hx, hy, hz);
    case MAG_CHIP_HMC5883L:
    case MAG_CHIP_HMC5983:
        return hmc58xx_read_ut(&s_hmc, hx, hy, hz);
    default:
        return false;
    }
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
                apply_selection(job.chip, true);
            } else {
                apply_selection(MAG_CHIP_NONE, false);
            }
        }

        uint32_t now_ms = (uint32_t)(esp_timer_get_time() / 1000ULL);
        if (!s_present && now_ms - last_rescan_ms >= MAG_RESCAN_MS) {
            last_rescan_ms = now_ms;
            bool was = s_present;
            apply_selection(s_chip, false);
            if (!was && s_present) {
                protocol_send_hello();
            }
        }

        if (s_present && !protocol_ota_active()) {
            float hx = 0, hy = 0, hz = 0;
            bool ok = read_active(&hx, &hy, &hz);
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
        const char *seen[MAG_SEEN_MAX];
        int count = mag_get_seen_ids(seen, MAG_SEEN_MAX);
        protocol_send_mag_chip(mag_chip_name(s_chip), false, s_present, seen, count);
        return;
    }
    mag_job_t job = { .type = MAG_JOB_SET_CHIP, .chip = chip };
    if (xQueueSend(s_job_q, &job, 0) != pdTRUE) {
        ESP_LOGW(TAG, "job queue full");
        const char *seen[MAG_SEEN_MAX];
        int count = mag_get_seen_ids(seen, MAG_SEEN_MAX);
        protocol_send_mag_chip(mag_chip_name(s_chip), false, s_present, seen, count);
    }
}

void mag_init(void)
{
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
        apply_selection(MAG_CHIP_NONE, false);
    }
    update_hello();
    ESP_LOGI(TAG, "chip=%s present=%d", mag_chip_name(s_chip), s_present);

    xTaskCreate(mag_task, "mag", 4096, NULL, 4, NULL);
}
