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
| **Панели** | Список панелей; **Добавить / Удалить / Плитки…**; справа сетка с шаблонными значениями |
| **Раскладка ГУ** | Dual-canvas; **Плитки…** / двойной клик по панели — тот же редактор плиток |
| **JSON** | Полное редактирование `theme.json` (панели, плитки, позиции кнопок) |

### Раскладка ГУ (dual-canvas)

На ГУ Jetour главное окно приложений живёт во **вложенном виртуальном дисплее** (обычно `1320×856`), а плавающие панели — в координатах **всего физического экрана** (`1920×1080`).

Вкладка показывает:

1. **Подложку** — скриншот экрана (в комплекте образец Jetour Dashing; можно загрузить свой `adb screencap -p`).
2. **Рамку App VD** — область, куда попадает `MainActivity` / панели главного экрана.
3. **Панели** с **превью плиток** внутри (шаблонные значения; переключатель **Светлая / Тёмная** меняет плитки, цвет холста `canvasBackground` и обои текущей страницы).
4. Drag/resize панелей; двойной клик — редактор плиток.

### Плитки панелей

Диалог **Плитки…** (вкладка «Панели» или двойной клик на «Раскладка ГУ») повторяет приложение:

1. **Тип** — поиск по каталогу dataKey (~100 виджетов)
2. **Дополнительно** — заголовок, единица, масштаб, цвета (`…` / палитра пресетов темы), обои плитки (файл или выбор из `tile_backgrounds`), паддинги; блоки music/launcher/HTTP/поездки и т.д. по типу
3. **Вся панель** — имя, rows/cols, страница (ГЭ)

В сетке и в превью диалога показываются **шаблонные** значения (не live TBox). Выбор установленных приложений не делается — package/плееры вводятся текстом.

Для подложки желателен Pillow: `pip install Pillow`.

Снять свой скриншот с ГУ:

```bat
adb connect 192.168.1.128:5555
adb shell screencap -p /sdcard/hu.png
adb pull /sdcard/hu.png
```

Затем на вкладке «Раскладка ГУ» → **Подложка…**.

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

## Сборка .exe (auto-py-to-exe)

Конфиг: [`auto-py-to-exe-config.json`](auto-py-to-exe-config.json) (окно без консоли, папка `assets/` с подложкой ГУ, имя `TboxThemeEditor`, выход в `dist/`).

```bat
cd tools\theme_editor
pip install auto-py-to-exe Pillow
auto-py-to-exe --config auto-py-to-exe-config.json
```

В UI нажмите Convert. Готовый бинарник: `dist\TboxThemeEditor\TboxThemeEditor.exe`.

Либо вручную через PyInstaller:

```bat
pip install pyinstaller Pillow
pyinstaller --noconfirm --windowed --name TboxThemeEditor --add-data "assets;assets" __main__.py
```
