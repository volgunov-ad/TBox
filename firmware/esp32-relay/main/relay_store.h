#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/** NVS-backed pairing identity, counters and last channel state. */
void relay_store_init(void);

/** 4 random bytes generated at first boot. */
const uint8_t *relay_store_id(void);
/** 16 random bytes generated at first boot. */
const uint8_t *relay_store_key(void);
/** Pairing name: RELAY_NAME with a trailing "XX" replaced by the id hex. */
const char *relay_store_name(void);
bool relay_store_paired(void);
/** Latched once the first valid 0x30 arrives; never cleared except factory reset. */
void relay_store_set_paired(void);

/** Current outbound (0x31) counter; bumped by RELAY_REBOOT_MARGIN at every boot. */
uint32_t relay_store_tx_counter(void);
/** ++ and mark dirty; NVS write happens in relay_store_tick with a 10 s throttle. */
uint32_t relay_store_next_tx(void);

/** Replay gate for inbound (0x30) counters: only strictly greater is accepted. */
bool relay_store_rx_accept(uint32_t counter);
/** Accept and persist the RX counter immediately (commands are rare). */
void relay_store_commit_rx(uint32_t counter);

/** False when NVS failed and the key is still all zeros. */
bool relay_store_key_ok(void);

/** Companion address learned from the first valid 0x30. */
bool relay_store_has_peer(void);
bool relay_store_peer_match(uint8_t addr_type, const uint8_t addr[6]);
void relay_store_set_peer(uint8_t addr_type, const uint8_t addr[6]);

uint8_t relay_store_mask(void);
/** Written through to NVS immediately; command rate is tiny. */
void relay_store_set_mask(uint8_t mask);

/** Flush dirty counters when their throttle expires. Call from the main loop. */
void relay_store_tick(void);
/** Erase the whole NVS namespace and reboot. */
void relay_store_factory_reset(void);
