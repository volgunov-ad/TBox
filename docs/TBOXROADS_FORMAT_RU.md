# Формат дорожных карт: региональный bundle + `.tboxroads`-тайлы

Компактный offline дорожный граф для map-matching на ГУ (API 28+).  
Исходник: OpenStreetMap → `tools/osm_to_tboxroads.py` → пакет; приложение **не** читает `.osm.pbf` в рантайме.

См. также [MAP_MATCHING_PLAN_RU.md](MAP_MATCHING_PLAN_RU.md).

---

## 1. Региональный файл для скачивания

Пользователь и remote-каталог видят **один субъект = один файл**:

`{regionId}-v4.tboxroads.zip`

ZIP хранит entries без повторного сжатия (`STORED`), потому что каждый тайл уже
gzip-сжат независимо:

```text
index.json
tiles/0000_0000.tboxroads
tiles/0000_0001.tboxroads
...
```

`index.json` содержит `regionId`, `graphVersion`, общий `bbox` и список тайлов
(`id`, `file`, `bbox`, `bytes`, `edgeCount`). Bbox тайлов перекрываются примерно
на 150 м: на границе одновременно загружаются соседние тайлы, связность не рвётся.

После скачивания приложение проверяет индекс и заголовок каждого тайла, атомарно
устанавливает каталог `{regionId}.tboxroads.d`, удаляет ZIP и старую версию.
В UI это по-прежнему одна область. В RAM находятся только 1–4 тайла у машины;
дальние тайлы удаляются из `RoadGraphStore`.

## 2. Внутренний файл тайла `.tboxroads`

| Поле | Значение |
|------|----------|
| Расширение | `.tboxroads` |
| Заголовок | 8 байт ASCII: `TBOXRDS1` |
| Тело | gzip (DEFLATE) над UTF-8 JSON |
| Имя файла | `tiles/{x}_{y}.tboxroads` внутри локального bundle-каталога |

Проверка: первые 8 байт == `TBOXRDS1`, далее `GZIPInputStream` → JSON.

Загрузчик (`RoadGraph.load`) читает gzip+JSON **потоково** (`android.util.JsonReader`):
не держит весь распакованный JSON одной `String`/`JSONObject` — иначе крупные
области (например Московская) ловили OOM на ГУ при установке (~128 MB на UTF-16).

---

## 3. JSON schema тайла (после gunzip)

```json
{
  "format": 1,
  "regionId": "ru-moscow",
  "graphVersion": 1,
  "bbox": [37.2, 55.5, 37.9, 56.0],
  "edges": [
    {
      "id": 1,
      "class": "primary",
      "lengthM": 142.5,
      "from": 0,
      "to": 1,
      "oneway": 1,
      "coords": [[37.61, 55.75], [37.62, 55.75]]
    }
  ]
}
```

| Поле | Тип | Описание |
|------|-----|----------|
| `format` | int | Всегда `1` для v1 |
| `regionId` | string | id из каталога областей |
| `graphVersion` | int | Версия графа (≥1); для update UI |
| `bbox` | `[west, south, east, north]` | Охват пакета (lon/lat WGS84) |
| `edges` | array | Рёбра дорог |

### Ребро

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | long | Стабильный id в пакете |
| `class` | string | OSM highway class: `motorway`, `trunk`, `primary`, `secondary`, `tertiary`, `residential`, `unclassified`, `service`, … |
| `lengthM` | double | Длина polyline, метры |
| `from` / `to` | int | Индексы узлов связности (логические; могут совпадать с концами coords) |
| `oneway` | int (опц.) | `0`/нет поля — оба направления; `1` — только вдоль `coords`; `-1` — только против `coords` (OSM `oneway=yes` / `-1`, `junction=roundabout` → `1`) |
| `coords` | `[[lon, lat], …]` | Polyline ≥ 2 точек, WGS84 |

Узлы как отдельный массив в v1 **не обязательны**: связность через `from`/`to`
(одинаковый индекс = общая вершина). Tool квантует концы polyline (~1 м) и
переиспользует node id. Загрузчик дополнительно связывает рёбра по совпадению
координат концов — старые пакеты с уникальными `from`/`to` тоже получают adjacency.

Матчер учитывает `oneway` **мягким штрафом** (~18 м к score) за встречное
направление, а не жёстким reject: ошибки OSM, временные схемы, задний ход
(`allowAgainstOneway` при reverse gear). Старые пакеты без поля = двусторонние.

---

## 4. Классы дорог (фильтр tool)

По умолчанию в пакет попадают:

`motorway`, `motorway_link`, `trunk`, `trunk_link`, `primary`, `primary_link`, `secondary`, `secondary_link`, `tertiary`, `tertiary_link`, `residential`, `unclassified`, `living_street`

Исключаются: `footway`, `path`, `cycleway`, `steps`, `pedestrian`, `track` (можно расширить флагом tool later).

---

## 5. Совместимость

- Демо-пакеты Phase A (plain text stub) **не** валидны как v1 — заменены на синтетические графы через tool.
- Монолитные корневые `*.tboxroads` **не поддерживаются**: runtime их не загружает, скачивание принимает только ZIP-bundle.
- Если на диске остался старый монолит (с предыдущей версии), при `ensureLoaded` файл **удаляется без парсинга в RAM**, запись убирается из манифеста — регион снова «не установлен» (без краша/OOM).
- Внутренний формат тайла остаётся `TBOXRDS1`; bundle имеет отдельный `index.json`.

---

## 6. Tool

```bash
# GeoJSON LineString / MultiLineString → пакет
python3 tools/osm_to_tboxroads.py \
  --geojson tools/samples/ru_moscow_demo.geojson \
  --region-id ru-moscow \
  --graph-version 1 \
  --out app/src/main/assets/road_maps/stubs/ru-moscow-demo.tboxroads

# Overpass (сеть): bbox west,south,east,north
python3 tools/osm_to_tboxroads.py \
  --fetch-overpass \
  --region-id ru-nizhny \
  --bbox 43.90,56.28,44.05,56.36 \
  --graph-version 2 \
  --out tools/out/road_maps/ru-nizhny-v2.tboxroads

# Синтетическая сетка по bbox (для демо / тестов)
python3 tools/osm_to_tboxroads.py \
  --synthetic \
  --region-id ru-crimea \
  --bbox 32.2,44.2,36.8,46.3 \
  --out /tmp/ru-crimea-demo.tboxroads
```

Батч пилотных регионов: `python3 tools/build_road_map_packs.py --fetch --graph-version 2`  
Хостинг крупных пакетов: [ROAD_MAPS_HOSTING_RU.md](ROAD_MAPS_HOSTING_RU.md).

Зависимости: только stdlib Python 3.
