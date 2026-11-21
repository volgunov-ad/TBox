# Магазин приложений ГУ — план

Статус: **в разработке** (MVP: модуль `:store`, каталог, установка Intent, кнопка из Monitor OTA).

Отдельное Android-приложение — витрина и установщик экосистемы TBox на ГУ Jetour
Dashing (A9 mbCAN / A10 VHAL). Хостинг артефактов: **Яндекс.Диск** (те же каналы
`release` / `dev`, что OTA Monitor) с **зеркалом на облаке Mail.ru**.

Связанные документы:

| Документ | Роль |
|----------|------|
| [ROAD_MAPS_HOSTING_RU.md](ROAD_MAPS_HOSTING_RU.md) | Публичный share, `yandex-disk:`, `catalog.json` |
| [USER_GUIDE_RU.md](USER_GUIDE_RU.md) | Текущая OTA-вкладка Monitor |
| [HEAD_UNITS_DEVICE_NOTES_RU.md](HEAD_UNITS_DEVICE_NOTES_RU.md) | Файрвол A10, `/data`, dexopt |
| [SYSTEMUI_A9_DISABLE_RU.md](SYSTEMUI_A9_DISABLE_RU.md) | DCLInstall / UsbMount / окружение A9 |
| [MQTT_APK_RU.md](MQTT_APK_RU.md), [CAMD_RU.md](CAMD_RU.md), [VOICE_APK_RU.md](VOICE_APK_RU.md) | Сайдлоад-приложения без своей OTA |
| [BRANCHING.md](BRANCHING.md) | Ветки `preRelease` / `master` |

---

## 0. Зафиксированные решения

| Тема | Решение |
|------|---------|
| Форма | **Отдельный APK**, не вкладка внутри Monitor |
| Имя / package | **TBox Store** / `vad.dashing.store` (модуль `:store`) |
| Каналы | Имена **release / development** как у OTA Monitor; отдельный share AppStore |
| Public key магазина | `https://disk.yandex.ru/d/KDaHLxbFtPAjYA` (`BuildConfig.STORE_PUBLIC_KEY`) — **не** ключи OTA Monitor |
| Зеркало Mail.ru | `https://cloud.mail.ru/public/voi3/4Lx1RhqV9` (`STORE_MAILRU_PUBLIC_URL`, weblink `voi3/4Lx1RhqV9`) |
| Раскладка на share | `/release/apps/…`, `/dev/apps/…` (каталог: `/release/apps/catalog.json`) — **одинаково** на Яндексе и Mail.ru |
| Хостинг | Яндекс.Диск primary; Mail.ru Cloud — **зеркало 1:1** той же раскладки |
| Состав каталога | Приложения экосистемы **и сторонние проверенные** APK |
| Лаунчер DCL | Пакет `ras.dashing.tbox.launcher` **не собирается** в этом репо; разработчику даём доступ к папке публикации на Диске |
| DCLInstall | Остаётся отдельным инструментом окружения A9; магазин **раздаёт** его APK, но не повторяет hide SystemUI / dealer mode |
| OTA Monitor | На переходный период **сохраняется**; позже может читать тот же `apps/tbox-monitor/version.json` или открывать магазин |
| Платежи / аккаунты / отзывы | **Вне скоупа** |
| RuStore | Не цель; свой каталог |

---

## 1. Зачем отдельное приложение

Сейчас Monitor обновляется сам через `/version.json` на Яндекс.Диске
(`app/.../update/`: `UpdateRepository`, `YandexDiskClient`, `ApkInstaller` через
`Intent` + FileProvider). MQTT, camd, voice, DCLInstall, утилиты A10 — только
сайдлоад. Картпакеты уже живут рядом в `release/maps/catalog.json`.

Магазин закрывает разрыв: один UI для списка, скачивания, проверки и установки
нескольких пакетов + проверенных сторонних, без раздувания Monitor и без
дублирования OTA-логики в каждом APK.

---

## 2. Цели MVP

1. На ГУ открыть магазин → увидеть каталог (с фильтром A9/A10).
2. Скачать APK с Яндекс.Диска → проверить sha256 (+ packageName, опционально cert) → установить.
3. Показать «установлено» / «есть обновление» / «не для этой ГУ».
4. Канал release / development — те же public key, что у Monitor.
5. В каталоге: Monitor, MQTT, camd, DCLInstall, лаунчер DCL + минимум один слот под стороннее проверенное приложение (схема полей готова сразу).
6. Bootstrap: первая установка магазина с USB / из инструкции; из Monitor — кнопка «Все приложения» (если магазин уже стоит).

Вне MVP (фаза 2+):

- автоматический fallback на Mail.ru при сбое Яндекса (зеркало готовить можно раньше);
- voice, dataresize, usbaudio, fwunlock, winpatch в витрине;
- тихие фоновые проверки обновлений;
- свёртка OTA-вкладки Monitor в «открыть магазин»;
- root-`pm install` / авто-dexopt как в DCLInstall (MVP — Intent + подсказка про compile).

---

## 3. Архитектура

```text
Яндекс.Диск AppStore share  https://disk.yandex.ru/d/KDaHLxbFtPAjYA
  /release/apps/catalog.json ──► Store APK (канал Релиз)
  /release/apps/<id>/version.json + *.apk
  /dev/apps/…                 ──► канал Разработка
         │
         ├─ download (YandexDiskClient / public API)
         ├─ verify (sha256, packageName, signingCertSha256?)
         ├─ install (Intent + FileProvider; REQUEST_INSTALL_PACKAGES)
         └─ optional: dexopt speed (фаза 2 / root)

Mail.ru Cloud ── https://cloud.mail.ru/public/voi3/4Lx1RhqV9
               зеркало 1:1 (weblink `voi3/4Lx1RhqV9`; listing API
               `GET https://cloud.mail.ru/api/v2/folder?weblink=…`)
```

OTA Monitor по-прежнему на своих public key (`version.json` в корне `release/` /
`dev/` синхронизации TBM). Магазин их не читает.

Клиентский fallback: `StoreCloudFacade` + `MailRuCloudClient` (weblink
`voi3/4Lx1RhqV9`). В UI магазина источник: Авто (Яндекс→Mail.ru) / только Яндекс /
только Mail.ru.

Переиспользовать из Monitor (вынести в общую библиотеку или скопировать тонкий
слой в `:store` на первом шаге, рефакторинг — следом):

| Компонент | Сейчас | Роль в магазине |
|-----------|--------|-----------------|
| `YandexDiskClient` | `update/` | Download + metadata |
| `UpdateManifest` / `version.json` | один пакет Monitor | Per-app манифест |
| `ApkVerifier` | sha256 + package | То же + cert whitelist |
| `ApkInstaller` | Intent | То же |
| `InstallPermissionHelper` | unknown sources | То же |
| `RoadMapRemoteUrl` (`yandex-disk:`) | карты | Пути к APK/иконкам в catalog |

DCLInstall (`tools/dcl_install/`) **не** становится магазином: там hardcoded
пакет лаунчера, root `pm install`, hide SystemUI, dealer mode. Магазин ставит
свежий DCLInstall; пользователь запускает его для окружения.

---

## 4. Раскладка на Диске

Отдельный публичный корень **AppStore** (не папка OTA Monitor):

```text
AppStore/                         # https://disk.yandex.ru/d/KDaHLxbFtPAjYA
  release/
    apps/
      catalog.json                # витрина
      catalog.meta.json           # опционально для tools/build_apps_catalog.py
      tbox-monitor/
        version.json
        tbox_monitor-….apk
      mqtt/
        version.json
        tbox_mqtt-….apk
      camd/
      dclinstall/
      dcl-launcher/               # кладёт разработчик лаунчера
      <third-party-id>/
  dev/
    apps/…                        # та же схема
```

Legacy OTA Monitor (`UPDATE_*_PUBLIC_KEY`, корневые `version.json` + APK + `maps/`)
не трогаем. Зеркало Mail.ru: та же относительная структура. В `catalog.json`
логические URL вида `yandex-disk:/apps/<id>/version.json`; клиент добавляет
префикс `/release` или `/dev` по выбранному каналу.

### 4.1. `apps/catalog.json` (черновик схемы)

```json
{
  "schemaVersion": 1,
  "publishedAt": "2026-11-21T12:00:00Z",
  "apps": [
    {
      "id": "tbox-monitor",
      "packageName": "vad.dashing.tbox",
      "title": "TBox Monitor",
      "summary": "Телеметрия, поездки, панели, CAN",
      "publisher": "TBox",
      "origin": "first-party",
      "hu": ["a9", "a10"],
      "category": "app",
      "verified": true,
      "icon": "yandex-disk:/apps/tbox-monitor/icon.png",
      "manifest": "yandex-disk:/apps/tbox-monitor/version.json",
      "signingCertSha256": ["…"],
      "minStoreVersionCode": 1
    },
    {
      "id": "example-nav",
      "packageName": "com.example.nav",
      "title": "Пример навигации",
      "summary": "Стороннее, проверенное вручную",
      "publisher": "Vendor Name",
      "origin": "third-party",
      "hu": ["a9", "a10"],
      "category": "app",
      "verified": true,
      "manifest": "yandex-disk:/apps/example-nav/version.json",
      "signingCertSha256": ["…"],
      "reviewNotes": "Проверено на A9/A10: сеть, тач, не ломает HOME"
    }
  ]
}
```

| Поле | Смысл |
|------|--------|
| `origin` | `first-party` \| `third-party` |
| `verified` | В MVP в каталог попадают **только** `verified: true`; непроверянное не публикуем |
| `hu` | Фильтр по типу ГУ (`a9` / `a10`) |
| `category` | `app` \| `tool` \| `oneshot` (утилиты вроде dataresize — фаза 2) |
| `signingCertSha256` | Белый список отпечатков; несовпадение → отказ / явное предупреждение |
| `manifest` | Путь к per-app `version.json` (тот же формат, что `UpdateManifest`) |

Per-app `version.json`: как сейчас у Monitor (`schemaVersion`, `releases[]` с
`versionCode`, `versionName`, `flavor` при необходимости, `apkFileName`,
`sha256`, `apkSizeBytes`, `changelog`, `publishedAt`,
`minSupportedVersionCode`). Для приложений без ru/en flavor — одна запись
`flavor: "default"` или ослабить требование flavor в общем парсере магазина.

### 4.2. Сторонние проверенные приложения

- В каталог только после ручной проверки на целевых ГУ (сеть/файрвол A10,
  тач, не перехват HOME, размер, подпись стабильна).
- Отдельный `id` и папка `apps/<id>/`; APK и `version.json` кладёт куратор
  каталога (не обязательно автор приложения).
- Поля `publisher`, `reviewNotes`, `verified` обязательны для `third-party`.
- Обновления стороннего APK с **другой** подписью → как сейчас у DCLInstall:
  только через удаление (данные пользователя предупредить).
- Не цель MVP: произвольная загрузка APK пользователем «с URL».

### 4.3. Лаунчер DCL и доступ разработчику

- Пакет: `ras.dashing.tbox.launcher`.
- Публикация: папка `apps/dcl-launcher/` на обоих каналах (или только
  `release/`, если dev ведёт сам разработчик в `dev/apps/dcl-launcher/`).
- Разработчику лаунчера выдаётся доступ к соответствующим папкам Яндекс.Диска
  (и при зеркале — к Mail.ru), чтобы он сам выкладывал APK + обновлял
  `version.json` по согласованному шаблону.
- Куратор каталога добавляет/правит запись в `apps/catalog.json` (или
  доверяет скрипту, который подхватывает наличие `version.json`).
- Совместимость подписи с уже установленным лаунчером на машинах пользователей
  — ответственность публикатора; в `signingCertSha256` фиксируем ожидаемые
  отпечатки (в т.ч. смена en/ru ключей, известная из [HU_PERF_TESTS_RU.md](HU_PERF_TESTS_RU.md)).

---

## 5. Клиент магазина

### 5.1. UI MVP

1. Список приложений (иконка, название, статус, бейдж «стороннее» при
   `origin=third-party`).
2. Карточка: описание, publisher, размер, changelog, HU, кнопки
   Скачать / Обновить / Открыть.
3. Настройки: канал release/dev; позже — источник Яндекс / Mail.ru / авто.
4. Пустое состояние / ошибка сети с понятным текстом (A10: намёк на файрвол).

### 5.2. Установка и ГУ-ограничения

| Тема | Поведение MVP |
|------|----------------|
| Unknown sources | `REQUEST_INSTALL_PACKAGES` + проверка `canRequestPackageInstalls` |
| Install API | Intent + FileProvider (как Monitor OTA) |
| Подпись | Отказ или явный диалог, если cert ∉ `signingCertSha256` |
| Dexopt | Подсказка / кнопка «Компилировать» позже; полный auto — фаза 2 |
| A10 firewall | Магазин должен быть в whitelist / чиниться runtime iptables; иначе каталог не скачается. Документировать зависимость от Monitor/`fwunlock` |
| `/data` | Перед закачкой проверить свободное место (критично на стоковом A10 ~2 GiB) |
| Root | Не требовать для MVP |

### 5.3. Фильтр витрины (ориентир)

| id | A9 | A10 | MVP |
|----|----|-----|-----|
| tbox-monitor | ✓ | ✓ | да |
| mqtt | ✓ | ✓ | да |
| camd | ✓ | ✗ | да |
| dclinstall | ✓ | ✗ | да |
| dcl-launcher | ✓ | ✗ | да |
| сторонние verified | по полю `hu` | по полю `hu` | да (схема + ≥0 записей) |
| voice | ✓ | ✓ | фаза 2 |
| dataresize / usbaudio / fwunlock / winpatch | ✗ | ✓ | фаза 2 (`oneshot`) |
| phone | — | — | нет (не ГУ) |

---

## 6. Публикация и инструменты

1. **GUI наполнения (Windows):** `tools/appstore_manager/` — указывает на
   sync-папку AppStore, список приложений, правка meta, замена/добавление APK,
   запись `catalog.meta.json` + `version.json` + `catalog.json`. Инструкция:
   `tools/appstore_manager/ИНСТРУКЦИЯ.md`.
2. CLI: `tools/build_apps_catalog.py --apps-dir …/release/apps --write`
   (та же библиотека `appstore_catalog.py`).
3. Для first-party Gradle-модулей: копировать APK в `apps/<id>/` (GUI или
   вручную), versionCode/versionName в диалоге замены APK.
4. Для DCLInstall / blank wallpaper: committed APK из `tools/…` / `build.ps1`.
5. Лаунчер и сторонние: кладут APK в свою папку (доступ на Диске) или через GUI.
6. Зеркало Mail.ru: после правок в sync Яндекса скопировать то же дерево
   `release/apps` / `dev/apps` (или второй sync-корень).
7. Bundled fallback catalog в APK — не обязателен в MVP.

---

## 7. Интеграция с экосистемой

| Компонент | Изменение |
|-----------|-----------|
| Monitor | Кнопка/пункт «Магазин приложений» (implicit Intent / package); OTA вкладка пока жива |
| MQTT / camd / voice docs | Дистрибуция: «через TBox Store» вместо «только USB» |
| DCLInstall | Без слияния; магазин отдаёт APK |
| `:fwunlock` / iptables | Документировать, что магазину нужен исходящий HTTPS к API Яндекса/Mail.ru |
| AGENTS.md / README | Упомянуть модуль `:store` после появления кода |

---

## 8. Безопасность

- Публичные share только на чтение; mapping/keystore на Диск не класть.
- Каталог — источник истины по `verified` и cert pin; клиент не ставит
  APK вне записей каталога.
- Третьи стороны: только проверенные; смена cert = новая рецензия.
- Целостность: sha256 из `version.json` обязателен до `install`.
- Не логировать временные download URL дольше сессии.

---

## 9. Этапы

| # | Этап | Результат |
|---|------|-----------|
| 1 | Спека на Диске | Share AppStore + пустые `release/apps`, `dev/apps` — **есть** (`KDaHLxbFtPAjYA`); осталось наполнить APK + `catalog.json` |
| 2 | Доступ разработчику DCL | Права на `apps/dcl-launcher/`; шаблон `version.json` — **с вашей стороны** |
| 3 | Публикация first-party MVP | Monitor (копия/ссылка), MQTT, camd, DCLInstall в `apps/` + catalog — CLI `tools/build_apps_catalog.py` + GUI `tools/appstore_manager/` (Windows) |
| 4 | Модуль `:store` | **сделано (MVP):** UI списка/карточки, download, verify, install Intent; unit-тесты парсера |
| 5 | Связка с Monitor | **сделано:** кнопка на вкладке OTA + `TboxStoreLauncher` / action `vad.dashing.store.action.OPEN` |
| 6 | Сторонние | Процесс рецензии + первая запись `third-party` в catalog (схема готова; DCL launcher как пример) |
| 7 | Зеркало Mail.ru | Share + клиентский fallback — **сделано** (`StoreCloudFacade`: Авто / Яндекс / Mail.ru); контент на зеркале нужно синхронизировать вручную |
| 8 | Фаза 2 | voice, A10 oneshot, dexopt/root install, свертка OTA Monitor |

---

## 10. Открытые мелочи (не блокируют старт спеки)

- Финальные `applicationId` / название на экране («TBox Store» vs «Магазин TBox»).
- ~~Отдельный public key vs подпапка OTA~~ — решено: **отдельный** AppStore share.
- Иконки: файл `apps/<id>/icon.png` (+ webp/jpg), в catalog поле `icon`;
  Store качает только явный `icon`, иначе иконка установленного пакета / буква.
- Формат иконки (PNG в папке приложения vs URL).
- Кто куратор `catalog.json` при одновременной публикации лаунчера разработчиком
  (merge вручную vs скрипт «собрать catalog из всех `apps/*/version.json` + overlay метаданных»).

---

## 11. Критерий готовности MVP

На A9 и A10 (где применимо): магазин ставится один раз с USB, открывает
`apps/catalog.json` с канала release, ставит MQTT (или другое лёгкое
first-party) поверх, показывает статус «установлено», предлагает обновление
после поднятия `versionCode` на Диске. Legacy OTA Monitor из корня `release/`
продолжает работать.
