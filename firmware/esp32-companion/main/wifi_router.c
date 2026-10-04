#include "wifi_router.h"

#include <errno.h>
#include <stdint.h>
#include <string.h>

#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "lwip/ip4_addr.h"
#include "lwip/lwip_napt.h"
#include "lwip/sockets.h"
#include "mdns.h"

#include "protocol.h"

static const char *TAG = "wifi_router";

#define AP_SSID "TBox"
#define AP_PSK "tbox8765"
#define PANEL_PORT 8765
#define PROXY_MAX 4

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
static bool s_proxy_started;
static bool s_dns_started;
static uint32_t s_up_dns;
static SemaphoreHandle_t s_proxy_slots;
static int s_panel_port = PANEL_PORT;
static bool s_mdns_ready;
static int s_mdns_port;
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
                            ip, s_freq, s_channel, hu, s_panel_port);
}

static void sync_mdns_port(void)
{
    if (!s_mdns_ready || s_mdns_port == s_panel_port) return;
    if (s_mdns_port != 0) {
        mdns_service_remove("_http", "_tcp");
    }
    if (mdns_service_add(NULL, "_http", "_tcp", s_panel_port, NULL, 0) == ESP_OK) {
        s_mdns_port = s_panel_port;
    }
}

static void enable_napt(void)
{
    if (s_napt || !s_ap || !esp_netif_is_netif_up(s_ap)) return;
    esp_netif_ip_info_t info;
    if (esp_netif_get_ip_info(s_ap, &info) != ESP_OK || info.ip.addr == 0) return;
    /* NAPT flag belongs on the AP. Packets to the AP's own address are then
     * delivered locally, so a port map cannot steal :8765. A TCP proxy does. */
    ip_napt_enable(info.ip.addr, 1);
    s_napt = true;
    ESP_LOGI(TAG, "NAPT on");
}

static void shuttle(int left, int right)
{
    char buf[1024];
    for (;;) {
        fd_set readfds;
        FD_ZERO(&readfds);
        FD_SET(left, &readfds);
        FD_SET(right, &readfds);
        int maxfd = left > right ? left : right;
        struct timeval wait = { .tv_sec = 60, .tv_usec = 0 };
        int ready = select(maxfd + 1, &readfds, NULL, NULL, &wait);
        if (ready <= 0) return;
        int from = -1;
        if (FD_ISSET(left, &readfds)) from = left;
        else if (FD_ISSET(right, &readfds)) from = right;
        if (from < 0) continue;
        int to = from == left ? right : left;
        int got = recv(from, buf, sizeof(buf), 0);
        if (got == 0) return;
        if (got < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) continue;
            return;
        }
        int off = 0;
        while (off < got) {
            int sent = send(to, buf + off, got - off, 0);
            if (sent < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) continue;
            if (sent <= 0) return;
            off += sent;
        }
    }
}

static void proxy_session(void *arg)
{
    int client = (int)(intptr_t)arg;
    int upstream = -1;
    struct sockaddr_in dest = {0};
    dest.sin_family = AF_INET;
    dest.sin_port = htons((uint16_t)s_panel_port);
    dest.sin_addr.s_addr = s_hu_gw.addr;
    if (dest.sin_addr.s_addr != 0) {
        upstream = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    }
    if (upstream >= 0) {
        esp_netif_ip_info_t sta;
        if (s_sta && esp_netif_get_ip_info(s_sta, &sta) == ESP_OK && sta.ip.addr != 0) {
            struct sockaddr_in local = {0};
            local.sin_family = AF_INET;
            local.sin_addr.s_addr = sta.ip.addr;
            bind(upstream, (struct sockaddr *)&local, sizeof(local));
        }
        if (connect(upstream, (struct sockaddr *)&dest, sizeof(dest)) == 0) {
            shuttle(client, upstream);
        } else {
            ESP_LOGW(TAG, "panel upstream failed errno=%d", errno);
        }
    }
    if (upstream >= 0) close(upstream);
    close(client);
    xSemaphoreGive(s_proxy_slots);
    vTaskDelete(NULL);
}

static int open_panel_listener(int port)
{
    int fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (fd < 0) return -1;
    int yes = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));
    struct sockaddr_in addr = {0};
    addr.sin_family = AF_INET;
    addr.sin_port = htons((uint16_t)port);
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    if (bind(fd, (struct sockaddr *)&addr, sizeof(addr)) != 0 || listen(fd, 2) != 0) {
        ESP_LOGE(TAG, "panel listen %d failed", port);
        close(fd);
        return -1;
    }
    ESP_LOGI(TAG, "panel proxy :%d", port);
    return fd;
}

static void proxy_listen(void *arg)
{
    (void)arg;
    int fd = -1;
    int bound = 0;
    for (;;) {
        int want = s_panel_port;
        if (want < 1 || want > 65535) want = PANEL_PORT;
        if (fd < 0 || want != bound) {
            if (fd >= 0) close(fd);
            fd = open_panel_listener(want);
            bound = fd >= 0 ? want : 0;
            if (fd < 0) {
                vTaskDelay(pdMS_TO_TICKS(1000));
                continue;
            }
        }
        fd_set readfds;
        FD_ZERO(&readfds);
        FD_SET(fd, &readfds);
        struct timeval wait = { .tv_sec = 1, .tv_usec = 0 };
        if (select(fd + 1, &readfds, NULL, NULL, &wait) <= 0) continue;
        int client = accept(fd, NULL, NULL);
        if (client < 0) continue;
        if (xSemaphoreTake(s_proxy_slots, 0) != pdTRUE) {
            close(client);
            continue;
        }
        if (xTaskCreate(proxy_session, "panel", 8192, (void *)(intptr_t)client, 4, NULL) != pdPASS) {
            close(client);
            xSemaphoreGive(s_proxy_slots);
        }
    }
}

static uint32_t ap_ipv4(void)
{
    esp_netif_ip_info_t ip;
    if (s_ap && esp_netif_get_ip_info(s_ap, &ip) == ESP_OK && ip.ip.addr != 0) return ip.ip.addr;
    return htonl(0xC0A80401);
}

static bool dns_query(const uint8_t *pkt, int len)
{
    if (len < 12) return false;
    uint16_t flags = ((uint16_t)pkt[2] << 8) | pkt[3];
    if ((flags & 0x8000) != 0 || ((flags >> 11) & 0xF) != 0) return false;
    return pkt[4] == 0 && pkt[5] == 1;
}

static int dns_question_end(const uint8_t *pkt, int len)
{
    int i = 12;
    while (i < len) {
        int lab = pkt[i];
        if (lab == 0) {
            i++;
            break;
        }
        if ((lab & 0xC0) != 0) return -1;
        i += 1 + lab;
    }
    if (i + 4 > len) return -1;
    return i + 4;
}

static bool dns_name_is_panel(const uint8_t *pkt, int len)
{
    char name[64];
    int n = 0;
    int i = 12;
    while (i < len) {
        int lab = pkt[i];
        if (lab == 0) break;
        if ((lab & 0xC0) != 0 || n + 1 + lab >= (int)sizeof(name)) return false;
        if (n > 0) name[n++] = '.';
        memcpy(name + n, pkt + i + 1, (size_t)lab);
        n += lab;
        i += 1 + lab;
    }
    name[n] = 0;
    for (int k = 0; k < n; k++) {
        if (name[k] >= 'A' && name[k] <= 'Z') name[k] = (char)(name[k] - 'A' + 'a');
    }
    return strcmp(name, "tbox.local") == 0 || strcmp(name, "tbox") == 0;
}

static int panel_dns_reply(const uint8_t *query, int qlen, uint8_t *out, int outcap)
{
    if (!dns_query(query, qlen) || !dns_name_is_panel(query, qlen)) return 0;
    int qend = dns_question_end(query, qlen);
    if (qend < 16 || qend > outcap) return 0;
    uint16_t qtype = ((uint16_t)query[qend - 4] << 8) | query[qend - 3];
    memcpy(out, query, (size_t)qend);
    out[2] = 0x81;
    out[3] = 0x80;
    out[6] = 0;
    out[7] = 0;
    out[8] = 0;
    out[9] = 0;
    out[10] = 0;
    out[11] = 0;
    if (qtype == 28) return qend;
    if (qtype != 1 || qend + 16 > outcap) return 0;
    out[7] = 1;
    int o = qend;
    out[o++] = 0xC0;
    out[o++] = 0x0C;
    out[o++] = 0;
    out[o++] = 1;
    out[o++] = 0;
    out[o++] = 1;
    out[o++] = 0;
    out[o++] = 0;
    out[o++] = 0;
    out[o++] = 60;
    out[o++] = 0;
    out[o++] = 4;
    uint32_t ip = ap_ipv4();
    memcpy(out + o, &ip, 4);
    return o + 4;
}

static void forward_dns(int fd, uint8_t *buf, int n, const struct sockaddr_in *from, socklen_t flen)
{
    if (s_up_dns == 0 || n <= 0) return;
    int upstream = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (upstream < 0) return;
    struct timeval tv = { .tv_sec = 2, .tv_usec = 0 };
    setsockopt(upstream, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    struct sockaddr_in up = {0};
    up.sin_family = AF_INET;
    up.sin_port = htons(53);
    up.sin_addr.s_addr = s_up_dns;
    if (sendto(upstream, buf, (size_t)n, 0, (struct sockaddr *)&up, sizeof(up)) == n) {
        int got = recvfrom(upstream, buf, 512, 0, NULL, NULL);
        if (got > 0) sendto(fd, buf, (size_t)got, 0, (struct sockaddr *)from, flen);
    }
    close(upstream);
}

static void dns_task(void *arg)
{
    (void)arg;
    int fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (fd < 0) {
        vTaskDelete(NULL);
        return;
    }
    struct sockaddr_in addr = {0};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(53);
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    if (bind(fd, (struct sockaddr *)&addr, sizeof(addr)) != 0) {
        ESP_LOGW(TAG, "dns bind errno=%d", errno);
        close(fd);
        vTaskDelete(NULL);
        return;
    }
    ESP_LOGI(TAG, "dns tbox.local");
    uint8_t query[512];
    uint8_t reply[512];
    for (;;) {
        struct sockaddr_in from;
        socklen_t flen = sizeof(from);
        int n = recvfrom(fd, query, sizeof(query), 0, (struct sockaddr *)&from, &flen);
        if (n < 12) continue;
        int answered = panel_dns_reply(query, n, reply, sizeof(reply));
        if (answered > 0) {
            sendto(fd, reply, (size_t)answered, 0, (struct sockaddr *)&from, flen);
            continue;
        }
        forward_dns(fd, query, n, &from, flen);
    }
}

static void start_dns(void)
{
    if (s_dns_started) return;
    if (xTaskCreate(dns_task, "dns", 4096, NULL, 3, NULL) == pdPASS) s_dns_started = true;
}

static void refresh_upstream_dns(void)
{
    esp_netif_dns_info_t dns;
    if (!s_sta) return;
    if (esp_netif_get_dns_info(s_sta, ESP_NETIF_DNS_MAIN, &dns) == ESP_OK) {
        s_up_dns = dns.ip.u_addr.ip4.addr;
    }
}

static void start_proxy(void)
{
    if (s_proxy_started) return;
    s_proxy_slots = xSemaphoreCreateCounting(PROXY_MAX, PROXY_MAX);
    if (!s_proxy_slots) return;
    if (xTaskCreate(proxy_listen, "panel_l", 4096, NULL, 4, NULL) == pdPASS) {
        s_proxy_started = true;
    }
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
            enable_napt();
            start_proxy();
            start_dns();
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
    enable_napt();
    start_proxy();
    start_dns();
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
    refresh_upstream_dns();
    if (s_ap_started) enable_napt();
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
        s_mdns_ready = true;
        sync_mdns_port();
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

void wifi_router_request(bool on, const char *hu_ssid, const char *hu_psk, int port)
{
    if (!s_q) return;
    if (port >= 1 && port <= 65535 && port != s_panel_port) {
        s_panel_port = port;
        sync_mdns_port();
        publish();
    }
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
