#pragma once

#include <stddef.h>
#include <stdint.h>

#define BTHOME_KEY_LEN 16

/**
 * Decrypt BTHome v2 service data (the bytes after UUID 0xFCD2):
 * info byte, ciphertext, counter (u32 LE), MIC (4 bytes), AES-CCM.
 * mac is the sender address in printed order (MSB first).
 * Returns plaintext length or -1 if the frame is short or the MIC is wrong.
 */
int bthome_decrypt(const uint8_t key[BTHOME_KEY_LEN], const uint8_t mac[6],
                   const uint8_t *svc, size_t len,
                   uint8_t *out, size_t cap, uint32_t *counter_out);

/** 32 hex digits; ' ', ':' and '-' between digits are ignored. */
int bthome_parse_key(const char *s, uint8_t out[BTHOME_KEY_LEN]);
