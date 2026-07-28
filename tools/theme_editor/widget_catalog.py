"""Widget catalog for TBox Theme Editor (ported from WidgetsRepository)."""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class WidgetType:
    data_key: str
    title: str
    unit: str = ""
    sample: str = ""
    supports_show_unit: bool = True
    supports_value_accuracy: bool = True
    supports_single_line_dual: bool = False
    supports_mbcan: bool = False
    supports_datetime_format: bool = False
    supports_stepper_icons: bool = False
    is_music: bool = False
    is_launcher: bool = False
    is_http: bool = False
    is_trip: bool = False
    is_drive_mode: bool = False


def _entry(
    data_key: str,
    title: str,
    unit: str = "",
    sample: str = "",
) -> WidgetType:
    trip = data_key in TRIP_KEYS
    no_unit = data_key in NO_SHOW_UNIT or trip or not data_key
    no_acc = data_key in NO_VALUE_ACCURACY or trip or not data_key
    return WidgetType(
        data_key=data_key,
        title=title,
        unit=unit,
        sample=sample,
        supports_show_unit=not no_unit,
        supports_value_accuracy=not no_acc,
        supports_single_line_dual=data_key in DUAL_KEYS,
        supports_mbcan=data_key in MBCAN_KEYS,
        supports_datetime_format=data_key in DATETIME_KEYS,
        supports_stepper_icons=data_key in STEPPER_KEYS,
        is_music=data_key in MUSIC_KEYS,
        is_launcher=data_key == "appLauncherWidget",
        is_http=data_key == "httpRequestWidget",
        is_trip=trip,
        is_drive_mode=data_key == "driveModeWidget",
    )


NO_SHOW_UNIT = ['airQualityWidget', 'appLauncherWidget', 'dateWidget', 'dayNightThemeWidget', 'driveModeWidget', 'emptyTileWidget', 'externalAppWidget', 'frontLeftSeatHeatVentSingleWidget', 'frontLeftSeatHeatVentWidget', 'frontRightSeatHeatVentSingleWidget', 'frontRightSeatHeatVentWidget', 'frontWindscreenHeatWidget', 'gearBoxModeCurrentGear', 'hideFloatingPanelsWidget', 'httpRequestWidget', 'hvacAcWidget', 'hvacAirRecirculationWidget', 'hvacAutoWidget', 'hvacBlowModeCycleWidget', 'hvacBlowModePanelWidgetHorizontal', 'hvacBlowModePanelWidgetVertical', 'hvacDefrosterFrontWidget', 'hvacFanWidgetHorizontal', 'hvacFanWidgetVertical', 'hvacSyncWidget', 'hvacTempLeftWidgetHorizontal', 'hvacTempLeftWidgetVertical', 'hvacTempRightWidgetHorizontal', 'hvacTempRightWidgetVertical', 'locWidget', 'mediaVolumeWidgetHorizontal', 'mediaVolumeWidgetVertical', 'mirrorAdjustModeWidget', 'mirrorFoldWidget', 'musicButtonsWidgetHorizontal', 'musicButtonsWidgetVertical', 'musicWidget', 'netWidget', 'netWidgetColored', 'netWidgetNew', 'parkingRadarWidget', 'rearLeftSeatHeatWidget', 'rearRightSeatHeatWidget', 'rearWindowMirrorsDefrostWidget', 'restartTbox', 'slaSpeedLimitWidget', 'steeringWheelHeatWidget', 'timeWidget', 'toggleFloatingPanelsEnabledWidget', 'trunkDoorWidget', 'wiperMaintenanceWidget']
NO_VALUE_ACCURACY = ['airQualityWidget', 'appLauncherWidget', 'dateWidget', 'dayNightThemeWidget', 'driveModeWidget', 'emptyTileWidget', 'externalAppWidget', 'frontLeftSeatHeatVentSingleWidget', 'frontLeftSeatHeatVentWidget', 'frontRightSeatHeatVentSingleWidget', 'frontRightSeatHeatVentWidget', 'frontWindscreenHeatWidget', 'gearBoxModeCurrentGear', 'hideFloatingPanelsWidget', 'httpRequestWidget', 'hvacAcWidget', 'hvacAirRecirculationWidget', 'hvacAutoWidget', 'hvacBlowModeCycleWidget', 'hvacBlowModePanelWidgetHorizontal', 'hvacBlowModePanelWidgetVertical', 'hvacDefrosterFrontWidget', 'hvacFanWidgetHorizontal', 'hvacFanWidgetVertical', 'hvacSyncWidget', 'hvacTempLeftWidgetHorizontal', 'hvacTempLeftWidgetVertical', 'hvacTempRightWidgetHorizontal', 'hvacTempRightWidgetVertical', 'locWidget', 'mediaVolumeWidgetHorizontal', 'mediaVolumeWidgetVertical', 'mirrorAdjustModeWidget', 'mirrorFoldWidget', 'musicButtonsWidgetHorizontal', 'musicButtonsWidgetVertical', 'musicWidget', 'netWidget', 'netWidgetColored', 'netWidgetNew', 'parkingRadarWidget', 'rearLeftSeatHeatWidget', 'rearRightSeatHeatWidget', 'rearWindowMirrorsDefrostWidget', 'restartTbox', 'slaSpeedLimitWidget', 'steeringWheelHeatWidget', 'timeWidget', 'toggleFloatingPanelsEnabledWidget', 'trunkDoorWidget', 'wiperMaintenanceWidget']
DUAL_KEYS = ['airQualityWidget', 'frontLeftSeatHeatVentWidget', 'frontRightSeatHeatVentWidget', 'fuelLevelWidget', 'gearBoxWidget', 'motorHoursWidget', 'tempInOutWidget', 'voltage+engineTemperatureWidget']
MBCAN_KEYS = ['carSpeed', 'engineRPM', 'engineTemperature', 'fuelLevelPercentage', 'mediaVolumeWidgetHorizontal', 'mediaVolumeWidgetVertical', 'odometer', 'outsideTemperature']
MUSIC_KEYS = ['musicButtonsWidgetHorizontal', 'musicButtonsWidgetVertical', 'musicWidget']
STEPPER_KEYS = ['hvacFanWidgetHorizontal', 'hvacFanWidgetVertical', 'hvacTempLeftWidgetHorizontal', 'hvacTempLeftWidgetVertical', 'hvacTempRightWidgetHorizontal', 'hvacTempRightWidgetVertical', 'mediaVolumeWidgetHorizontal', 'mediaVolumeWidgetVertical']
TRIP_KEYS = ['activeTripWidget', 'activeTripWidgetCustom', 'activeTripWidgetMini', 'activeTripWidgetSimple']
DATETIME_KEYS = ['dateWidget', 'timeWidget']


WIDGET_TYPES: list[WidgetType] = [
    _entry("", "(пусто)", sample=""),
    _entry('voltage', 'Напряжение', 'В', '14.2'),
    _entry('steerAngle', 'Угол поворота руля', '°', '12'),
    _entry('steerSpeed', 'Скорость вращения руля', '', '30'),
    _entry('engineRPM', 'Обороты двигателя', 'об/мин', '2100'),
    _entry('carSpeed', 'Скорость автомобиля', 'км/ч', '87'),
    _entry('carSpeedAccurate', 'Точная скорость автомобиля', 'км/ч', '86.4'),
    _entry('cruiseSetSpeed', 'Скорость круиз-контроля', 'км/ч', '90'),
    _entry('odometer', 'Одометр', 'км', '45230'),
    _entry('distanceToNextMaintenance', 'Пробег до следующего ТО', 'км', '3200'),
    _entry('distanceToFuelEmpty', 'Пробег на остатке топлива', 'км', '412'),
    _entry('fuelLevelPercentage', 'Уровень топлива', '', '64'),
    _entry('fuelLevelPercentageFiltered', 'Уровень топлива (сглажено)', '', '63'),
    _entry('fuelLevelLiters', 'Уровень топлива в литрах', 'л', '35.2'),
    _entry('fuelLevelLitersActual', 'Уровень топлива в литрах (с поправкой на температуру)', 'л', '34.8'),
    _entry('currentFuelConsumption', 'Мгновенный расход топлива', 'л/100км', '8.4'),
    _entry('breakingForce', 'Усилие торможения', '', '18'),
    _entry('engineTemperature', 'Температура двигателя', '°C', '92'),
    _entry('gearBoxOilTemperature', 'Температура масла КПП', '°C', '78'),
    _entry('gearBoxCurrentGear', 'Текущая передача КПП', '', 'D3'),
    _entry('gearBoxPreparedGear', 'Приготовленная передача КПП', '', '4'),
    _entry('gearBoxChangeGear', 'Выполнение переключения', '', '—'),
    _entry('gearBoxMode', 'Режим КПП', '', 'D'),
    _entry('gearBoxDriveMode', 'Режим движения КПП', '', 'Normal'),
    _entry('gearBoxWork', 'Работа КПП', '', 'OK'),
    _entry('gnssSpeed', 'Скорость GNSS', 'км/ч', '85'),
    _entry('visibleSatellites', 'Видимые спутники', '', '14'),
    _entry('longitude', 'Долгота', '°', '37.62'),
    _entry('latitude', 'Широта', '°', '55.75'),
    _entry('altitude', 'Высота', 'м', '156'),
    _entry('trueDirection', 'Направление', '', 'NE'),
    _entry('outsideTemperature', 'Температура на улице', '°C', '18'),
    _entry('insideTemperature', 'Температура в машине', '°C', '22'),
    _entry('outsideAirQuality', 'Качество воздуха на улице', '', 'Good'),
    _entry('insideAirQuality', 'Качество воздуха в машине', '', 'Good'),
    _entry('motorHours', 'Моточасы двигателя', 'ч', '1240.5'),
    _entry('motorHoursTrip', 'Моточасы двигателя за поездку', 'ч', '1.2'),
    _entry('motorHoursWidget', 'Виджет моточасов', '', '1240 / 1.2'),
    _entry('timeWidget', 'Время', '', '16:05'),
    _entry('dateWidget', 'Дата', '', '27.07.2026'),
    _entry('activeTripWidget', 'Поездка', '', '42 км · 0:38'),
    _entry('activeTripWidgetSimple', 'Поездка (упрощённый)', '', '42 км'),
    _entry('activeTripWidgetMini', 'Поездка (мини)', '', '42'),
    _entry('activeTripWidgetCustom', 'Поездка (настраиваемый)', '', '42 км · 8.1 л'),
    _entry('netWidget', 'Виджет сигнала сети', '', 'LTE 4'),
    _entry('netWidgetNew', 'Виджет сигнала сети (новый)', '', 'LTE 4'),
    _entry('netWidgetColored', 'Виджет сигнала сети (цветной)', '', 'LTE 4'),
    _entry('locWidget', 'Виджет навигации', '', 'OK'),
    _entry('voltage+engineTemperatureWidget', 'Виджет напряжения и температуры двигателя', '', '14.2 В · 92 °C'),
    _entry('gearBoxWidget', 'Виджет режима КПП с текущей передачей и температурой', '', 'D · 78 °C'),
    _entry('gearBoxModeCurrentGear', 'Режим КПП и передача', '', 'D3'),
    _entry('driveModeWidget', 'Виджет режима вождения', '', 'Normal'),
    _entry('wheel1Pressure', 'Давление колеса ПЛ', 'бар', '2.4'),
    _entry('wheel2Pressure', 'Давление колеса ПП', 'бар', '2.4'),
    _entry('wheel3Pressure', 'Давление колеса ЗЛ', 'бар', '2.3'),
    _entry('wheel4Pressure', 'Давление колеса ЗП', 'бар', '2.3'),
    _entry('wheel1Temperature', 'Температура колеса ПЛ', '°C', '28'),
    _entry('wheel2Temperature', 'Температура колеса ПП', '°C', '29'),
    _entry('wheel3Temperature', 'Температура колеса ЗЛ', '°C', '27'),
    _entry('wheel4Temperature', 'Температура колеса ЗП', '°C', '27'),
    _entry('wheelsPressureWidget', 'Виджет давления в шинах', 'бар', '2.4'),
    _entry('wheelsPressureTemperatureWidget', 'Виджет давления и температуры в шинах', 'бар / °C', '2.4 / 28'),
    _entry('tempInOutWidget', 'Виджет температуры снаружи и внутри', '', '18 / 22'),
    _entry('fuelLevelWidget', 'Виджет уровня топлива', '', '64% · 35 л'),
    _entry('airQualityWidget', 'Виджет качества воздуха', '', 'Good'),
    _entry('steeringWheelHeatWidget', 'Виджет обогрева рулевого колеса', '', 'ON'),
    _entry('wiperMaintenanceWidget', 'Виджет режима обслуживания стеклоочистителей', '', 'OFF'),
    _entry('parkingRadarWidget', 'Виджет парковочного радара', '', 'ON'),
    _entry('slaSpeedLimitWidget', 'Ограничение по знаку', '', '60'),
    _entry('speedLimiterWidget', 'Ограничитель скорости', '', '—'),
    _entry('frontWindscreenHeatWidget', 'Виджет обогрева лобового стекла', '', 'OFF'),
    _entry('rearWindowMirrorsDefrostWidget', 'Виджет обогрева заднего стекла и зеркал', '', 'OFF'),
    _entry('hvacAirRecirculationWidget', 'Виджет рециркуляции воздуха', '', 'OFF'),
    _entry('hvacAcWidget', 'Виджет A/C', '', 'ON'),
    _entry('hvacAutoWidget', 'Виджет AUTO HVAC', '', 'AUTO'),
    _entry('hvacDefrosterFrontWidget', 'Виджет обдува лобового стекла', '', 'OFF'),
    _entry('hvacSyncWidget', 'Виджет SYNC климат-контроля', '', 'SYNC'),
    _entry('hvacFanWidgetHorizontal', 'Вентилятор (горизонтально)', '', '3'),
    _entry('hvacFanWidgetVertical', 'Вентилятор (вертикально)', '', '3'),
    _entry('hvacTempLeftWidgetHorizontal', 'Температура слева (горизонтально)', '', '22'),
    _entry('hvacTempLeftWidgetVertical', 'Температура слева (вертикально)', '', '22'),
    _entry('hvacTempRightWidgetHorizontal', 'Температура справа (горизонтально)', '', '22'),
    _entry('hvacTempRightWidgetVertical', 'Температура справа (вертикально)', '', '22'),
    _entry('hvacBlowModeCycleWidget', 'Режим обдува (цикл)', '', 'FACE'),
    _entry('hvacBlowModePanelWidgetHorizontal', 'Режим обдува (панель, горизонтально)', '', 'FACE'),
    _entry('hvacBlowModePanelWidgetVertical', 'Режим обдува (панель, вертикально)', '', 'FACE'),
    _entry('trunkDoorWidget', 'Багажник', '', 'Closed'),
    _entry('mirrorAdjustModeWidget', 'Регулировка зеркал', '', 'OFF'),
    _entry('mirrorFoldWidget', 'Складывание зеркал', '', 'Unfolded'),
    _entry('dayNightThemeWidget', 'Тема день/ночь (темная/светлая)', '', 'Day'),
    _entry('frontLeftSeatHeatVentWidget', 'Виджет обогрева и вентиляции левого переднего сиденья (двойная кнопка)', '', 'Heat 2'),
    _entry('frontRightSeatHeatVentWidget', 'Виджет обогрева и вентиляции правого переднего сиденья (двойная кнопка)', '', 'Vent 1'),
    _entry('frontLeftSeatHeatVentSingleWidget', 'Виджет обогрева и вентиляции левого переднего сиденья (одна кнопка)', '', 'Heat 2'),
    _entry('frontRightSeatHeatVentSingleWidget', 'Виджет обогрева и вентиляции правого переднего сиденья (одна кнопка)', '', 'Vent 1'),
    _entry('rearLeftSeatHeatWidget', 'Виджет обогрева левого заднего сиденья', '', 'OFF'),
    _entry('rearRightSeatHeatWidget', 'Виджет обогрева правого заднего сиденья', '', 'OFF'),
    _entry('musicWidget', 'Виджет управления музыкой', '', 'Artist — Title'),
    _entry('musicButtonsWidgetHorizontal', 'Виджет управления музыкой (только кнопки, горизонтально)', '', '▶'),
    _entry('musicButtonsWidgetVertical', 'Виджет управления музыкой (только кнопки, вертикально)', '', '▶'),
    _entry('mediaVolumeWidgetHorizontal', 'Виджет громкости медиа (горизонтально)', '', '12'),
    _entry('mediaVolumeWidgetVertical', 'Виджет громкости медиа (вертикально)', '', '12'),
    _entry('appLauncherWidget', 'Ярлык приложения', '', 'App'),
    _entry('httpRequestWidget', 'HTTP-запрос', '', 'OK'),
    _entry('emptyTileWidget', 'Пустая', '', ''),
    _entry('restartTbox', 'Кнопка перезагрузки TBox', '', 'Restart'),
    _entry('externalAppWidget', 'Виджет стороннего приложения', '', 'Widget'),
    _entry('hideFloatingPanelsWidget', 'Скрытие плавающих панелей', '', 'Hide'),
    _entry('toggleFloatingPanelsEnabledWidget', 'Отключение плавающих панелей', '', 'On/Off'),
]


WIDGET_BY_KEY: dict[str, WidgetType] = {w.data_key: w for w in WIDGET_TYPES}


def get_widget_type(data_key: str) -> WidgetType:
    return WIDGET_BY_KEY.get(data_key) or _entry(data_key, data_key or "(пусто)")


def format_sample_value(data_key: str, value_accuracy: int | None = None) -> str:
    w = get_widget_type(data_key)
    sample = w.sample
    if value_accuracy is None or sample in {"", "—"}:
        return sample
    try:
        num = float(sample.replace(",", "."))
    except ValueError:
        return sample
    if value_accuracy == 0:
        return str(int(round(num)))
    return f"{num:.{value_accuracy}f}"

