#!/usr/bin/env python3
"""Build Russian OBD DTC TSV from English catalog via token/phrase glossary."""

from __future__ import annotations

import re
from pathlib import Path

# Longest phrases first is handled by matcher; keys are lowercase token tuples.
PHRASES: dict[tuple[str, ...], str] = {
    ("mass", "or", "volume", "air", "flow"): "массовый/объёмный расход воздуха (MAF)",
    ("manifold", "absolute", "pressure/barometric", "pressure"): "ДАД/барометрическое давление",
    ("manifold", "absolute", "pressure"): "абсолютное давление во впускном коллекторе (MAP)",
    ("barometric", "pressure"): "барометрическое давление",
    ("intake", "air", "temperature"): "температура впускного воздуха",
    ("engine", "coolant", "temperature"): "температура ОЖ",
    ("throttle", "position", "sensor"): "датчик положения дросселя",
    ("throttle/pedal", "position", "sensor/switch"): "датчик/выключатель положения дросселя/педали",
    ("throttle/pedal", "position", "sensor"): "датчик положения дросселя/педали",
    ("accelerator", "pedal", "position"): "положение педали акселератора",
    ("heated", "oxygen", "sensor"): "подогреваемый датчик кислорода",
    ("oxygen", "sensor", "heater"): "подогреватель датчика кислорода",
    ("o2", "sensor", "heater"): "подогреватель датчика O2",
    ("oxygen", "sensor"): "датчик кислорода",
    ("o2", "sensor"): "датчик O2",
    ("fuel", "trim"): "коррекция топлива",
    ("system", "too", "lean"): "слишком бедная смесь",
    ("system", "too", "rich"): "слишком богатая смесь",
    ("fuel", "rail", "pressure"): "давление в топливной рампе",
    ("random/multiple", "cylinder", "misfire", "detected"): "обнаружены случайные/множественные пропуски зажигания",
    ("cylinder", "misfire", "detected"): "обнаружен пропуск зажигания в цилиндре",
    ("misfire", "detected"): "обнаружен пропуск зажигания",
    ("knock", "sensor"): "датчик детонации",
    ("crankshaft", "position", "sensor"): "датчик положения коленвала",
    ("camshaft", "position", "sensor"): "датчик положения распредвала",
    ("crankshaft", "position"): "положение коленвала",
    ("camshaft", "position"): "положение распредвала",
    ("ignition", "coil"): "катушка зажигания",
    ("catalyst", "system", "efficiency", "below", "threshold"): "эффективность катализатора ниже порога",
    ("evaporative", "emission", "control", "system"): "система EVAP",
    ("evaporative", "emission", "system"): "система EVAP",
    ("secondary", "air", "injection"): "система вторичного воздуха",
    ("exhaust", "gas", "recirculation"): "система EGR",
    ("vehicle", "speed", "sensor"): "датчик скорости автомобиля",
    ("idle", "air", "control"): "регулятор ХХ",
    ("transmission", "control", "system", "malfunction"): "неисправность системы управления АКПП",
    ("transmission", "control", "system"): "система управления АКПП",
    ("torque", "converter", "clutch"): "блокировка гидротрансформатора",
    ("shift", "solenoid"): "соленоид переключения",
    ("transmission", "fluid", "temperature"): "температура ATF",
    ("input/turbine", "speed", "sensor"): "датчик скорости входного вала/турбины",
    ("output", "speed", "sensor"): "датчик выходной скорости",
    ("brake", "switch"): "выключатель тормоза",
    ("clutch", "pedal", "switch"): "выключатель педали сцепления",
    ("park/neutral", "switch"): "выключатель P/N",
    ("power", "steering", "pressure"): "давление ГУР",
    ("a/c", "refrigerant", "pressure"): "давление хладагента А/С",
    ("a/c", "clutch", "relay"): "реле муфты А/С",
    ("engine", "oil", "temperature"): "температура масла ДВС",
    ("engine", "oil", "pressure"): "давление масла ДВС",
    ("fuel", "level", "sensor"): "датчик уровня топлива",
    ("fuel", "pump"): "топливный насос",
    ("fuel", "pressure"): "давление топлива",
    ("air", "flow"): "расход воздуха",
    ("circuit", "range/performance", "problem"): "цепь вне диапазона/производительности",
    ("circuit", "range/performance"): "цепь: диапазон/производительность",
    ("range/performance", "problem"): "проблема диапазона/производительности",
    ("circuit", "malfunction"): "неисправность цепи",
    ("circuit", "failure"): "отказ цепи",
    ("circuit", "intermittent"): "цепь с перебоями",
    ("circuit", "short", "to", "battery"): "КЗ цепи на питание",
    ("circuit", "short", "to", "ground"): "КЗ цепи на массу",
    ("short", "to", "battery"): "КЗ на питание",
    ("short", "to", "ground"): "КЗ на массу",
    ("short", "circuit", "to", "battery"): "КЗ на питание",
    ("short", "circuit", "to", "ground"): "КЗ на массу",
    ("circuit", "open"): "обрыв цепи",
    ("open", "circuit"): "обрыв цепи",
    ("circuit", "low", "input"): "низкий уровень сигнала цепи",
    ("circuit", "high", "input"): "высокий уровень сигнала цепи",
    ("low", "input"): "низкий входной сигнал",
    ("high", "input"): "высокий входной сигнал",
    ("low", "voltage"): "низкое напряжение",
    ("high", "voltage"): "высокое напряжение",
    ("no", "activity", "detected"): "активность не обнаружена",
    ("slow", "response"): "медленный отклик",
    ("out", "of", "range"): "вне диапазона",
    ("invalid", "or", "missing", "data", "for"): "неверные или отсутствующие данные для",
    ("invalid", "or", "missing", "data"): "неверные или отсутствующие данные",
    ("lost", "communication", "with"): "потеряна связь с",
    ("lost", "communication"): "потеряна связь",
    ("see", "manufacturer"): "см. документацию производителя",
    ("internal", "control", "module"): "внутренний модуль управления",
    ("control", "module"): "модуль управления",
    ("keep", "alive", "memory"): "память KAM",
    ("memory", "checksum"): "контрольная сумма памяти",
    ("air", "bag"): "подушка безопасности",
    ("seat", "belt"): "ремень безопасности",
    ("driver", "side"): "сторона водителя",
    ("passenger", "side"): "сторона пассажира",
    ("left", "front"): "левый передний",
    ("right", "front"): "правый передний",
    ("left", "rear"): "левый задний",
    ("right", "rear"): "правый задний",
    ("front", "left"): "передний левый",
    ("front", "right"): "передний правый",
    ("rear", "left"): "задний левый",
    ("rear", "right"): "задний правый",
    ("bank", "1", "sensor", "1"): "банк 1, датчик 1",
    ("bank", "1", "sensor", "2"): "банк 1, датчик 2",
    ("bank", "2", "sensor", "1"): "банк 2, датчик 1",
    ("bank", "2", "sensor", "2"): "банк 2, датчик 2",
    ("bank", "1", "or", "single", "sensor"): "банк 1 или один датчик",
    ("bank", "1"): "банк 1",
    ("bank", "2"): "банк 2",
    ("sensor", "1"): "датчик 1",
    ("sensor", "2"): "датчик 2",
    ("cylinder", "1"): "цилиндр 1",
    ("cylinder", "2"): "цилиндр 2",
    ("cylinder", "3"): "цилиндр 3",
    ("cylinder", "4"): "цилиндр 4",
    ("cylinder", "5"): "цилиндр 5",
    ("cylinder", "6"): "цилиндр 6",
    ("cylinder", "7"): "цилиндр 7",
    ("cylinder", "8"): "цилиндр 8",
    ("cylinder", "9"): "цилиндр 9",
    ("cylinder", "10"): "цилиндр 10",
    ("cylinder", "11"): "цилиндр 11",
    ("cylinder", "12"): "цилиндр 12",
    ("scp", "(j1850)"): "SCP (J1850)",
    ("scp", "j1850"): "SCP (J1850)",
    ("primary", "id"): "основной ID",
    ("transfer", "case"): "раздаточная коробка",
    ("air", "suspension"): "пневмоподвеска",
    ("height", "sensor"): "датчик высоты кузова",
    ("wheel", "speed"): "скорость колеса",
    ("speed", "sensor"): "датчик скорости",
    ("temperature", "sensor"): "датчик температуры",
    ("pressure", "sensor"): "датчик давления",
    ("position", "sensor"): "датчик положения",
    ("level", "sensor"): "датчик уровня",
    ("flow", "sensor"): "датчик расхода",
    ("solenoid", "valve"): "электромагнитный клапан",
    ("relay", "coil"): "обмотка реле",
    ("cooling", "fan"): "вентилятор охлаждения",
    ("fan", "control"): "управление вентилятором",
    ("power", "window"): "электростеклоподъёмник",
    ("running", "board"): "подножка",
    ("glass", "break", "sensor"): "датчик разбития стекла",
    ("solar", "radiation", "sensor"): "датчик солнечного излучения",
    ("servo", "motor"): "серводвигатель",
    ("longitudinal", "acceleration", "threshold", "exceeded"): "превышен порог продольного ускорения",
    (
        "anti-theft",
        "number",
        "of",
        "programmed",
        "keys",
        "is",
        "below",
        "minimum",
    ): "число запрограммированных ключей иммобилайзера ниже минимума",
    ("eic", "switch-1", "assembly"): "блок переключателей EIC-1",
    ("eic", "switch-2", "assembly"): "блок переключателей EIC-2",
    ("cooling", "fan", "power/ground"): "питание/масса вентилятора охлаждения",
    ("power/ground",): "питание/масса",
    ("fuel", "level", "output"): "выход датчика уровня топлива",
    ("fuel", "level"): "уровень топлива",
    ("fuel", "sender"): "датчик уровня топлива",
    ("climate", "control", "pushbutton"): "кнопка климат-контроля",
    ("pushbutton",): "кнопка",
    ("wiper", "washer"): "омыватель стеклоочистителя",
    ("washer",): "омыватель",
    ("blend", "door"): "заслонка смешивания",
    ("recirculation", "door"): "заслонка рециркуляции",
    ("air", "flow", "blend", "door"): "заслонка смешивания потока воздуха",
    ("air", "flow", "recirculation", "door"): "заслонка рециркуляции воздуха",
    ("express", "window", "down"): "быстрое опускание стекла",
    ("express", "window", "up"): "быстрый подъём стекла",
    ("dim", "panel"): "диммер панели",
    ("panel", "dim"): "диммер панели",
    ("emergency", "&", "road", "side", "assistance"): "экстренная/дорожная помощь",
    ("road", "side", "assistance"): "дорожная помощь",
    ("contribution/range",): "вклад/диапазон",
    ("system", "lean"): "бедная смесь",
    ("system", "rich"): "богатая смесь",
    ("short", "to", "vbatt"): "КЗ на питание АКБ",
    ("to", "vbatt"): "на питание АКБ",
    ("vbatt",): "питание АКБ",
}

TOKENS: dict[str, str] = {
    "malfunction": "неисправность",
    "failure": "отказ",
    "fault": "неисправность",
    "circuit": "цепь",
    "sensor": "датчик",
    "switch": "выключатель",
    "button": "кнопка",
    "lamp": "лампа",
    "indicator": "индикатор",
    "motor": "электродвигатель",
    "pump": "насос",
    "valve": "клапан",
    "heater": "подогреватель",
    "cooling": "охлаждение",
    "heating": "обогрев",
    "ignition": "зажигание",
    "injection": "впрыск",
    "injector": "форсунка",
    "catalyst": "катализатор",
    "evaporative": "испарительная",
    "emission": "токсичность",
    "exhaust": "выпуск",
    "intake": "впуск",
    "throttle": "дроссель",
    "turbocharger": "турбокомпрессор",
    "supercharger": "нагнетатель",
    "boost": "наддув",
    "wastegate": "wastegate",
    "egr": "EGR",
    "evap": "EVAP",
    "mil": "MIL",
    "battery": "АКБ",
    "ground": "масса",
    "power": "питание",
    "supply": "питание",
    "output": "выход",
    "input": "вход",
    "driver": "водителя",
    "passenger": "пассажира",
    "rear": "задний",
    "front": "передний",
    "left": "левый",
    "right": "правый",
    "side": "сторона",
    "door": "дверь",
    "window": "стекло",
    "mirror": "зеркало",
    "seat": "сиденье",
    "wiper": "стеклоочиститель",
    "horn": "клаксон",
    "suspension": "подвеска",
    "steering": "рулевое",
    "transmission": "трансмиссия",
    "clutch": "сцепление",
    "brake": "тормоз",
    "engine": "двигатель",
    "cylinder": "цилиндр",
    "fuel": "топливо",
    "air": "воздух",
    "oil": "масло",
    "coolant": "ОЖ",
    "temperature": "температура",
    "pressure": "давление",
    "speed": "скорость",
    "position": "положение",
    "system": "система",
    "control": "управление",
    "module": "модуль",
    "unit": "блок",
    "coil": "катушка",
    "open": "обрыв",
    "short": "КЗ",
    "high": "высокий",
    "low": "низкий",
    "range": "диапазон",
    "invalid": "неверный",
    "missing": "отсутствует",
    "data": "данные",
    "for": "для",
    "or": "или",
    "and": "и",
    "of": "",
    "to": "на",
    "with": "с",
    "from": "от",
    "below": "ниже",
    "above": "выше",
    "in": "в",
    "on": "на",
    "is": "",
    "a/c": "А/С",
    "abs": "ABS",
    "pcm": "PCM",
    "ecm": "ECM",
    "tcm": "TCM",
    "eeprom": "EEPROM",
    "relay": "реле",
    "actuator": "привод",
    "potentiometer": "потенциометр",
    "feedback": "обратная связь",
    "signal": "сигнал",
    "voltage": "напряжение",
    "current": "ток",
    "resistance": "сопротивление",
    "performance": "производительность",
    "efficiency": "эффективность",
    "threshold": "порог",
    "intermittent": "с перебоями",
    "detected": "обнаружено",
    "insufficient": "недостаточно",
    "excessive": "чрезмерный",
    "incorrect": "некорректный",
    "implausible": "неправдоподобный",
    "correlation": "корреляция",
    "calibration": "калибровка",
    "configuration": "конфигурация",
    "communication": "связь",
    "network": "сеть",
    "bus": "шина",
    "solenoid": "соленоид",
    "assembly": "узел",
    "primary": "основной",
    "secondary": "вторичный",
    "range/performance": "диапазон/производительность",
    "j1850": "J1850",
    "scp": "SCP",
    "id": "ID",
    "rf": "RF",
    "defrost": "обдув стекла",
    "vent": "обдув",
    "foot": "в ноги",
    "bypass": "байпас",
    "coolair": "холодный воздух",
    "servo": "серво",
    "solar": "солнечный",
    "radiation": "излучение",
    "glass": "стекло",
    "break": "разбитие",
    "anti-theft": "противоугонная система",
    "number": "число",
    "programmed": "запрограммированных",
    "keys": "ключей",
    "minimum": "минимума",
    "longitudinal": "продольное",
    "acceleration": "ускорение",
    "exceeded": "превышен",
    "see": "см.",
    "manufacturer": "производителя",
    "bank": "банк",
    "problem": "проблема",
    "single": "один",
    "multiple": "множественный",
    "random": "случайный",
    "misfire": "пропуск зажигания",
    "lean": "бедная смесь",
    "rich": "богатая смесь",
    "too": "слишком",
    "activity": "активность",
    "response": "отклик",
    "no": "нет",
    "case": "коробка",
    "fluid": "жидкость",
    "level": "уровень",
    "height": "высота",
    "wheel": "колесо",
    "climate": "климат",
    "sender": "датчик уровня",
    "pushbutton": "кнопка",
    "washer": "омыватель",
    "blend": "смешивание",
    "recirculation": "рециркуляция",
    "express": "экспресс",
    "panel": "панель",
    "dim": "подсветка",
    "emergency": "аварийный",
    "assistance": "помощь",
    "road": "дорога",
    "down": "вниз",
    "up": "вверх",
    "contribution": "вклад",
    "contribution/range": "вклад/диапазон",
    "vbatt": "питание АКБ",
    "power/ground": "питание/масса",
}

MAX_PHRASE = max(len(p) for p in PHRASES)
TOKEN_RE = re.compile(r"\w+(?:[/\-][\w]+)*|\([^)]+\)|[^\w\s]", re.UNICODE)


def tokenize(text: str) -> list[str]:
    return TOKEN_RE.findall(text)


def translate_tokens(toks: list[str]) -> list[str]:
    out: list[str] = []
    i = 0
    while i < len(toks):
        matched = False
        for n in range(min(MAX_PHRASE, len(toks) - i), 0, -1):
            key = tuple(t.lower() for t in toks[i : i + n])
            if key in PHRASES:
                out.append(PHRASES[key])
                i += n
                matched = True
                break
        if matched:
            continue
        t = toks[i]
        # Expand parenthetical groups: "(Bank 1)" → translate inner tokens.
        if len(t) >= 2 and t[0] == "(" and t[-1] == ")":
            inner = translate_tokens(tokenize(t[1:-1]))
            out.append("(" + " ".join(inner) + ")")
            i += 1
            continue
        low = t.lower()
        if low in TOKENS:
            ru = TOKENS[low]
            if ru:
                out.append(ru)
        elif re.fullmatch(r"\d+", t):
            out.append(t)
        elif re.fullmatch(r"[A-Za-z0-9]{1,8}", t) and t.upper() == t:
            out.append(t)
        elif t in "()[]{},.;:\"'/-&":
            out.append(t)
        else:
            out.append(t)
        i += 1
    return out


def translate(text: str) -> str:
    out = translate_tokens(tokenize(text))
    s = " ".join(out)
    s = re.sub(r"\s+([,.;:/)])", r"\1", s)
    s = re.sub(r"([(])\s+", r"\1", s)
    s = re.sub(r"\s+", " ", s).strip(" ,")
    if s:
        s = s[0].upper() + s[1:]
    return s


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    src = root / "app/src/main/assets/obd/dtc_en.tsv"
    dst = root / "app/src/main/assets/obd/dtc_ru.tsv"
    rows: list[tuple[str, str]] = []
    latinish = 0
    samples: list[tuple[str, str, str, list[str]]] = []
    for line in src.read_text(encoding="utf-8").splitlines():
        if "\t" not in line:
            continue
        code, desc = line.split("\t", 1)
        ru = translate(desc)
        bad = re.findall(r"[A-Za-z]{5,}", ru)
        # allow known acronyms
        bad = [w for w in bad if w.upper() not in {
            "SCP", "J1850", "EEPROM", "WASTEGATE", "EVAP", "EGR", "MAF", "MAP",
            "ATF", "PCM", "ECM", "TCM", "ABS", "MIL", "KAM",
        } and not w.isupper()]
        if bad:
            latinish += 1
            if len(samples) < 15:
                samples.append((code, desc, ru, bad))
        rows.append((code, ru))
    dst.write_text("\n".join(f"{c}\t{d}" for c, d in rows) + "\n", encoding="utf-8")
    print(f"wrote {dst} ({len(rows)} rows), residual_latinish={latinish}")
    for code, en, ru, bad in samples:
        print(f"--- {code} bad={bad}")
        print(f"EN: {en}")
        print(f"RU: {ru}")
    en_map = dict(rows)
    checks = [
        "P0100", "P0101", "P0171", "P0172", "P0300", "P0301",
        "P0420", "P0430", "P0440", "P0500", "P0700", "B1232",
    ]
    src_map = {
        line.split("\t", 1)[0]: line.split("\t", 1)[1]
        for line in src.read_text(encoding="utf-8").splitlines()
        if "\t" in line
    }
    for code in checks:
        print(f"{code}:")
        print(f"  EN: {src_map.get(code)}")
        print(f"  RU: {en_map.get(code)}")


if __name__ == "__main__":
    main()
