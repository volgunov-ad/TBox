/* Host test: gcc -Wall -Wextra -Werror -I../main phone_link_policy_test.c ../main/phone_link_policy.c */
#include <stdio.h>

#include "phone_link_policy.h"

static int s_fail;

#define CHECK(cond)                                               \
    do {                                                          \
        if (!(cond)) {                                            \
            printf("FAIL %s:%d %s\n", __FILE__, __LINE__, #cond); \
            s_fail++;                                             \
        }                                                         \
    } while (0)

int main(void)
{
    uint16_t min = 0, max = 0, latency = 1, timeout = 0;
    phone_link_preferred(&min, &max, &latency, &timeout);
    CHECK(min == 160);
    CHECK(max == 240);
    CHECK(latency == 0);
    CHECK(timeout == 500);
    CHECK(PHONE_LINK_MAX == 3);
    /* 200 ms is the floor. A phone asking for 100 ms must be refused. */
    CHECK(phone_link_params_ok(160));
    CHECK(phone_link_params_ok(240));
    CHECK(!phone_link_params_ok(159));
    CHECK(!phone_link_params_ok(80));
    CHECK(!phone_link_params_ok(6));
    if (s_fail) return 1;
    printf("phone_link_policy: ok\n");
    return 0;
}
