# Bugbot — TBox Monitor

Android-приложение (Kotlin/Compose) для ГУ Jetour Dashing. Нет серверного бэкенда. Данные — TBox UDP и CAN (mbCAN или VHAL).

## Критичные инварианты

1. **Поездки и топливо.** Отфильтрованный % и калиброванные литры — только при активной поездке (`CanFramesProcess`). Заправки — `BackgroundService.applyActiveTripFuelStep`. Суточная поездка (`isPersistent`) не является `activeTrip` и не должна открывать этот gate.
2. **CAN.** mbCAN vs VHAL — runtime (`HeadUnitCanMode`), не flavor. «Android 10» = Adayo/VHAL, часто API 28, не `SDK_INT == 29`. Новые/изменённые read/write property должны совпадать с `MbCanCommandRegistry` / `FirmwareVehicleJsonMapper` / `*Domain.kt` и с `docs/MBCAN_VHAL_PARAMETERS_RU.md`.
3. **Строки.** Flavor `ru` = `app/src/main/res` (русский). Flavor `en` = `app/src/en/res`. Новый `R.string` без пары в обоих файлах — дефект.
4. **Тесты.** Правки поездок/топлива/тем/виджетов без расширения существующего unit-теста — риск регрессии. Ориентир: `./gradlew testRuDebugUnitTest`.
5. **Git.** В diff не должно быть `app/build/`, `.gradle/`, `local.properties`.

## Что не считать багом

- Падающий `lintRuDebug` / уже существующие lint-замечания.
- Отсутствие instrumented-тестов (нужно устройство API 28+).
- Расхождение `Build.VERSION.RELEASE == 10` и API 28 на ГУ Adayo — так задумано.

## Как комментировать

Писать только проверяемые дефекты по diff. Не предлагать Hilt, Navigation Compose, kapt, отдельные product flavor под CAN и не просить «починить lint заодно».
