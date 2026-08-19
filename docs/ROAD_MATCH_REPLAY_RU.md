# Replay дорожной привязки по geo-debug

`tools/run_road_match_replay.py` прогоняет полевые `tbox_geo_debug_*.txt`
через **production Kotlin `RoadMatchRuntime`** и опубликованный `.tboxroads`
bundle. Это регрессионная проверка matcher после каждого изменения.

## Запуск

```bash
python tools/run_road_match_replay.py \
  --region ru-moscow \
  --logs /path/to/tbox_geo_debug_20260813_*.txt \
  --baseline tools/road_match_replay_baseline.json \
  --report /tmp/road_match_replay.json
```

Скрипт:

1. Читает публичный release key из `app/build.gradle.kts`.
2. Скачивает `/maps/catalog.json` и нужный v4 bundle в
   `~/.cache/tbox-road-replay`.
3. Временно устанавливает bundle с тем же layout, что приложение.
4. Запускает Robolectric-тест `RoadMatchFieldReplayTest`.
5. Печатает метрики и проверяет min/max baseline.

Можно не скачивать карту:

```bash
python tools/run_road_match_replay.py \
  --maps-dir /tmp/installed-road-maps \
  --logs /tmp/logs/*.txt
```

## Режимы движения позы (`--motion`)

| Режим | CLI | Путь | Курс | Калибровки |
|-------|-----|------|------|------------|
| **delta** (по умолчанию) | — | дельты `preMatch`/`mock` | как в журнале (уже с match-yaw) | не влияют на путь |
| **strip** | `--motion strip` / `--kinematic` | `integ.dDistM` | полевой гибрид минус `bearingDelta` тика | scale из лога не крутит yaw |
| **dr** | `--motion dr` (алиас `gyro`) | `dDistM × speedScale` | `dYawDebDeg × yawScale × yawSign` | **можно переопределить** |

### Открытый DR с независимой калибровкой

В журнале уже есть всё нужное для **сравнительного** ресимулятора на тиках 0,5 с
(не побитовая копия production-DR с ВЧ сэмплами):

- старт: `preMatch.*` или `truth.*` (`--seed`);
- путь: `integ.dDistM` (сырой CAN) × `drive.speedScale`;
- курс: `integ.dYawDebDeg` (debiased, без L/R scale) × `yawScale` × `yawSign`;
- скорость для matcher: `can.accountingKmh`;
- опора для метрик: `truth.*`.

```bash
# Тот же матчер, но yawScale=1.05 вместо значения из лога; без hardResync-snap
python tools/run_road_match_replay.py \
  --region ru-nizhny-novgorod \
  --logs /path/to/tbox_geo_debug_….txt \
  --motion dr \
  --yaw-scale 1.05 \
  --speed-scale 1.0 \
  --seed preMatch \
  --ignore-hard-resync \
  --report /tmp/replay_dr.json
```

Сравнение «куда приехали до/после правок матчера»: два прогона `--motion dr`
на одном журнале (один на старом коде / worktree, второй на новом) и смотреть
`finalLat/Lon`, `truthLag*`, `headingErr*` в JSON-отчёте. Финал сравнивают с
последним `truth.*` журнала.

По умолчанию `--motion dr` **не** применяет:
- GNSS `hardResync` (можно вернуть `--allow-hard-resync`);
- ручные подтяжки F3 `manualSeed=true` (можно вернуть `--allow-manual-seed`).

Движение только из `integ.*` + матчер; телепорты пользователя/GNSS в позу
не входят. В старых журналах без поля `manualSeed` флаг считается `false`
(ручные снэпы тогда видны только как скачок `preMatch` — на `dr` это всё равно
не влияет, т.к. путь из `dDistM`).

Переопределения также через env: `TBOX_ROADMATCH_REPLAY_YAW_SCALE`,
`…_YAW_SIGN`, `…_SPEED_SCALE`, `…_SEED=truth|preMatch`,
`…_IGNORE_HARD_RESYNC=1|0`, `…_IGNORE_MANUAL_SEED=1|0`,
`…_KINEMATIC=dr|strip|gyro`.

Чего **нет** в логе (и replay это не эмулирует): полный ВЧ поток gyro/CAN,
пересчёт bias с нуля, точный гибрид GYRO_STEER с bicycle-моделью — для этого
нужен сырой поток, а не тики 0,5 с. `dSteerPathDeg` пишется и годится для
офлайн-оценки `k`, но в `--motion dr` пока не смешивается с гиро.

Опция **`--path-odometer-sync`**: после softCorrect тянет позу к точке на
связанном графе, пройденной на `∫dDist` от последнего sync-якоря (закрывает
укорочение пути при срезании поворотов). Не срабатывает mid-turn и при
скачке на несвязанное ребро. Env: `TBOX_ROADMATCH_PATH_ODOMETER_SYNC=1`.

## Метрики

- `corr/rate` — применённые soft-correction / HOLD_EDGE / connected corridor;
- `cor` — число graph-only коррекций `CONNECTED_CORRIDOR` после `no_candidate`;
- `truthLag*` — расстояние sim-позы до скрытой опоры: строка `truth.lat/lon` или, в старых журналах, `$GNRMC`;
- `switch` / `edges` — смены и число разных matched-рёбер;
- `nearRej` — отказ при кандидате не дальше 20 м;
- `fastYaw` / `maxYaw` — коррекции курса к ребру больше старого лимита
  6° и максимум за тик;
- `maxGap` — максимальное число движущихся тиков подряд без коррекции;
- `finalLat/Lon/BearingDeg`, `seed*`, `yawScale` / `speedScale` — куда
  приехала открытая DR+match траектория при выбранных калибровках.

Baseline хранит не точные значения, а допустимые min/max, чтобы небольшие
безопасные изменения scoring не ломали проверку. Новый полевой журнал сначала
запускается без baseline; после ручной оценки для него добавляются границы.

## Ограничение

Geo-debug содержит позиции/курс раз в 0,5 с (старые журналы — раз в секунду),
но не полный поток высокочастотных gyro/CAN samples. Длинная запись режется
по 20 МБ на файл (`# part=` / `# continuedFrom=`) — в replay передавать все куски по порядку. В режиме **delta** replay накладывает записанные дельты траектории
на исправленную matcher-позу и отдельно воспроизводит hard-resync/reset. Поэтому
обычный режим проверяет именно поиск рёбер, confidence, переключения, HOLD и softCorrect,
но не заменяет полный тест DR-интеграторов или поездку на HU. Режим **dr** —
открытая реинтеграция по тиковым `integ.*` + новый matcher (см. выше).
Для `--match-mode RAILS` replay, как production, ведёт две позы: независимый
free/retain DR получает следующий тик, а возвращённая rail-поза используется
только как опубликованный output и для метрик. Подстановка rail-output обратно
во free-путь давала ложное накопление сотен метров и не воспроизводила сход с
рельс по зазору.

Новые журналы: входная поза тика — `preMatch.lat/lon/bearing` (до snap),
опора — `truth.*`. Старые логи без этих строк по-прежнему читают `mock.*` и `$GNRMC`.

Если в журнале есть `turn.latched=L|R`, replay берёт его как
`UniversalCanRepository.turnSignalsLatchedSide` на ГУ.
`turn.intent` / `turn.flashes` — intentional stalk (не comfort 3×); без них
intent считается из того же `TurnSignalsLatch` по вспышкам.
Старые логи только с сырым `turn.side` прогоняются через тот же `TurnSignalsLatch`
(2,5 с). Без `turn.*` — `turnHint=null`.

`--kinematic` / `--motion strip` двигает позу по `integ.dDistM`
и крутит курс как полевой mock минус `bearingDeltaDeg` (гибрид гиро/руль
без match-yaw), затем снова применяет matcher. `--motion dr` вместо этого берёт
`dYawDebDeg * yawScale` (с опциональным override). Обычный режим и baseline без
флага не меняются.

`--match-mode RAILS` прогоняет тот же журнал через Rails-коридор (навигатор
Ordinary выбирает ребро; опубликованная поза — free DR + поперечный снэп).
`--match-mode FREE_TURNS` — экспериментальный Ordinary с усиленным курсом
и отвязкой у узлов >2 линий (3+ рёбра). Default — `ORDINARY`.

Обычный `testRuDebugUnitTest` пропускает field replay, если переменные
`TBOX_ROADMATCH_REPLAY_*` не заданы; большие журналы и карты в Git не хранятся.
