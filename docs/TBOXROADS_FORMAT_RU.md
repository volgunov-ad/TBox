# Формат `.tboxroads` v1

Компактный offline дорожный граф для map-matching на ГУ (API 28+).  
Исходник: OpenStreetMap → `tools/osm_to_tboxroads.py` → пакет; приложение **не** читает `.osm.pbf` в рантайме.

См. также [MAP_MATCHING_PLAN_RU.md](MAP_MATCHING_PLAN_RU.md).

---

## 1. Файл на диске

| Поле | Значение |
|------|----------|
| Расширение | `.tboxroads` |
| Заголовок | 8 байт ASCII: `TBOXRDS1` |
| Тело | gzip (DEFLATE) над UTF-8 JSON |
| Имя файла | обычно `{regionId}.tboxroads` (как в download manager) |

Проверка: первые 8 байт == `TBOXRDS1`, далее `GZIPInputStream` → JSON.

---

## 2. JSON schema (после gunzip)

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
| `coords` | `[[lon, lat], …]` | Polyline ≥ 2 точек, WGS84 |

Узлы как отдельный массив в v1 **не обязательны**: связность через `from`/`to` (одинаковый индекс = общая вершина). Matcher (этап C) использует `coords` + `from`/`to`.

---

## 3. Классы дорог (фильтр tool)

По умолчанию в пакет попадают:

`motorway`, `motorway_link`, `trunk`, `trunk_link`, `primary`, `primary_link`, `secondary`, `secondary_link`, `tertiary`, `tertiary_link`, `residential`, `unclassified`, `living_street`

Исключаются: `footway`, `path`, `cycleway`, `steps`, `pedestrian`, `track` (можно расширить флагом tool later).

---

## 4. Совместимость

- Демо-пакеты Phase A (plain text stub) **не** валидны как v1 — заменены на синтетические графы через tool.
- Будущий `format: 2` — новый magic (`TBOXRDS2`) или поле `format`; loader отклоняет неизвестный format.

---

## 5. Tool

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
