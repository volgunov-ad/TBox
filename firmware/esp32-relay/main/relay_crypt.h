#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define RELAY_BODY_LEN 11
#define RELAY_PKT_LEN 24

/* Same sealed packet as the phone channel: type, id[4], counter^mask, cipher[11], tag[4]. */
void relay_crypt_seal(const uint8_t key[16], uint8_t type, const uint8_t id[4],
                      uint32_t counter, const uint8_t body[RELAY_BODY_LEN],
                      uint8_t out[RELAY_PKT_LEN]);

bool relay_crypt_unseal(const uint8_t key[16], const uint8_t *pkt, size_t len,
                        uint8_t *type_out, uint32_t *counter_out,
                        uint8_t body[RELAY_BODY_LEN]);
