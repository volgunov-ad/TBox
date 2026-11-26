# Замер семантики `mb_daemon` на A9 (W0)

Скрипт для стенда Андрея: ПК в одной LAN с ГУ A9, ADB по Wi‑Fi. Нужен, чтобы зафиксировать поведение канала `/sdcard/autorun.sh` перед протоколом захвата (W1) и таймингами сторожа (W3). План: §13 в shared-root-service-plan.

Фоновая теория канала: [docs/HEAD_UNITS_DEVICE_NOTES_RU.md](../../docs/HEAD_UNITS_DEVICE_NOTES_RU.md) §10.1. Общие правила `hu_perf`: [docs/HU_PERF_TESTS_RU.md](../../docs/HU_PERF_TESTS_RU.md) §4.

## Что нужно на ПК

- Windows PowerShell 5.1+ (как у остальных `tools/hu_perf/*.ps1`)
- `adb` в `PATH` (достаточно platform-tools)
- ГУ A9 в той же сети, TCP ADB включён (обычно `192.168.1.128:5555`)

Скрипт **не** ставит APK и **не** пишет в `/system`. На время прогона лучше не трогать ГУ руками (лаунчер/camd/UsbMount тоже пишут в `autorun.sh`).

## Быстрый прогон (без перезагрузки)

На ПК:

```powershell
cd <репозиторий>\tools\hu_perf
adb connect 192.168.1.128:5555
.\hu_mb_daemon_bench.ps1 -Device 192.168.1.128:5555
```

По умолчанию suite `all`:

| Блок | Что отвечает на вопрос §13 |
|------|----------------------------|
| `arm_latency` | задержка взвода → маркер (5 повторов, опрос ~50 мс) |
| `delete_timing` | когда `mb_daemon` удаляет `autorun.sh` (до/после середины скрипта) |
| `poll_while_armed` | подхватывает ли демон скрипт, записанный **после** уже взведённого свойства |
| `rearm_same_prop` | повтор без нового `setprop` и полный повторный взвод подряд |
| `rename_atomic` | атомарность `mv` на `/sdcard` + перезапись чужим скриптом посреди `sleep` |
| `race_two_writers` | два почти одновременных писателя — один маркер или оба |

Результаты: `tools/hu_perf/hu_mbd_bench_results/<tag>/`

- `results.txt` — сводка `ключ=значение`
- `bench.log` — ход на ГУ
- `meta.txt` — устройство, tag, время
- при `-Boot` дополнительно `first_arm_after_boot.txt`, `a11y_hits.txt`, дампы accessibility и boot-логи

Один suite:

```powershell
.\hu_mb_daemon_bench.ps1 -Suite arm
.\hu_mb_daemon_bench.ps1 -Suite delete
.\hu_mb_daemon_bench.ps1 -Suite poll
.\hu_mb_daemon_bench.ps1 -Suite rename
```

## С перезагрузкой (Accessibility + первый ответ `mb_daemon`)

Точное время подключения спецвозможности и готовность `mb_daemon` после boot **ещё не мерили** (§13). Этот режим:

1. Чистит logcat, перезагружает ГУ, ждёт `sys.boot_completed`
2. Снимает `dumpsys accessibility` и ищет в logcat строки bind/`onServiceConnected` для camd / DCL / Monitor / UsbMount / dcl_install (или `-A11yPkg`)
3. Сразу после ADB начинает короткие взводы (`-Suite arm`) раз в 2 с, пока не получит `result=ok`, и пишет попытку / host_s / uptime
4. Затем гоняет полный suite `all` уже в «тёплом» состоянии

```powershell
.\hu_mb_daemon_bench.ps1 -Boot -Device 192.168.1.128:5555
# только свой пакет спецвозможности в фильтре logcat:
.\hu_mb_daemon_bench.ps1 -Boot -A11yPkg vad.dashing.camd
```

Прогон с `-Boot` занимает несколько минут (ожидание загрузки + до ~80 с на поиск первого arm + suite).

## Как читать ответ

Ориентиры для W1/W3 (что прислать / сохранить):

1. **`arm_N=... from_setprop_ms=... result=ok`** — типичная задержка после `setprop` (ожидание: сотни мс … единицы секунд; клиенты ждут до 8 с с шагом 200 мс).
2. **`autorun_exists_at_script_start` / `autorun_exists_mid` / `autorun_gone_ms` / `gone_after_script_start`** — удаление до, во время или после выполнения.
3. **`poll_armed_result`** — `ok` значит демон опрашивает файл при уже взведённом свойстве (важный комментарий в `CameraWindows.arm`); `timeout` — нужен повторный фронт `setprop`.
4. **`rearm_without_setprop`** — можно ли повторно запустить только записью файла.
5. **`rename_torn_samples`** — `0` хорошо для протокола «тело в своём файле + `rename` autorun»; ненулевое — на sdcardfs rename не считать атомарным без оговорок.
6. **`race_both_ran` / `race_final_marker`** — параллельность vs «последний писатель победил».
7. При `-Boot`: **`first_arm_ok_attempt` / `host_s` / `uptime`** и строки в `a11y_hits.txt` — спецвозможность раньше или позже первого успешного взвода.

После прогона канал очищается (`autorun.sh` / флаг / свойство). Чужой скрипт, который висел дольше 10 с ожидания, мог быть перезаписан — на спокойном ГУ без ручных действий это редко.

## Файлы

| Файл | Роль |
|------|------|
| `hu_mb_daemon_bench.ps1` | драйвер с ПК (ADB connect, опциональный reboot, pull) |
| `hu_mb_daemon_bench.sh` | замеры на ГУ (`/data/local/tmp/…`) |
| `hu_mbd_bench_results/` | вывод (в `.gitignore` рядом с `hu_perf_results/`) |
