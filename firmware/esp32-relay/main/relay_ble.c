#include <string.h>

#include "esp_log.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
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
#define BACKOFF_MIN_MS 3000u
#define BACKOFF_MAX_MS 10000u
#define PAIR_NAME_MAX 26

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

static uint32_t now_ms(void)
{
    return (uint32_t)(esp_timer_get_time() / 1000ULL);
}

static uint32_t backoff_ms(void)
{
    return BACKOFF_MIN_MS + esp_random() % (BACKOFF_MAX_MS - BACKOFF_MIN_MS);
}

static void svc_disc_cb(uint16_t conn_handle, const struct ble_gatt_error *error,
                        const struct ble_gatt_svc *svc, void *arg);
static void chr_disc_cb(uint16_t conn_handle, const struct ble_gatt_error *error,
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

static int write_cb(uint16_t conn_handle, const struct ble_gatt_error *error,
                    struct ble_gatt_attr *attr, void *arg)
{
    (void)conn_handle;
    (void)attr;
    (void)arg;
    if (error->status != 0) {
        ESP_LOGW(TAG, "write status=%d", error->status);
    }
    return 0;
}

static void send_state(void)
{
    uint8_t body[RELAY_BODY_LEN];
    uint8_t pkt[RELAY_PKT_LEN];
    memset(body, 0, sizeof(body));
    body[0] = relay_logic_mask();
    body[1] = (uint8_t)((s_cmd_echo ? 0x01 : 0) |
                        (relay_logic_take_restored() ? 0x02 : 0) |
                        (relay_logic_pulse_active() ? 0x04 : 0));
    uint32_t up = (uint32_t)(esp_timer_get_time() / 1000000ULL);
    body[2] = (uint8_t)up;
    body[3] = (uint8_t)(up >> 8);
    body[4] = s_gen_echo;
    relay_crypt_seal(relay_store_key(), STATE_TYPE, relay_store_id(),
                     relay_store_next_tx(), body, pkt);
    int rc = ble_gattc_write(s_conn, s_chr_write, pkt, RELAY_PKT_LEN, write_cb, NULL);
    if (rc != 0) {
        ESP_LOGW(TAG, "state rc=%d", rc);
    }
    s_cmd_echo = false;
}

/** Host task context (GATT callback): touch RAM flags only, writes go via relay_task. */
static void on_notify(const uint8_t *pkt, uint16_t len)
{
    if (len != RELAY_PKT_LEN) return;
    uint8_t type = 0;
    uint32_t counter = 0;
    uint8_t body[RELAY_BODY_LEN];
    if (!relay_crypt_unseal(relay_store_key(), pkt, len, &type, &counter, body)) return;
    if (type != CMD_TYPE) return;
    if (!relay_store_rx_accept(counter)) return;
    relay_store_commit_rx(counter);
    if (!relay_store_paired()) {
        /* The first valid 0x30 proves the companion saved the record. */
        relay_store_set_paired();
        ESP_LOGI(TAG, "paired (latched)");
    }
    uint8_t chan = body[0] & 0x01;
    uint16_t pulse_ms = (uint16_t)(body[2] | ((uint16_t)body[3] << 8));
    uint8_t states = chan ? (body[1] & 0x01) : relay_logic_mask();
    uint8_t actual = relay_logic_apply(states, chan ? pulse_ms : 0);
    s_gen_echo = body[4];
    s_cmd_echo = true;
    s_state_pending = true;
    ESP_LOGI(TAG, "cmd counter=%lu chan=%d states=%d pulse=%u mask=%d",
             (unsigned long)counter, chan, states, pulse_ms, actual);
}

static void svc_disc_cb(uint16_t conn_handle, const struct ble_gatt_error *error,
                        const struct ble_gatt_svc *svc, void *arg)
{
    (void)arg;
    if (error->status == 0 && svc) {
        s_svc_found = true;
        ble_gattc_disc_all_chrs(conn_handle, svc->start_handle, svc->end_handle,
                                chr_disc_cb, NULL);
        return;
    }
    if (!s_svc_found) {
        ESP_LOGW(TAG, "service not found");
        ble_gap_terminate(conn_handle, BLE_ERR_REM_USER_CONN_TERM);
    }
}

static void chr_disc_cb(uint16_t conn_handle, const struct ble_gatt_error *error,
                        const struct ble_gatt_chr *chr, void *arg)
{
    (void)arg;
    if (error->status == 0 && chr) {
        if (ble_uuid_cmp(chr->uuid, &s_chr_write_uuid.u) == 0) {
            s_chr_write = chr->val_handle;
        } else if (ble_uuid_cmp(chr->uuid, &s_chr_notify_uuid.u) == 0) {
            s_chr_notify = chr->val_handle;
        }
        return;
    }
    if (s_chr_write == 0 || s_chr_notify == 0) {
        ESP_LOGW(TAG, "chars not found (w=%d n=%d)", s_chr_write, s_chr_notify);
        ble_gap_terminate(conn_handle, BLE_ERR_REM_USER_CONN_TERM);
        return;
    }
    uint8_t cccd[2] = { 0x01, 0x00 };
    int rc = ble_gattc_write(conn_handle, (uint16_t)(s_chr_notify + 1), cccd, 2,
                             cccd_cb, NULL);
    if (rc != 0) {
        ESP_LOGW(TAG, "cccd rc=%d", rc);
    }
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
        s_next_state_ms = now_ms();
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

static int disc_gap_cb(struct ble_gap_event *event, void *arg)
{
    switch (event->type) {
    case BLE_GAP_EVENT_DISC: {
        if (s_conn != BLE_HS_CONN_HANDLE_NONE) return 0;
        struct ble_hs_adv_fields fields;
        if (ble_hs_adv_parse_fields(&fields, event->disc.data, event->disc.length) != 0) {
            return 0;
        }
        bool match = false;
        if (fields.name_len >= 4 && memcmp(fields.name, "TBox", 4) == 0) match = true;
        for (int i = 0; i < fields.num_uuids16; i++) {
            if (fields.uuids16[i].value == SVC_UUID) match = true;
        }
        if (!match) return 0;
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
    params.itvl_min = 0x0060; /* 60 ms */
    params.itvl_max = 0x00C0; /* 120 ms */
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
        if (s_subscribed) {
            if (!relay_store_paired() && s_mtu >= (uint16_t)(s_pair_len + 3)) {
                if ((int32_t)(now - s_next_pair_ms) >= 0) {
                    int rc = ble_gattc_write_no_rsp(s_conn, s_chr_write,
                                                    s_pair_pkt, s_pair_len);
                    if (rc != 0) ESP_LOGW(TAG, "pair rc=%d", rc);
                    s_next_pair_ms = now + PAIR_RETRY_MS;
                }
            }
            if (s_mtu >= (uint16_t)(RELAY_PKT_LEN + 3)) {
                bool send = s_state_pending;
                if (!send && relay_store_paired() &&
                    (int32_t)(now - s_next_state_ms) >= 0) {
                    send = true;
                }
                if (send) {
                    s_state_pending = false;
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
