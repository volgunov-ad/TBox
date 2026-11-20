# Окно приложений штатного лаунчера A9 (`com.wt.launcher3`)

Как штатный лаунчер WT на Android 9 решает, открыть activity на основном дисплее или в своём окне приложений, и почему корневой скрипт через `mb_daemon` сам по себе это не переключает.

Источник: статический разбор снятого с ГУ `WT_Launcher3` **2.1.0.ov** (`versionCode` 20005000, `/system/app/WT_Launcher3`) и парного `WT_Launcher3.vdex`. Код приложения лежит во втором compact-dex vdex. Образ `system.img` с лаунчером **1.0.2.bk** — другая сборка, ниже не используется. Обёртка сервиса на машине не проверялась (ноябрь 2026).

Связанные доки: [STOCK_APPS_ANDROID9_MBCAN_RU.md](STOCK_APPS_ANDROID9_MBCAN_RU.md), [BOOT_AUTOSTART_DEVICE_RU.md](BOOT_AUTOSTART_DEVICE_RU.md) (скрытие лаунчера), [HEAD_UNITS_DEVICE_NOTES_RU.md](HEAD_UNITS_DEVICE_NOTES_RU.md) §10 (`mb_daemon`), окно A10 — [LAUNCHER_WINDOW_AA_CARPLAY_RU.md](LAUNCHER_WINDOW_AA_CARPLAY_RU.md).

## 1. Что лаунчер отдаёт наружу

`com.wt.launcher3`, `sharedUserId=android.uid.system`, флаг `persistent`. В манифесте 2.1.0.ov:

| Компонент | Наружу |
|-----------|--------|
| `com.wt.launcher3.MainActivity` | HOME (`MAIN` / `HOME` / `DEFAULT` и action `com.wt.launcher3.HOME`), `launchMode=singleInstance`. Своя meta-data `flagActivityFullScreen=true` |
| `com.wt.launcher3.applist.classical.AppListActivity` | без intent-filter |
| `com.wt.launcher3.provider.MyProvider` | exported, authority `com.wt.launcher3` |
| два `BroadcastReceiver` Tencent WeCar | action `WECARSPEECH_VFRAMEWORK_BROADCAST`, со своим permission |

Отдельного Android-сервиса в манифесте нет, другие приложения к лаунчеру не биндятся. Выбор дисплея для чужих activity идёт через binder, который процесс регистрирует в `ServiceManager` (раздел 2).

Пока процесс лаунчера не запущен (`pm hide` после `pm enable`, см. [BOOT_AUTOSTART_DEVICE_RU.md](BOOT_AUTOSTART_DEVICE_RU.md)), `system_server` пишет `ActivityStarterPlugs: startActivity: getService start_activity is null`, и activity остаётся на дисплее 0.

## 2. Сервис `start_activity`

Класс `com.wt.launcher3.wtutils.ActivityStartCallback` в конструкторе делает `ServiceManager.addService("start_activity", this)`. Это реализация AIDL `com.wt.utils.IStartActivityCallback`:

| Транзакция | Код | Кто вызывает |
|------------|-----|----------------|
| `onActivityStart(Bundle) → Bundle` | 1 | `ActivityStarterPlugs` в `system_server` на уже начатом `startActivity` |
| `onLaunchVirtualActivity(Bundle)` | 2 | тот же фреймворк, когда старт идёт в виртуальный дисплей окна |

Сервис ничего не запускает. Команды «открой пакет» у него нет. `am start` из root-скрипта `mb_daemon` проходит через тот же `ActivityStarterPlugs`: корень не обходит выбор дисплея.

### 2.1. `onActivityStart`

Из `Bundle` читается `componentName`. Обратно в тот же `Bundle` пишется `displayId`:

- **0** — основной дисплей, если пакет есть в списке пакетов полного экрана **или** `ComponentName` есть в списке полноэкранных activity;
- иначе id виртуального дисплея, у которого `Display.getName()` совпадает с `normal_app_display_name` (константа `TypeConstants.APP_DISPLAY_NAME`). Если таких дисплеев несколько, берётся последний. Если нет ни одного, в `Bundle` уходит `-1`.

Пустой `Bundle` или `componentName == null` — метод возвращает `null`.

Список пакетов полного экрана зашит и содержит один пакет: `com.changba.sd`.

Список activity собирает `ParseManager.getFullScreenActivities()`:

1. Обход установленных пакетов (`GET_ACTIVITIES | GET_RECEIVERS | GET_META_DATA`). Activity с meta-data `flagActivityFullScreen=true`, которую `MyUtil.checkActExit` считает живой, попадает в список. В лог пишется «全屏的Activity配置».
2. К списку добавляются три зашитых компонента:
   - `com.mengbo.factory` / `com.mengbo.factory.activity.MainActivity`
   - `com.tencent.wecarmas` / `com.tencent.wecar.MainActivity`
   - `com.bytedance.byteautoservice` / `com.bytedance.auto.lite.activity.JetourMainActivity`

Всё остальное уходит в окно приложений лаунчера, а не на дисплей 0. Свой `MainActivity` лаунчера тоже помечен `flagActivityFullScreen`, поэтому сам рабочий стол на виртуальный дисплей не уезжает.

Дисплей 0 — это полный экран фреймворка. При живом SystemUI сверху остаётся статусбар: в логах загрузки `mAppBounds` основного дисплея `1920×981` при экране `1920×1080`.

### 2.2. `onLaunchVirtualActivity`

Лаунчер вызывает `MyUtil.goHome()` и кладёт extra `launchIntent` из `Bundle` в `ControlManager.appIntent`. Дальше `WTActivityViews.startActivity` подставляет этот `Intent` в `ActivityView` окна (`normal_app_display_name`), если компонент совпал. Без этого вызова у живого лаунчера окно приложений жест запуска не получает.

Виртуальный дисплей существует, только пока процесс лаунчера держит свой `ActivityView`.

## 3. Карточки рабочего стола — не полный экран

Сетка карточек: клетка **176×152 dp**, зазор **32 dp** (`ControlManager`: `WIDTH_1_1` / `HEIGHT_1_1` / `WIDGET_SPLIT`). Имя типа `card_Ш_В` — ширина в колонках и высота в рядах. Колонок в типе бывает 1, 2, 3 или 6; рядов — 1, 2 или 3. Самый большой тип, `card_6_3`, занимает всю сетку карточек (6×3).

Это область карточек на домашнем экране. Статусбар и боковая колонка (маршрут, ADAS, медиа — типы `tbt_widget`, `adas_widget`, `media_widget`) этим размером не закрываются.

Exported-провайдер принимает `call("resizeTask", …, bundle)` на authority `com.wt.launcher3`. Extra:

| Ключ | Смысл |
|------|--------|
| `packageName` | пакет виджета, который уже стоит на столе |
| `activityClassName` | его activity |
| `featureId` | см. таблицу |

| `featureId` | Имя в коде | Действие |
|-------------|------------|----------|
| 30 (`0x1e`) | `FEATURE_ID_RESIZE_TASK_RESET_3_0` | вернуть прежний прямоугольник, если карточка была растянута |
| 31 (`0x1f`) | `FEATURE_ID_RESIZE_TASK_3_3` | растянуть к 3×3 (половина сетки по ширине) |
| 32 (`0x20`) | `FEATURE_ID_RESIZE_TASK_6_3` | растянуть к 6×3 (вся сетка карточек) |

`ControlManager.getExtendNewRect` урезает запрошенный span, если клеток справа или снизу от текущей позиции в сетке нет. Провайдер ищет виджет в модели стола. Чужой пакет не находится — метод возвращает пустой `Bundle` и ничего не двигает. Жест уходит локальным `LocalBroadcastManager` (`action_request_resize`, extra `newRect` и `componentName`), это не системный broadcast.

В конфигурации виджетов стола (`ControlManager.sWidgetsConfings`) лежат свои activity, не произвольные пакеты: список приложений `com.android.launcherWT`, дорожная книга, мини-программы, медиакарточка `com.wt.multimedia.local`, журнал звонков, климат, личный кабинет. Растягивание карточки произвольное приложение не открывает.

## 4. Как выбрать полный экран или окно для любого приложения

Штатный binder командой не переключается. Подмена сервиса «всегда `displayId = 0`» даёт только полный экран и ломает окно: `onLaunchVirtualActivity` перестаёт доходить до лаунчера.

Рабочая схема — обёртка, которая держит прежний binder и сама регистрируется как `start_activity` (после старта лаунчера; SELinux на A9 Permissive, регистрация из root через `mb_daemon`):

1. Забрать текущий binder `start_activity`.
2. Команда с нашей стороны только запоминает режим следующего компонента: дисплей 0 или `normal_app_display_name`. Сам запуск — обычный `am start` / `startActivity`.
3. На `onActivityStart` для этого компонента подставить нужный `displayId`. Остальные старты отдать лаунчеру без изменений, чтобы его белый список и домашний экран не разъехались.
4. `onLaunchVirtualActivity` всегда переслать в лаунчер.

Лаунчер при своём старте снова делает `addService` и затирает обёртку. После перезапуска процесса её нужно ставить заново. Оконный режим имеет смысл только пока лаунчер жив и дисплей `normal_app_display_name` есть в `DisplayManager`. Ограничения activity те же, что у штатного окна: `launchMode`, отказ хоста виртуального дисплея и тому подобное обёртка не снимает.
