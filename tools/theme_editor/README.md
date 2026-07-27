# TBox Theme Editor (Windows)

Редактор файлов **`.tboxtheme`** для приложения **TBox Monitor**.  
Позволяет создавать и править темы на ПК, затем копировать файл на головное устройство и применять во вкладке «Темы».

Формат совместим с Android-экспортом (`ThemeBundleExport` / `ThemeLayoutExport`): ZIP-архив с `theme.json` и папкой `assets/`.

## Требования

- Windows 10/11
- **Python 3.10+** с [python.org](https://www.python.org/downloads/)
  - при установке включите **tcl/tk** (tkinter)
  - желательно «Add python.exe to PATH»
- Опционально: `pip install Pillow` — превью обоев и изображений

На Linux/macOS редактор тоже запускается, если установлен `python3-tk` (и желательно Pillow).

## Запуск

Из этой папки:

```bat
run_windows.bat
```

или:

```bat
python __main__.py
```

Открыть конкретный файл:

```bat
run_windows.bat D:\themes\eco.tboxtheme
```

Из корня репозитория:

```bat
python -m theme_editor
```

(нужен `PYTHONPATH=tools` либо запуск из `tools/`: `python -m theme_editor` при `cd tools`).

Удобный вариант из корня:

```bat
cd tools
python -m theme_editor
```

## Возможности

| Вкладка | Что делает |
|---------|------------|
| **Общие** | Разделы темы (`mainScreen` / `floatingPanels` / `appIcons`), число страниц, шрифт, обрезка обоев |
| **Цвета** | Фон холста, угловые кнопки, 8 пресетов цветов виджетов (`#AARRGGBB`) |
| **Обои** | Добавление/удаление light/dark, превью, назначение файла на страницу |
| **Иконки приложений** | PNG в `assets/icons/` (`package.name.png`) |
| **Иконки HTTP** | PNG в `assets/http_request_icons/` (`{panelId}-{index}.png`) |
| **Фоны плиток** | Файлы в `assets/tile_backgrounds/` |
| **Панели** | Обзор панелей из `theme.json` |
| **JSON** | Полное редактирование `theme.json` (панели, плитки, позиции кнопок) |

Кнопка **Проверить** валидирует `type`, `formatVersion`, разделы и ссылки обоев на реальные файлы в архиве.

## Типичный сценарий

1. В приложении на ГУ: **Темы → Создать тему** → скопировать `.tboxtheme` на ПК.  
2. Открыть файл в редакторе, заменить обои/цвета/иконки, при необходимости поправить JSON панелей.  
3. **Сохранить** → скопировать обратно на ГУ → **Применить тему**.

Либо **Новая тема** с нуля: включить нужные разделы, добавить обои и сохранить. Раскладку панелей проще взять из экспорта с ГУ (вкладка JSON).

## Структура файла

```
theme.json
assets/wallpaper/light/
assets/wallpaper/dark/
assets/icons/
assets/http_request_icons/
assets/tile_backgrounds/
```

Подробности формата: [docs/Themes.md](../../docs/Themes.md).

## Тесты

```bat
cd tools\theme_editor
python -m unittest discover -s tests -v
```

## Сборка .exe (опционально)

```bat
pip install pyinstaller
pyinstaller --noconfirm --windowed --name TboxThemeEditor __main__.py
```

Готовый бинарник появится в `dist/TboxThemeEditor/`.
