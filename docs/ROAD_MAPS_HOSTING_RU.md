# Хостинг пакетов `.tboxroads` (этап D)

См. формат: [TBOXROADS_FORMAT_RU.md](TBOXROADS_FORMAT_RU.md), план: [MAP_MATCHING_PLAN_RU.md](MAP_MATCHING_PLAN_RU.md).

## 1. Сборка

```bash
# Один регион (Overpass, нужны сеть и bbox west,south,east,north)
python3 tools/osm_to_tboxroads.py \
  --fetch-overpass \
  --region-id ru-nizhny \
  --bbox 43.90,56.28,44.05,56.36 \
  --graph-version 2 \
  --out tools/out/road_maps/ru-nizhny-v2.tboxroads

# Пилотный набор + обновление assets/catalog.json
python3 tools/build_road_map_packs.py --fetch --graph-version 2
```

Пакеты ≤ ~1.5 MB кладутся в `app/src/main/assets/road_maps/stubs/` с URL `asset://…` (работают offline в APK).  
Более крупные — в `tools/out/road_maps/`; в каталог пишется HTTPS URL после публикации.

## 2. Публикация (GitHub Releases)

1. Создать release tag, например `road-maps-v2`.
2. Прикрепить файлы `{id}-v{graphVersion}.tboxroads` из `tools/out/road_maps/`.
3. Пересобрать каталог с базой:

```bash
python3 tools/build_road_map_packs.py --fetch --graph-version 2 \
  --release-base https://github.com/volgunov-ad/TBox/releases/download/road-maps-v2
```

Приложение уже умеет качать `https://` в `RoadMapDownloadManager` (очередь, прогресс, update/delete).

## 3. Версии и обновления

- `graphVersion` в каталоге > версии в install-manifest → кнопка «Обновить».
- Смена нарезки bbox / фильтра highway → поднять `graphVersion`.
- ODbL: строка attribution уже в окне «Карты дорог»; при публикации release — указать © OSM contributors.

## 4. Пилот этапа D (в APK)

Городские срезы (не целые субъекты): Москва (центр), Нижний Новгород, Симферополь, Донецк, Луганск, Минск, Алматы, Ереван, Баку, Ташкент.  
«Беларусь (вся)» и полные области РФ — через release, когда пакеты собраны и проверены по размеру на ГУ.

## 5. Проверка на HU

1. Удалить старые демо-пакеты (graphVersion 1 / крошечный размер).
2. Скачать пилотные области → покрытие в bbox, `mapMatch` в geo-debug при включённой привязке.
3. Следить за местом на диске и временем загрузки.
