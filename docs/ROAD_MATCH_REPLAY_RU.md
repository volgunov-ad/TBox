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

## Метрики

- `corr/rate` — применённые soft-correction / HOLD_EDGE / connected corridor;
- `cor` — число graph-only коррекций `CONNECTED_CORRIDOR` после `no_candidate`;
- `truthLag*` — расстояние sim-позы до NMEA truth (если в журнале есть `$GNRMC`);
- `posNudge*` — сдвиг позы softCorrect за тик; `posNudgeTurnSumM` — сумма на `turnActive`;
- `crossTrack*` — cross-track выбранного/удержанного ребра;
- `reshetikha*` — окно крутого поворота 2026-08-14 074349 (`elapsedMs` 504–527 с);
- `switch` / `edges` — смены и число разных matched-рёбер;
- `nearRej` — отказ при кандидате не дальше 20 м;
- `fastYaw` / `maxYaw` — коррекции курса к ребру больше старого лимита
  6° и максимум за тик;
- `maxGap` — максимальное число движущихся тиков подряд без коррекции.

Baseline хранит не точные значения, а допустимые min/max, чтобы небольшие
безопасные изменения scoring не ломали проверку. Новый полевой журнал сначала
запускается без baseline; после ручной оценки для него добавляются границы.

## Ограничение

Geo-debug содержит позиции/курс раз в секунду, но не полный поток
высокочастотных gyro/CAN samples. Replay накладывает записанные дельты траектории
на исправленную matcher-позу и отдельно воспроизводит hard-resync/reset. Поэтому
он проверяет именно поиск рёбер, confidence, переключения, HOLD и softCorrect,
но не заменяет полный тест DR-интеграторов или поездку на HU.

Обычный `testRuDebugUnitTest` пропускает field replay, если переменные
`TBOX_ROADMATCH_REPLAY_*` не заданы; большие журналы и карты в Git не хранятся.
