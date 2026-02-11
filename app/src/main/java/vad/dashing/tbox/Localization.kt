package vad.dashing.tbox

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

enum class AppLanguagePreference(val code: String) {
    SYSTEM("system"),
    RUSSIAN("ru"),
    ENGLISH("en");

    companion object {
        fun fromCode(code: String?): AppLanguagePreference {
            return when (code) {
                RUSSIAN.code -> RUSSIAN
                ENGLISH.code -> ENGLISH
                else -> SYSTEM
            }
        }
    }
}

enum class ResolvedAppLanguage(val localeCode: String) {
    RUSSIAN("ru"),
    ENGLISH("en")
}

object AppLanguageManager {
    fun resolveLanguage(
        preferenceCode: String?,
        systemLocale: Locale = systemLocale()
    ): ResolvedAppLanguage {
        return when (AppLanguagePreference.fromCode(preferenceCode)) {
            AppLanguagePreference.RUSSIAN -> ResolvedAppLanguage.RUSSIAN
            AppLanguagePreference.ENGLISH -> ResolvedAppLanguage.ENGLISH
            AppLanguagePreference.SYSTEM -> {
                if (systemLocale.language.equals("ru", ignoreCase = true)) {
                    ResolvedAppLanguage.RUSSIAN
                } else {
                    ResolvedAppLanguage.ENGLISH
                }
            }
        }
    }

    fun applyLanguage(context: Context, preferenceCode: String?): ResolvedAppLanguage {
        val resolved = resolveLanguage(preferenceCode)
        val locale = Locale(resolved.localeCode)
        Locale.setDefault(locale)

        val updatedConfig = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            }
        }

        context.resources.updateConfiguration(updatedConfig, context.resources.displayMetrics)
        context.applicationContext.resources.updateConfiguration(
            updatedConfig,
            context.applicationContext.resources.displayMetrics
        )
        return resolved
    }

    fun isRussian(): Boolean {
        return resolveLanguage(
            preferenceCode = null,
            systemLocale = Locale.getDefault()
        ) == ResolvedAppLanguage.RUSSIAN
    }

    fun select(ru: String, en: String): String {
        return if (isRussian()) ru else en
    }

    private fun systemLocale(): Locale {
        val locales = Resources.getSystem().configuration.locales
        return if (!locales.isEmpty) locales[0] else Locale.ENGLISH
    }
}

object UiTextTranslator {
    private val ruToEn = mapOf(
        "Модем" to "Modem",
        "AT команды" to "AT commands",
        "Геопозиция" to "Location",
        "Данные авто" to "Vehicle data",
        "Настройки" to "Settings",
        "Журнал" to "Logs",
        "Информация" to "Info",
        "Плитки" to "Tiles",
        "Загрузка..." to "Loading...",
        "Скрыть меню" to "Hide menu",
        "Показать меню" to "Show menu",
        "Не выбрано" to "Not selected",
        "Отображать название" to "Show title",
        "Отображать единицу измерения" to "Show unit",
        "Выберите данные для плитки " to "Select data for tile ",
        "Дополнительные настройки плитки " to "Additional tile settings ",
        "Режим модема" to "Modem mode",
        "Включен" to "On",
        "Режим полета" to "Flight mode",
        "Выключен" to "Off",
        "Системный (русский только при системном русском)" to
            "System (Russian only when system language is Russian)",
        "Русский" to "Russian",
        "Язык интерфейса" to "Interface language",
        "По умолчанию язык берется из системы: русский только для системного русского, иначе английский" to
            "By default language follows system: Russian only for system Russian, otherwise English",
        "Настройки контроля сети" to "Network control settings",
        "Автоматический перезапуск модема" to "Automatic modem restart",
        "Автоматически перезапускать модем при потере подключения к сети. Проверка происходит с периодичностью 10 секунд в первый раз и 5 минут в последующие разы (сброс таймера до 10 секунд происходит при подключении сети)" to
            "Automatically restart the modem when network connectivity is lost. Checks run every 10 seconds on the first attempt and every 5 minutes afterwards (the timer resets to 10 seconds when network is restored).",
        "Автоматическая перезагрузка TBox" to "Automatic TBox reboot",
        "Автоматически презагружать TBox, если перезапуск модема не помогает. Перезагрузка просходит через 60 секунд после попытки перезапуска модема, если это не помогло, в первый раз. Далее таймер устанавливается на 30 минут (сброс таймера до 60 секунд происходит при подключении сети)" to
            "Automatically reboot TBox if modem restart does not help. The first reboot happens 60 seconds after a failed modem restart attempt. Afterwards the timer is set to 30 minutes (the timer resets to 60 seconds when network is restored).",
        "Настройки предотвращения перезагрузки" to "Reboot prevention settings",
        "ПРЕДУПРЕЖДЕНИЕ" to "WARNING",
        "При переключении опций SUSPEND и STOP требуется ручная перезагрузка TBox" to
            "Switching between SUSPEND and STOP requires a manual TBox reboot",
        "Автоматическая отправка команды SUSPEND приложению APP в TBox" to
            "Automatically send SUSPEND command to APP on TBox",
        "Приостановка приложения APP позволяет избежать периодической перезагрузки TBox, но может происходить регулярное переподключение модема, если установлена SIM-карта на TBox HW 0.0.5" to
            "Suspending APP can prevent periodic TBox reboots, but regular modem reconnections may happen when a SIM card is installed on TBox HW 0.0.5.",
        "Автоматическая отправка команды STOP приложению APP в TBox" to
            "Automatically send STOP command to APP on TBox",
        "Полное отключение приложения APP позволяет избежать периодической перезагрузки TBox и переподключения модема. После включения опции может произойти однократная перезагрузка TBox.\nНе рекомендуется использовать данную опцию на TBox HW 0.0.1, 0.0.4" to
            "Stopping APP completely can prevent periodic TBox reboots and modem reconnections. A one-time TBox reboot may happen after enabling this option.\nThis option is not recommended for TBox HW 0.0.1 and 0.0.4.",
        "Автоматическая отправка команды SUSPEND приложению MDC в TBox" to
            "Automatically send SUSPEND command to MDC on TBox",
        "Не рекомендуется включать данную опцию, если в TBox установлена SIM карта" to
            "This option is not recommended when a SIM card is installed in TBox.",
        "Автоматическая отправка команды STOP приложению MDC в TBox" to
            "Automatically send STOP command to MDC on TBox",
        "Автоматическая отправка команды SUSPEND приложению SWD в TBox" to
            "Automatically send SUSPEND command to SWD on TBox",
        "Автоматическая отправка команды PREVENT RESTART приложению SWD в TBox" to
            "Automatically send PREVENT RESTART command to SWD on TBox",
        "Отключение проверки состояния сети и аномалий в работе TBox. Эти проверки могут приводить к лишним перезагрузкам" to
            "Disable network and anomaly checks in TBox. These checks may cause extra reboots.",
        "Настройки плавающих панелей" to "Floating panel settings",
        "Показывать плавающую панель" to "Show floating panel",
        "Включить фон плавающей панели" to "Enable floating panel background",
        "Если выключено, то фон будет прозрачный" to "If disabled, the background is transparent",
        "Открывать окно программы при одиночном нажатии на элемент плавающей панели" to
            "Open app window on single tap of a floating panel item",
        "Количество строк плиток плавающей панели" to "Number of floating panel tile rows",
        "Количество столбцов плиток плавающей панели" to "Number of floating panel tile columns",
        "Настройки виджетов для Overlays" to "Overlay widget settings",
        "Показывать индикатор подключения TBox в виджете" to
            "Show TBox connection indicator in widget",
        "Индикатор в виджете в виде круга может иметь 3 цвета: \n- красный - нет данных от фоновой службы;\n- желтый - нет связи с TBox;\n- зеленый - есть связь с TBox" to
            "The circular widget indicator can have 3 colors:\n- red: no data from background service;\n- yellow: no link with TBox;\n- green: connected to TBox.",
        "Показывать индикатор состояния геопозиции в виджете" to
            "Show location status indicator in widget",
        "Индикатор в виджете в виде стрелки может иметь 3 цвета: \n- красный - нет фиксации местоположения;\n- желтый - данные о реальной скорости сильно не совпадают с данными со спутников;\n- зеленый - есть фиксация местоположения, данные в норме" to
            "The arrow indicator can have 3 colors:\n- red: no location fix;\n- yellow: actual speed strongly differs from satellite data;\n- green: location fix is present, data is normal.",
        "Настройки экрана Плитки" to "Tiles screen settings",
        "Показывать графики изменения величин на плитках" to
            "Show value trend charts on tiles",
        "Количество строк плиток" to "Number of tile rows",
        "Количество столбцов плиток" to "Number of tile columns",
        "Получение данных от TBox" to "TBox data acquisition",
        "Получать данные CAN от TBox" to "Receive CAN data from TBox",
        "Получать данные о геопозиции от TBox" to "Receive location data from TBox",
        "Прочее" to "Other",
        "Все изменения в экспертном режиме вы делаете на свой страх и риск.\nНо к необратимым последствиям ваши действия в этом режиме привести не могут" to
            "All changes in expert mode are at your own risk.\nHowever, your actions in this mode cannot cause irreversible consequences.",
        "Экспертный режим" to "Expert mode",
        "Количество сохраняемых CAN фреймов (1...3600)" to
            "Number of stored CAN frames (1...3600)",
        "Искать другие IP адреса TBox" to "Search for other TBox IP addresses",
        "Подменять системные данные о геопозиции (Фиктивные местоположения)" to
            "Override system location data (Mock locations)",
        "Готово к использованию" to "Ready to use",
        "Требует настройки разрешений и настройки фиктивных местоположений" to
            "Requires permissions and mock location setup",
        "Нажмите для просмотра требований к фиктивным местоположениям" to
            "Tap to view mock location requirements",
        "Перезагрузка TBox" to "Reboot TBox",
        "Данные модема и SIM" to "Modem and SIM data",
        "Оператор" to "Operator",
        "Данные подключения" to "Connection data",
        "Регистрация" to "Registration",
        "SIM статус" to "SIM status",
        "Сеть" to "Network",
        "Время подключения" to "Connection time",
        "Тип APN" to "APN type",
        "Шлюз APN" to "APN gateway",
        "Время изменения" to "Change time",
        "Тип APN2" to "APN2 type",
        "Шлюз APN2" to "APN2 gateway",
        "Последнее обновление" to "Last refresh",
        "Последнее изменение" to "Last update",
        "Фиксация местоположения" to "Location fix",
        "Правдивость местоположения" to "Location validity",
        "Долгота" to "Longitude",
        "Широта" to "Latitude",
        "Высота, м" to "Altitude, m",
        "Видимые спутники" to "Visible satellites",
        "Используемые спутники" to "Used satellites",
        "Скорость, км/ч" to "Speed, km/h",
        "Истинное направление" to "True heading",
        "Магнитное направление" to "Magnetic heading",
        "Дата и время UTC" to "UTC date and time",
        "Сырые данные" to "Raw data",
        "Подтверждение команды SUSPEND приложению APP" to
            "SUSPEND command confirmation for APP",
        "Подтверждение команды SUSPEND приложению MDC" to
            "SUSPEND command confirmation for MDC",
        "Подтверждение команды SUSPEND приложению SWD" to
            "SUSPEND command confirmation for SWD",
        "Подтверждение команды STOP приложению APP" to
            "STOP command confirmation for APP",
        "Подтверждение команды STOP приложению MDC" to
            "STOP command confirmation for MDC",
        "Подтверждение команды PREVENT RESTART приложению SWD" to
            "PREVENT RESTART command confirmation for SWD",
        "Сохраненный IP адрес TBox" to "Saved TBox IP address",
        "Список возможных IP адресов TBox" to "List of possible TBox IP addresses",
        "Версия приложения APP" to "APP version",
        "Версия приложения CRT" to "CRT version",
        "Версия приложения LOC" to "LOC version",
        "Версия приложения MDC" to "MDC version",
        "Версия приложения SWD" to "SWD version",
        "Версия SW" to "SW version",
        "Версия HW" to "HW version",
        "VIN код" to "VIN code",
        "Запросить информацию из TBox" to "Request info from TBox",
        "Cycle напряжение, В" to "Cycle voltage, V",
        "Cycle скорость, км/ч" to "Cycle speed, km/h",
        "Cycle обороты двигателя, об/мин" to "Cycle engine speed, rpm",
        "Cycle поперечное ускорение, м/с2" to "Cycle lateral acceleration, m/s2",
        "Cycle продольное ускорение, м/с2" to "Cycle longitudinal acceleration, m/s2",
        "Cycle давление ПЛ, бар" to "Cycle pressure FL, bar",
        "Cycle давление ПП, бар" to "Cycle pressure FR, bar",
        "Cycle давление ЗЛ, бар" to "Cycle pressure RL, bar",
        "Cycle давление ЗП, бар" to "Cycle pressure RR, bar",
        "Cycle температура ПЛ, °C" to "Cycle temperature FL, °C",
        "Cycle температура ПП, °C" to "Cycle temperature FR, °C",
        "Cycle температура ЗП, °C" to "Cycle temperature RR, °C",
        "Cycle температура ЗЛ, °C" to "Cycle temperature RL, °C",
        "Фильтр по тексту (минимум 3 символа)" to "Text filter (minimum 3 characters)",
        "Введите текст для поиска..." to "Enter text to search...",
        "Очистить" to "Clear",
        "Свернуть" to "Collapse",
        "Развернуть" to "Expand",
        "Сохранить в файл" to "Save to file",
        "Сохранение файла" to "Save file",
        "Сохранить журнал в папку Загрузки" to "Save log to Downloads folder",
        "Сохранить текущие CAN данные в файл" to "Save current CAN data to file",
        "AT команда" to "AT command",
        "Введите AT команду" to "Enter AT command",
        "Отправить" to "Send",
        "  Нет данных" to "  No data",
        "Ширина плавающей панели (px)" to "Floating panel width (px)",
        "Высота плавающей панели (px)" to "Floating panel height (px)",
        "Позиция X плавающей панели (px)" to "Floating panel X position (px)",
        "Позиция Y плавающей панели (px)" to "Floating panel Y position (px)",
        "Приложение " to "App ",
        "Приостановить" to "Suspend",
        "Возобновить" to "Resume",
        "Остановить" to "Stop",
        "Сохранить" to "Save",
        "Отмена" to "Cancel",
        "Закрыть" to "Close",
        "Настроить" to "Configure",
        "Требуется разрешение" to "Permission required",
        "Для работы приложения необходимо разрешение\n«Отображение поверх других приложений»" to
            "The app requires the \"Display over other apps\" permission",
        "Требования для mock-локации (Настройки разработчика)" to
            "Mock location requirements (Developer options)",
        "Нет разрешения на доступ к местоположению\n" to "No location permission granted\n",
        "Не включена mock-локация в настройках разработчика\n" to
            "Mock location is not enabled in Developer options\n",
        "Не удается добавить приложение в список провайдеров фиктивных местоположений\n" to
            "Cannot select this app as a mock location provider\n",
        "Служба TBox" to "TBox Service",
        "Фоновый мониторинг состояния TBox" to "Background TBox monitoring",
        "Монитор TBox" to "TBox Monitor",
        "Запуск службы" to "Service starting",
        "Остановка службы" to "Service stopping",
        "TBox подключен" to "TBox connected",
        "TBox отключен" to "TBox disconnected",
        "нет данных" to "no data",
        "домашняя сеть" to "home network",
        "поиск сети" to "searching network",
        "регистрация отклонена" to "registration denied",
        "роуминг" to "roaming",
        "нет SIM" to "no SIM",
        "SIM готова" to "SIM ready",
        "требуется PIN" to "PIN required",
        "ошибка SIM" to "SIM error",
        "нет сети" to "no network",
        "Параметр 1" to "Parameter 1",
        "Параметр 2" to "Parameter 2",
        "Параметр 3" to "Parameter 3",
        "Параметр 4" to "Parameter 4",
        "Скорость колеса 1" to "Wheel speed 1",
        "Скорость колеса 2" to "Wheel speed 2",
        "Скорость колеса 3" to "Wheel speed 3",
        "Скорость колеса 4" to "Wheel speed 4",
        "Давление колеса ПЛ" to "Wheel pressure FL",
        "Давление колеса ПП" to "Wheel pressure FR",
        "Давление колеса ЗЛ" to "Wheel pressure RL",
        "Давление колеса ЗП" to "Wheel pressure RR",
        "Температура колеса ПЛ" to "Wheel temperature FL",
        "Температура колеса ПП" to "Wheel temperature FR",
        "Температура колеса ЗЛ" to "Wheel temperature RL",
        "Температура колеса ЗП" to "Wheel temperature RR",
        "Режим левого переднего сиденья" to "Front-left seat mode",
        "Режим правого переднего сиденья" to "Front-right seat mode",
        "Время изменения GNSS" to "GNSS change time",
        "Время получения GNSS" to "GNSS receive time",
        "Уровень сигнала сети" to "Network signal level",
        "Тип сети" to "Network type",
        "Регистрация в сети" to "Network registration",
        "Состояние SIM" to "SIM status",
        "Блокировка окон" to "Window lock",
        "Напряжение" to "Voltage",
        "Угол поворота руля" to "Steering angle",
        "Скорость вращения руля" to "Steering speed",
        "Обороты двигателя" to "Engine RPM",
        "Скорость автомобиля" to "Vehicle speed",
        "Точная скорость автомобиля" to "Accurate vehicle speed",
        "Скорость круиз-контроля" to "Cruise speed",
        "Одометр" to "Odometer",
        "Пробег до следующего ТО" to "Distance to next service",
        "Пробег на остатке топлива" to "Distance to empty",
        "Уровень топлива" to "Fuel level",
        "Уровень топлива (сглажено)" to "Fuel level (smoothed)",
        "Усилие торможения" to "Braking force",
        "Температура двигателя" to "Engine temperature",
        "Температура масла КПП" to "Gearbox oil temperature",
        "Текущая передача КПП" to "Current gearbox gear",
        "Приготовленная передача КПП" to "Prepared gearbox gear",
        "Выполнение переключения" to "Shift in progress",
        "Режим КПП" to "Gearbox mode",
        "Режим движения КПП" to "Gearbox drive mode",
        "Работа КПП" to "Gearbox operation",
        "Скорость GNSS" to "GNSS speed",
        "Высота" to "Altitude",
        "Направление" to "Heading",
        "Температура на улице" to "Outside temperature",
        "Температура в машине" to "Inside temperature",
        "Качество воздуха на улице" to "Outside air quality",
        "Качество воздуха в машине" to "Inside air quality",
        "Моточасы двигателя" to "Engine motor hours",
        "Моточасы двигателя за поездку" to "Trip motor hours",
        "Виджет моточасов" to "Motor hours widget",
        "Виджет сигнала сети" to "Network signal widget",
        "Виджет навигации" to "Navigation widget",
        "Виджет напряжения и температуры двигателя" to
            "Voltage and engine temperature widget",
        "Виджет режима КПП с текущей передачей и температурой" to
            "Gearbox mode, current gear and temperature widget",
        "Виджет давления в шинах" to "Tire pressure widget",
        "Виджет давления и температуры в шинах" to "Tire pressure and temperature widget",
        "Виджет температуры снаружи и внутри" to "Outside/inside temperature widget",
        "Кнопка перезагрузки TBox" to "TBox reboot button",
        "Панель 1" to "Panel 1",
        "Панель 2" to "Panel 2",
        "Панель 3" to "Panel 3",
        "выключено" to "off",
        "обогрев 1" to "heating 1",
        "обогрев 2" to "heating 2",
        "обогрев 3" to "heating 3",
        "вентиляция 1" to "ventilation 1",
        "вентиляция 2" to "ventilation 2",
        "вентиляция 3" to "ventilation 3",
        "да" to "yes",
        "нет" to "no",
        "подключен" to "connected",
        "отключен" to "disconnected",
        "переключение" to "switching",
        "заблокированы" to "locked",
        "разблокированы" to "unlocked",
        "км/ч" to "km/h",
        "км" to "km",
        "бар" to "bar",
        "об/мин" to "rpm",
        "м" to "m",
        "В" to "V",
        "ч" to "h"
    )

    fun translate(text: String): String {
        if (AppLanguageManager.isRussian()) {
            return text
        }
        val direct = ruToEn[text]
        if (direct != null) {
            return direct
        }
        return applyFragmentReplacements(text)
    }

    private fun applyFragmentReplacements(source: String): String {
        var text = source
        val replacements = listOf(
            "Выберите данные для плитки " to "Select data for tile ",
            "Дополнительные настройки плитки " to "Additional tile settings ",
            "Панель " to "Panel ",
            "подключен в" to "connected at",
            "отключен в" to "disconnected at",
            "Служба запущена в" to "Service started at",
            "Версия программы" to "App version",
            "Последние данные" to "Last data",
            "Приложение " to "App ",
            "Сохранение файла" to "Save file",
            "Сохранить журнал в папку Загрузки" to "Save log to Downloads folder",
            "Сохранить текущие CAN данные в файл" to "Save current CAN data to file",
            "Сохранить " to "Save ",
            " в папку Загрузки" to " to Downloads folder"
        )
        replacements.forEach { (ru, en) ->
            text = text.replace(ru, en)
        }
        return text
    }
}

fun localizedText(text: String): String {
    return UiTextTranslator.translate(text)
}

@Composable
fun localizedTextC(text: String): String {
    // Depend on LocalConfiguration to refresh localized text after language switch.
    LocalConfiguration.current
    return localizedText(text)
}

fun selectLanguageText(ru: String, en: String): String {
    return AppLanguageManager.select(ru, en)
}

@Composable
fun selectLanguageTextC(ru: String, en: String): String {
    LocalConfiguration.current
    return selectLanguageText(ru, en)
}
