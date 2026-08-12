"""Whole-region road-map catalog: Russian federal subjects + Belarus regions."""

from __future__ import annotations

from typing import TypedDict


class Region(TypedDict):
    id: str
    country: str
    title_ru: str
    title_en: str
    osm_name: str
    osm_relation_id: int


def r(
    id: str,
    ru: str,
    en: str,
    osm: str | None = None,
    relation_id: int = 0,
) -> Region:
    return {
        "id": id,
        "country": "RU",
        "title_ru": ru,
        "title_en": en,
        "osm_name": osm or ru,
        "osm_relation_id": relation_id,
    }


def b(id: str, ru: str, en: str, osm: str | None = None) -> Region:
    return {
        "id": id,
        "country": "BY",
        "title_ru": ru,
        "title_en": en,
        "osm_name": osm or ru,
        "osm_relation_id": 0,
    }


# 89 federal subjects. Separate packages intentionally overlap where an autonomous
# okrug is geographically inside an oblast (users choose what to install).
RUSSIA: list[Region] = [
    r("ru-adygea", "Республика Адыгея", "Republic of Adygea"),
    r("ru-altai-republic", "Республика Алтай", "Altai Republic"),
    r("ru-bashkortostan", "Республика Башкортостан", "Republic of Bashkortostan"),
    r("ru-buryatia", "Республика Бурятия", "Republic of Buryatia"),
    r("ru-dagestan", "Республика Дагестан", "Republic of Dagestan"),
    r("ru-dnr", "Донецкая Народная Республика", "Donetsk People's Republic", "Донецька область", 71973),
    r("ru-ingushetia", "Республика Ингушетия", "Republic of Ingushetia"),
    r("ru-kabardino-balkaria", "Кабардино-Балкарская Республика", "Kabardino-Balkarian Republic"),
    r("ru-kalmykia", "Республика Калмыкия", "Republic of Kalmykia"),
    r("ru-karachay-cherkessia", "Карачаево-Черкесская Республика", "Karachay-Cherkess Republic"),
    r("ru-karelia", "Республика Карелия", "Republic of Karelia"),
    r("ru-komi", "Республика Коми", "Komi Republic"),
    r("ru-crimea", "Республика Крым", "Republic of Crimea", relation_id=3795586),
    r("ru-lnr", "Луганская Народная Республика", "Luhansk People's Republic", "Луганська область", 71971),
    r("ru-mari-el", "Республика Марий Эл", "Mari El Republic"),
    r("ru-mordovia", "Республика Мордовия", "Republic of Mordovia"),
    r("ru-sakha", "Республика Саха (Якутия)", "Sakha Republic (Yakutia)"),
    r("ru-north-ossetia", "Республика Северная Осетия — Алания", "Republic of North Ossetia–Alania"),
    r("ru-tatarstan", "Республика Татарстан", "Republic of Tatarstan"),
    r("ru-tuva", "Республика Тыва", "Tuva Republic"),
    r("ru-udmurtia", "Удмуртская Республика", "Udmurt Republic"),
    r("ru-khakassia", "Республика Хакасия", "Republic of Khakassia"),
    r("ru-chechnya", "Чеченская Республика", "Chechen Republic"),
    r("ru-chuvashia", "Чувашская Республика", "Chuvash Republic"),
    r("ru-altai-krai", "Алтайский край", "Altai Krai"),
    r("ru-zabaykalsky", "Забайкальский край", "Zabaykalsky Krai"),
    r("ru-kamchatka", "Камчатский край", "Kamchatka Krai"),
    r("ru-krasnodar", "Краснодарский край", "Krasnodar Krai"),
    r("ru-krasnoyarsk", "Красноярский край", "Krasnoyarsk Krai"),
    r("ru-perm", "Пермский край", "Perm Krai"),
    r("ru-primorsky", "Приморский край", "Primorsky Krai"),
    r("ru-stavropol", "Ставропольский край", "Stavropol Krai"),
    r("ru-khabarovsk", "Хабаровский край", "Khabarovsk Krai"),
    r("ru-amur", "Амурская область", "Amur Oblast"),
    r("ru-arkhangelsk", "Архангельская область", "Arkhangelsk Oblast"),
    r("ru-astrakhan", "Астраханская область", "Astrakhan Oblast"),
    r("ru-belgorod", "Белгородская область", "Belgorod Oblast"),
    r("ru-bryansk", "Брянская область", "Bryansk Oblast"),
    r("ru-vladimir", "Владимирская область", "Vladimir Oblast"),
    r("ru-volgograd", "Волгоградская область", "Volgograd Oblast"),
    r("ru-vologda", "Вологодская область", "Vologda Oblast"),
    r("ru-voronezh", "Воронежская область", "Voronezh Oblast"),
    r("ru-zaporozhye", "Запорожская область", "Zaporozhye Oblast", "Запорізька область", 71980),
    r("ru-ivanovo", "Ивановская область", "Ivanovo Oblast"),
    r("ru-irkutsk", "Иркутская область", "Irkutsk Oblast"),
    r("ru-kaliningrad", "Калининградская область", "Kaliningrad Oblast"),
    r("ru-kaluga", "Калужская область", "Kaluga Oblast"),
    r("ru-kemerovo", "Кемеровская область — Кузбасс", "Kemerovo Oblast — Kuzbass"),
    r("ru-kirov", "Кировская область", "Kirov Oblast"),
    r("ru-kostroma", "Костромская область", "Kostroma Oblast"),
    r("ru-kurgan", "Курганская область", "Kurgan Oblast"),
    r("ru-kursk", "Курская область", "Kursk Oblast"),
    r("ru-leningrad", "Ленинградская область", "Leningrad Oblast"),
    r("ru-lipetsk", "Липецкая область", "Lipetsk Oblast"),
    r("ru-magadan", "Магаданская область", "Magadan Oblast"),
    r("ru-moscow-oblast", "Московская область", "Moscow Oblast"),
    r("ru-murmansk", "Мурманская область", "Murmansk Oblast"),
    r("ru-nizhny-novgorod", "Нижегородская область", "Nizhny Novgorod Oblast"),
    r("ru-novgorod", "Новгородская область", "Novgorod Oblast"),
    r("ru-novosibirsk", "Новосибирская область", "Novosibirsk Oblast"),
    r("ru-omsk", "Омская область", "Omsk Oblast"),
    r("ru-orenburg", "Оренбургская область", "Orenburg Oblast"),
    r("ru-oryol", "Орловская область", "Oryol Oblast"),
    r("ru-penza", "Пензенская область", "Penza Oblast"),
    r("ru-pskov", "Псковская область", "Pskov Oblast"),
    r("ru-rostov", "Ростовская область", "Rostov Oblast"),
    r("ru-ryazan", "Рязанская область", "Ryazan Oblast"),
    r("ru-samara", "Самарская область", "Samara Oblast"),
    r("ru-saratov", "Саратовская область", "Saratov Oblast"),
    r("ru-sakhalin", "Сахалинская область", "Sakhalin Oblast"),
    r("ru-sverdlovsk", "Свердловская область", "Sverdlovsk Oblast"),
    r("ru-smolensk", "Смоленская область", "Smolensk Oblast"),
    r("ru-tambov", "Тамбовская область", "Tambov Oblast"),
    r("ru-tver", "Тверская область", "Tver Oblast"),
    r("ru-tomsk", "Томская область", "Tomsk Oblast"),
    r("ru-tula", "Тульская область", "Tula Oblast"),
    r("ru-tyumen", "Тюменская область", "Tyumen Oblast"),
    r("ru-ulyanovsk", "Ульяновская область", "Ulyanovsk Oblast"),
    r("ru-kherson", "Херсонская область", "Kherson Oblast", "Херсонська область", 71022),
    r("ru-chelyabinsk", "Челябинская область", "Chelyabinsk Oblast"),
    r("ru-yaroslavl", "Ярославская область", "Yaroslavl Oblast"),
    r("ru-moscow", "Москва", "Moscow"),
    r("ru-saint-petersburg", "Санкт-Петербург", "Saint Petersburg"),
    r("ru-sevastopol", "Севастополь", "Sevastopol", relation_id=1574364),
    r("ru-jewish-autonomous", "Еврейская автономная область", "Jewish Autonomous Oblast"),
    r("ru-nenets", "Ненецкий автономный округ", "Nenets Autonomous Okrug"),
    r("ru-khanty-mansi", "Ханты-Мансийский автономный округ — Югра", "Khanty-Mansi Autonomous Okrug — Yugra"),
    r("ru-chukotka", "Чукотский автономный округ", "Chukotka Autonomous Okrug"),
    r("ru-yamalo-nenets", "Ямало-Ненецкий автономный округ", "Yamalo-Nenets Autonomous Okrug"),
]


BELARUS: list[Region] = [
    b("by-brest", "Брестская область", "Brest Region"),
    b("by-vitebsk", "Витебская область", "Vitebsk Region"),
    b("by-gomel", "Гомельская область", "Gomel Region"),
    b("by-grodno", "Гродненская область", "Grodno Region"),
    b("by-minsk-region", "Минская область", "Minsk Region"),
    b("by-mogilev", "Могилёвская область", "Mogilev Region"),
    b("by-minsk", "Минск", "Minsk"),
]


REGIONS: list[Region] = RUSSIA + BELARUS

