idf_component_register(
    SRCS "main.c" "relay_store.c" "relay_crypt.c" "relay_logic.c" "relay_ble.c"
    INCLUDE_DIRS "."
    REQUIRES driver esp_timer nvs_flash bt mbedtls
)
