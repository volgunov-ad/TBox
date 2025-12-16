package vad.dashing.tbox

object CsnOperatorResolver {
    private val operators = mapOf(
        // Россия (MCC = 250)
        "25001" to "МТС",
        "25002" to "МегаФон",
        "25003" to "Ростелеком",
        "25004" to "МТС (Сибчеллендж)",
        "25005" to "ЕТК",
        "25006" to "Danycom (MVNO-T2)",
        "25007" to "Смартс",
        "25008" to "Вайнах Телеком",
        "25009" to "Скайлинк / Сотел ССБ",
        "25010" to "МТС (Донтелеком)",
        "25011" to "Yota",
        "25012" to "АКОС / Дальсвязь (T2)",
        "25013" to "МТС (Кубань GSM)",
        "25014" to "МегаФон (дублирующий)",
        "25016" to "Miatel",
        "25017" to "Уралсвязьинформ",
        "25018" to "Астран (MVNO-T2)",
        "25019" to "Альфа-Мобайл (MVNO-Билайн)",
        "25020" to "T2 (Tele2)",
        "25023" to "Джи Ти Эн Ти",
        "25026" to "ВТБ Мобайл (MVNO-T2)",
        "25027" to "Летай (Tattelecom)",
        "25030" to "Остелеком",
        "25032" to "WIN-Mobile",
        "25033" to "СевМобайл",
        "25034" to "Крымтелеком",
        "25035" to "МОТИВ",
        "25037" to "MCN Telecom",
        "25039" to "Ростелеком (Utel и др.)",
        "25040" to "Воентелеком (MVNO-T2)",
        "25042" to "Межрегиональный ТранзитТелеком",
        "25043" to "Спринт",
        "25045" to "Газпромбанк Мобайл",
        "25047" to "Next Mobile / ГородМобайл",
        "25048" to "V-Tell",
        "25050" to "СберМобайл",
        "25051" to "Центр 2М",
        "25054" to "Miranda-Media",
        "25055" to "ГЛОНАСС (НП)",
        "25059" to "Wifire",
        "25060" to "Волна мобайл",
        "25061" to "Интертелеком",
        "25062" to "Т-Мобайл",
        "25077" to "ГЛОНАСС (АО)",
        "25091" to "МегаФон (Соник Дуо)",
        "25092" to "МТС (Примтелефон)",
        "25094" to "МирТелеком",
        "25096" to "+7Телеком",
        "25097" to "Феникс (ДНР)",
        "25098" to "МКС (ЛНР)",
        "25099" to "Билайн / Экстел / НТК",

        // Беларусь (MCC = 257) :contentReference[oaicite:5]{index=5}
        "25701" to "A1 Belarus (velcom)",
        "25702" to "MTS Belarus",
        "25704" to "life:)",
        "25705" to "Beltelecom (RUE Beltelecom)",
        "25706" to "beCloud",

        // Украина (MCC = 255) — оставлены по предыдущим данным

        "25501" to "Vodafone Ukraine",
        "25502" to "Kyivstar (старый код)",
        "25503" to "Kyivstar",
        "25504" to "Intertelecom",
        "25505" to "Kyivstar (Golden Telecom)",
        "25506" to "lifecell",
        "25507" to "3Mob / Lycamobile",
        "25508" to "Ukrtelecom",
        "25509" to "Farlep-Invest",
        "25510" to "Atlantis Telecom",
        "25521" to "PEOPLEnet",
        "25523" to "CDMA Ukraine",
        "25525" to "NEWTONE",
        "25599" to "Phoenix / MKS (ДНР/ЛНР)",

        // Казахстан (MCC = 401) :contentReference[oaicite:6]{index=6}
        "40101" to "Beeline Kazakhstan",
        "40102" to "Kcell",
        "40107" to "Altel",
        "40108" to "Kazakhtelecom",
        "40177" to "Tele2 Kazakhstan",

        // Азербайджан (MCC = 400) :contentReference[oaicite:7]{index=7}
        "40001" to "Azercell",
        "40002" to "Bakcell",
        "40003" to "Catel (FONEX)",
        "40004" to "Azerfon (Nar Mobile)",
        "40005" to "SSPS (спецслужбы)",
        "40006" to "Nakhtel",

        // Армения (MCC = 283) :contentReference[oaicite:8]{index=8}
        "28301" to "Team (Telecom Armenia)",
        "28304" to "Karabakh Telecom (не работает)",
        "28305" to "Viva Armenia (K Telecom)",
        "28310" to "Ucom",

        // Кыргызстан (MCC = 437) :contentReference[oaicite:9]{index=9}
        "43701" to "Beeline Kyrgyzstan",
        "43703" to "NurTelecom (Fonex)",
        "43705" to "MegaCom",
        "43709" to "O!",
        "43704" to "Alfa Telecom", // из Википедии RU расширение
        "43706" to "Kyrgyztelecom",
        "43710" to "Saima Telecom",

        // Узбекистан (MCC = 434) :contentReference[oaicite:10]{index=10}
        "43403" to "Uztelecom (CDMA)",
        "43404" to "Beeline UZ",
        "43405" to "Ucell",
        "43406" to "Perfectum Mobile",
        "43407" to "Mobiuz",
        "43408" to "Uztelecom (GSM)",
        "43410" to "Humans (MVNO Uztelecom)",

        // Таджикистан (MCC = 436)
        // Только MCC указан; подробности нужно получить из других источников
        // Пока добавляем заглушку:
        "436" to "Оператор Таджикистана (код 436)"
    )

    fun getOperatorName(mcc: String, mnc: String): String {
        val key = mcc + mnc
        return operators[key] ?: key
    }
}