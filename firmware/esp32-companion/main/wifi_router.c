#include "wifi_router.h"

#include <string.h>

#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "lwip/ip4_addr.h"
#include "lwip/lwip_napt.h"
#include "mdns.h"

#include "protocol.h"

static const char *TAG = "wifi_router";

#define AP_SSID "TBox"
#define AP_PSK "tbox8765"
#define PANEL_PORT 8765
#define IP_PROTO_TCP 6

typedef struct {
    bool on;
    char hu_ssid[33];
    char hu_psk[64];
    uint8_t follow_channel;
} wifi_job_t;

static char s_hu_ssid[33];
static char s_hu_psk[64];

static QueueHandle_t s_q;
static esp_netif_t *s_ap;
static esp_netif_t *s_sta;
static bool s_wifi_up;
static bool s_ap_started;
static bool s_hold_reconnect;
static bool s_want_sta;
static bool s_sta_up;
static bool s_napt;
static bool s_portmap;
static int s_freq;
static int s_channel;
static esp_ip4_addr_t s_hu_gw;

static int freq_mhz(int channel)
{
    if (channel <= 0) return 0;
    if (channel == 14) return 2484;
    return 2407 + channel * 5;
}

static void publish(void)
{
    char ip[16] = "";
    char hu[16] = "";
    if (s_ap) {
        esp_netif_ip_info_t info;
        if (esp_netif_get_ip_info(s_ap, &info) == ESP_OK) {
            esp_ip4addr_ntoa(&info.ip, ip, sizeof(ip));
        }
    }
    if (s_sta_up && s_hu_gw.addr != 0) {
        esp_ip4addr_ntoa(&s_hu_gw, hu, sizeof(hu));
    }
    protocol_send_ap_status(s_wifi_up && s_want_sta, s_sta_up, AP_SSID, AP_PSK,
                            ip, s_freq, s_channel, hu, PANEL_PORT);
}

static void apply_portmap(void)
{
    if (!s_ap || s_hu_gw.addr == 0) return;
    esp_netif_ip_info_t info;
    if (esp_netif_get_ip_info(s_ap, &info) != ESP_OK) return;
    if (!s_napt) {
        ip_napt_enable(info.ip.addr, 1);
        s_napt = true;
    }
    if (s_portmap) {
        ip_portmap_remove(IP_PROTO_TCP, PANEL_PORT);
    }
    ip_portmap_add(IP_PROTO_TCP, info.ip.addr, PANEL_PORT, s_hu_gw.addr, PANEL_PORT);
    s_portmap = true;
    ESP_LOGI(TAG, "DNAT %d -> head unit", PANEL_PORT);
}

static void on_wifi(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    (void)arg;
    (void)base;
    if (id == WIFI_EVENT_STA_DISCONNECTED) {
        s_sta_up = false;
        s_freq = 0;
        s_channel = 0;
        if (s_want_sta && !s_hold_reconnect) {
            esp_wifi_connect();
        }
        publish();
    } else if (id == WIFI_EVENT_STA_CONNECTED && data) {
        const wifi_event_sta_connected_t *event = data;
        s_channel = event->channel;
        s_freq = freq_mhz(event->channel);
        if (!s_ap_started && event->channel > 0 && s_q) {
            wifi_job_t job = {0};
            job.follow_channel = event->channel;
            xQueueSend(s_q, &job, 0);
        }
        publish();
    }
}

static void fill_ap_config(wifi_config_t *ap, uint8_t channel)
{
    memset(ap, 0, sizeof(*ap));
    strncpy((char *)ap->ap.ssid, AP_SSID, sizeof(ap->ap.ssid) - 1);
    ap->ap.ssid_len = strlen(AP_SSID);
    strncpy((char *)ap->ap.password, AP_PSK, sizeof(ap->ap.password) - 1);
    ap->ap.channel = channel;
    ap->ap.max_connection = 4;
    ap->ap.authmode = WIFI_AUTH_WPA2_PSK;
}

/** One radio: the SoftAP has to beacon on the station channel. Called from the worker, not the event task. */
static void start_softap(uint8_t channel)
{
    if (channel == 0 || !s_wifi_up) return;
    wifi_config_t ap;
    fill_ap_config(&ap, channel);
    if (!s_ap_started) {
        esp_err_t err = esp_wifi_set_mode(WIFI_MODE_APSTA);
        if (err != ESP_OK) {
            ESP_LOGW(TAG, "apsta %s, restarting wifi", esp_err_to_name(err));
            wifi_config_t sta = {0};
            strncpy((char *)sta.sta.ssid, s_hu_ssid, sizeof(sta.sta.ssid) - 1);
            strncpy((char *)sta.sta.password, s_hu_psk, sizeof(sta.sta.password) - 1);
            sta.sta.threshold.authmode = WIFI_AUTH_WPA2_PSK;
            s_hold_reconnect = true;
            esp_wifi_stop();
            ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_APSTA));
            ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP, &ap));
            ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &sta));
            ESP_ERROR_CHECK(esp_wifi_start());
            s_ap_started = true;
            s_hold_reconnect = false;
            esp_wifi_connect();
            return;
        }
        s_ap_started = true;
    }
    esp_err_t err = esp_wifi_set_config(WIFI_IF_AP, &ap);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "ap config %s", esp_err_to_name(err));
        return;
    }
    s_channel = channel;
    s_freq = freq_mhz(channel);
    if (s_sta_up) apply_portmap();
    ESP_LOGI(TAG, "SoftAP %s ch %u", AP_SSID, channel);
}

static void on_ip(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    (void)arg;
    (void)base;
    if (id != IP_EVENT_STA_GOT_IP || !data) return;
    const ip_event_got_ip_t *event = data;
    s_hu_gw = event->ip_info.gw;
    s_sta_up = true;
    if (s_ap_started) apply_portmap();
    publish();
}

static void ensure_wifi(void)
{
    if (s_wifi_up) return;
    esp_err_t err = esp_netif_init();
    if (err != ESP_OK && err != ESP_ERR_INVALID_STATE) {
        ESP_LOGE(TAG, "netif %s", esp_err_to_name(err));
        return;
    }
    err = esp_event_loop_create_default();
    if (err != ESP_OK && err != ESP_ERR_INVALID_STATE) {
        ESP_LOGE(TAG, "event loop %s", esp_err_to_name(err));
        return;
    }
    s_ap = esp_netif_create_default_wifi_ap();
    s_sta = esp_netif_create_default_wifi_sta();
    if (!s_ap || !s_sta) {
        ESP_LOGE(TAG, "netif create failed");
        return;
    }
    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));
    ESP_ERROR_CHECK(esp_event_handler_instance_register(
        WIFI_EVENT, ESP_EVENT_ANY_ID, on_wifi, NULL, NULL));
    ESP_ERROR_CHECK(esp_event_handler_instance_register(
        IP_EVENT, IP_EVENT_STA_GOT_IP, on_ip, NULL, NULL));
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_set_ps(WIFI_PS_NONE));
    ESP_ERROR_CHECK(esp_wifi_start());

    if (mdns_init() == ESP_OK) {
        mdns_hostname_set("tbox");
        mdns_instance_name_set("TBox");
        mdns_service_add(NULL, "_http", "_tcp", PANEL_PORT, NULL, 0);
    }
    s_wifi_up = true;
    ESP_LOGI(TAG, "STA ready");
}

static void connect_sta(const char *ssid, const char *psk)
{
    strncpy(s_hu_ssid, ssid, sizeof(s_hu_ssid) - 1);
    s_hu_ssid[sizeof(s_hu_ssid) - 1] = '\0';
    strncpy(s_hu_psk, psk, sizeof(s_hu_psk) - 1);
    s_hu_psk[sizeof(s_hu_psk) - 1] = '\0';
    wifi_config_t sta = {0};
    strncpy((char *)sta.sta.ssid, s_hu_ssid, sizeof(sta.sta.ssid) - 1);
    strncpy((char *)sta.sta.password, s_hu_psk, sizeof(sta.sta.password) - 1);
    sta.sta.threshold.authmode = WIFI_AUTH_WPA2_PSK;
    esp_wifi_set_config(WIFI_IF_STA, &sta);
    s_want_sta = true;
    s_sta_up = false;
    esp_wifi_disconnect();
    esp_wifi_connect();
}

static void handle_job(const wifi_job_t *job)
{
    if (job->follow_channel) {
        start_softap(job->follow_channel);
        publish();
        return;
    }
    if (!job->on) {
        s_want_sta = false;
        if (s_wifi_up) {
            esp_wifi_disconnect();
        }
        s_sta_up = false;
        s_freq = 0;
        s_channel = 0;
        s_hu_gw.addr = 0;
        publish();
        return;
    }
    if (job->hu_ssid[0] == '\0' || strlen(job->hu_psk) < 8) {
        ESP_LOGW(TAG, "reject apCfg");
        publish();
        return;
    }
    ensure_wifi();
    if (!s_wifi_up) {
        publish();
        return;
    }
    connect_sta(job->hu_ssid, job->hu_psk);
    publish();
}

static void worker(void *arg)
{
    (void)arg;
    wifi_job_t job;
    while (1) {
        if (xQueueReceive(s_q, &job, portMAX_DELAY) == pdTRUE) {
            handle_job(&job);
        }
    }
}

void wifi_router_init(void)
{
    s_q = xQueueCreate(4, sizeof(wifi_job_t));
    configASSERT(s_q);
    xTaskCreate(worker, "wifi_router", 12288, NULL, 5, NULL);
}

void wifi_router_request(bool on, const char *hu_ssid, const char *hu_psk)
{
    if (!s_q) return;
    wifi_job_t job = {0};
    job.on = on;
    if (hu_ssid) {
        strncpy(job.hu_ssid, hu_ssid, sizeof(job.hu_ssid) - 1);
    }
    if (hu_psk) {
        strncpy(job.hu_psk, hu_psk, sizeof(job.hu_psk) - 1);
    }
    if (xQueueSend(s_q, &job, 0) != pdTRUE) {
        ESP_LOGW(TAG, "queue full");
    }
}
