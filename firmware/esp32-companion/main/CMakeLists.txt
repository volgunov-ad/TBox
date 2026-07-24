idf_component_register(
    SRCS "main.c" "protocol.c" "um980_uart.c" "gpio_io.c" "ota_update.c"
    INCLUDE_DIRS "."
    REQUIRES driver esp_timer nvs_flash app_update espressif__esp_tinyusb
)
