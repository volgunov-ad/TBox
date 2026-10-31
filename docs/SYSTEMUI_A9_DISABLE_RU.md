# A9: плавающая кнопка «Домой», шторка и отключение SystemUI без долгой загрузки

При стороннем лаунчере (Dashing Club Launcher, `ras.dashing.tbox.launcher`) на ГУ **A9** остаются штатная плавающая кнопка «Домой» и шторка, выдвигающаяся сверху. Обе принадлежат `com.android.systemui`. Если SystemUI отключить, по сообщениям пользователей ГУ начинает долго загружаться.

Итог исследования на стенде (A9, `192.168.1.128:5555`, 31.10.2026):

- долгая загрузка вызвана обоями: штатные обои `ImageWallpaper` находятся внутри SystemUI, без них WindowManager ждёт окно обоев и снимает заставку только по 30-секундному таймауту;
- решение — маленький APK [`tools/blank_wallpaper/`](../tools/blank_wallpaper/) с «пустыми» живыми обоями (`directBootAware`). Со скрытым SystemUI и этими обоями экран готов на **11,1 с** против 12,7 с штатно и 41,1 с со скрытым SystemUI без обоев;
- плавающую кнопку можно скрыть и без отключения SystemUI (appops), а шторку — нет.

Инструкция для пользователей: [`tools/blank_wallpaper/ИНСТРУКЦИЯ.md`](../tools/blank_wallpaper/ИНСТРУКЦИЯ.md).

## 1. Что рисует SystemUI поверх лаунчера

Пакет `/system/priv-app/WT_WtSystemUI/WT_WtSystemUI.apk`, `versionName=9.3.1.43`, флаги `SYSTEM PERSISTENT PRIVILEGED`, uid `10037`. Окна по `dumpsys window windows`:

| Окно | Тип | Что это |
|---|---|---|
| `StatusBar` | `STATUS_BAR`, во всю ширину × 36 px, сверху | полоса, свайп из которой раскрывает шторку |
| `com.android.systemui` | `APPLICATION_OVERLAY` 70×70, `appop=SYSTEM_ALERT_WINDOW` | плавающая кнопка «Домой» (`HomeFloatView`) |
| `WtStatusBar` | `SYSTEM_DIALOG`, 1378 px × wrap, справа сверху | штатная строка состояния WT; при полноэкранном приложении скрыта (`View.GONE`) |
| `com.android.systemui.ImageWallpaper` | `WALLPAPER` | штатные обои |

Шторка — стандартная AOSP-панель уведомлений («Быстрые операции» + «Центр уведомлений»). Верхняя строка и фон на главном экране рисует сам лаунчер, а не SystemUI.

## 2. Без отключения SystemUI

### 2.1. Плавающая кнопка

Класс обфусцирован (`b.h.a.g`, лог-тег `HomeFloatView`). Видимость решает `setVisibility(ComponentName top)`:

1. верхняя activity или пакет в чёрном списке → скрыть. Списки — ресурсы `config_hide_homefloatview_activity` (5 activity `com.mengbo.factory` / `com.mengbo.smartscene`) и `config_hide_homefloatview_app` (`com.android.provision`);
2. переключатель = 1 → показать;
3. иначе показать, если верхнее приложение полноэкранное и `bool/support_homeFloatView_always_visible_in_fullscreen` = `true` (так в прошивке).

Переключатель — `Settings.Global`, ключ из `string/fall_ball_key` = **`ro.mb.btn_home_flow`** (по умолчанию `integer/fall_ball_default_value` = 1). На стенде уже `0`, но кнопка видна: из-за `policy_control=immersive.full=*` все приложения полноэкранные, и срабатывает пункт 3. Ключ `settings_floating_ball_status` в `Settings.Global` SystemUI не читает: проверено, `0` ничего не меняет.

**Рабочий способ — appops.** Окно кнопки подчиняется `SYSTEM_ALERT_WINDOW`, у shell есть `MANAGE_APP_OPS_MODES`:

```
adb shell appops set com.android.systemui SYSTEM_ALERT_WINDOW ignore    # скрыть
adb shell appops set com.android.systemui SYSTEM_ALERT_WINDOW default   # вернуть
```

WindowManager скрывает окно сразу (`mAppOpVisibility=false`, `isOnScreen=false`), сам SystemUI и шторка работают. Режим appops хранится в `/data/system/appops.xml`, но **сохранение после перезагрузки не проверялось**.

### 2.2. Шторка

`StatusBar.panelsEnabled()` разрешает раскрытие, только если не выставлены `DISABLE_EXPAND` (`0x10000`) и `DISABLE2_NOTIFICATION_SHADE` (`0x4`), а WT-флаг `mIsWtPanelExpandable` равен `true` (в коде он нигде не сбрасывается). Настроек `Settings.*` и broadcast-команд для блокировки шторки в SystemUI нет.

Флаги выставляет только `StatusBarManager.disable()`. Для него нужно `android.permission.STATUS_BAR` (`signature|privileged`) и живой процесс, держащий binder-токен: при его смерти запрет снимается. У shell есть только `EXPAND_STATUS_BAR`, `INTERNAL_SYSTEM_WINDOW`, `MANAGE_APP_OPS_MODES`; `service call` binder-токен передать не умеет. Поэтому без отключения SystemUI шторку штатно не заблокировать.

Непроверенные идеи: постоянный процесс от root через `mb_daemon` ([HEAD_UNITS_DEVICE_NOTES_RU.md](HEAD_UNITS_DEVICE_NOTES_RU.md) §10), вызывающий `IStatusBarService.disable(DISABLE_EXPAND, token, …)`; прозрачное окно `TYPE_ACCESSIBILITY_OVERLAY` поверх верхних 36 px, перехватывающее свайп.

## 3. Отключение SystemUI

### 3.1. `pm disable-user` не работает, работает `pm hide`

После `pm disable-user --user 0 com.android.systemui` и перезагрузки пакет снова `enabled=1`, процесс стартует, кнопка и шторка на месте. Как и с другими persistent-пакетами ([BOOT_AUTOSTART_DEVICE_RU.md](BOOT_AUTOSTART_DEVICE_RU.md) §5), действует только `pm hide`: SystemUI не запускается, кнопки и шторки нет, свайп сверху ничего не открывает.

### 3.2. Причина долгой загрузки

Лог загрузки со скрытым SystemUI:

```
I SystemServer: StartWallpaperManagerService
W KeyguardServiceDelegate: onScreenTurningOn(): no keyguard service!
I WallpaperManagerService: No static wallpaper imagery; defaults will be shown
I SystemServer: StartSystemUI
W ActivityManager: Unable to start service Intent { flg=0x100 cmp=com.android.systemui/.SystemUIService } U=0: not found
W WallpaperManagerService: Attempted wallpaper ComponentInfo{com.android.systemui/com.android.systemui.ImageWallpaper} is unavailable
W WallpaperManagerService: Failure starting previous wallpaper; clearing
E WallpaperManagerService: Default wallpaper component not found!
   ... 30 с тишины ...
W WindowManager: ***** BOOT TIMEOUT: forcing display enabled
```

`WindowManagerService.performEnableScreen()` не снимает загрузочную анимацию, пока `checkWaitingForWindows()` возвращает `true`. При включённых `config_enableWallpaperService` и `config_checkWallpaperAtBoot` в этой проверке есть условие «окно обоев существует и отрисовано». Без SystemUI окна обоев нет вообще, и анимацию снимает только `BOOT_TIMEOUT` через 30 с после `boot_progress_enable_screen` (11,06 + 30 = 41,08 с). Отсутствие keyguard загрузку не держит.

### 3.3. Решение: свои обои с `directBootAware`

Достаточно, чтобы при загрузке нашлись любые другие обои. `BlankWallpaper` (`vad.dashing.blankwallpaper/.BlankWallpaperService`) — `WallpaperService`, который заливает поверхность чёрным. Лаунчер рисует свой фон поверх, внешне ничего не меняется.

- **`android:directBootAware="true"` обязателен.** Разблокировка пользователя 0 происходит в `ActivityManagerService.finishBooting()`, то есть уже после снятия загрузочной анимации. Обои, не готовые к direct boot, `WallpaperManagerService` до разблокировки заменяет на `ImageWallpaper` («Wallpaper isn't direct boot aware; using fallback until unlocked»), а его без SystemUI нет — ожидание осталось бы.
- **Установка компонента.** `SET_WALLPAPER_COMPONENT` нет ни у shell, ни у обычных приложений. Поэтому через штатный `com.android.wallpaper.livepicker`: интент `android.service.wallpaper.CHANGE_LIVE_WALLPAPER` с `android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT` открывает `LiveWallpaperChange`, пользователь подтверждает кнопкой «Установить фоновый рисунок» и выбирает «Главный экран и экран блокировки». Проверка: `dumpsys wallpaper | grep mWallpaperComponent`.
- Порядок «обои → hide» или «hide → обои» не важен, важно, чтобы обои стояли до перезагрузки. Если при скрытом SystemUI удалить `BlankWallpaper` или сменить обои, 30-секундная задержка вернётся.

APK собирается без Gradle (`tools/blank_wallpaper/build.ps1`: aapt2 + javac + d8 + apksigner, подпись репозиторным `keystore/debug.keystore`). В репозитории лежит именно проверенный на стенде `BlankWallpaper.apk` (MD5 `D3AEBDD853D4071B60B64F0DC4C6CB01`); пересборка даёт тот же код с другим MD5 из-за меток времени в zip.

## 4. Замеры

Скрипт [`tools/hu_perf/hu_boot_markers.ps1`](../tools/hu_perf/hu_boot_markers.ps1): перезагрузка, ожидание `sys.boot_completed`, сохранение `events`/`main`/окон/скриншота и отметки в секундах от старта ядра (пересчёт сдвинутых меток logcat через `boot_progress_start`, как в [HU_PERF_TESTS_RU.md](HU_PERF_TESTS_RU.md)). Запуск: `.\tools\hu_perf\hu_boot_markers.ps1 -Tag <имя>` (`-NoReboot` — только разобрать текущую загрузку).

| Состояние | `enable_screen` | `sf_stop_bootanim` (экран готов) | `framework_boot_completed` | процесс лаунчера |
|---|---|---|---|---|
| SystemUI включён (штатно) | 11,14 | **12,73** | 13,71 | 13,82 |
| `pm disable-user` (SystemUI всё равно стартовал) | 11,20 | 12,80 | 14,03 | 14,13 |
| `pm hide` | 11,06 | **41,08** (`BOOT TIMEOUT`) | 42,26 | 42,30 |
| `pm hide` + BlankWallpaper, прогон 1 | 11,20 | **11,23** | 12,30 | 12,34 |
| `pm hide` + BlankWallpaper, прогон 2 | 11,05 | **11,07** | 12,20 | 12,28 |

Естественная загрузка до тестов (SystemUI включён): `sf_stop_bootanim` 13,20 с. Со скрытым SystemUI и BlankWallpaper экран готов на ~1,5 с раньше штатного: анимация снимается сразу после `enable_screen`, без ожидания отрисовки SystemUI. Крашей и ANR в логах нет, TBox Monitor и лаунчер стартуют как обычно.

Влияние остальных штатных пакетов на загрузку в этом состоянии (A/B со скрытием 9 ненужных пакетов, очередь `BOOT_COMPLETED`, нагрузка по процессам) — [BOOT_AUTOSTART_DEVICE_RU.md](BOOT_AUTOSTART_DEVICE_RU.md) §5: время до экрана от них не зависит.

## 5. Что пропадает без SystemUI

Вместе с кнопкой и шторкой пропадают: быстрые настройки шторки (Wi-Fi, BT, яркость, громкости), уведомления и heads-up, всплывающий индикатор громкости, меню питания, системные диалоги SystemUI — запрос доступа к USB-устройству (`UsbPermissionActivity`), разрешение на запись экрана, подтверждение отладки по USB; штатные обои и заставка (`DozeService`). Загрузка, Wi-Fi, ADB по сети, mbCAN и сам лаунчер от SystemUI не зависят.

## 6. Возврат

```
adb shell pm unhide com.android.systemui
adb reboot
```

`BlankWallpaper` можно оставить (SystemUI будет рисовать поверх свои элементы, обои останутся чёрными под лаунчером) или сменить обои штатно и удалить APK: `adb uninstall vad.dashing.blankwallpaper`.

## 7. Открытые вопросы

- Сохраняется ли `appops … SYSTEM_ALERT_WINDOW ignore` после перезагрузки (вариант «оставить SystemUI, убрать только кнопку»).
- Поведение без SystemUI на машине (стенд без автомобиля): зажигание/сон, кнопки громкости на руле, камера заднего хода, OTA (может ли обновление снять `hide`).
- Блокировка шторки при работающем SystemUI (§2.2): root-процесс через `mb_daemon` или accessibility-overlay — не проверялись.
- Сброс до заводских настроек снимает `pm hide` и обои — после сброса шаги нужно повторить.
