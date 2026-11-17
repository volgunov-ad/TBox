#pragma once

/* PC simulator config for LVGL 9.x (SDL2 backend, MinGW-w64).
 * Mirrors the firmware choices where they matter for UI look:
 * fonts and stdlib usage. Color depth is per-display in LVGL 9,
 * the SDL window runs 32-bit which is fine on PC. */

#define LV_CONF_H

#define LV_USE_STDLIB_MALLOC LV_STDLIB_CLIB
#define LV_USE_STDLIB_STRING LV_STDLIB_CLIB
#define LV_USE_STDLIB_SPRINTF LV_STDLIB_CLIB

#define LV_USE_OS LV_OS_NONE

#define LV_FONT_MONTSERRAT_14 1
#define LV_FONT_MONTSERRAT_16 1
#define LV_FONT_MONTSERRAT_20 1
#define LV_FONT_MONTSERRAT_24 1
#define LV_FONT_MONTSERRAT_32 1
#define LV_FONT_MONTSERRAT_48 1
#define LV_FONT_DEFAULT &lv_font_montserrat_16

#define LV_USE_SDL 1
#define LV_SDL_INCLUDE_PATH "SDL2/SDL.h"
#define LV_SDL_RENDER_MODE LV_DISPLAY_RENDER_MODE_DIRECT
#define LV_SDL_BUF_COUNT 1
#define LV_SDL_FULLSCREEN 0
#define LV_SDL_DIRECT_EXIT 1
