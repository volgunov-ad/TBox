idf_component_register(
    SRCS "main.c" "protocol.c" "um980_uart.c" "gnss_detect.c" "gpio_io.c" "ota_update.c"
         "mcp2515.c" "mag.c" "rm3100_i2c.c" "mmc5983_i2c.c" "ist8310_i2c.c"
         "qmc5883l_i2c.c" "hmc58xx_i2c.c"
    INCLUDE_DIRS "."
    REQUIRES driver esp_timer nvs_flash app_update espressif__esp_tinyusb
)
