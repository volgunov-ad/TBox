# Тайловые bundle-пакеты дорожных карт на Яндекс.Диске

См. формат: [TBOXROADS_FORMAT_RU.md](TBOXROADS_FORMAT_RU.md), план: [MAP_MATCHING_PLAN_RU.md](MAP_MATCHING_PLAN_RU.md).

## 1. Размещение

Используется тот же публичный корень Яндекс.Диска, что OTA release
(`BuildConfig.UPDATE_RELEASE_PUBLIC_KEY`). В синхронизированной папке `release`
создать подпапку:

```
release/
  version.json
  tbox_monitor-....apk
  maps/
    catalog.json
    ru-nizhny-novgorod-v4.tboxroads.zip
    ru-moscow-oblast-v4.tboxroads.zip
    by-brest-v4.tboxroads.zip
    ...
```

В каталоге URL имеет вид `yandex-disk:/maps/{file}`. Приложение получает
временный download URL через публичный API Яндекс.Диска — прямые HTTP-ссылки
на страницу share не используются.

## 2. Каталог

Fallback-каталог в APK содержит 89 субъектов РФ и 7 регионов РБ без URL.
При открытии окна карт приложение читает `/maps/catalog.json`; только реально
присутствующие при сборке пакеты получают URL и кнопку «Скачать».

KZ, AM, AZ, UZ удалены. Нарезка — только целая область/субъект, не центр города.

## 3. Сборка одной области

```bash
# ID областей
python tools/build_road_map_packs.py --list

# Целая Нижегородская область по OSM admin_level=4.
# Путь по умолчанию:
# C:\Users\volgu\AndroidStudioProjects\TBM\release\maps
python tools/build_road_map_packs.py \
  --fetch-region ru-nizhny-novgorod \
  --graph-version 4

# Несколько областей за запуск
python tools/build_road_map_packs.py \
  --fetch-region ru-moscow-oblast \
  --fetch-region by-brest \
  --graph-version 4
```

Скрипт:

- запрашивает **точную административную область** через Overpass;
- временно строит целый регион, режет его на тайлы 0.1° с overlap 150 м;
- пишет один `{id}-v4.tboxroads.zip` в `release/maps`;
- внутри ZIP: маленький `index.json` + независимо gzip-сжатые `.tboxroads`-тайлы;
- пересобирает `release/maps/catalog.json` с размерами и bbox;
- обновляет bundled fallback `assets/road_maps/catalog.json` (без URL).

Для другого расположения синхронизированной папки:

```bash
python tools/build_road_map_packs.py \
  --output-base D:\TBox \
  --fetch-region ru-nizhny-novgorod
```

> Overpass для крупных субъектов может вернуть timeout. Для массовой production-
> сборки нужно перейти на Geofabrik PBF + polygon extract; формат/каталог приложения
> от этого не меняются. Не заменять целую область bbox-срезом города.

## 4. Публикация

Яндекс.Диск-клиент синхронизирует `release/maps`. После синхронизации проверить
публичным API, что доступны `/maps/catalog.json` и один пакет. Отдельный share
для `maps` не нужен и создавать второй public key не требуется.

`graphVersion` в remote catalog выше установленного → UI показывает «Обновить».

ODbL attribution уже есть в окне «Карты дорог».

## 5. Проверка на HU

1. Открыть «Карты дорог» с сетью — remote catalog должен показать опубликованные области.
2. Скачать целую область одним действием; размер download должен совпасть с `catalog.json`.
3. На диске появляется один внутренний каталог `{id}.tboxroads.d`; UI тайлы не показывает.
4. Проверить координаты в разных частях области и `mapMatch.*` в geo-debug.
5. В RAM должны находиться только тайлы около текущей точки, не весь субъект.
6. Следить за местом на диске и временем загрузки/распаковки.
