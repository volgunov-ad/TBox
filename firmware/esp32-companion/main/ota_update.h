#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/** Max app image size (matches ota_0/ota_1 partition). */
#define OTA_MAX_IMAGE_SIZE (0x180000u)

bool ota_is_active(void);

/**
 * Start OTA to the next update partition.
 * @param size total image bytes
 * @param crc32 IEEE CRC32 of full image (host-computed)
 */
bool ota_begin(uint32_t size, uint32_t crc32, char *err, size_t err_sz);

bool ota_write(const uint8_t *data, size_t len, char *err, size_t err_sz);

/** Finalize, set boot partition. Caller should reboot after ACK. */
bool ota_finish(char *err, size_t err_sz);

void ota_abort(void);

uint32_t ota_bytes_written(void);
uint32_t ota_expected_size(void);
