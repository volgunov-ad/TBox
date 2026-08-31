# HU device smoke test (TBox Monitor)

Повторяемый прогон на ГУ `192.168.1.128:5555` (ADB / scrcpy). ГУ без машины, есть TBox; режимы вождения переключаются, остальные mbCAN могут молчать.

## Что делает

1. Ставит RU release APK (`-r -g`).
2. Выдаёт runtime-разрешения, `WRITE_SECURE_SETTINGS`, overlay, usage stats, mock location.
3. Генерирует и заливает фикстуры: backup JSON, автоматизации, три `.tboxtheme` (ECO/NOR/SPT) с обоями и плавающей панелью.
4. Через UI: импорт backup, apply тем, назначение тем на режимы, тапы ECO/NOR/SPT.
5. Автоматизации foreground: Settings → Monitor → (если есть) Yandex Navi.
6. Снимает logcat/meminfo/windows и пишет `analysis.txt`.

## Команды

```powershell
cd C:\Users\volgu\AndroidStudioProjects\1
python .\scripts\hu-device-test\generate_hu_test_fixtures.py
powershell -ExecutionPolicy Bypass -File .\scripts\hu-device-test\Invoke-HuFullTest.ps1
```

Параметры:

- `-Apk` — путь к APK (по умолчанию `tbox_monitor-v.1.0.0-ru-test29.apk`)
- `-Device` — `host:port`
- `-Adb` — `adb.exe` (по умолчанию scrcpy 3.3.4)
- `-SkipInstall` — только прогон на уже установленной сборке

Результаты: `scripts/hu-device-test/results/<timestamp>/` (скриншоты, uidump, logcat, report).

## Расширение

- Новые сценарии — функции в `Invoke-HuFullTest.ps1` (`Click-Text`, `Save-Screenshot`).
- Новые правила — `automations_doc()` в `generate_hu_test_fixtures.py`.
- Новые темы/виджеты — `theme_json()` / `theme_widgets()`.
- Не ходить в DocumentsUI, если появится debug-intent импорта: добавить шаг в скрипт вместо `Import-BackupUi`.

## Ограничения и грабли с прогона

- Сначала закрывать in-app диалог **«Разрешения приложения»** (`Закрыть`), иначе dump/клики бьют мимо.
- Клики по тексту надёжнее через `click_from_dump.py` (UTF-8), не через строки внутри PS 5.1.
- Перед стартом: `am force-stop`; logcat снимать **без** `--pid` (на API 28 иначе почти пусто).
- `ACCESS_BACKGROUND_LOCATION` на API 28 нет; `WRITE_SETTINGS` — через appops, не `pm grant`.
- Notification listener: `settings put secure enabled_notification_listeners …MediaControlNotificationListenerService`.
- Автоматизации из backup не приедут, пока не закрыт диалог прав и не прошёл импорт JSON.
- Инструментальные тесты на ГУ этот скрипт не заменяет.
