"""Whole-region road-map catalog: Russian federal subjects + Belarus regions.

`title_ru` / `title_en` are UI labels. `osm_name` must match the OSM relation
primary `name` tag (often a short form for republics; Belarusian for BY).
`osm_relation_id` is preferred for Overpass fetch — name lookup is a fallback.
"""

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


def b(
    id: str,
    ru: str,
    en: str,
    osm: str | None = None,
    relation_id: int = 0,
) -> Region:
    return {
        "id": id,
        "country": "BY",
        "title_ru": ru,
        "title_en": en,
        "osm_name": osm or ru,
        "osm_relation_id": relation_id,
    }


# 89 federal subjects. Separate packages intentionally overlap where an autonomous
# okrug is geographically inside an oblast (users choose what to install).
RUSSIA: list[Region] = [
    r("ru-adygea", "Республика Адыгея", "Republic of Adygea", osm="Адыгея", relation_id=253256),
    r("ru-altai-republic", "Республика Алтай", "Altai Republic", relation_id=145194),
    r("ru-bashkortostan", "Республика Башкортостан", "Republic of Bashkortostan", osm="Башкортостан", relation_id=77677),
    r("ru-buryatia", "Республика Бурятия", "Republic of Buryatia", relation_id=145729),
    r("ru-dagestan", "Республика Дагестан", "Republic of Dagestan", osm="Дагестан", relation_id=109876),
    r("ru-dnr", "Донецкая Народная Республика", "Donetsk People's Republic", "Донецька область", 71973),
    r("ru-ingushetia", "Республика Ингушетия", "Republic of Ingushetia", osm="Ингушетия", relation_id=253252),
    r(
        "ru-kabardino-balkaria",
        "Кабардино-Балкарская Республика",
        "Kabardino-Balkarian Republic",
        osm="Кабардино-Балкария",
        relation_id=109879,
    ),
    r("ru-kalmykia", "Республика Калмыкия", "Republic of Kalmykia", osm="Калмыкия", relation_id=108083),
    r(
        "ru-karachay-cherkessia",
        "Карачаево-Черкесская Республика",
        "Karachay-Cherkess Republic",
        osm="Карачаево-Черкесия",
        relation_id=109878,
    ),
    r("ru-karelia", "Республика Карелия", "Republic of Karelia", osm="Карелия", relation_id=393980),
    r("ru-komi", "Республика Коми", "Komi Republic", relation_id=115136),
    r("ru-crimea", "Республика Крым", "Republic of Crimea", relation_id=3795586),
    r("ru-lnr", "Луганская Народная Республика", "Luhansk People's Republic", "Луганська область", 71971),
    r("ru-mari-el", "Республика Марий Эл", "Mari El Republic", osm="Марий Эл", relation_id=115114),
    r("ru-mordovia", "Республика Мордовия", "Republic of Mordovia", osm="Мордовия", relation_id=72196),
    r("ru-sakha", "Республика Саха (Якутия)", "Sakha Republic (Yakutia)", relation_id=151234),
    r(
        "ru-north-ossetia",
        "Республика Северная Осетия — Алания",
        "Republic of North Ossetia–Alania",
        osm="Северная Осетия — Алания",
        relation_id=110032,
    ),
    r("ru-tatarstan", "Республика Татарстан", "Republic of Tatarstan", osm="Татарстан", relation_id=79374),
    r("ru-tuva", "Республика Тыва", "Tuva Republic", relation_id=145195),
    r("ru-udmurtia", "Удмуртская Республика", "Udmurt Republic", osm="Удмуртия", relation_id=115134),
    r("ru-khakassia", "Республика Хакасия", "Republic of Khakassia", relation_id=190911),
    r("ru-chechnya", "Чеченская Республика", "Chechen Republic", osm="Чечня", relation_id=109877),
    r("ru-chuvashia", "Чувашская Республика", "Chuvash Republic", osm="Чувашия", relation_id=80513),
    r("ru-altai-krai", "Алтайский край", "Altai Krai", relation_id=144764),
    r("ru-zabaykalsky", "Забайкальский край", "Zabaykalsky Krai", relation_id=145730),
    r("ru-kamchatka", "Камчатский край", "Kamchatka Krai", relation_id=151233),
    r("ru-krasnodar", "Краснодарский край", "Krasnodar Krai", relation_id=108082),
    r("ru-krasnoyarsk", "Красноярский край", "Krasnoyarsk Krai", relation_id=190090),
    r("ru-perm", "Пермский край", "Perm Krai", relation_id=115135),
    r("ru-primorsky", "Приморский край", "Primorsky Krai", relation_id=151225),
    r("ru-stavropol", "Ставропольский край", "Stavropol Krai", relation_id=108081),
    r("ru-khabarovsk", "Хабаровский край", "Khabarovsk Krai", relation_id=151223),
    r("ru-amur", "Амурская область", "Amur Oblast", relation_id=147166),
    r("ru-arkhangelsk", "Архангельская область", "Arkhangelsk Oblast", relation_id=140337),
    r("ru-astrakhan", "Астраханская область", "Astrakhan Oblast", relation_id=112819),
    r("ru-belgorod", "Белгородская область", "Belgorod Oblast", relation_id=83184),
    r("ru-bryansk", "Брянская область", "Bryansk Oblast", relation_id=81997),
    r("ru-vladimir", "Владимирская область", "Vladimir Oblast", relation_id=72197),
    r("ru-volgograd", "Волгоградская область", "Volgograd Oblast", relation_id=77665),
    r("ru-vologda", "Вологодская область", "Vologda Oblast", relation_id=115106),
    r("ru-voronezh", "Воронежская область", "Voronezh Oblast", relation_id=72181),
    r("ru-zaporozhye", "Запорожская область", "Zaporozhye Oblast", "Запорізька область", 71980),
    r("ru-ivanovo", "Ивановская область", "Ivanovo Oblast", relation_id=85617),
    r("ru-irkutsk", "Иркутская область", "Irkutsk Oblast", relation_id=145454),
    r("ru-kaliningrad", "Калининградская область", "Kaliningrad Oblast", relation_id=103906),
    r("ru-kaluga", "Калужская область", "Kaluga Oblast", relation_id=81995),
    r(
        "ru-kemerovo",
        "Кемеровская область — Кузбасс",
        "Kemerovo Oblast — Kuzbass",
        osm="Кемеровская область",
        relation_id=144763,
    ),
    r("ru-kirov", "Кировская область", "Kirov Oblast", relation_id=115100),
    r("ru-kostroma", "Костромская область", "Kostroma Oblast", relation_id=85963),
    r("ru-kurgan", "Курганская область", "Kurgan Oblast", relation_id=140290),
    r("ru-kursk", "Курская область", "Kursk Oblast", relation_id=72223),
    r("ru-leningrad", "Ленинградская область", "Leningrad Oblast", relation_id=176095),
    r("ru-lipetsk", "Липецкая область", "Lipetsk Oblast", relation_id=72169),
    r("ru-magadan", "Магаданская область", "Magadan Oblast", relation_id=151228),
    r("ru-moscow-oblast", "Московская область", "Moscow Oblast", relation_id=51490),
    r("ru-murmansk", "Мурманская область", "Murmansk Oblast", relation_id=2099216),
    r("ru-nizhny-novgorod", "Нижегородская область", "Nizhny Novgorod Oblast", relation_id=72195),
    r("ru-novgorod", "Новгородская область", "Novgorod Oblast", relation_id=89331),
    r("ru-novosibirsk", "Новосибирская область", "Novosibirsk Oblast", relation_id=140294),
    r("ru-omsk", "Омская область", "Omsk Oblast", relation_id=140292),
    r("ru-orenburg", "Оренбургская область", "Orenburg Oblast", relation_id=77669),
    r("ru-oryol", "Орловская область", "Oryol Oblast", relation_id=72224),
    r("ru-penza", "Пензенская область", "Penza Oblast", relation_id=72182),
    r("ru-pskov", "Псковская область", "Pskov Oblast", relation_id=155262),
    r("ru-rostov", "Ростовская область", "Rostov Oblast", relation_id=85606),
    r("ru-ryazan", "Рязанская область", "Ryazan Oblast", relation_id=71950),
    r("ru-samara", "Самарская область", "Samara Oblast", relation_id=72194),
    r("ru-saratov", "Саратовская область", "Saratov Oblast", relation_id=72193),
    r("ru-sakhalin", "Сахалинская область", "Sakhalin Oblast", relation_id=394235),
    r("ru-sverdlovsk", "Свердловская область", "Sverdlovsk Oblast", relation_id=79379),
    r("ru-smolensk", "Смоленская область", "Smolensk Oblast", relation_id=81996),
    r("ru-tambov", "Тамбовская область", "Tambov Oblast", relation_id=72180),
    r("ru-tver", "Тверская область", "Tver Oblast", relation_id=2095259),
    r("ru-tomsk", "Томская область", "Tomsk Oblast", relation_id=140295),
    r("ru-tula", "Тульская область", "Tula Oblast", relation_id=81993),
    r("ru-tyumen", "Тюменская область", "Tyumen Oblast", relation_id=140291),
    r("ru-ulyanovsk", "Ульяновская область", "Ulyanovsk Oblast", relation_id=72192),
    r("ru-kherson", "Херсонская область", "Kherson Oblast", "Херсонська область", 71022),
    r("ru-chelyabinsk", "Челябинская область", "Chelyabinsk Oblast", relation_id=77687),
    r("ru-yaroslavl", "Ярославская область", "Yaroslavl Oblast", relation_id=81994),
    r("ru-moscow", "Москва", "Moscow", relation_id=102269),
    r("ru-saint-petersburg", "Санкт-Петербург", "Saint Petersburg", relation_id=337422),
    r("ru-sevastopol", "Севастополь", "Sevastopol", relation_id=1574364),
    r("ru-jewish-autonomous", "Еврейская автономная область", "Jewish Autonomous Oblast", relation_id=147167),
    r("ru-nenets", "Ненецкий автономный округ", "Nenets Autonomous Okrug", relation_id=274048),
    r(
        "ru-khanty-mansi",
        "Ханты-Мансийский автономный округ — Югра",
        "Khanty-Mansi Autonomous Okrug — Yugra",
        relation_id=140296,
    ),
    r("ru-chukotka", "Чукотский автономный округ", "Chukotka Autonomous Okrug", relation_id=151231),
    r("ru-yamalo-nenets", "Ямало-Ненецкий автономный округ", "Yamalo-Nenets Autonomous Okrug", relation_id=191706),
]


BELARUS: list[Region] = [
    b("by-brest", "Брестская область", "Brest Region", osm="Брэсцкая вобласць", relation_id=59189),
    b("by-vitebsk", "Витебская область", "Vitebsk Region", osm="Віцебская вобласць", relation_id=59506),
    b("by-gomel", "Гомельская область", "Gomel Region", osm="Гомельская вобласць", relation_id=59161),
    b("by-grodno", "Гродненская область", "Grodno Region", osm="Гродзенская вобласць", relation_id=59275),
    b("by-minsk-region", "Минская область", "Minsk Region", osm="Мінская вобласць", relation_id=59752),
    b("by-mogilev", "Могилёвская область", "Mogilev Region", osm="Магілёўская вобласць", relation_id=59162),
    b("by-minsk", "Минск", "Minsk", osm="Мінск", relation_id=59195),
]


REGIONS: list[Region] = RUSSIA + BELARUS
