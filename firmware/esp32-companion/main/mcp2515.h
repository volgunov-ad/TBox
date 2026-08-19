#pragma once

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

/** DevKitC-1 SPI pins for HW-184 MCP2515 (+ EM-409 level shifter). No INT. */
#define MCP2515_PIN_MOSI 11
#define MCP2515_PIN_MISO 13
#define MCP2515_PIN_SCK  12
#define MCP2515_PIN_CS   14

#define MCP2515_DEFAULT_BAUD 500000
#define MCP2515_DEFAULT_XTAL_HZ 8000000
#define MCP2515_MAX_FILTERS 6

typedef struct {
    uint32_t id;
    bool ext;
    bool rtr;
    uint8_t dlc;
    uint8_t data[8];
} mcp2515_frame_t;

typedef struct {
    uint32_t id;
    uint32_t mask;
    bool ext;
} mcp2515_filter_t;

bool mcp2515_init(uint32_t baud, uint32_t xtal_hz);
void mcp2515_deinit(void);
bool mcp2515_ok(void);

bool mcp2515_set_baud(uint32_t baud);
uint32_t mcp2515_get_baud(void);

/** accept_all=true clears filters. Otherwise up to MCP2515_MAX_FILTERS entries. */
bool mcp2515_set_filters(bool accept_all, const mcp2515_filter_t *filters, int count);

bool mcp2515_send(const mcp2515_frame_t *frame);
/** Non-blocking: returns true if a frame was read. */
bool mcp2515_recv(mcp2515_frame_t *frame);

bool mcp2515_baud_supported(uint32_t baud);
