#include <stdbool.h>
#include <stdio.h>
#include <string.h>

#include <SDL2/SDL.h>

#include "lvgl.h"

#include "climate_model.h"
#include "climate_view.h"

static volatile bool s_dirty = false;

static void on_model_changed(const climate_state_t *state)
{
    (void)state;
    s_dirty = true;
}

int main(int argc, char *argv[])
{
    bool auto_exit = false;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--auto-exit") == 0) {
            auto_exit = true;
        }
    }

    climate_model_init();

    lv_init();
    lv_tick_set_cb(SDL_GetTicks);

    lv_display_t *disp = lv_sdl_window_create(800, 480);
    if (disp == NULL) {
        fprintf(stderr, "lv_sdl_window_create failed\n");
        return 1;
    }
    lv_indev_t *mouse = lv_sdl_mouse_create();
    if (mouse == NULL) {
        fprintf(stderr, "lv_sdl_mouse_create failed\n");
        return 1;
    }

    climate_view_create(lv_screen_active());
    climate_state_t state;
    climate_model_get(&state);
    climate_view_refresh(&state);
    climate_model_set_change_cb(on_model_changed);

    uint32_t sim_last = lv_tick_get();
    uint32_t sim_tick = 0;
    uint32_t boot = lv_tick_get();
    bool quit = false;
    while (!quit) {
        uint32_t delay = lv_timer_handler();
        if (delay > 20) {
            delay = 20;
        }
        if (delay == 0) {
            delay = 5;
        }
        SDL_Delay(delay);

        if (s_dirty) {
            s_dirty = false;
            climate_model_get(&state);
            climate_view_refresh(&state);
        }

        uint32_t now = lv_tick_get();
        if (now - sim_last >= 2000) {
            sim_last = now;
            sim_tick++;
            if (sim_tick % 3 == 0) {
                climate_model_adjust_temp((sim_tick & 1) != 0, CLIMATE_TEMP_STEP_C10);
            }
            if (sim_tick % 4 == 0) {
                climate_model_cycle_fan(1);
            }
            if (sim_tick % 5 == 0) {
                climate_model_toggle_ac();
            }
            if (sim_tick % 6 == 0) {
                climate_model_cycle_mode();
            }
        }

        if (auto_exit && now - boot >= 4000) {
            printf("SIM AUTO-EXIT OK\n");
            quit = true;
        }
    }
    return 0;
}
