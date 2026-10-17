# Окно лаунчера A10 (ActivityView) и Android Auto / CarPlay в нём

Исследование штатного лаунчера `com.adayo.launcher` (A10 / Adayo / VHAL-линейка, ГУ
`192.168.1.7:5555`, `Build.VERSION.RELEASE`=10, платформа API 28) и причин, по которым
Android Auto (`com.adayo.androidauto`) и CarPlay (`com.adayo.carplay` / `com.adayo.carplay.view`),
запущенные в «окне приложений» лаунчера, теряют тач и скрывают штатные бары.
Всё ниже — статический анализ dexdump'ов APK c ГУ + проверка на живом устройстве (октябрь 2026).

## 1. Как устроено окно

- Лаунчер держит окно «приложения в окне» на **виртуальном дисплее фреймворка** —
  `android.app.ActivityView` (скрытый API P). Обёртка: `com.adayo.launcher.view.HKActivityView`
  (extends `ActivityView`): через рефлексию достаёт приватное поле `ActivityView.mSurfaceView`,
  делает `setZOrderOnTop(true)`, `setBackgroundColor(0)`, `setFormat(TRANSPARENT)`.
  Больше никакой логики в классе нет.
- `LauncherActivity` берёт `HKActivityView` из XML-layout (`onCreate`), вешает
  `ActivityView$StateCallback` (inner `$6`): `onActivityViewReady` → `mIsAvReady=true` +
  `launchMainPage()` (дефолт в окно — `com.adayo.app.carsettings/.ui.activity.MainActivity`),
  `onActivityViewDestroyed` → `mIsAvReady=false`. `onDestroy` лаунчера → `mAv.release()`.
- Приложение, запущенное в окно, работает на **Display #1** (виртуальный), его окна там
  `fillxfill`, своих системных баров у дисплея нет.

Проверено на живом ГУ (октябрь 2026, в окне были наши `fwunlock`/`tbox`/`usbaudio`):

```
$ adb shell dumpsys window displays | grep -A2 'mDisplayId=1'
  Display: mDisplayId=1
    init=1327x865 160dpi cur=1327x865 app=1327x865 rng=865x865-1327x1327
    mTouchExcludeRegion=SkRegion((0,0,1327,865))
```

## 2. Размеры окна захардкожены в лаунчере

`LauncherActivity.setFullScreen(boolean)` (единственное место, меняющее геометрию окна):

| Режим | width×height | rightMargin | topMargin | Позиция `layout(l,t,r,b)` |
|---|---|---|---|---|
| оконный (`false`) | **1327×865** | 23 | 94 | (570, 94, 1897, 959) |
| фуллскрин (`true`) | 1920×1080 | 0 | 0 | (0, 0, 1920, 1080) |

После установки LayoutParams лаунчер вручную вызывает `mAv.layout(...)`, `requestLayout()`,
`setLayoutParams(...)` и `ScreenUtil.switchFullscreen(getWindow(), fullscreen)`:

- `switchFullscreen(true)`: `clearFlags(DRAWS_SYSTEM_BAR_BACKGROUNDS|FORCE_NOT_FULLSCREEN)`,
  `setSystemUiVisibility(0x1707)` — иммерсив, бары скрыты;
- `switchFullscreen(false)`: `clearFlags(FULLSCREEN)`, `addFlags(DRAWS_SYSTEM_BAR_BACKGROUNDS)`,
  `setSystemUiVisibility(0x700)` — бары видимы. **В оконном режиме лаунчер сам бары не прячет.**

Автовозврат в оконный режим: `resumeUnfullscreenIfNeeded(visibility)` (вызывается из
`onNewIntent` и из `LauncherActivity$2` = `OnSystemUiVisibilityChangeListener` декора):
если пришёл бит `SYSTEM_UI_FLAG_FULLSCREEN` и карточка приложений видима —
`"force unfullscreen"` → `setFullScreen(false)`.

## 3. Запуск приложений в окно

Все пути сходятся в `LauncherActivity.launchActivityViewAppInternal(Intent)` →
`mAv.startActivity(intent)` (при `mIsAvReady`; иначе/при ошибке — `launchMainPage()`):

1. **Интент в лаунчер** (внешний вход, им пользуется и TBox Monitor —
   `app/src/main/java/vad/dashing/tbox/AdayoStockAppWindow.kt`):
   action `com.adayo.launcher.LAUNCH_APP`, extras `app_pkg` / `app_cls` / `app_action`
   (строки, опциональны) → `AppUtil.getAppIntent(...)` → окно.
2. **Переключение источника**: интент лаунчеру с extras-`Bundle "map"` (`HashMap`) и ключом
   `"SourceType"` → `openSourcetype(String)` (hashCode-sparse-switch по строке → int →
   packed-switch по int). Подтверждённые ветки:
   - `ADAYO_SOURCE_ANDROID_AUTO` → интент `com.adayo.androidauto` → в окно;
   - CarPlay-ветка → интент `com.adayo.carplay.view` → в окно (запускается именно
     **CarPlayView** — приложение-рендерер, не сервисный `com.adayo.carplay`;
     точный номер кейса 14 `ADAYO_SOURCE_CARPLAY_MEDIA` или 15 `"com.adayo.phonelink"`
     не декодирован однозначно — таблица packed-switch в dexdump обрезана);
   - `ADAYO_SOURCE_SETTING` → `com.hk.systemsettings`; медиа-источники → `com.haoke.media`
     (VIDEO/IMAGE/MUSIC/BT_MUSIC/RADIO/*_USB); BT-телефония CarPlay/AA →
     `AdayoSourceHelper.open*` (фоновые источники, без окна);
   - строка-пакет `"com.adayo.phonelink"` — отдельный SourceType.
3. **Карточки лаунчера**: `BaseView.launchActivityViewApp(ctx, pkg, cls, action)` →
   если хост `LauncherActivity` → `launchActivityViewAppInternal`, иначе статический фолбэк.
4. Пустой/ошибочный интент → `launchMainPage()` (carsettings).

## 4. Фуллскрин/бары: бродкаст `com.adayo.launcher.FULLSCREEN`

`LauncherActivity$5` (поле `mBroadcastReceiver`, создаётся в `<init>`) — приёмник:

- action **`com.adayo.launcher.FULLSCREEN`**, extra **`fullscreen:int`** (`1` = развернуть,
  остальное = оконный) → `setFullScreen(bool)`.

Отправители строки (бинарный `grep -rl launcher.FULLSCREEN /system/app /system/priv-app`):
`BTService.apk`, `Launcher.apk` (сам приёмник), `ProjectAdapter.apk`, `AtcSystemUI.apk` —
то есть **штатное переключение источника разворачивает окно этим же бродкастом**.
В разобранных APK AA/CarPlay/CarPlayView отправки нет.

Проверено на живом ГУ (обратимо):

```
$ adb shell am broadcast -a com.adayo.launcher.FULLSCREEN --ei fullscreen 1
  → Display #1: init=1920x1080 160dpi cur=1920x1080   (окно развернуто, бары скрыты штатно)
$ adb shell am broadcast -a com.adayo.launcher.FULLSCREEN --ei fullscreen 0
  → Display #1: init=1327x865   (обратно)
```

## 5. Почему AA/CarPlay в окне 1327×865 ломаются

Оба приложения сконфигурированы под **единственный экран 1920×1080** и не знают про окно.

### 5.1 Конфиги на ГУ

- CarPlay (нативный SDK): `/system/etc/adayo/carplay/CarPlayManagerConfig.json` —
  `displays.mainDisplay`: 1920×1080, physical 345×195, maxFps 30, `supportsViewAreas:false`;
  `vehicleInfo.model` HM5102A. `CarPlayJniProxyConfig.json` → `configPath` → ManagerConfig;
  `CarPlayServerConfig.json` (deviceId, аудио-потоки, геометрии нет);
  `CarPlayViewConfig.json` (только Logger).
- Android Auto: `/system/etc/adayo/AndroidAuto/AndroidAutoConfig.json` —
  `headunitModel` **HMA214A**, `widthPixel/heightPixel` 1920/1080, physical 285×160,
  `scaleDpiH/M/L` 240/160/100 («настройки размеров» AA = пресеты разрешения),
  `pixelAspectRatio 1.002f`, `usbPort /dev/bus/usb/002`, wireless+wired.
- Конфиги с `//`-комментариями — парсер lenient-Gson.

### 5.2 Бары

- CarPlayView `MainActivity` (`com.adayo.carplay.view.ui.activity.MainActivity`):
  `onCreate` → сразу `hideBottomUIMenu()` (`setSystemUiVisibility(0x1006)` + window
  `systemUiVisibility 0x802`) и `Window.setFlags(0x2000,0x2000)` = **FLAG_LAYOUT_NO_LIMITS**;
  повтор в `onWindowFocusChanged(true)`.
- AndroidAuto `AAActivity` (`com.adayo.androidauto.ui.AAActivity`):
  `onWindowFocusChanged(true)` → `hideBottomUIMenu()`; `onResume` → sink'овский
  `com.google.android.projection.sink.ui.SystemUiHider` — ставит `0xE06` и
  **пере-применяет каждые 2 с** (`HIDE_DELAY_MILLIS=2000`).
- Вызовы идут из окон на Display #1, но агрегация systemUiVisibility в WMS (Android 9)
  скрывает бары и на основном дисплее; лаунчер в оконном режиме им не противодействует
  (см. §2). Штатный сценарий это «легитимно»: при фуллскрине бары всё равно скрыты.

### 5.3 Тач CarPlay

`MainActivity.onTouchEvent` (DOWN+MOVE с дедупом координат) → `CarPlayApplication.touchEvent(III)`
(**сырые координаты окна, без скейла**) → AIDL `ICarPlayProxy.reportTouchEvent` →
`CarPlayService$CarPlayProxyBinder.reportTouchEvent` → `CarPlaySDKAdapter.requestTouchHid(JIII)`
(пасc-through) → `CarPlayJNI.reportTouchHid` — маппинг координат только в нативе, в
пространстве конфига **1920×1080**.

В окне 1327×865: тап в точку (x,y) окна уходит как есть и интерпретируется в пространстве
1920×1080 → попадания смещены к левому-верхнему углу (масштаб по осям 1327/1920≈0.69 и
865/1080≈0.80). В фуллскрине окно = 1920×1080 → **тач 1:1 без всяких патчей**.

### 5.4 Тач Android Auto

`AAService$3` (OnTouchListener на SurfaceView видео) → `AAProjectionRenderer.transformMotion`:
`x' = x·mTouchXScale − xMargin`, `y' = y·mTouchYScale − yMargin` (по каждому поинтеру).

- Скейлы динамические: `mTouchXScale = videoW / mSurfaceFrame.width` (аналогично Y) —
  корректны для любого фактического размера SurfaceView.
- Маргины — из `DimensionUtils.calSreen(widthPixel=1920, heightPixel=1080, videoW, videoH,
  widthPhysical, heightPhysical)` → `mWidthMargin/2`, `mHeightMargin/2`, т.е. считаются для
  **полного дисплея 1920×1080**. В окне 1327×865 это даёт ложный вертикальный офсет
  (letterbox-подложка не там).
- Есть **хардкод-оверрайд** для `headunitModel` HMA212/219/220/227/228/229
  (videoW 1920 → scale 1.0, yMargin 180; 1280 → 0.667/120; 800 → 0.417/90 — константы
  фуллскрина). Наша модель **HMA214A** под него не попадает → динамический путь.
- В фуллскрине: SurfaceView 1920×1080, видео 1920×1080 (аспект 16:9 = physical 285×160
  16:9) → scale=1.0, маргины=0 → **тач 1:1 без патчей**. Для пресетов пониженного
  разрешения (`scaleDpiM/L`) динамический путь тоже корректен — он опирается на фактический
  кадр SurfaceView.

## 6. Решение без патчей (проверено): разворачивать окно бродкастом

После запуска приложения в окно (`LAUNCH_APP`) послать вторым интентом штатный бродкаст:

```
adb shell am broadcast -a com.adayo.launcher.FULLSCREEN --ei fullscreen 1   # окно → 1920×1080
adb shell am broadcast -a com.adayo.launcher.FULLSCREEN --ei fullscreen 0   # назад в 1327×865
```

- Окно становится 1920×1080 → геометрия совпадает с ожиданиями обоих приложений:
  тач CarPlay 1:1 (нативный маппинг), тач AA 1:1 (scale=1, маргины=0), видео без letterbox.
- Бары скрываются — но это штатное поведение фуллскрина (то же делает переключение
  источника через BTService/ProjectAdapter). Это не «запретить лаунчеру скрывать бары»,
  а наоборот — «развернуть окно», чтобы приложения попали в родную геометрию.
- Возврат: HOME (лаунчер сам делает «force unfullscreen» при видимой карточке приложений)
  или бродкаст `fullscreen 0`.
- Ограничение: это фуллскрин — карточки лаунчера и бары не видны, пока активен режим.
  Оконный режим 1327×865 с барами для AA/CarPlay возможен только патчами (§7).

В TBox Monitor точка интеграции — `AdayoStockAppWindow.kt` (сейчас шлёт только `LAUNCH_APP`);
для ярлыков AA/CarPlay достаточно добавить второй `sendBroadcast(FULLSCREEN, 1)` после
запуска (и опционально `fullscreen 0` при закрытии).

## 7. Вариант с патчами APK (оконный режим с барами и корректным тачом)

Если нужен именно оконный режим 1327×865 — правки в системных APK (apktool → smali →
переподпись → установка в `/system/app/...` с eMMC WP-танцем по образцу `:fwunlock`,
см. [HEAD_UNITS_DEVICE_NOTES_RU.md](HEAD_UNITS_DEVICE_NOTES_RU.md) §5.2 и модуль `:fwunlock`):

1. `AndroidAuto.apk`, `AAActivity`: NOP `hideBottomUIMenu()` (и при желании — не создавать
   `SystemUiHider` в `onResume`).
2. `CarPlayView.apk`, `MainActivity`: NOP `hideBottomUIMenu()`; в `onTouchEvent` перед
   `CarPlayApplication.touchEvent` домножать координаты: `x' = x·1920/getWidth()`,
   `y' = y·1080/getHeight()` (в фуллскрине множители = 1, т.е. патч безопасен и там).
3. `AndroidAuto.apk`, `AAProjectionRenderer`: считать маргины от фактического размера
   SurfaceView (`mSurfaceFrame`), а не от 1920×1080 из конфига — или добавить HMA214A в
   существующий хардкод-блок с корректными оконными константами.
4. Опционально `Launcher.apk`: `setFullScreen(false)` — если нужен другой размер окна
   (значения 1327/865/23/94 захардкожены только там).

Порядок установки и механика WP описаны в doc'е выше; перед заменой — сохранить оригиналы
и md5 (как в `:fwunlock`).

### 7.1. Подпись: патч осуществим (проверено по сертификату прошивки)

Все четыре APK (`AndroidAuto`, `CarPlay`, `CarPlayView`, `Launcher`) объявляют
`android:sharedUserId="android.uid.system"` → переподпись «личным» ключом невозможна
(PackageManager отбракует). Но сертификат UID `android.uid.system` на ГУ — **байт-в-байт
публичный AOSP platform test-key** (проверено по `/data/system/packages.xml`,
`<shared-user name="android.uid.system">` → cert index 0):

| Параметр | Значение (ГУ == AOSP) |
|---|---|
| SHA-256 (DER) | `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8` |
| Serial | `00B3998086D056CFFA` |
| Subject/Issuer | `E=android@android.com, CN=Android, OU=Android, O=Android, L=Mountain View, S=California, C=US` (self-signed) |
| Ключ / сигнатура | RSA-2048, md5WithRSA (сигнатура самого сертификата на подпись APK не влияет) |
| Срок | 2008-04-16 … 2035-09-02 |
| Размер DER | 1196 байт |

Источник эталона: `platform.x509.pem` из AOSP `android-10.0.0_r1`
(`build/target/product/security/`); приватный ключ `platform.pk8` публичен там же.
Значит: apktool → правки smali → сборка → **подпись `platform.pk8` + `platform.x509.pem`**
(apksigner / signapk) → замена в `/system/app/...` WP-танцем. sharedUserId и сертификат
совпадают с прошивочными — PackageManager примет обновления.

Дополнительно проверено: `META-INF/CERT.RSA` стоковых `AndroidAuto.apk` и `CarPlayView.apk`
содержит тот же сертификат (serial `b3998086d056cffa`) — запись в packages.xml не протухла.

Гоча этой прошивки: в `packages.xml` атрибут `key=` элемента `<cert>` содержит **hex**, а не
base64 (в стоке AOSP — base64); серийник и хэш выше посчитаны после hex-декода.

### 7.2. Артефакты проверки подписи (`Temp\rufp\`)

- `sysuid_cert_real.der` — сертификат UID system с ГУ (hex-декод из packages.xml);
- `aosp_platform_cert.der` / `.pem` — эталон AOSP `android-10.0.0_r1`;
- `packages.xml` — слепок с ГУ;
- `sysuid_cert.b64` — сырой hex-текст атрибута (имя историческое, содержимое — hex).

### 7.3. Патч CarPlayView: собран и проверен на стенде (2025, Wi-Fi bench)

Собрано и установлено в `/system/app/CarPlayView/CarPlayView.apk`. Все три патча из §7
применены к smali, плюс правка `styles.xml` (`windowFullscreen` → false). Сборка:

1. `java -jar apktool.jar d CarPlayView.apk -o carplayview_dec` (apktool 3.0.3, aapt2;
   флага `--use-aapt1` в 3.x нет — и не нужен: public.xml сохраняет ID,
   `layout/activity_main` остался `0x7f030000`, `resources.arsc` STORED);
2. правки smali (`MainActivity.smali`):
   `hideBottomUIMenu` → `return-void`; `setFlags(FLAG_SECURE)` → `setFlags(0)`;
   `onTouchEvent` — scale `x·1920/w, y·1080/h` (w/h = `surfaceView` `getWidth/getHeight`,
   при `w<=0||h<=0` — 1, без ветвлений в точках слияния регистров);
3. `java -jar apktool.jar b carplayview_dec -o CarPlayView_patched.apk`;
4. `zipalign -f 4` → `apksigner sign --key platform.pk8 --cert aosp_platform_cert.pem`
   (cert = AOSP platform test-key, §7.1).

Гоча деплоя без перезагрузки: **`pm install` отказывает persistent-приложению**
(`INSTALL_FAILED_ALREADY_EXISTS` даже с `-r -d --force-queryable`), поэтому замена файла
`/system/app/CarPlayView/CarPlayView.apk` + **обязательная чистка
`/data/dalvik-cache/arm64/system@app@CarPlayView@CarPlayView.apk@classes.{dex,vdex}`**
иначе ART продолжает грузить старый vdex (VerifyError «бит-в-бит» от предыдущей сборки —
симптом именно этого). Затем `am force-stop` и проверка `pidof` (persistent
авторестартует процесс за ~1 с — запускать активити только после смерти старого).

Верификация на стенде (телефона нет, `launchCarPlay NONE`, сессии нет — чёрный контент):

- полный жизненный цикл без крашей: `onCreate → onStart → onResume → onSurfaceCreate →
  onWindowFocusChanged(true)`;
- бары лаунчера и левая колонка видны, окно CarPlayView ≈1327×865 справа —
  фуллскрин не включается (патчи работают в headless-режиме);
- тач: активити получает координаты окна (0..1327 × 0..865), патч масштабирует в
  1920×1080 с точностью ±1 px (лог `CPViewPatch`):
  `(900,400)→(330,306)→(477,382)`; `(1500,800)→(930,706)→(1345,881)`;
  `(1200,500)→(630,406)→(911,506)` — `x·1920/1327, y·1080/865` подтверждён.

Артефакты (`Temp\rufp\`): `CarPlayView_patched_signed.apk` (md5 `ab1ebdfe8b572d799965c84f1d8f1ad7`),
`apktool.jar`, `platform.pk8`/`aosp_platform_cert.pem`, скрипты `patch_cpv.sh` (замена
файла через WP-танец, идемпотентный) и `clean_cpv_cache.sh`; бэкап оригинала на ГУ
`/data/local/tmp/CarPlayView_orig_backup.apk` (md5 `2718803663e016a969e6d2fa0d2b4c16`).
Откат — тем же скриптом с бэкапом + чистка dalvik-cache + force-stop.

Живая сессия проверена 2026-10-17 — см. §7.5 (бары видны, тач точный, стек
уводит активити на дисплей 0 «между барами»).

### 7.4. Патч CarPlayView на ГУ: встраивание в ActivityView и тач (проверено, 2026-10)

Деплой на ГУ — тем же WP-танцем (`patch_cpv.sh`; бэкап оригинала пишется только при
первом запуске). Цепочка md5 `/system/app/CarPlayView/CarPlayView.apk`:
сток `2718803663e016a969e6d2fa0d2b4c16` → p1 `035e74c3792a77d5cee55a13e1db9a39` →
**p2 `b652a30c36d11fea4343c0e3123ab942`** (`CarPlayView_p2_signed.apk`, 202071 Б,
cert SHA-256 `c8a2e9bc…92ab8`, §7.1). `pm install -r` по-прежнему невозможен
(«persistent apps are not updateable» — in-memory флаг persistent живёт до ребута).

**Главное: встраивание в ActivityView сорвалось на `launchMode="singleInstance"` и
лечится `singleTop`.** В `handleIntent` лаунчера ветка `LAUNCH_APP` всегда зовёт
`launchActivityViewAppInternal` (отдельного маршрута «source → display 0» нет, дамп
481442–481499), но с манифестом стока CarPlayView открывался обычным окном на
дисплее 0 (stack 6, `fillxfill TRANSLUCENT LAYOUT_INSET_DECOR`, surface 1920×960 —
«между барами», сырые координаты дисплея в `onTouchEvent`). После правки манифеста на
`singleTop` (единственное отличие p1→p2, smali не менялся) то же приложение встаёт на
виртуальный дисплей: `MainActivity mDisplayId=1 stackId=1`, поверх carsettings и
TBox; на скриншоте чёрный SurfaceView ровно в области 1327×865@(570,94), бары и левая
колонка лаунчера на месте. Механизм отказа до конца не изолирован (вклад reuse
старого таска t1282 vs ограничения singleInstance на виртуальном дисплее P), но
правка воспроизводимо решает; для окна это и нужно — переиспользование активити
сохраняется.

Тач в режиме встраивания: активити получает **локальные** координаты дисплея 1
(0..1327 × 0..865), формула патча `x·1920/w, y·1080/h` даёт корректные логические
координаты: тап в центр окна `(1233,526)` → локально `(663,432)` → логически
`(959,539)` ≈ центр 1920×1080. Повторно подтверждено 2026-10-17 22:42 (после правки
wireless-конфигов CarPlay и рестарта стека, pid 17815): `(1233,526)→(959,539)`,
`(700,150)→(130,56)→(188,69)` — оба тапа ±1 px. Жест с удержанием (`input swipe`, 400 мс) — 23 события
одним трансформированным потоком, без сырых вкраплений. **Остаточный риск**:
однократное «сырое» событие (дисплей-0 координаты без пересчёта, `(1233,526)`) наблюено
сразу после создания таска — у лаунчера, видимо, параллельно живёт свой форвардер сырых
координат (под него заточен нативный маппинг стока); требуется проверка реальным пальцем.

Эксплуатационные заметки headless-верификации (UART `uart_cmd.ps1`: COM21, 921600,
чанки 4 Б/20 мс, маркеры MK1S/MK1E, вывод в `-RawOut`):

- `logcat -d` из UART-консоли может зависнуть и заблокировать shell — вызывать как
  `timeout 8 logcat …`; Ctrl+C консоли отправляется байтом 0x03 (`-Command "$([char]3)"`).
- Отвал Wi-Fi (известный, §5.1/§7.3) воспроизведён: wlan0 уходит в 192.168.43.164/24
  без default route, ADB мёртв. Лечение по UART: `svc wifi disable; sleep 2;
  svc wifi enable` → wlan0 возвращается на 192.168.1.7, ADB восстановился без ребута.
- Для p2 перезагрузка/рестарт процесса не понадобились: система подхватила новый
  манифест при следующем `LAUNCH_APP` (новый таск t1283 на дисплее 1).

Артефакты: `Temp\rufp\{CarPlayView_p2_signed.apk, uart_cmd.ps1, w_cp1.png (до
встраивания: окно дисплей-0 + диалог TBox в окне), w_emb.png (встроено: чёрная
поверхность ровно в области окна), uart_log.txt}`.

### 7.5. Живая сессия CarPlay (iPhone по USB, ADB, 2026-10-17)

Сессия поднялась штатно: BT-флаг `CpConnection=true` (`PDBluetoothController`,
`CpConnAddress=2C:DF:68:37:4A:0C`), транспорт — интерфейс **`carplay0`** с link-local
`169.254.3.249/16` (DHCP-сервер не ответил — `dhcpcd` в DISCOVER — но сессии не мешает);
`usb0` (`NCMNetCardName`) так и не появился: при первом подключении телефон сидел в
зарядном (гаджет) порту — ГУ-хост его не энумерировал вообще (в dmesg ноль attach,
`sys.usb.state=ncm` — гаджет-контроллер 13010000.cdncusb видел телефон как хост). В
data-порту сессия завелась. Развязка Wi-Fi работает: **wlan0/ADB переживают живую
сессию** (`WIFINetCardName: cpnowlan0`), хотя раньше кабельный CarPlay валил wlan0 ещё
до сессии (§5.1/§7.3).

**Главное: при старте сессии стек CarPlay перезапускает CarPlayView из ActivityView на
дисплей 0** — новый таск t1284, `mDisplayId=0 stackId=7`, окно 1920×960 «между барами»,
`MainActivity` в фокусе (бродкаста FULLSCREEN в логе нет — стек стартует активити сам).
Малое окно 1327×865 (§7.4) живёт только headless. Фактический результат закрывает
исходную цель иначе: CarPlay на всю ширину между несокрытыми барами:

- бары лаунчера (верх/низ) видны всю сессию — `hideBottomUIMenu`-патч работает и на
  дисплее 0 (`w_sess2.png`, `w_home.png`, `w_t4.png`; скриншоты в `Temp\rufp\`);
- тач: координаты окна на дисплее 0 = экранным; эмпирический скейл патча
  **x×1.0, y×1.25 (getHeight()≈864), смещение 0** — подтверждено попаданиями:
  физ. (56,818)→логич. (56,1022) = кнопка Home CarPlay (переключило на home-экран);
  физ. (685,320)→(685,400) = открылось приложение «Настройки». Все тапы логируются
  `CPViewPatch` — форвардер лаунчера не вмешивается (риск из §7.4 на дисплее 0 не
  проявился);
- каприз не тача: WhatsApp из сетки home не стартует (non-certified CarPlay),
  «Настройки»/Home открываются точно.

Не проверено: тач реальным пальцем с физического экрана (все тесты — `input tap`).
Побочное: поведение этапа 2 (AndroidAuto) при живой сессии, вероятно, такое же —
стек, скорее всего, тоже уводит `AAActivity` на дисплей 0.

Гоча logcat: `logcat -t N -s TAG` применяет лимит N к буферу **до** фильтрации — при
фоновом спаме (PhoneDetect каждые 2 с) свежие строки тега выпадают и вывод пуст;
фильтровать полный `logcat -d`.

## 8. Артефакты анализа (ПК, `C:\Users\volgu\AppData\Local\Temp\rufp\`)

| APK на ГУ | Локальная копия | Дамп (dexdump -d) | Ключевые строки дампа |
|---|---|---|---|
| `/system/app/Launcher/Launcher.apk` (v1.0) | `Launcher.apk` | `launcher_x/dump_classes.txt` (46,6 МБ) | `HKActivityView` 232272; `LauncherActivity` 480996 (`setFullScreen` 482124, `launchActivityViewAppInternal` 482471, `openSourcetype` 481739, `onCreate` 482601); `$5` FULLSCREEN 208221; `$6` StateCallback 208275; `ScreenUtil.switchFullscreen` 230367 |
| `/system/app/AndroidAuto/AndroidAuto.apk` | `AndroidAuto.apk` | `androidauto_x/dump_classes.txt` (109,7 МБ) | `AAActivity` 140493; `AAService$3` 135746; `AAProjectionRenderer` 144600 (transformMotion 146494, скейлы ~145440+); `SystemUiHider` 369466 |
| `/system/app/CarPlay/CarPlay.apk` | `CarPlay.apk` | `carplay_x/dump_classes.txt` (9,1 МБ) | binder `reportTouchEvent` 126586; `requestTouchHid` 77328 |
| `/system/app/CarPlayView/CarPlayView.apk` | `CarPlayView.apk` | `carplayview_x/dump_classes.txt` (3,9 МБ) | `MainActivity` 9182; `CarPlayApplication.touchEvent` 38641 |

dexdump: `C:\Users\volgu\AppData\Local\Android\Sdk\build-tools\35.0.0\dexdump.exe`.
Там же `carservice_x/` (дамп `com.adayo.carservice`) — в этом исследовании не разбирался.

## 9. Краткая сводка

- Окно = виртуальный дисплей `ActivityView`; размеры **захардкожены** в лаунчере:
  1327×865@(570,94) / 1920×1080.
- Лаунчер бары в оконном режиме не прячет; фуллскрин переключается штатным бродкастом
  `com.adayo.launcher.FULLSCREEN` (`fullscreen` 0/1), отправители — BTService /
  ProjectAdapter / AtcSystemUI.
- AA и CarPlay сконфигурированы под 1920×1080: в окне у CarPlay ломается тач (нативный
  маппинг в 1920×1080), у AA — вертикальный офсет (маргины от полного дисплея); бары
  скрывают сами приложения (`hideBottomUIMenu`, у AA ещё SystemUiHider с циклом 2 с,
  у CarPlayView FLAG_LAYOUT_NO_LIMITS).
- **Проверенное решение без патчей**: после `LAUNCH_APP` слать `com.adayo.launcher.FULLSCREEN
  --ei fullscreen 1` — окно разворачивается до 1920×1080, тач обоих приложений становится
  1:1, бары скрыты штатно. Возврат — `fullscreen 0` / HOME.
- Оконный режим с барами для AA/CarPlay — только патчами APK (§7). Подпись прошивки —
  публичный AOSP platform test-key (§7.1), так что патчи можно переподписать и поставить
  в `/system` без root-костылей вокруг подписей.
- Патч `CarPlayView` (бары + FLAG_SECURE + тач + **манифест `singleTop`**) собран,
  подписан, стоит в `/system` ГУ и проверен headless (§7.3 стенд, §7.4 ГУ) и **в живой
  сессии** (§7.5): бары на месте весь сеанс, тач точный (Home/Настройки открываются),
  Wi-Fi/ADB не отваливаются. Нюанс: при старте сессии стек уводит активити из
  ActivityView на дисплей 0 (1920×960 между барами) — малое окно только headless.
  Осталось: тач реальным пальцем (остаточный «сырой» форвардер) и этап 2 — тот же
  набор патчей для `AndroidAuto.apk` (там ещё `SystemUiHider` и `calSreen`-маргины,
  §5.4; при живой сессии, вероятно, тот же уход на дисплей 0).
