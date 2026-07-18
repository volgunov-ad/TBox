idf_component_register(
    SRCS "main.c" "protocol.c" "um980_uart.c" "gpio_io.c"
    INCLUDE_DIRS "."
    REQUIRES driver esp_timer tinyusb
)
