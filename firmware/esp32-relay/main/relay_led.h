#pragma once

/**
 * Status LED (CONFIG_RELAY_LED_GPIO). Blink patterns are driven from
 * relay_led_poll, called from the main loop; the mode is chosen by main.
 */
typedef enum {
    RELAY_LED_FAST, /* not paired: 5 Hz */
    RELAY_LED_SLOW, /* paired, no companion link: 400/600 ms */
    RELAY_LED_ON,   /* paired and connected */
    RELAY_LED_HOLD, /* reset button held: 10 Hz */
} relay_led_mode_t;

void relay_led_init(void);
void relay_led_set_mode(relay_led_mode_t mode);
void relay_led_poll(void);
