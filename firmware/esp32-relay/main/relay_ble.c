#include <string.h>

#include "esp_log.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/portmacro.h"
#include "freertos/task.h"
#include "host/ble_gap.h"
#include "host/ble_gatt.h"
#include "host/ble_hs.h"
#include "host/ble_hs_adv.h"

#include "relay_ble.h"
#include "relay_crypt.h"
#include "relay_logic.h"
#include "relay_store.h"
#include "sdkconfig.h"

#define SVC_UUID 0x7B0E
#define CHR_WRITE_UUID 0x7B0F
#define CHR_NOTIFY_UUID 0x7B10
#define PAIR_TYPE 0x11
#define CMD_TYPE 0x30
#define STATE_TYPE 0x31
#define PAIR_RETRY_MS 1000u
#define PAIR_ATTEMPT_MAX 5
#define BACKOFF_MIN_MS 3000u
#define BACKOFF_MAX_MS 10000u
#define PAIR_BLOCK_MS 60000u
#define DISC_TIMEOUT_MS 8000u
#define PAIR_NAME_MAX 26
#define CMD_Q 4

static const char *TAG = "relay_ble";

static ble_uuid16_t s_svc_uuid = BLE_UUID16_INIT(SVC_UUID);
static ble_uuid16_t s_chr_write_uuid = BLE_UUID16_INIT(CHR_WRITE_UUID);
static ble_uuid16_t s_chr_notify_uuid = BLE_UUID16_INIT(CHR_NOTIFY_UUID);

static uint8_t s_own_addr_type;
static volatile bool s_synced;
static volatile uint16_t s_conn = BLE_HS_CONN_HANDLE_NONE;
static volatile bool s_subscribed;
static volatile uint16_t s_mtu = 23;
static volatile bool s_state_pending;

static uint16_t s_chr_write;
static uint16_t s_chr_notify;
static bool s_svc_found;
static bool s_cmd_echo;
static uint8_t s_gen_echo;
static uint8_t s_pair_pkt[64];
static uint16_t s_pair_len;
static uint32_t s_next_pair_ms;
static uint32_t s_next_state_ms;
static uint32_t s_backoff_until_ms;
static uint32_t s_conn_ms;
static uint32_t s_subscribed_ms;
static uint32_t s_last_sealed_ms;
static int s_pair_attempts;
static bool s_adv_learn;
static bool s_seen_peer_valid;
static uint8_t s_seen_peer_type;
static uint8_t s_seen_peer[6];
static uint8_t s_cmd_q[CMD_Q][RELAY_PKT_LEN];
static int s_cmd_q_w;
static int s_cmd_q_r;
static portMUX_TYPE s_cmd_mux = portMUX_INITIALIZER_UNLOCKED;

static uint32_t now_ms(void)
{
    return (uint32_t)(esp_timer_get_time() / 1000ULL);
}

static uint32_t backoff_ms(void)
{
    return BACKOFF_MIN_MS + esp_random() % (BACKOFF_MAX_MS - BACKOFF_MIN_MS);
}

static int svc_disc_cb(uint16_t conn_handle, const struct ble_gatt_error *error,
                       const struct ble_gatt_svc *svc, void *arg);
static int chr_disc_cb(uint16_t conn_handle, const struct ble_gatt_error *error,
                       const struct ble_gatt_chr *chr, void *arg);
static int cccd_cb(uint16_t conn_handle, const struct ble_gatt_error *error,
                   struct ble_gatt_attr *attr, void *arg);
static int conn_gap_cb(struct ble_gap_event *event, void *arg);

static void build_pair(void)
{
    const char *name = relay_store_name();
    uint8_t name_len = (uint8_t)strlen(name);
    if (name_len > PAIR_NAME_MAX) name_len = PAIR_NAME_MAX;
    s_pair_len = (uint16_t)(21 + name_len);
    s_pair_pkt[0] = PAIR_TYPE;
    memcpy(s_pair_pkt + 1, relay_store_id(), 4);
    memcpy(s_pair_pkt + 5, relay_store_key(), 16);
    memcpy(s_pair_pkt + 21, name, name_len);
}

/** Identity of this pointer marks a state write, as opposed to a PAIR write. */
static uint8_t s_write_state_arg;
static bool s_have_gen;
static uint8_t s_applied_gen;

static int write_cb(uint16_t conn_handle, const struct ble_gatt_error *error,
                    struct ble_gatt_attr *attr, void *arg)
{
    (void)conn_handle;
    (void)attr;
    if (error->status != 0) {
        ESP_LOGW(TAG, "write status=%d", error->status);
        if (arg == &s_write_state_arg) s_state_pending = true;
        return 0;
    }
    /* A keepalive 0x31 the companion accepted is liveness. The companion
     * does not send a periodic 0x30, so waiting for one drops a quiet link. */
    if (arg == &s_write_state_arg) s_last_sealed_ms = now_ms();
    return 0;
}

static void send_state(void)
{
    uint8_t body[RELAY_BODY_LEN];
    uint8_t pkt[RELAY_PKT_LEN];
    relay_state_lock();
    memset(body, 0, sizeof(body));
    body[0] = relay_logic_mask();
    body[1] = (uint8_t)((s_cmd_echo ? 0x01 : 0) |
                        (relay_logic_take_restored() ? 0x02 : 0) |
                        (relay_logic_pulse_active() ? 0x04 : 0) |
                        (relay_logic_take_self_opened() ? 0x08 : 0));
    uint32_t up = (uint32_t)(esp_timer_get_time() / 1000000ULL);
    body[2] = (uint8_t)up;
    body[3] = (uint8_t)(up >> 8);
    body[4] = s_gen_echo;
    relay_crypt_seal(relay_store_key(), STATE_TYPE, relay_store_id(),
                     relay_store_next_tx(), body, pkt);
    s_cmd_echo = false;
    relay_state_unlock();
    int rc = ble_gattc_write_flat(s_conn, s_chr_write, pkt, RELAY_PKT_LEN,
                                  write_cb, &s_write_state_arg);
    if (rc != 0) {
        ESP_LOGW(TAG, "state rc=%d", rc);
        s_state_pending = true;
        return;
    }
    s_state_pending = false;
}

/** Host task: copy the notify. The main loop unseals and touches NVS. */
static void on_notify(const uint8_t *pkt, uint16_t len)
{
    if (len != RELAY_PKT_LEN) return;
    portENTER_CRITICAL(&s_cmd_mux);
    int next = (s_cmd_q_w + 1) % CMD_Q;
    if (next != s_cmd_q_r) {
        memcpy(s_cmd_q[s_cmd_q_w], pkt, RELAY_PKT_LEN);
        s_cmd_q_w = next;
    }
    portEXIT_CRITICAL(&s_cmd_mux);
}

static int svc_disc_cb(uint16_t conn_handle, const struct ble_gatt_error *error,
                       const struct ble_gatt_svc *svc, void *arg)
{
    (void)arg;
    if (error->status == 0 && svc) {
        s_svc_found = true;
        ble_gattc_disc_all_chrs(conn_handle, svc->start_handle, svc->end_handle,
                                chr_disc_cb, NULL);
        return 0;
    }
    if (!s_svc_found) {
        ESP_LOGW(TAG, "service not found");
        ble_gap_terminate(conn_handle, BLE_ERR_REM_USER_CONN_TERM);
    }
    return 0;
}

static int chr_disc_cb(uint16_t conn_handle, const struct ble_gatt_error *error,
                       const struct ble_gatt_chr *chr, void *arg)
{
    (void)arg;
    if (error->status == 0 && chr) {
        if (ble_uuid_cmp(&chr->uuid.u, &s_chr_write_uuid.u) == 0) {
            s_chr_write = chr->val_handle;
        } else if (ble_uuid_cmp(&chr->uuid.u, &s_chr_notify_uuid.u) == 0) {
            s_chr_notify = chr->val_handle;
        }
        return 0;
    }
    if (s_chr_write == 0 || s_chr_notify == 0) {
        ESP_LOGW(TAG, "chars not found (w=%d n=%d)", s_chr_write, s_chr_notify);
        ble_gap_terminate(conn_handle, BLE_ERR_REM_USER_CONN_TERM);
        return 0;
    }
    uint8_t cccd[2] = { 0x01, 0x00 };
    int rc = ble_gattc_write_flat(conn_handle, (uint16_t)(s_chr_notify + 1), cccd, 2,
                                  cccd_cb, NULL);
    if (rc != 0) {
        ESP_LOGW(TAG, "cccd rc=%d", rc);
    }
    return 0;
}

static int cccd_cb(uint16_t conn_handle, const struct ble_gatt_error *error,
                   struct ble_gatt_attr *attr, void *arg)
{
    (void)conn_handle;
    (void)attr;
    (void)arg;
    if (error->status == 0) {
        s_subscribed = true;
        s_state_pending = true;
        s_subscribed_ms = now_ms();
        s_next_state_ms = s_subscribed_ms;
    } else {
        ESP_LOGW(TAG, "cccd status=%d", error->status);
    }
    return 0;
}

static int mtu_cb(uint16_t conn_handle, const struct ble_gatt_error *error,
                  uint16_t mtu, void *arg)
{
    (void)arg;
    if (error->status != 0) {
        ESP_LOGW(TAG, "mtu status=%d", error->status);
    }
    (void)conn_handle;
    (void)mtu; /* the actual value arrives via BLE_GAP_EVENT_MTU */
    return 0;
}

static int conn_gap_cb(struct ble_gap_event *event, void *arg)
{
    switch (event->type) {
    case BLE_GAP_EVENT_CONNECT:
        if (event->connect.status != 0) {
            ESP_LOGW(TAG, "connect status=%d", event->connect.status);
            s_backoff_until_ms = now_ms() + backoff_ms();
            return 0;
        }
        s_conn = event->connect.conn_handle;
        s_subscribed = false;
        s_mtu = 23;
        s_svc_found = false;
        s_chr_write = 0;
        s_chr_notify = 0;
        s_next_pair_ms = 0;
        s_conn_ms = now_ms();
        s_subscribed_ms = 0;
        s_last_sealed_ms = 0;
        s_pair_attempts = 0;
        s_seen_peer_valid = false;
        {
            struct ble_gap_conn_desc desc;
            memset(&desc, 0, sizeof(desc));
            if (ble_gap_conn_find(s_conn, &desc) == 0) {
                s_seen_peer_type = desc.peer_id_addr.type;
                memcpy(s_seen_peer, desc.peer_id_addr.val, 6);
                s_seen_peer_valid = true;
            }
        }
        ESP_LOGI(TAG, "connected");
        ble_gattc_exchange_mtu(s_conn, mtu_cb, NULL);
        return 0;
    case BLE_GAP_EVENT_DISCONNECT:
        s_conn = BLE_HS_CONN_HANDLE_NONE;
        s_subscribed = false;
        ESP_LOGI(TAG, "disconnected reason=%d", event->disconnect.reason);
        s_backoff_until_ms = now_ms() + backoff_ms();
        return 0;
    case BLE_GAP_EVENT_MTU:
        s_mtu = event->mtu.value;
        ble_gattc_disc_svc_by_uuid(s_conn, &s_svc_uuid.u, svc_disc_cb, NULL);
        return 0;
    case BLE_GAP_EVENT_NOTIFY_RX: {
        struct os_mbuf *om = event->notify_rx.om;
        uint8_t buf[RELAY_PKT_LEN];
        uint16_t len = 0;
        if (!om) return 0;
        ble_hs_mbuf_to_flat(om, buf, sizeof(buf), &len);
        on_notify(buf, len);
        return 0;
    }
    default:
        return 0;
    }
}

static bool adv_is_companion(const struct ble_hs_adv_fields *fields, bool *learn_out)
{
    bool match = false;
    if (fields->name_len >= 4 && memcmp(fields->name, "TBox", 4) == 0) match = true;
    for (int i = 0; i < fields->num_uuids16; i++) {
        if (fields->uuids16[i].value == SVC_UUID) match = true;
    }
    if (!match) return false;
    bool learn = false;
    /* Companion manufacturer payload: company 0xFFFF, bit0 = learn window. */
    if (fields->mfg_data_len >= 3 &&
        fields->mfg_data[0] == 0xFF && fields->mfg_data[1] == 0xFF) {
        learn = (fields->mfg_data[2] & 0x01) != 0;
    }
    if (learn_out) *learn_out = learn;
    return true;
}

static int disc_gap_cb(struct ble_gap_event *event, void *arg)
{
    switch (event->type) {
    case BLE_GAP_EVENT_DISC: {
        if (s_conn != BLE_HS_CONN_HANDLE_NONE) return 0;
        struct ble_hs_adv_fields fields;
        if (ble_hs_adv_parse_fields(&fields, event->disc.data,
                                    event->disc.length_data) != 0) {
            return 0;
        }
        bool learn = false;
        if (!adv_is_companion(&fields, &learn)) return 0;
        if (!relay_store_key_ok()) return 0;
        bool paired = relay_store_paired();
        /* Unpaired boards send the key only while the companion learn window is open. */
        if (!paired && !learn) return 0;
        if (paired && relay_store_has_peer() &&
            !relay_store_peer_match(event->disc.addr.type, event->disc.addr.val)) {
            return 0;
        }
        s_adv_learn = learn;
        ble_gap_disc_cancel();
        int rc = ble_gap_connect(s_own_addr_type, &event->disc.addr, 0, NULL,
                                 conn_gap_cb, NULL);
        if (rc != 0) {
            ESP_LOGW(TAG, "connect rc=%d", rc);
            s_backoff_until_ms = now_ms() + backoff_ms();
        }
        return 0;
    }
    case BLE_GAP_EVENT_DISC_COMPLETE:
        return 0;
    default:
        return 0;
    }
}

static void start_disc(void)
{
    struct ble_gap_disc_params params = { 0 };
    params.itvl = 0x0060;   /* scan interval 60 ms */
    params.window = 0x0030; /* scan window 30 ms */
    params.passive = 0;
    params.filter_duplicates = 0;
    int rc = ble_gap_disc(s_own_addr_type, BLE_HS_FOREVER, &params, disc_gap_cb, NULL);
    if (rc != 0 && rc != BLE_HS_EALREADY) {
        ESP_LOGW(TAG, "disc rc=%d", rc);
        s_backoff_until_ms = now_ms() + backoff_ms();
    }
}

static void relay_task(void *arg)
{
    build_pair();
    while (!s_synced) {
        vTaskDelay(pdMS_TO_TICKS(100));
    }
    for (;;) {
        if (!relay_store_key_ok()) {
            vTaskDelay(pdMS_TO_TICKS(1000));
            continue;
        }
        if (s_conn == BLE_HS_CONN_HANDLE_NONE) {
            if ((int32_t)(now_ms() - s_backoff_until_ms) < 0) {
                vTaskDelay(pdMS_TO_TICKS(200));
                continue;
            }
            if (!ble_gap_disc_active()) start_disc();
            vTaskDelay(pdMS_TO_TICKS(300));
            continue;
        }
        uint32_t now = now_ms();
        if (!s_subscribed && s_conn_ms != 0 &&
            (uint32_t)(now - s_conn_ms) > DISC_TIMEOUT_MS) {
            ESP_LOGW(TAG, "discovery timeout");
            ble_gap_terminate(s_conn, BLE_ERR_REM_USER_CONN_TERM);
            vTaskDelay(pdMS_TO_TICKS(200));
            continue;
        }
        if (s_subscribed) {
            bool paired = relay_store_paired();
            if (!paired && !s_adv_learn) {
                ble_gap_terminate(s_conn, BLE_ERR_REM_USER_CONN_TERM);
                vTaskDelay(pdMS_TO_TICKS(200));
                continue;
            }
            if (!paired && s_adv_learn && s_pair_attempts < PAIR_ATTEMPT_MAX &&
                s_mtu >= (uint16_t)(s_pair_len + 3)) {
                if ((int32_t)(now - s_next_pair_ms) >= 0) {
                    int rc = ble_gattc_write_flat(s_conn, s_chr_write,
                                                  s_pair_pkt, s_pair_len,
                                                  write_cb, NULL);
                    if (rc != 0) ESP_LOGW(TAG, "pair rc=%d", rc);
                    s_pair_attempts++;
                    s_next_pair_ms = now + PAIR_RETRY_MS;
                    if (s_pair_attempts >= PAIR_ATTEMPT_MAX) {
                        s_backoff_until_ms = now + PAIR_BLOCK_MS;
                    }
                }
            }
            uint32_t sealed_limit =
                (uint32_t)CONFIG_RELAY_KEEPALIVE_S * 1000u * 2u;
            uint32_t since = s_last_sealed_ms != 0 ? s_last_sealed_ms : s_subscribed_ms;
            if (since != 0 && (uint32_t)(now - since) > sealed_limit) {
                ESP_LOGW(TAG, "no sealed traffic");
                ble_gap_terminate(s_conn, BLE_ERR_REM_USER_CONN_TERM);
                vTaskDelay(pdMS_TO_TICKS(200));
                continue;
            }
            if (s_mtu >= (uint16_t)(RELAY_PKT_LEN + 3)) {
                bool send = s_state_pending;
                if (!send && relay_store_paired() &&
                    (int32_t)(now - s_next_state_ms) >= 0) {
                    send = true;
                }
                if (send) {
                    send_state();
                    uint32_t jitter = esp_random() % 4001u; /* +-2 s dephase */
                    s_next_state_ms = now +
                                      (uint32_t)CONFIG_RELAY_KEEPALIVE_S * 1000u +
                                      jitter - 2000u;
                }
            }
        }
        vTaskDelay(pdMS_TO_TICKS(100));
    }
}

static void on_sync(void)
{
    int rc = ble_hs_id_infer_auto(0, &s_own_addr_type);
    if (rc != 0) {
        ESP_LOGE(TAG, "infer addr rc=%d", rc);
        return;
    }
    s_synced = true;
}

static void on_reset(int reason)
{
    s_synced = false;
    s_conn = BLE_HS_CONN_HANDLE_NONE;
    s_subscribed = false;
    ESP_LOGW(TAG, "host reset reason=%d", reason);
}

void relay_ble_start(void)
{
    ble_hs_cfg.sync_cb = on_sync;
    ble_hs_cfg.reset_cb = on_reset;
    if (xTaskCreate(relay_task, "relay_ble", 4096, NULL, 4, NULL) != pdPASS) {
        ESP_LOGE(TAG, "task create failed");
    }
}

void relay_ble_kick_state(void)
{
    s_state_pending = true;
}

bool relay_ble_online(void)
{
    return s_conn != BLE_HS_CONN_HANDLE_NONE && s_subscribed;
}

bool relay_ble_take_cmd(uint8_t pkt[24])
{
    bool got = false;
    portENTER_CRITICAL(&s_cmd_mux);
    if (s_cmd_q_r != s_cmd_q_w) {
        memcpy(pkt, s_cmd_q[s_cmd_q_r], RELAY_PKT_LEN);
        s_cmd_q_r = (s_cmd_q_r + 1) % CMD_Q;
        got = true;
    }
    portEXIT_CRITICAL(&s_cmd_mux);
    return got;
}

void relay_ble_handle_cmd(const uint8_t pkt[24])
{
    uint8_t type = 0;
    uint32_t counter = 0;
    uint8_t body[RELAY_BODY_LEN];
    relay_state_lock();
    if (!relay_crypt_unseal(relay_store_key(), pkt, RELAY_PKT_LEN, &type, &counter, body) ||
        type != CMD_TYPE || !relay_store_rx_accept(counter)) {
        relay_state_unlock();
        return;
    }
    relay_store_commit_rx(counter);
    s_last_sealed_ms = now_ms();
    if (!relay_store_paired()) {
        relay_store_set_paired();
        ESP_LOGI(TAG, "paired (latched)");
    }
    if (s_seen_peer_valid) {
        relay_store_set_peer(s_seen_peer_type, s_seen_peer);
    }
    /* Channel bit 0 clear must not cancel a running pulse. */
    if ((body[0] & 0x01) != 0) {
        /* A retry reuses the generation and gets a new counter. Echo it,
         * but do not pulse or latch a second time. */
        if (s_have_gen && body[4] == s_applied_gen) {
            s_gen_echo = body[4];
            s_cmd_echo = true;
            s_state_pending = true;
        } else {
            uint16_t pulse_ms = (uint16_t)(body[2] | ((uint16_t)body[3] << 8));
            uint8_t actual = relay_logic_apply(body[1] & 0x01, pulse_ms);
            s_applied_gen = body[4];
            s_have_gen = true;
            s_gen_echo = body[4];
            s_cmd_echo = true;
            s_state_pending = true;
            ESP_LOGI(TAG, "cmd counter=%lu states=%d pulse=%u mask=%d",
                     (unsigned long)counter, body[1] & 1, pulse_ms, actual);
        }
    }
    relay_state_unlock();
}

bool relay_ble_connected_peer(uint8_t *addr_type, uint8_t addr[6])
{
    if (!s_seen_peer_valid || !addr_type || !addr) return false;
    *addr_type = s_seen_peer_type;
    memcpy(addr, s_seen_peer, 6);
    return true;
}
