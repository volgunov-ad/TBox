# Окно лаунчера A10 (ActivityView) и Android Auto / CarPlay в нём

Исследование штатного лаунчера `com.adayo.launcher` (A10 / Adayo / VHAL-линейка, ГУ
`192.168.1.7:5555`, `Build.VERSION.RELEASE`=10, платформа API 28) и причин, по которым
Android Auto (`com.adayo.androidauto`) и CarPlay (`com.adayo.carplay` / `com.adayo.carplay.view`),
запущенные в «окне приложений» лаунчера, теряют тач и скрывают штатные бары.
Всё ниже — статический анализ dexdump'ов APK c ГУ + проверка на живом устройстве (октябрь 2026).

**Цель (зафиксирована владельцем):** Android Auto и CarPlay запускаются **в окне лаунчера**
(Display #1, 1327×865) — **штатные бары видимы всю сессию, тач работает** — в обоих
сценариях: старт ярлыком при живой сессии и старт сессии без открытого окна.
**Полный экран не нужен:** полноэкранное открытие CP/AA считается провалом решения,
а не допустимым исходом. Критерий приёмки любой сборки CP/AA — окно + бары + тач,
иначе сборка не принимается (пример: p4, §7.6.1).

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
и md5 (как в `:fwunlock`). Для готовых p5/p3 есть приложение-установщик `:winpatch`
(§7.8).

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

**Обязательные требования пользователя (уточнены 2026-10-18):**

- CarPlay и Android Auto должны работать именно **в окне приложений лаунчера
  (ActivityView)**, в том числе во время живой сессии с телефоном.
- Способ запуска: **патч соответствующего приложения + ярлык приложения в TBox
  Monitor с запуском в окне приложений лаунчера**.
- Верхний и нижний бары должны оставаться видимыми. Разворот приложения на
  дисплей 0 «между барами» не заменяет оконный режим и не считается решением.
- Изображение должно сохранять правильные пропорции без растяжения или сжатия.
  Геометрия видеопотока и области его отображения должна быть согласована с
  фактическим окном, а не с полным экраном ГУ.
- Тач должен соответствовать отображаемому изображению: учитывать размер и
  положение видео внутри окна, масштабирование и возможные поля. Требуются
  проверки нажатий и жестов, включая физический экран.

Описанная ниже проверка на дисплее 0 подтверждает лишь отдельные свойства
патча (бары и синтетические нажатия), но **не выполнение задачи в целом**.
Последующий эксперимент `CarPlayManagerConfig.json`: `heightPixels=864`,
`heightPhysical=155` (вместо 1080/195) рассчитан на широкую область и не решает
растяжение в ActivityView 1327×865. Конфиг экспериментальный; его необходимо
пересмотреть вместе с геометрией SurfaceView и преобразованием тача. Патч p2
по-прежнему преобразует координаты в 1920×1080; сочетание с новым разрешением
1920×864 не считается согласованным или проверенным решением.

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
однократное «сырое» событие (дисплей-0 координаты без пересчёта, `(1233,526)`) наблюдено
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
data-порту сессия завелась. Втыкание кабеля попутно останавливает `adbd` — Wi-Fi-ADB
возвращается по UART: `start adbd`, затем `adb connect 192.168.1.7:5555` (adb-сессии
при этом переживают отключение). Развязка Wi-Fi работает: **wlan0/ADB переживают живую
сессию** (`WIFINetCardName: cpnowlan0`), хотя раньше кабельный CarPlay валил wlan0 ещё
до сессии (§5.1/§7.3).

**Главное: при старте сессии стек CarPlay перезапускает CarPlayView из ActivityView на
дисплей 0** — новый таск t1284, `mDisplayId=0 stackId=7`, окно 1920×960 «между барами»,
`MainActivity` в фокусе (бродкаста FULLSCREEN в логе нет — стек стартует активити сам).
В этой проверке малое окно 1327×865 (§7.4) сохранялось только headless.
CarPlay на всю ширину между несокрытыми барами **не закрывает исходную цель**:
требуется живая сессия именно внутри ActivityView (требования §7.4).
Ниже — результаты промежуточной проверки, не готовое решение:

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

### 7.6. CarPlayView p3: aspect-fit в живом ActivityView (2026-10-18)

Эксперимент с `CarPlayManagerConfig.json` 1920×864 отменён: восстановлен исходный
конфиг 1920×1080, `heightPhysical=195`, md5
`71082dfb83baf61eb449297d4df588ee`. Лог живой сессии подтверждает
`VideoSetWidthHeigth width=1920, height=1080`.

Установлен `CarPlayView_p3_signed.apk`, md5 файла на ГУ
`e9c8d958178875c0712f6bd0d39ae8a2`; platform-подпись проверена перед установкой.
Патч сохраняет правки манифеста и баров p2 и добавляет:

- `WindowSurface` вместо обычного SurfaceView: aspect-fit 16:9, центрирование на
  чёрном фоне. В окне 1327×865 фактическая поверхность — **1327×746@(0,59)**,
  поля 59 px сверху / 60 px снизу. Экранная область видео —
  **1327×746@(570,153)**. Округление до целых даёт менее 0,1% расхождения масштаба
  по осям. Квадратные и круглые элементы на скриншотах больше не сжаты;
- единый `dispatchTouchEvent`: вычитает положение поверхности, масштабирует в
  1920×1080, ограничивает координаты допустимым диапазоном. Начало жеста в поле
  игнорируется; начатый внутри видео жест завершается и при выходе за границу;
- `startInWindow` при запросе UI стеком использует дисплей уже существующей оконной
  активити; при её отсутствии отправляет `LAUNCH_APP` лаунчеру;
- `onNewIntent` обновляет `surfaceId` и передаёт поверхность стеку при повторном
  использовании активити.

После деплоя и рестарта подтверждена работа именно нового класса:
`dumpsys activity top` показывает
`com.adayo.carplay.view.patch.WindowSurface ... 0,59-1327,805`.
Живая сессия остаётся в `stackId=1`, `displayId=1`, task t1293,
`launchedFromPackage=com.adayo.launcher`; лог `startInWindow display=1`.
Верхний и нижний бары, левая колонка лаунчера видны. Это отличается от
промежуточной проверки p2 на дисплее 0 (§7.5).

Проверки p3 на ГУ:

- скриншот `cpv_p3.png`: живая сетка приложений, видео с сохранением 16:9 и полями;
- тап по «Настройкам» в доке `(608,607)` → локально `(38,513)` →
  видео `(54,657)`, события down/up; открылся сохранённый раздел «Дисплей»
  (`cpv_p3_dock_settings.png`, 00:18:58);
- тап по нижней кнопке CarPlay переключил экран на dashboard
  (`cpv_p3_home_checked.png`). Эта кнопка переключает dashboard / сетку / последнее
  приложение в зависимости от текущего состояния; последующий тап по старым
  координатам иконки «Настройки» в сетке открыл карту, а не «Настройки»
  (`cpv_p3_settings_checked.png`). Имя файла не является подтверждением попадания;
- тап `(1233,120)` в верхнее чёрное поле не создал `CPViewPatch` touch-событий;
- свайп `(1550,530)→(950,530)`, 400 мс: один поток из 23 событий,
  видео `(1417,545)→(549,545)`, с down/move/up без сырых вкраплений;
- ADB и живая USB-сессия доступны одновременно; новых `AndroidRuntime` ошибок
  в выбранном интервале проверки не найдено.

Артефакты p3 в `Temp\rufp`: `WindowSurface.java`, `DecodeHelper.java`,
`cphelper.zip`, `carplayview_dec`, `CarPlayView_p3{,_al,_signed}.apk`,
`restore_cpmgr.sh`, `clean_cpv_cache.sh`, `cpv_p3*.png`.
Исходный Manager-конфиг также сохранён на ГУ в
`/data/local/tmp/CarPlayManagerConfig.json.orig`.

**Проверка пользователем на физическом экране завершена:** запуск через ярлык
приложения TBox Monitor работает, тач правильный, изображение не растянуто.
Пользователь согласовал сохранение чёрных полей сверху и снизу; текущую геометрию
CarPlay p3 оставляем без изменений. Этап Android Auto можно продолжать.
Повторное подключение телефона и сценарий отсутствия оконной активити проверены
отдельной сборкой p4/p5 — §7.6.1.

### 7.6.1. CarPlayView p4/p5: старт сессии без окна (2026-10-18)

Сценарий «старт сессии без оконной активити» закрыт отдельной сборкой **p4**
(`CarPlayView_p4_signed.apk`, md5 `719289e15bc858241e805060cfe845cd`, 206167 байт,
установлен в `/system/app/CarPlayView/CarPlayView.apk`). Отличие от p3: в
редирект-ветке `startInWindow` (нет живой оконной активити) — прямой запуск
`startActivity` с `NEW_TASK|MULTIPLE_TASK` и
`ActivityOptions.setLaunchDisplayId` найденного через DisplayManager
`ActivityViewVirtualDisplay@`, с завершением полноэкранного дубля и защёлкой
однократного редиректа (`REDIRECTED`).

Проверка без сессии (21:53–21:56): хук не вызывается (`requestCarPlayUI →
launchCarPlay NONE → session == null`); ручной `LAUNCH_APP` кладёт MainActivity
fullscreen в Stack #0 (чёрный экран; `p4_acts.txt`, `wp3.png`) — ожидаемое
поведение p3 без сессии.

Решающий тест с живой USB-сессией (22:04–22:07, `carplay0` LOWER_UP):

- 22:04:56.922 хук сработал в процессе view (19896): `ContextImpl.startActivity:906
  ← WindowSurface.startInWindow:166 ← CarPlayApplication$1.onVideoStatusChange:89`;
- 22:04:56.945 AMS принял `START u0 {flg=0x18000000}`; 22:04:57.075
  `CAR.AM: New top task … taskId=1324, stackId=1` — задача CarPlayView создана
  **в окне на Display #1**; 22:04:57.183 `ActivityViewVirtualDisplay` → ON
  (не OFF, как без сессии); 22:04:57.307 `fit16x9=1327x746` — геометрия окна p3;
  fullscreen-дубль в Stack #0 не появился. ~1.2 с всё работало;
- 22:04:57.056–059 `SourceMngService` (по `onTaskStackChanged` после старта,
  `setM_curUIDByActivity packageName=com.adayo.carplay.view`) ответил
  `checkToStoreUI AppInfo is null or UnStore sourceType=ADAYO_SOURCE_CARPLAY_MEDIA`
  → откат `curUID=ADAYO_SOURCE_HOME`; лаунчер `onRestart mNeedRestartMainPage=true`;
  22:04:57.635 старт HOME, 22:04:57.688 перезапуск carsettings в ActivityView;
  22:04:58.176 SurfaceFlinger уничтожил слой `MainActivity t1324` — окно
  вернулось к carsettings за ~1.1 с после запуска;
- конечное состояние 22:07 (`p4_acts3.txt`, `wp4.png`): carplay.view в задачах
  нет нигде, Display #1 resumed carsettings t4 (1327×865).

Причина подтверждена логом (`p4_log4.txt` — полный буфер 21:56–22:38, 14 МБ):
прямой `startActivity` в обход лаунчера не регистрирует источник в
SourceMngService (`AppInfo … UnStore`), тот откатывается на HOME, лаунчер
возвращает окно к последнему оконному приложению и убивает задачу. Путь ярлыка
(p3) держит окно именно потому, что лаунчер сам выполняет `launchActivityViewApp`
и согласует источник.

Хвост 22:08:52–53 (ручной тап по иконке CarPlay при живой защёлке): тап запустил
MainActivity fullscreen (`cat=LAUNCHER`), VirtualDisplay OFF, хук ответил
`redirect suppressed`, `startInWindow display=-1` — защёлка `REDIRECTED` от
22:04:57 не сбросилась и оставила fullscreen-состояние.

**Вердикт: механика p4 корректна** (окно, displayId, геометрия, без fullscreen-дубля),
**но окно не удерживается stock-логикой лаунчера — p4 не принимается.**

Сборка **p5** (база p3 + заменённая редирект-ветка): при отсутствии живой оконной
активити патч завершает все живие (fullscreen) активити и отправляет лаунчеру
`LAUNCH_APP` (`app_pkg`/`app_cls`) — обкатанный путь ярлыка, при котором лаунчер
сам открывает окно и регистрирует источник; доставку `surfaceId` выполняет
повторный callback стека через первую ветку `startInWindow` (существующая оконная
активити, in-place `onNewIntent`). Защёлка `REDIRECTED` обезврежена (оба исхода
CAS ведут в путь делегирования), ветка `redirect suppressed` недостижима;
делегированный путь помечается в логе `startInWindow display=-2`. Урок AA p2
(§7.7 — циклический `LAUNCH_APP` из `onResume`) учтён: реле срабатывает только в
callback стека при отсутствии живой оконной активити, что разрывает цикл.

`CarPlayView_p5_signed.apk`: platform-подпись подтверждена (`apksigner verify`,
SHA-256 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`, как у
AA p5), md5 `bc5ce29096eef28d8a68709bf90f9dfc`, 206167 байт. Установлен в
`/system/app/CarPlayView/CarPlayView.apk` поверх p4 (`patch_cpv.sh`:
`CPVPATCH_OK`, md5 на ГУ `bc5ce29096eef28d8a68709bf90f9dfc`; сток-резерв
`2718803663e016a969e6d2fa0d2b4c16` не тронут; процесс view остановлен перед
записью — поднимется стеком на следующей сессии). Поведение p5 проверяется следующей
живой CarPlay-сессией по цели из шапки: **окно с барами удерживается всю сессию**
(оба сценария старта), `startInWindow display=-2`, тач работает; до приёмки ассет
`:winpatch` остаётся на CP p3. Артефакты в
`Temp\rufp`: `carplayview_p5_dec`, `CarPlayView_p5{,_al,_signed}.apk`,
`p4_log4.txt`, `p4_acts.txt`/`wp3.png` (без сессии), `p4_acts3.txt`/`wp4.png`
(живая сессия), `carplayview_p4_dec`.

### 7.7. Android Auto p1–p5: оконный рендерер и маршрутизация (2026-10-18)

Исходный APK сохранён на ГУ в `/data/local/tmp/AndroidAuto_orig_backup.apk`,
md5 `821cf2db51435747bd974a81fd52d1bb`. Рабочие исходники и сборки находятся в
`Temp\rufp`: `AAWindowPatch.java`, `androidauto_dec`, `aahelper.zip`,
`AndroidAuto_p1{,_al,_signed}.apk` … `AndroidAuto_p5{,_al,_signed}.apk`,
`patch_aa.sh`. Это локальные артефакты исследования, не файлы репозитория.

Общие изменения Android Auto:

- `AAWindowPatch.viewport` вычисляет aspect-fit по фактическим размерам родителя
  SurfaceView и прямоугольнику видео; `AAProjectionRenderer` использует этот viewport;
- `AAWindowPatch.touch` преобразует копию MotionEvent из размеров поверхности в
  координаты видео; реальная точность попадания и свайпов пока не проверена;
- отключено штатное скрытие системных баров; `AAActivity` отслеживается helper-классом;
- `ApiServiceManager` использует helper для запуска UI; начиная с p2, `onResume`
  проверяет дисплей, а `AAActivity` имеет `launchMode="singleTop"` вместо `singleTask`.

Ход проверки и причины последующих исправлений:

1. **p1**, md5 `b47ebdd0a56953d215217fbac7cb7485`: запуск на Display #1
   первоначально подтверждён, но скриншот закрыт предложением обновления прошивки.
   Позже source-manager запустил AAActivity на Display #0
   (`launchedFromPackage=com.adayo.service.sourcemngservice`), минуя helper.
2. **p2**, md5 `380839b160c0a48743c08ae6e0e61c0b`: добавленный возврат через
   `LAUNCH_APP` оказался циклическим. В дампе обнаружены десятки завершающихся
   AAActivity на Display #0; бинарный logcat подтвердил повторение
   `start via launcher` / `redirect external launch from display=0`.
   Для остановки цикла пакет временно отключён через `pm disable-user`, затем
   включён обратно. **p2 использовать нельзя.**
3. **p3**, md5 `e6c527effb3e4cfc3ce9dbd5ff9b93e3`: поиск существующего
   `ActivityViewVirtualDisplay@…` через DisplayManager, явный displayId,
   `NEW_TASK | MULTIPLE_TASK`, метка однократного перенаправления и обработка ошибки.
   Цикл устранён; явная ADB-команда `am start --display 1 -f 0x18000000 -n
   com.adayo.androidauto/.ui.AAActivity` действительно создавала окно 1327×865.
   Автоматический переход при этом ещё не закреплялся на нужном дисплее.
4. **p4**, md5 `0ca6fc37a46629ea08095165d0acca05`: запуск через application context
   и замена исходных flags. Само по себе это также не обеспечило оконный переход.
5. **p5**, md5 установленного `/system/app/AndroidAuto/AndroidAuto.apk`
   **`f91c1def77c9105db35eed2dc0732c27`**: перед запуском завершается исходная
   полноэкранная активити; helper получает stackId существующего виртуального
   дисплея через `IActivityManager.getAllStackInfos` и пытается задать
   `ActivityOptions.setLaunchStackId` вместе с displayId. После чистой загрузки
   рефлексия `setLaunchStackId(int)` завершилась `NoSuchMethodException`;
   helper обработал исключение и продолжил с `setLaunchDisplayId`.
   Доступность этого скрытого метода **не подтверждена**, и успешное размещение
   окна нельзя приписывать установке stackId. Переносимость не подтверждена.

Для всех установленных сборок проверены platform-подпись и совпадение md5 после
записи с отключением/восстановлением eMMC WP. Для p5 `apksigner verify` подтвердил
v2/v3 и SHA-256 сертификата
`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`.

**Что подтверждено для p5:** обычная команда `LAUNCH_APP` создала AAActivity
`t1311`, `stackId=1`, **Display #1**, конфигурация **1327×865**,
`mResumed=true`, `mFinished=false`. Повторный снимок состояния сохранил задачу на
Display #1; прямой `am start -n` был доставлен существующей оконной активити.
Это проверка размещения окна, а не приёмка живой проекции.

**Сбои и ограничения текущей сессии:**

- после p2 зарегистрирован реальный native crash `system_server`:
  `/data/system/dropbox/system_server_native_crash@1792275837139.txt.gz`,
  SIGSEGV в `libandroidfw.so`, `LoadedPackage::GetEntryOffset`, при создании
  splash screen (`PhoneWindowManager.addSplashScreen`). Стек не доказывает,
  что причиной был именно redirect-loop; замена APK по тому же пути при живых
  процессах также остаётся непроверенной гипотезой. Повторять такую замену при
  работающем AA не следует;
- после частых рестартов штатный `AAService` падал на `TcpDelegate.tryOpen`:
  provider не смог занять TCP 9527 (`EADDRINUSE`), вернул null, затем вызван
  `ServerSocket.setSoTimeout` на null. После паузы подтверждён успешный listener
  `:::9527` и запуск процесса; исправление TCP-кода в этот патч не включено;
- после успешного оконного запуска p5 процесс 1178 перестал отвечать на dump,
  затем оказался в состоянии **Z (zombie)**. Listener 9527 ещё отображался без
  PID. Причина завершения не установлена; в считанном crash buffer новой
  Java-ошибки p5 не было. `aa_p5_anr.txt` сохранён с ГУ, но содержит стеки других
  процессов, а не доказанный стек зависания Android Auto;
- `system_server` после восстановления оставался PID 22110. Отдельный последующий
  обрыв ADB сопровождался `init.svc.adbd=stopped`, а не сменой system_server;
  adbd восстановлен через COM21. Подключён USB-телефон Xiaomi Redmi 6, но факт
  его наличия не подтверждает поток Android Auto;
- обычный текстовый logcat в этой сессии не выдавал пригодный вывод, хотя
  `logcat -B` возвращал записи. Для анализа использован разбор бинарных заголовков
  и сообщений на ПК; пустой текстовый вывод не означает отсутствия ошибок.

**Проверка после согласованной перезагрузки (2026-10-18, 01:50–01:57 MSK):**

- ADB восстановлен; md5 установленного p5 повторно совпал с
  `f91c1def77c9105db35eed2dc0732c27`. `system_server` PID 2216 оставался тем же
  в последующих проверках; процесс AA PID 8836 слушал TCP 9527. Нового
  `EADDRINUSE` в этом интервале не подтверждено. Нагрузка оставалась высокой:
  load average около 19 / 15 / 7 при uptime около 6 минут;
- USB host обнаруживает Xiaomi Redmi 6. Логи различают транспорт и проекцию:
  `usbConnectionState=true` встречается вместе с `connectionState=false`.
  Это не доказательство работающей Android Auto-сессии;
- в 01:50:46 зарегистрированы `onByeByeRequest`, отключение
  `StopReason: USER_SWITCH`, затем повторяющийся
  `AHU.GAL_RECEIVER: Encountered unrecoverable error!`. Причина переключения
  и ошибка транспорта по этим сообщениям не установлены; это не доказательство
  ошибки viewport или необходимости разрешения на телефоне;
- в 01:51:38 helper зафиксировал `activity display=0`, не нашёл
  `setLaunchStackId(int)`, но продолжил с `start display=1` и получил
  `activity display=1`. В 01:51:48 штатный handler выполнил
  `MSG_IS_RESTART_APP:killProcess`. Последующий процесс в 01:51:51 установил
  USB transport, но сообщил `mAAServiceManager.getAAActivity()==null`.
  Упоминание размера 1920×720 в `initAAGal` — запрос конфигурации, а не
  свидетельство полученного или отображённого видеокадра;
- отдельная контролируемая попытка обычным `LAUNCH_APP` после восстановления
  создала задачу **t1317 на Display #1**, конфигурация **1327×865**,
  `mResumed=true`, `mFinished=false`, PID 8836. Повторная проверка спустя более
  10 секунд сохранила ту же задачу и PID: прежнее завершение через 10 секунд
  не следует считать неизбежным при каждом запуске;
- `dumpsys activity top` показывает SurfaceView 1327×865 и видимый слой
  `app:id/prompt`. Скриншот `Temp\rufp\aa_p5_recheck.png` в 01:57 показывает
  **«Запустите Android Auto»** с индикатором в настоящем окне лаунчера; верхний
  и нижний бары и левая колонка сохранены. Это экран ожидания, **не живая
  проекция**. Более ранний `aa_p5_reboot.png` показывал TBox Monitor;
- в считанных бинарных логах нет подтверждённого p5 viewport с согласованным
  видеокадром и нет проверенных попаданий или свайпов по живому интерфейсу.
  Текстовый logcat всё ещё непригоден для отрицательных выводов.

**Приёмка Android Auto не завершена:** восстановление после перезагрузки и
повторная проверка оконного запуска выполнены, но требуется работающая сессия
телефона, живое видео с aspect-fit и точные тапы/свайпы. Состояние экрана телефона
и физический тач удалённо не проверены. Следующая проверка с пользователем:
разблокировать телефон, завершить отображаемые шаги Android Auto, запустить
ярлык TBox Monitor и сообщить, появляется ли проекция, сохраняется ли окно,
правильны ли пропорции и попадания. Если сессии нет — нужен точный текст
сообщения на телефоне. p5 оставлен экспериментальным, пакет включён;
повторные рестарты и новые замены APK не выполнялись. CarPlay p3 и его
wireless-изоляция не изменялись.

Проверки репозитория, завершённые до этого продолжения:
`.\gradlew.bat :app:lintRuDebug :app:compileRuDebugKotlin --console=plain`
успешно выполнена; сохранённый `Temp\rufp\gradle_lint2.txt` содержит
`compileRuDebugKotlin UP-TO-DATE`, `lintRuDebug` и `BUILD SUCCESSFUL in 14m 5s`.
В `app/build/reports/lint-results-ruDebug.xml` нет issues severity Error/Fatal.
Повторный запуск не требовался: изменения этого продолжения — только отчёт.
`git diff --check` выполнен без ошибок; предупреждения LF→CRLF не являются
ошибками пробелов. Эти проверки не проверяют OEM smali или проекцию на ГУ.

### 7.8. Установщик `:winpatch`: постоянная установка/откат p5 и p3 (2026-10-18)

Для повторяемой установки пропатченных APK без ноутбука создан отдельный модуль
**`:winpatch`** — приложение «AA/CarPlay окна ГУ» (`vad.dashing.winpatch`, v0.1.0),
архитектурная копия `:fwunlock` (Compose-экран, ViewModel, `RootShell` через
`:adbcore`: собственный adbd ГУ на 127.0.0.1:5555 и `su 0 sh -c ...`).

- Патченные APK зашиты в assets модуля и сверяются по MD5: `AndroidAuto_p5.apk`
  (`f91c1def77c9105db35eed2dc0732c27`), `CarPlayView_p3.apk`
  (`e9c8d958178875c0712f6bd0d39ae8a2`). Стоковые md5 целей:
  `821cf2db51435747bd974a81fd52d1bb` / `2718803663e016a969e6d2fa0d2b4c16`.
- Применение: staging assets → `getExternalFilesDir` (локальная проверка MD5 до
  заливки), затем сгенерированный в приложении скрипт `apply.sh` в рабочей папке
  `/data/local/tmp/winpatch`. Скрипт: preflight **до** снятия eMMC WP — копия
  стейджа из мирочитаемого `/sdcard/Android/data/...` в root-only рабочую папку
  с md5-сверкой (`stage_*`), запись идёт только из этой копии; `ensure_backup`
  перенимает легаси-резервы `/data/local/tmp/AndroidAuto_orig_backup.apk` и
  `CarPlayView_orig_backup.apk` (только со стоковым md5) как `*.orig.apk`;
  резерв создаётся через `*.tmp` + md5 + `mv`, битый резерв удаляется с отказом
  `badbackup_*` (следующий запуск пересоздаст его со стокового `/system`).
  Запись атомарная: проверка места (`df -k`), `<apk>.new` в той же папке +
  `chown root:root` + `chmod 644` + `chcon u:object_r:system_file:s0` + `sync` +
  `drop_caches` + md5 `.new`, затем `mv` поверх цели (запущенный AA/CarPlay
  держит старый inode). Если после `remount,rw` что-то упало (или пришёл сигнал),
  цель с md5 «ни сток, ни патч» восстанавливается из резерва тем же атомарным
  путём (код `… rolledback` / `… norollback`). Идемпотентный `wp_on` делает
  `remount,ro /` и возврат WP на любом выходе (`trap … EXIT` + `HUP INT TERM PIPE`).
  При отсутствии резерва и не-стоковом `/system` — отказ `nobackup_*`
  (единственный источник отката не перезаписывается).
- Откат `restore.sh` — обратная запись из `*.orig.apk` с той же защитой.
- После записи скрипт перезапускает процессы `com.adayo.androidauto` и
  `com.adayo.carplay.view`; перезагрузка ГУ — отдельная кнопка
  (`su 0 sh -c 'sync; reboot'`).
- `CarPlayManagerConfig.json` установщик **не изменяет** — текущий md5 показывается
  в фактах для контроля (сток `71082dfb83baf61eb449297d4df588ee`).
- Результат сообщается маркерами `TBOX_WINPATCH_APPLIED/RESTORED/ERR` и
  контрольными `md5sum` целей.
- Верификация: `WinPatchCodecTest` (8 тестов: генерация apply/restore, парсинг
  исходов, отказы `nobackup_*`), `:winpatch:testDebugUnitTest` 8/8,
  `:winpatch:assembleDebug`, `:winpatch:lintDebug` — чисто.

Установка на ГУ (2026-10-18): `adb install -r` (streaming-протокол) на этом HU
**зависает** — 180 с без вывода, пакет не встаёт; рабочий путь:
`adb push ... /data/local/tmp/winpatch-debug.apk` + `pm install -r`
(`Success`, v0.1.0). Статус-экран на ГУ корректно распознал состояние: оба APK
«патч установлен», резервы «в /data/local/tmp (переймутся при установке)»,
конфиг не изменяется. Нажатие «Установить» перепишет те же p5/p3 (идемпотентно)
и перенесёт легаси-резервы в рабочую папку — после этого доступен откат.

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

**Статус: задача не завершена.** Критерий готовности — CarPlay **и** Android Auto
в ActivityView лаунчера при живой сессии, запуск ярлыком TBox Monitor, оба бара
видны, изображение без искажения пропорций, тач согласован с изображением.
CarPlay p3 принят пользователем в настоящем ActivityView (§7.6). Android Auto p5
после чистой загрузки повторно достиг Display #1 и размеров 1327×865;
окно сохранялось, но осталось на экране «Запустите Android Auto». Ранее
зафиксировано штатное завершение процесса; живая проекция и тач не приняты (§7.7).
Эксперимент с потоком CarPlay 1920×864 отменён.

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
- `CarPlayView` p3 установлен и принят пользователем: живая сессия в ActivityView,
  aspect-fit 1327×746 с полями, тач и запуск ярлыком TBox Monitor работают (§7.6).
  Ранее наблюдавшийся уход p2 на Display #0 — исторический промежуточный результат.
- `AndroidAuto` p5 установлен, platform-подпись и md5 проверены. Переход в
  stackId=1 / Display #1 / 1327×865 подтверждён; после чистой загрузки окно
  повторно сохраняется с видимыми барами, но показывает ожидание запуска AA.
  Живая проекция и физический тач остаются непроверенными (§7.7).
  p2 с redirect-loop не использовать.
- Постоянная установка/откат готовых p5/p3 — приложение `:winpatch` (§7.8): APK в
  assets с фиксированными MD5, WP-танец по образцу `:fwunlock`, перенимание
  легаси-резервов, отказ без резерва, кнопка отката и перезагрузки. Установка на
  ГУ — `adb push` + `pm install -r` (`adb install -r` зависает).
