#include "bthome_crypt.h"

#include <string.h>

#include "mbedtls/ccm.h"

#define BTHOME_UUID_LO 0xD2
#define BTHOME_UUID_HI 0xFC
#define COUNTER_LEN 4
#define MIC_LEN 4

int bthome_decrypt(const uint8_t key[BTHOME_KEY_LEN], const uint8_t mac[6],
                   const uint8_t *svc, size_t len,
                   uint8_t *out, size_t cap, uint32_t *counter_out)
{
    if (!key || !mac || !svc || !out) return -1;
    if (len < 1 + 1 + COUNTER_LEN + MIC_LEN) return -1;
    size_t ct_len = len - 1 - COUNTER_LEN - MIC_LEN;
    if (ct_len > cap) return -1;
    const uint8_t *ct = svc + 1;
    const uint8_t *counter = ct + ct_len;
    const uint8_t *mic = counter + COUNTER_LEN;

    uint8_t nonce[13];
    memcpy(nonce, mac, 6);
    nonce[6] = BTHOME_UUID_LO;
    nonce[7] = BTHOME_UUID_HI;
    nonce[8] = svc[0];
    memcpy(nonce + 9, counter, COUNTER_LEN);

    mbedtls_ccm_context ctx;
    mbedtls_ccm_init(&ctx);
    int rc = mbedtls_ccm_setkey(&ctx, MBEDTLS_CIPHER_ID_AES, key, 128);
    if (rc == 0) {
        rc = mbedtls_ccm_auth_decrypt(&ctx, ct_len, nonce, sizeof(nonce), NULL, 0,
                                      ct, out, mic, MIC_LEN);
    }
    mbedtls_ccm_free(&ctx);
    if (rc != 0) return -1;
    if (counter_out) {
        *counter_out = (uint32_t)counter[0] | ((uint32_t)counter[1] << 8) |
                       ((uint32_t)counter[2] << 16) | ((uint32_t)counter[3] << 24);
    }
    return (int)ct_len;
}

static int hex_digit(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

int bthome_parse_key(const char *s, uint8_t out[BTHOME_KEY_LEN])
{
    if (!s) return 0;
    uint8_t tmp[BTHOME_KEY_LEN];
    int n = 0;
    int hi = -1;
    for (const char *p = s; *p; p++) {
        if (*p == ' ' || *p == ':' || *p == '-') {
            if (hi >= 0) return 0;
            continue;
        }
        int v = hex_digit(*p);
        if (v < 0 || n >= BTHOME_KEY_LEN) return 0;
        if (hi < 0) {
            hi = v;
        } else {
            tmp[n++] = (uint8_t)((hi << 4) | v);
            hi = -1;
        }
    }
    if (n != BTHOME_KEY_LEN || hi >= 0) return 0;
    memcpy(out, tmp, BTHOME_KEY_LEN);
    return 1;
}
