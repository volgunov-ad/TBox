/* Host test: gcc -I../main bthome_crypt_test.c ../main/bthome_crypt.c -lmbedcrypto */
#include <stdio.h>
#include <string.h>

#include "bthome_crypt.h"

static int s_fail;

#define CHECK(cond)                                                    \
    do {                                                               \
        if (!(cond)) {                                                 \
            printf("FAIL %s:%d %s\n", __FILE__, __LINE__, #cond);      \
            s_fail++;                                                  \
        }                                                              \
    } while (0)

static size_t unhex(const char *s, uint8_t *out)
{
    size_t n = 0;
    while (s[0] && s[1]) {
        unsigned v;
        sscanf(s, "%2x", &v);
        out[n++] = (uint8_t)v;
        s += 2;
    }
    return n;
}

/* Example from https://bthome.io/encryption/ */
static void spec_vector(void)
{
    uint8_t key[16];
    uint8_t mac[6];
    uint8_t svc[32];
    uint8_t plain[32];
    uint8_t want[8];
    uint32_t counter = 0;
    CHECK(bthome_parse_key("231d39c1d7cc1ab1aee224cd096db932", key));
    unhex("5448e68f80a5", mac);
    size_t len = unhex("41e445f3c9962b332211006c7c4519", svc);
    size_t want_len = unhex("02ca0903bf13", want);

    int n = bthome_decrypt(key, mac, svc, len, plain, sizeof(plain), &counter);
    CHECK(n == (int)want_len);
    CHECK(n > 0 && memcmp(plain, want, want_len) == 0);
    CHECK(counter == 1122867u);

    svc[len - 1] ^= 1;
    CHECK(bthome_decrypt(key, mac, svc, len, plain, sizeof(plain), &counter) < 0);
    svc[len - 1] ^= 1;

    mac[5] ^= 1;
    CHECK(bthome_decrypt(key, mac, svc, len, plain, sizeof(plain), &counter) < 0);
    mac[5] ^= 1;

    CHECK(bthome_decrypt(key, mac, svc, 9, plain, sizeof(plain), &counter) < 0);
    CHECK(bthome_decrypt(key, mac, svc, len, plain, 2, &counter) < 0);
}

static void parse_key(void)
{
    uint8_t key[16];
    CHECK(bthome_parse_key("23:1D:39:C1:D7:CC:1A:B1:AE:E2:24:CD:09:6D:B9:32", key));
    CHECK(key[0] == 0x23 && key[15] == 0x32);
    CHECK(bthome_parse_key("231d 39c1 d7cc 1ab1 aee2 24cd 096d b932", key));
    CHECK(!bthome_parse_key("231d39c1d7cc1ab1aee224cd096db9", key));
    CHECK(!bthome_parse_key("231d39c1d7cc1ab1aee224cd096db93200", key));
    CHECK(!bthome_parse_key("231d39c1d7cc1ab1aee224cd096db93g", key));
    CHECK(!bthome_parse_key("2 31d39c1d7cc1ab1aee224cd096db932", key));
    CHECK(!bthome_parse_key("", key));
}

int main(void)
{
    spec_vector();
    parse_key();
    if (s_fail) return 1;
    printf("bthome_crypt: ok\n");
    return 0;
}
