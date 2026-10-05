# Кандидаты Wi‑Fi модемов (открытые API)

Каталог внешних reverse-engineered HTTP API, пригодных для расширения
`ModemSource.WIFI_HTTP` / `WifiModemModel`. Это **не** официальные SDK: поля и
логин плавают между прошивками — перед статусом VERIFIED нужен живой HAR
(как для Olax F95 / ZTE MF79U / Huawei E3372).

Уже в коде: [WIFI_MODEM_OLAX_F95_RU.md](./WIFI_MODEM_OLAX_F95_RU.md),
[WIFI_MODEM_ZTE_MF79U_RU.md](./WIFI_MODEM_ZTE_MF79U_RU.md),
[WIFI_MODEM_HUAWEI_E3372_RU.md](./WIFI_MODEM_HUAWEI_E3372_RU.md).

Сложность: **S** — расширить существующий драйвер; **M** — новый драйвер на
известном стеке; **L** — новый протокол / шифрование.

## Приоритет 1 — тот же стек, что уже есть

| Модель / линейка | Стек | Оценка | Открытые источники | Заметки |
|------------------|------|--------|--------------------|---------|
| **Olax M100** и др. Olax MiFi | `/reqproc` | **S** | [routspan](https://github.com/ajshovon/routspan) → `docs/olax-m100-api.md`; [Habr SMS/Olax](https://habr.com/ru/articles/974398/) | Часто Base64 LOGIN как F95; иногда LD/RD/AD. Probe диалекта + фикстуры |
| **ZLT / MTN CPE** (NV8645 и rebrand) | `/reqproc` + CSRF | **S–M** | [zltrouter](https://github.com/exbyte-dev/zltrouter) → `docs/protocol.md` | Nonce SHA-256 LOGIN, cookie `random`, `CSRFToken`. Каркас auth уже в `ZteReqprocAuth` |
| Другие **ZTE UFI goform** (MF79*, MF83*, …) | `/goform/…` | **S** | Сверка с MF79U HAR; JS админки устройства | Тот же `ZteGoformClient`; проверить LD LOGIN и списки `cmd=` |
| **Huawei HiLink** E5573 / E8372 / B3xx / B5xx | `/api/…` XML | **S** | [huawei-lte-api](https://github.com/Salamek/huawei-lte-api); [e3372-hilink-api-docs](https://github.com/weselow/e3372-hilink-api-docs); [hilinkapi](https://github.com/ezbik/hilinkapi) | Расширить `WifiModemModel` + smoke на `/api/device/signal`, dataswitch. Часть моделей требует login WebUI 10/17/21 |

## Приоритет 2 — новый драйвер, хорошая документация

| Модель / линейка | Стек | Оценка | Открытые источники | Заметки |
|------------------|------|--------|--------------------|---------|
| **Alcatel / TCL Linkhub** MW40 / MW41 / HH40 / HH72 / HH70 | JSON-RPC `POST /jrd/webapi` | **M** | [alcatel-modem-api](https://github.com/volkanncicek/alcatel-modem-api); [Alcatel_HH72](https://github.com/spolette/Alcatel_HH72); [gist HH40v](https://gist.github.com/lukpueh/a595f74d8edfb512d4f5be7056dfdb1e) | Популярны в РФ. Методы: `GetSystemStatus`, `GetNetworkInfo`, … Заголовок `_TclRequestVerificationKey`. login plain / encrypted по модели |

## Приоритет 3 — по спросу

| Модель / линейка | Стек | Оценка | Открытые источники | Заметки |
|------------------|------|--------|--------------------|---------|
| **TP-Link MiFi** M7350 / M7450 / M7650 | `cgi-bin/web_cgi` + шифр. JSON | **L** | [tplink_m7350_cpp](https://github.com/vpaeder/tplink_m7350_cpp) | Отдельный протокол (salt/login). Имеет смысл только при явном запросе пользователей |
| **Keenetic 4G** (Hero 4G+ и т.п.) | RCI `/rci/` | **L** | [keenetic-rci](https://github.com/hexqnt/keenetic-rci) | Это роутер с LTE, не классический USB MiFi; другой UX настроек |

## Что не брать как «готовые открытые данные»

- Официальные приложения вендоров без разбора трафика (закрытый протокол).
- AT-команды по USB serial — другой путь, не `WIFI_HTTP` (ГУ обычно видит только Wi‑Fi модема).
- Случайные форумные сниппеты без списка полей / примеров ответа — только как наводка на HAR.

## Рекомендуемый порядок внедрения

1. **Probe `reqproc` диалектов** (M100 / ZLT) на одном клиенте с автоопределением LOGIN → новые `WifiModemModel` без второго поллера.
2. **Каталог HiLink** (E5573, E8372…) поверх `HuaweiHilinkClient`.
3. **Alcatel `/jrd/webapi`** — новый `AlcatelJrdClient` + enum + UI model picker.
4. TP-Link / Keenetic — только по backlog-запросу.

## Чеклист VERIFIED для новой модели

1. HAR (логин + статус home/about + data on/off + reboot), обезличенный.
2. Фикстуры в `app/src/test/resources/wifimodem/<model>/`.
3. Unit-тесты маппинга → `NetState` / thrpt / dBm / IMEI.
4. Запись в `docs/WIFI_MODEM_<MODEL>_RU.md` + строка в этом файле → «в коде».
5. Пункт в [BACKLOG.md](./BACKLOG.md) S-02 / подзадачи.
