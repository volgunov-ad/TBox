#include "ota_update.h"

#include <stdio.h>
#include <string.h>

#include "esp_crc.h"
#include "esp_log.h"
#include "esp_ota_ops.h"
#include "esp_partition.h"

/** ESP app image magic (esp_image_header_t.magic). */
#define OTA_IMAGE_MAGIC 0xE9

static const char *TAG = "ota_update";

static const esp_partition_t *s_part;
static esp_ota_handle_t s_handle;
static bool s_active;
static bool s_header_ok;
static uint32_t s_expected;
static uint32_t s_crc_expected;
static uint32_t s_crc_running;
static uint32_t s_written;

bool ota_is_active(void)
{
    return s_active;
}

uint32_t ota_bytes_written(void)
{
    return s_written;
}

uint32_t ota_expected_size(void)
{
    return s_expected;
}

void ota_abort(void)
{
    if (s_active && s_handle) {
        esp_ota_abort(s_handle);
    }
    s_handle = 0;
    s_part = NULL;
    s_active = false;
    s_header_ok = false;
    s_expected = 0;
    s_crc_expected = 0;
    s_crc_running = 0;
    s_written = 0;
}

bool ota_begin(uint32_t size, uint32_t crc32, char *err, size_t err_sz)
{
    if (err && err_sz) err[0] = '\0';
    if (s_active) {
        ota_abort();
    }
    if (size < 32 || size > OTA_MAX_IMAGE_SIZE) {
        if (err && err_sz) snprintf(err, err_sz, "bad size");
        return false;
    }
    s_part = esp_ota_get_next_update_partition(NULL);
    if (!s_part) {
        if (err && err_sz) snprintf(err, err_sz, "no ota partition");
        return false;
    }
    if (size > s_part->size) {
        if (err && err_sz) snprintf(err, err_sz, "too large");
        return false;
    }
    esp_err_t e = esp_ota_begin(s_part, size, &s_handle);
    if (e != ESP_OK) {
        if (err && err_sz) snprintf(err, err_sz, "begin %s", esp_err_to_name(e));
        return false;
    }
    s_expected = size;
    s_crc_expected = crc32;
    s_crc_running = 0;
    s_written = 0;
    s_header_ok = false;
    s_active = true;
    ESP_LOGI(TAG, "OTA begin size=%lu crc=0x%08lx -> %s",
             (unsigned long)size, (unsigned long)crc32, s_part->label);
    return true;
}

bool ota_write(const uint8_t *data, size_t len, char *err, size_t err_sz)
{
    if (err && err_sz) err[0] = '\0';
    if (!s_active || !data || len == 0) {
        if (err && err_sz) snprintf(err, err_sz, "inactive");
        return false;
    }
    if (s_written + len > s_expected) {
        if (err && err_sz) snprintf(err, err_sz, "overflow");
        ota_abort();
        return false;
    }
    if (!s_header_ok) {
        if (s_written == 0) {
            if (data[0] != OTA_IMAGE_MAGIC) {
                if (err && err_sz) snprintf(err, err_sz, "bad magic");
                ota_abort();
                return false;
            }
            s_header_ok = true;
        }
    }
    esp_err_t e = esp_ota_write(s_handle, data, len);
    if (e != ESP_OK) {
        if (err && err_sz) snprintf(err, err_sz, "write %s", esp_err_to_name(e));
        ota_abort();
        return false;
    }
    s_crc_running = esp_crc32_le(s_crc_running, data, len);
    s_written += (uint32_t)len;
    return true;
}

bool ota_finish(char *err, size_t err_sz)
{
    if (err && err_sz) err[0] = '\0';
    if (!s_active) {
        if (err && err_sz) snprintf(err, err_sz, "inactive");
        return false;
    }
    if (s_written != s_expected) {
        if (err && err_sz) snprintf(err, err_sz, "size mismatch");
        ota_abort();
        return false;
    }
    if (s_crc_running != s_crc_expected) {
        if (err && err_sz) {
            snprintf(err, err_sz, "crc mismatch got=0x%08lx",
                     (unsigned long)s_crc_running);
        }
        ota_abort();
        return false;
    }
    esp_err_t e = esp_ota_end(s_handle);
    s_handle = 0;
    if (e != ESP_OK) {
        if (err && err_sz) snprintf(err, err_sz, "end %s", esp_err_to_name(e));
        s_active = false;
        return false;
    }
    e = esp_ota_set_boot_partition(s_part);
    if (e != ESP_OK) {
        if (err && err_sz) snprintf(err, err_sz, "set_boot %s", esp_err_to_name(e));
        s_active = false;
        return false;
    }
    ESP_LOGI(TAG, "OTA finish ok, boot=%s", s_part->label);
    s_active = false;
    s_part = NULL;
    return true;
}
