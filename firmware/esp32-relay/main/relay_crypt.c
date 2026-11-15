#include <string.h>

#include "mbedtls/aes.h"
#include "mbedtls/md.h"

#include "relay_crypt.h"

/* Byte-exact mirror of companion ble_phone.c: mask seed 0xA5, nonce seed 0xC7. */

static void put_counter(uint8_t dst[4], uint32_t counter)
{
    dst[0] = (uint8_t)counter;
    dst[1] = (uint8_t)(counter >> 8);
    dst[2] = (uint8_t)(counter >> 16);
    dst[3] = (uint8_t)(counter >> 24);
}

static uint32_t get_counter(const uint8_t src[4])
{
    return (uint32_t)src[0] | ((uint32_t)src[1] << 8) |
           ((uint32_t)src[2] << 16) | ((uint32_t)src[3] << 24);
}

static void aes_ecb(const uint8_t key[16], const uint8_t in[16], uint8_t out[16])
{
    mbedtls_aes_context ctx;
    mbedtls_aes_init(&ctx);
    mbedtls_aes_setkey_enc(&ctx, key, 128);
    mbedtls_aes_crypt_ecb(&ctx, MBEDTLS_AES_ENCRYPT, in, out);
    mbedtls_aes_free(&ctx);
}

static void aes_ctr_xor(const uint8_t key[16], const uint8_t nonce[16],
                        const uint8_t *in, uint8_t *out, size_t len)
{
    mbedtls_aes_context ctx;
    uint8_t stream[16];
    uint8_t nc[16];
    size_t off = 0;
    memcpy(nc, nonce, 16);
    memset(stream, 0, sizeof(stream));
    mbedtls_aes_init(&ctx);
    mbedtls_aes_setkey_enc(&ctx, key, 128);
    mbedtls_aes_crypt_ctr(&ctx, len, &off, nc, stream, in, out);
    mbedtls_aes_free(&ctx);
}

static void make_mask(const uint8_t key[16], uint8_t type, const uint8_t id[4], uint8_t mask[4])
{
    uint8_t block[16];
    uint8_t out[16];
    memset(block, 0, sizeof(block));
    memcpy(block, id, 4);
    block[4] = type;
    block[5] = 0xA5;
    aes_ecb(key, block, out);
    memcpy(mask, out, 4);
}

static void make_nonce(uint8_t type, const uint8_t id[4], uint32_t counter, uint8_t nonce[16])
{
    memset(nonce, 0, 16);
    memcpy(nonce, id, 4);
    nonce[4] = type;
    put_counter(nonce + 5, counter);
    nonce[9] = 0xC7;
}

static void make_tag(const uint8_t key[16], uint8_t type, const uint8_t id[4],
                     uint32_t counter, const uint8_t body[RELAY_BODY_LEN], uint8_t tag[4])
{
    uint8_t msg[1 + 4 + 4 + RELAY_BODY_LEN];
    uint8_t full[32];
    msg[0] = type;
    memcpy(msg + 1, id, 4);
    put_counter(msg + 5, counter);
    memcpy(msg + 9, body, RELAY_BODY_LEN);
    mbedtls_md_hmac(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256),
                    key, 16, msg, sizeof(msg), full);
    memcpy(tag, full, 4);
}

void relay_crypt_seal(const uint8_t key[16], uint8_t type, const uint8_t id[4],
                      uint32_t counter, const uint8_t body[RELAY_BODY_LEN],
                      uint8_t out[RELAY_PKT_LEN])
{
    uint8_t mask[4];
    uint8_t nonce[16];
    uint8_t tag[4];
    uint8_t counter_le[4];
    make_mask(key, type, id, mask);
    put_counter(counter_le, counter);
    out[0] = type;
    memcpy(out + 1, id, 4);
    for (int i = 0; i < 4; i++) {
        out[5 + i] = (uint8_t)(counter_le[i] ^ mask[i]);
    }
    make_nonce(type, id, counter, nonce);
    aes_ctr_xor(key, nonce, body, out + 9, RELAY_BODY_LEN);
    make_tag(key, type, id, counter, body, tag);
    memcpy(out + 9 + RELAY_BODY_LEN, tag, 4);
}

bool relay_crypt_unseal(const uint8_t key[16], const uint8_t *pkt, size_t len,
                        uint8_t *type_out, uint32_t *counter_out,
                        uint8_t body[RELAY_BODY_LEN])
{
    if (len != RELAY_PKT_LEN) return false;
    uint8_t type = pkt[0];
    const uint8_t *id = pkt + 1;
    uint8_t mask[4];
    uint8_t counter_le[4];
    uint8_t nonce[16];
    uint8_t tag[4];
    make_mask(key, type, id, mask);
    for (int i = 0; i < 4; i++) {
        counter_le[i] = (uint8_t)(pkt[5 + i] ^ mask[i]);
    }
    uint32_t counter = get_counter(counter_le);
    make_nonce(type, id, counter, nonce);
    aes_ctr_xor(key, nonce, pkt + 9, body, RELAY_BODY_LEN);
    make_tag(key, type, id, counter, body, tag);
    if (memcmp(tag, pkt + 9 + RELAY_BODY_LEN, 4) != 0) return false;
    *type_out = type;
    *counter_out = counter;
    return true;
}
