#pragma once

#include "FreeRTOS.h"

typedef void *SemaphoreHandle_t;

static inline SemaphoreHandle_t xSemaphoreCreateMutex(void)
{
    static int s_dummy;
    return (SemaphoreHandle_t)&s_dummy;
}

static inline void xSemaphoreTake(SemaphoreHandle_t handle, TickType_t wait)
{
    (void)handle;
    (void)wait;
}

static inline void xSemaphoreGive(SemaphoreHandle_t handle)
{
    (void)handle;
}
