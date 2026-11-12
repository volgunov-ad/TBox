# Помощник окон DCL

Инструкция для разработчика Dashing Club Launcher (`ras.dashing.tbox.launcher`).

Помощник — отдельный процесс от root. Он не рисует интерфейс и не запускает приложения. Лаунчер по-прежнему сам вызывает `startActivity`. Помощник только видит чужие задачи и двигает их стеки: список, прямоугольник, убрать за экран, закрыть стек. Этого на головном устройстве Android 9 не может сделать процесс самого лаунчера.

Проверено 12.11.2026 на ГУ `192.168.1.128` (сборка с `ro.build.version.sdk=28`, лаунчер `0.7.72-ru`, uid `10098`).

## Что умеет прошивка

| Право | Уровень | Есть у DCL | Следствие |
|---|---|---|---|
| `REORDER_TASKS` | `normal` | да | само право на перестановку задач выдано |
| `MANAGE_ACTIVITY_STACKS` | `signature\|privileged\|development` | да, через `pm grant` | `resizeTask` от uid лаунчера проходит, если номер задачи уже известен |
| `ACTIVITY_EMBEDDING` | `signature\|privileged\|development` | да | виртуальный дисплей лаунчер создать может, см. ниже |
| `REAL_GET_TASKS` | `signature\|privileged` | нет | `dumpsys activity` из процесса лаунчера почти пустой, чужих `taskId` нет |
| `REMOVE_TASKS` | `signature` | нет | `removeTask` из лаунчера не вызвать |

`enable_freeform_support=1`, `force_resizable_activities=1`, `policy_control=immersive.full=*`.

Свободное окно живёт в своём стеке поверх домашнего. `move-task` в домашний стек система запрещает даже root: `Attempt to move task … to stack 0`. Прямоугольник `0 0 1 1` не прячет окно: прошивка оставляет минимум 88×88 пикселя на экране. Рабочий способ спрятать окно — `am stack resize` в прямоугольник за экраном `2000,2000–2200,2200`. Задача при этом жива. Вернуть её — снова задать прямоугольник стеку и задаче.

Обычную полноэкранную задачу `am task resize` не берёт: `resizeTask not allowed`. Окно должно быть открыто как freeform (`--windowingMode 5` / `setLaunchWindowingMode`). Каждый такой запуск на этом ГУ получает свой стек.

`ActivityView` на этом ГУ не выбран как основа. От uid лаунчера виртуальный дисплей с флагами «публичный + только своё содержимое + касание» создаётся, в `dumpsys display` у него `touch VIRTUAL` и есть запись в `mVirtualTouchViewports`. Картинку внутри домашнего экрана и нажатие пальцем мы не довели: для этого нужна поверхность в активности лаунчера. Пока сворачивание делается помощником.

## Процесс

`mb_daemon` по-прежнему выполняет один короткий скрипт и заканчивается. Скрипт поднимает `app_process` через `setsid` и сразу выходит. Дальше процесс слушает абстрактный сокет `dclwm` и принимает команды, пока ГУ не перезагрузят.

Сокет пускает только uid `0` и uid пакета `ras.dashing.tbox.launcher`. Чужой uid получает `ERR forbidden`. Имя пакета зашито в `Protocol.LAUNCHER_PACKAGE`.

Команды помощника не проходят через оболочку строкой: аргументы `am` и `pm` — отдельные параметры.

## Протокол

Одно соединение — одна команда. Строка запроса, перевод строки, ответ и закрытие записи.

| Запрос | Ответ |
|---|---|
| `PING` | `OK` |
| `LIST` | `OK`, затем строки `task …`, затем `END` |
| `RESIZE <taskId> <left> <top> <right> <bottom>` | `OK` или `ERR …` |
| `MINIMIZE <taskId>` | `OK` или `ERR …` |
| `DISMISS <taskId>` | `OK` или `ERR …` |

Строка задачи:

```
task id=406 stack=4 pkg=vad.dashing.tbox activity=vad.dashing.tbox.MainActivity mode=freeform bounds=344,37,1920,1000 visible=1
```

`bounds` — прямоугольник задачи. `mode` — `mWindowingMode` стека (`freeform` или `fullscreen`).

Коды ошибок, первая строка ответа:

| Строка | Когда |
|---|---|
| `ERR forbidden` | сокет открыл не root и не лаунчер |
| `ERR bad_args` | кривые числа, пустая строка, `left >= right` или `top >= bottom` |
| `ERR unknown` | неизвестная команда |
| `ERR not_found` | нет такой задачи |
| `ERR launcher` | это задача самого лаунчера, её не двигаем |
| `ERR am …` | `am` завершился с ошибкой или прямоугольник после команды не совпал |

`MINIMIZE` двигает весь стек этой задачи за экран. Если в стеке две задачи, уйдут обе. На проверенном ГУ отдельный freeform-запуск даёт отдельный стек, поэтому сплит из двух запусков прячется двумя `MINIMIZE`. `DISMISS` делает `am stack remove` и задачу закрывает.

Координаты ограничены диапазоном −100…4000. Прямоугольник за экраном для `MINIMIZE` помощник подставляет сам.

## Что вызвать из лаунчера

Кнопка «домой»:

1. `LIST`.
2. `MINIMIZE` каждой задачи, чей пакет не `ras.dashing.tbox.launcher`.
3. Показать стол и выставить `screenMode=home`.

Режимы, которые лаунчер уже различает, остаются его прямоугольниками. Запуск — свой `startActivity` с freeform. Сразу после появления задачи в `LIST`:

| Режим | `RESIZE` |
|---|---|
| окно приложений | прямоугольник между барами и правее ADAS |
| по ширине | вся ширина между верхним и нижним баром |
| полный экран | весь кадр, например `0 0 1920 1080` |
| сплит | два `RESIZE`, левый и правый прямоугольники |

Если `RESIZE` вернул `ERR am` с текстом `resizeTask not allowed`, задача была открыта на весь экран обычным запуском. Её нужно открыть заново как freeform и повторить `RESIZE`.

Помощник не вызывает `startActivity`. Запуск от uid 0 приходит в чужое приложение как вызов от root.

## Как встроить

Исходники и готовый dex лежат в репозитории [TBox](https://github.com/volgunov-ad/TBox), ветка `preRelease`:

- [docs/DCL_WINDOW_HELPER_RU.md](https://github.com/volgunov-ad/TBox/blob/preRelease/docs/DCL_WINDOW_HELPER_RU.md) — этот текст
- [tools/dclwm/src/vad/dashing/dclwm/Helper.java](https://github.com/volgunov-ad/TBox/blob/preRelease/tools/dclwm/src/vad/dashing/dclwm/Helper.java) — процесс, который слушает сокет
- [tools/dclwm/src/vad/dashing/dclwm/Session.java](https://github.com/volgunov-ad/TBox/blob/preRelease/tools/dclwm/src/vad/dashing/dclwm/Session.java) — команды и вызовы `am`
- [tools/dclwm/src/vad/dashing/dclwm/Client.java](https://github.com/volgunov-ad/TBox/blob/preRelease/tools/dclwm/src/vad/dashing/dclwm/Client.java) — клиент для процесса лаунчера
- [tools/dclwm/src/vad/dashing/dclwm/Starter.java](https://github.com/volgunov-ad/TBox/blob/preRelease/tools/dclwm/src/vad/dashing/dclwm/Starter.java) — запуск через `mb_daemon`
- [tools/dclwm/dclwm.dex](https://github.com/volgunov-ad/TBox/blob/preRelease/tools/dclwm/dclwm.dex) — dex для `app_process`
- [tools/dclwm/build.ps1](https://github.com/volgunov-ad/TBox/blob/preRelease/tools/dclwm/build.ps1) — пересборка dex и проверки на компьютере

Рядом по устройству: [docs/HEAD_UNITS_DEVICE_NOTES_RU.md](https://github.com/volgunov-ad/TBox/blob/preRelease/docs/HEAD_UNITS_DEVICE_NOTES_RU.md) §10 (`mb_daemon`), [docs/LAUNCHER_A9_APP_WINDOW_RU.md](https://github.com/volgunov-ad/TBox/blob/preRelease/docs/LAUNCHER_A9_APP_WINDOW_RU.md) (как штатный лаунчер выбирает дисплей). Контракт Monitor → DCL не меняется: [docs/DCL_LAUNCHER_API_FOR_VENDOR_RU.md](https://github.com/volgunov-ad/TBox/blob/preRelease/docs/DCL_LAUNCHER_API_FOR_VENDOR_RU.md).

Шаги в проекте лаунчера:

1. Скопировать каталог `tools/dclwm/src/vad/dashing/dclwm/` в исходники лаунчера с тем же пакетом `vad.dashing.dclwm`. В приложении используются `Starter`, `Client`, `WindowTask`, `Protocol`, `ArmScript`. `Helper` в APK не вызывается, он нужен только внутри dex.
2. Положить [dclwm.dex](https://github.com/volgunov-ad/TBox/blob/preRelease/tools/dclwm/dclwm.dex) в `assets/dclwm.dex`. Имя файла менять нельзя: его ждёт `Protocol.DEX_ASSET`.
3. На старте процесса и в `BOOT_COMPLETED`, не с главного потока, вызвать `Starter.ensure(context)`. Он копирует dex в файлы приложения, взводит `vendor.autorun.sdcard.logfile` и ждёт маркер. Скрипт короткий: поднимает процесс и удаляет себя из `/sdcard/autorun.sh`, чтобы не пересечься с другими сценариями `mb_daemon`.
4. Кнопку «домой» и смену режима перевести на `Client`. Свой `Runtime.exec("am …")` и попытки `moveTaskToFront` для чужих задач убрать: они идут от uid лаунчера и либо не видят задачи, либо не двигают стек.
5. После правки помощника пересобрать dex: `tools/dclwm/build.ps1`. Скрипт гоняет проверки разбора `am stack list` и команд, затем собирает `dclwm.dex` под API 28.

Ручная проверка с компьютера, процесс уже запущен от root:

```
adb shell
export CLASSPATH=/data/local/tmp/dclwm.dex
export DCLWM_UID=10098
app_process /system/bin vad.dashing.dclwm.Client LIST
app_process /system/bin vad.dashing.dclwm.Client MINIMIZE 406
app_process /system/bin vad.dashing.dclwm.Client RESIZE 406 344 37 1920 1000
```

`DCLWM_UID` читает только `main` клиента, для отладки. Боевой лаунчер ходит в сокет своим uid и переменную не ставит.

## Проверка на ГУ 12.11.2026

Помощник был запущен тем же скриптом, который собирает `Starter`. От uid лаунчера `10098`:

- `LIST` вернул свободное окно TBox `task 406`, прямоугольник `344,37–1920,1000`, и задачу домашнего экрана.
- `MINIMIZE 406` вернул `OK`. Окно TBox ушло, на экране остался стол лаунчера.
- `RESIZE 406 344 37 1920 1000` вернул `OK`. Окно встало на прежнее место.

В журнал помощник записал и чужие uid (`2000`, `10100`): до команд `am` они не доходят. Ответ `ERR forbidden` для них проверяется на компьютере в `SessionTest`. Процесс `app_process`, который после старта от root сам делает `setUid` на чужой uid, это ГУ убивает сигналом 137 ещё до печати ответа. На боевой путь лаунчера это не влияет: лаунчер подключается своим uid.

После проверки процесс помощника на ГУ остановлен, dex из `/data/local/tmp` удалён. Постоянно его поднимает уже сам лаунчер через `Starter.ensure`.
