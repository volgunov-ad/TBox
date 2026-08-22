== TBox Monitor для Jetour Dashing

Приложение для головного устройства (ГУ) Jetour Dashing: мониторинг TBox, CAN и геопозиции, плитки на главном экране и в плавающих окнах, учёт поездок и топлива, климат/багажник/зеркала через mbCAN или VHAL, темы оформления, управление служебными процессами блока TBox.

**Текущая версия:** 0.18.1 (`ru` / `en` flavor).

== Основные возможности

* **TBox и сеть:** модем, SIM, APN, CSQ, перезапуск модема, перезагрузка TBox/ГУ, команды SUSPEND/STOP для APP/SWD/MDC/LOC, снижение лишних перезагрузок по таблице HW.
* **Данные с машины:** CAN (скорость, RPM, топливо, одометр, давление/температура шин и др.), геопозиция TBox; обмен через **tbox-proxy** (UDP).
* **CAN головного устройства:** **mbCAN** (Android 9) и **VHAL** (Android 10) — климат HVAC, сиденья/стёкла, багажник, зеркала, режимы вождения, настройки автомобиля.
* **Интерфейс:** вкладки «Модем», «Данные авто», «Геопозиция», «Поездки», **«Заправки»**, **«Темы»**, «Плитки», настройки; **главный экран** с панелями и обоями; **плавающие панели** поверх других приложений; настраиваемый вид плиток (выравнивание, вес шрифта, зазор сетки).
* **Поездки:** автоматический старт/стоп по RPM, split time, parking/moving/idle, расход и заправки по калиброванному уровню топлива, виджеты (полный / упрощённый / настраиваемый / мини), экспорт.
* **Топливо:** журнал заправок, цены с сети, калибровка нелинейности датчика по чекам, отфильтрованный % и калибровка с CAN **только в активной поездке**.
* **Темы:** экспорт/импорт `.tboxtheme`, темы по режиму вождения, обои light/dark по страницам.
* **Прочее:** музыкальный виджет, ярлыки и HTTP-запросы, App Widget, OTA-обновления, резервная копия JSON, журнал/AT/CAN в экспертном режиме.

== Плавающие панели

Несколько независимых панелей: сетка плиток, размер и позиция в пикселях, фон, индикатор TBox, порядок наложения, опционально скрытие при выбранных приложениях. Редактирование: **долгое нажатие** — режим перемещения/размера; **короткий тап** по ячейке в edit mode — назначение плитки (диалог в главном окне).

== Разрешения

* Отображение поверх других окон — плавающие панели.
* Хранилище — экспорт JSON, логов, файлов поездок.
* Геолокация — данные TBox и связанные плитки.
* Уведомления — музыкальный виджет.
* Статистика использования (по запросу) — скрытие панелей для выбранных приложений.
* **Изменение системных настроек** + ADB `WRITE_SECURE_SETTINGS` — виджеты **«Тема день/ночь»** и **«Регулировка зеркал»** (см. ниже).

### Виджеты «Тема день/ночь» и «Регулировка зеркал»

Эти плитки пишут штатные ключи головного устройства. После установки APK:

1. В настройках Android включите для **TBox Monitor** доступ к **изменению системных настроек**.
2. Выполните команду ADB:

```
adb shell pm grant vad.dashing.tbox android.permission.WRITE_SECURE_SETTINGS
```

== Хранение

Настройки — DataStore; поездки, заправки, моторные часы и др. — отдельное хранилище приложения. Лимиты: **31** поездка, **30** заправок, **25** избранных поездок.

== Варианты сборки по языку

* `ru` — русская сборка  
* `en` — английская сборка  

Примеры команд:

```
./gradlew assembleRuDebug
./gradlew assembleRuRelease
./gradlew assembleEnDebug
./gradlew assembleEnRelease
```

Debug и release подписываются `keystore/debug.keystore` из репозитория — облачная сборка ставится поверх уже установленного приложения.

=== APK из GitHub Actions

Workflow [Build APK](.github/workflows/build-apk.yml):

* **push в `preRelease`** — собирает `ruDebug` и кладёт APK в Artifacts;
* **Actions → Build APK → Run workflow** — вручную выбрать `ru`/`en` и `debug`/`release`.

Скачать: вкладка **Actions** → нужный run → блок **Artifacts** → `tbox-…-apk` (хранение 30 дней).

=== Прошивка компаньона (ESP32-S3) из GitHub Actions

Workflow [Build Companion Firmware](.github/workflows/build-companion-firmware.yml):

* **push в `preRelease`**, если менялось `firmware/esp32-companion/**` — собирает прошивку автоматически;
* **Actions → Build Companion Firmware → Run workflow** — ручной запуск в любой момент.

Скачать: **Actions** → run → Artifacts → `esp32-companion-<sha>` (30 дней). Внутри:

* `esp32_companion.bin` — app image для **OTA с ГУ** (вкладка «Компаньон»);
* `bootloader.bin`, `partition-table.bin`, `ota_data_initial.bin` — для первой прошивки по UART.

Пошагово с ПК (UART / CDC OTA): [docs/ESP32_COMPANION_FLASH_PC_RU.md](docs/ESP32_COMPANION_FLASH_PC_RU.md).

== Документация

| Файл | Содержание |
|------|------------|
| [docs/USER_GUIDE_RU.md](docs/USER_GUIDE_RU.md) | Руководство пользователя (интерфейс, TBox, настройки) |
| [docs/ESP32_COMPANION_FLASH_PC_RU.md](docs/ESP32_COMPANION_FLASH_PC_RU.md) | Прошивка компаньона ESP32-S3 с компьютера |
| [docs/TBOX_PROXY_RU.md](docs/TBOX_PROXY_RU.md) | Обмен с TBox по UDP через tbox-proxy, протокол, модули |
| [docs/CAN_BACKENDS_RU.md](docs/CAN_BACKENDS_RU.md) | mbCAN (Android 9) и VHAL (Android 10), `UniversalCanRepository` |
| [docs/RAW_VALUE_FORMULAS_RU.md](docs/RAW_VALUE_FORMULAS_RU.md) | Формулы пересчёта сырых значений TBox / mbCAN / VHAL |
| [docs/PANELS_AND_WIDGETS_RU.md](docs/PANELS_AND_WIDGETS_RU.md) | Плитки: вкладка «Плитки», главный экран, плавающие панели, новый виджет |
| [docs/Trips.md](docs/Trips.md) | Логика поездок: split, parking, перезапуск службы, топливо |
| [docs/Themes.md](docs/Themes.md) | Темы: `.tboxtheme`, кэш материализации, режимы вождения |
| [docs/fuel-refuels-calibration.md](docs/fuel-refuels-calibration.md) | Заправки, калибровка, пороги 4% / 0,3%, gate по активной поездке |
| [docs/BRANCHING.md](docs/BRANCHING.md) | Ветки `preRelease` / `master`, feature-ветки и релизный процесс |
| [Changelog.dm](Changelog.dm) | История версий по релизам (0.12 … 0.18.1) |
| [AGENTS.md](AGENTS.md) | Сборка и окружение для разработки (Cursor Cloud) |

== Архитектура данных

| Подсистема | Документ |
|------------|----------|
| TBox, модем, GPS, CAN с блока | [docs/TBOX_PROXY_RU.md](docs/TBOX_PROXY_RU.md) |
| CAN головного устройства (климат, сиденья, режимы) | [docs/CAN_BACKENDS_RU.md](docs/CAN_BACKENDS_RU.md) |
| Панели и виджеты | [docs/PANELS_AND_WIDGETS_RU.md](docs/PANELS_AND_WIDGETS_RU.md) |

== Техническая документация по CAN backend
Подробное описание выбора режима `mbCAN`/`VHAL`, работы `UniversalCanRepository`, подключения и диагностики:
* [docs/CAN_BACKENDS_RU.md](docs/CAN_BACKENDS_RU.md)
