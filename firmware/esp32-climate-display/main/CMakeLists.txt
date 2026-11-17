idf_component_register(
    SRCS "main.c" "board_bsp.c" "can_link.c" "can_map.c" "climate_model.c" "climate_view.c" "companion_link.c"
    INCLUDE_DIRS "."
    REQUIRES driver esp_timer esp_lcd json
)
