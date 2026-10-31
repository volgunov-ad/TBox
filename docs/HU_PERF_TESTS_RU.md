# Замеры скорости запуска и нагрузки CPU на ГУ (перезагрузка + переключение окон)

Методика и скрипты для сравнения приложения на ГУ «до/после» (например, до и после `cmd package compile -m speed`). Проверено на A9 (`192.168.1.128:5555`, Qualcomm SA6155P, 8 ядер, Android 9 / API 28) 20.10.2026 для Dashing Club Launcher 0.7.0-ru и TBox Monitor 1.1.0-ru. Про компиляцию на ГУ и почему фоновой dexopt нет — [HEAD_UNITS_DEVICE_NOTES_RU.md](HEAD_UNITS_DEVICE_NOTES_RU.md) §3.

Скрипты — `tools/hu_perf/`:

| Файл | Где работает | Что делает |
|------|--------------|------------|
| `hu_perf_run.ps1` | ПК (PowerShell, `adb` в `PATH`) | один прогон: перезагрузка ГУ, ожидание `sys.boot_completed`, сбор логов, запуск двух скриптов ниже |
| `hu_perf_sample.sh` | ГУ (`/data/local/tmp`) | каждые 5 с до заданного аптайма: строка `cpu` из `/proc/stat`, `utime+stime` процесса пакета, loadavg; `top` в начале и в конце |
| `hu_perf_switch.sh` | ГУ | N циклов «`am start -W` активности → 3 с → BACK → 3 с», CPU до/после, `dumpsys gfxinfo` пакета (сброс перед циклами) |
| `hu_perf_analyze.py` | ПК (Python 3, stdlib) | сводная таблица по папкам прогонов, колонки рядом |

## 1. Порядок

1. **Увеличить буфер логов** (иначе к подключению adb по Wi-Fi, ~55–60 с аптайма, спам mbCAN/chatty вытесняет первые кадры приложения из 256 КБ main):
   ```
   adb shell setprop persist.logd.size 16M
   ```
   Настройка переживает перезагрузку. После тестов вернуть: `adb shell "setprop persist.logd.size ''; logcat -G 256K"`.
2. **Состояние «до».** Сбросить компиляцию через `cmd package compile --reset` без root нельзя (`Only the system can clear all profile data`). Состояние «как после установки» (`[status=speed-profile] [reason=install]`, профиль почти пуст) получается переустановкой того же APK поверх, данные сохраняются:
   ```
   adb pull /data/app/<pkg>-*/base.apk app.apk
   adb install -r -t app.apk
   ```
   (`-t` нужен для APK, поставленных из Android Studio с `TEST_ONLY`). Альтернатива без переустановки — `cmd package compile -m verify -f <pkg>` (только интерпретатор + JIT, хуже, чем после установки).
3. **Два прогона «до»**, затем компиляция, затем **два прогона «после»**. Первый прогон сразу после установки нового APK считать прогревом. Прогоны запускать **строго по очереди**: два параллельных `hu_perf_run.ps1` попадают на одну загрузку и мешают друг другу в цикле окон.
   ```
   cd tools/hu_perf
   .\hu_perf_run.ps1 -Tag before1 -Pkg vad.dashing.tbox -Act vad.dashing.tbox.MainActivity
   .\hu_perf_run.ps1 -Tag before2 -Pkg vad.dashing.tbox -Act vad.dashing.tbox.MainActivity
   adb shell cmd package compile -m speed -f vad.dashing.tbox
   .\hu_perf_run.ps1 -Tag after1 -Pkg vad.dashing.tbox -Act vad.dashing.tbox.MainActivity
   .\hu_perf_run.ps1 -Tag after2 -Pkg vad.dashing.tbox -Act vad.dashing.tbox.MainActivity
   python hu_perf_analyze.py --pkg=vad.dashing.tbox hu_perf_results/before1 hu_perf_results/before2 hu_perf_results/after1 hu_perf_results/after2
   ```
   Параметры `hu_perf_run.ps1`: `-Device` (по умолчанию `192.168.1.128:5555`), `-OutDir` (по умолчанию `.\hu_perf_results`), `-SampleEnd` (аптайм конца сэмплинга, 180 с), `-Cycles` (10). Один прогон ≈ 4,5 мин.

Для лаунчера: `-Pkg ras.dashing.tbox.launcher -Act vad.dashing.tbox.MainActivity` (экран настроек лаунчера; BACK возвращает на `LauncherHomeActivity`; кнопка HOME настройки не закрывает — они в той же задаче).

## 2. Что измеряется

Из `events`-лога (`-v monotonic`):

- `boot_progress_enable_screen`, `wm_boot_animation_done` — контроль, что сама загрузка ГУ не изменилась;
- `am_proc_start` пакета — аптайм старта процесса.

Отметки времени логов до установки часов от MCU сдвинуты (отрицательные `monotonic`); анализатор пересчитывает их через `boot_progress_start`, значение которого — истинный аптайм в мс.

Из `main`-лога (по PID процесса):

- первый `OpenGLRenderer: Davey!` — `FrameCompleted` (нс аптайма) = первый кадр, `duration` = длительность этого кадра;
- `Choreographer: Skipped N frames` — сколько кадров главный поток пропустил на старте;
- `Compiler allocated … to compile` — JIT-компиляции крупных методов на старте (после `speed` их нет).

Метки `TboxTimings` TBox Monitor из начала загрузки ненадёжны: chatty их иногда вырезает.

Из сэмплера:

- `proc_cpu_at_adb_s` / `proc_cpu_at_end_s` — накопленное CPU процесса (с) к подключению adb (~16–18 с после старта процесса) и к ~180 с аптайма;
- `boot_window_*` — от подключения adb до конца; `idle60_*` — последние 60 с;
- `*_sys_pct` — загрузка всех 8 ядер (`busy / total` по `/proc/stat`, idle = idle + iowait); `*_proc_core_pct` — процесс в % одного ядра (100 % = одно ядро целиком).

Из цикла переключения окон:

- `open_ms_*` — `WaitTime` из `am start -W` (на A9 `TotalTime` печатается не всегда); `open_ms_first` — первое открытие после загрузки (активность создаётся впервые);
- `switch_*` — CPU за 10 циклов (≈ 63 с), включая сам `am` (одинаково в обеих сериях);
- `gfx_*` — `dumpsys gfxinfo`: число кадров, доля janky, перцентили времени кадра.

## 3. Результаты A9, 20.10.2026

### 3.1. Dashing Club Launcher 0.7.0-ru (`ras.dashing.tbox.launcher`)

| | `speed-profile [install]` | `speed` |
|---|---|---|
| Старт процесса → первый кадр | 4,2 / 4,3 с | 3,3 / 2,9 с |
| Длительность первого кадра | 3,8 / 3,9 с | 2,5 / 2,4 с |
| Пропущено кадров на старте | 202 / 209 | 144 / 134 |
| CPU процесса к ~60 с аптайма | ~25–29 с | ~20 с |
| Лаунчер в покое, % ядра | ~103 | ~102 |
| Система в покое, % от 8 ядер | 23–24 | 24–25 |
| Открытие настроек, среднее / первое | 245–248 / 400–426 мс | 181–184 / ~200 мс |
| Кадры p50 / p90 / p99 | 30 / 36 / 81 мс | 30 / 36 / 53 мс |
| Доля janky | 91–93 % | 90–93 % |

Компиляция (`compile -m speed -f`) — ~6 с. Запуск и открытие настроек заметно быстрее, но постоянная нагрузка не меняется: на главном экране лаунчер держит ~1 ядро (главный поток, `FEngine::loop` — Filament 3D-модель, `RenderThread`), кадр ~30 мс (~33 кадра/с), 90 % кадров janky. Это непрерывная перерисовка 3D-модели — лечится ограничением частоты/отрисовкой по изменениям, не компиляцией.

### 3.2. TBox Monitor 1.1.0-ru (`vad.dashing.tbox`)

TBox Monitor стартует на загрузке как сервис (без окна, плавающие панели), поэтому «запуск» — это CPU процесса в первые секунды и первое открытие `MainActivity`.

| | `speed-profile [install]` | `speed` |
|---|---|---|
| Старт процесса (аптайм) | ~42,1 с | ~42,2 с |
| CPU процесса за первые ~16 с | 4,7–5,0 с | 1,7 с |
| CPU процесса к ~180 с аптайма | 18,0–18,2 с | 10,1–10,3 с |
| Фон, % ядра | 9,3–9,5 | 6,5–6,8 |
| Система в покое, % от 8 ядер | 24,5–25 | 24,7–24,9 |
| Открытие главного экрана, первое / среднее | 347–389 / 255–274 мс | 156–178 / 209–217 мс |
| CPU TBox при переключениях, % ядра | 22–24 | ~13 |
| Кадры p90 / p95 / p99 | 61 / ~90 / 200 мс | 36–38 / 48–53 / 69–81 мс |
| Пропущенные кадры при загрузке | 0 | 0 |

Компиляция — ~11 с. Стартовая работа втрое дешевле по CPU, фон −30 %, первое открытие главного экрана вдвое быстрее, хвост задержек кадров в 2,5–3 раза короче. Общую загрузку ГУ (~25 % от 8 ядер) определяет лаунчер.

### 3.3. Общее

- Статус `speed` переживает перезагрузку (`[status=speed] [reason=unknown]`), но любая установка/обновление APK возвращает `speed-profile` с почти пустым профилем — компиляцию повторять после каждого обновления.
- Сама загрузка ГУ одинакова во всех прогонах: `boot_progress_enable_screen` ~10,9 с, конец загрузочной анимации ~40,9 с, процессы TBox Monitor и лаунчера стартуют на ~42 с, adb по Wi-Fi — ~56–60 с.
- Лаунчер 0.6.9-en и 0.7.0-ru подписаны разными ключами: обновление поверх даёт `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, переход — только через удаление, данные лаунчера теряются.
