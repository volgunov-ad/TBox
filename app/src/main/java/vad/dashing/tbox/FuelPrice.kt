package vad.dashing.tbox

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONObject

data class FuelTypeOption(
    val id: Int,
    val label: String,
) {
    override fun toString(): String = label
}

data class FuelCoordinates(
    val latitude: Double,
    val longitude: Double,
)

data class FuelPriceResult(
    val fuelId: Int,
    val name: String,
    val pricePerLiterRub: Float,
    val exact: Boolean,
)

object FuelTypes {
    const val DEFAULT_FUEL_ID = 11

    val options: List<FuelTypeOption> = listOf(
        FuelTypeOption(8, "Аи-92"),
        FuelTypeOption(9, "Аи-92+"),
        FuelTypeOption(11, "Аи-95"),
        FuelTypeOption(12, "Аи-95+"),
        FuelTypeOption(14, "Аи-98"),
        FuelTypeOption(15, "Аи-98+"),
        FuelTypeOption(16, "Аи-100"),
        FuelTypeOption(3, "ДТ"),
        FuelTypeOption(4, "ДТ+"),
        FuelTypeOption(18, "Метан/КПГ"),
        FuelTypeOption(1, "Газ (LPG)"),
    )

    fun optionFor(id: Int): FuelTypeOption =
        options.firstOrNull { it.id == id } ?: options.first { it.id == DEFAULT_FUEL_ID }

    fun baseId(id: Int): Int = when (id) {
        4 -> 3
        9 -> 8
        12 -> 11
        15 -> 14
        else -> id
    }
}

object FuelCostAccounting {
    fun refuelCostRub(refueledLiters: Float, pricePerLiterRub: Float): Float =
        refueledLiters * pricePerLiterRub
}

class FuelPriceClient(
    private val baseUrl: String = "https://multigo.ru",
) {
    fun fetchPrice(
        coordinates: FuelCoordinates,
        fuelId: Int,
    ): FuelPriceResult? {
        val target = FuelTypes.optionFor(fuelId)
        val data = FuelPriceData(
            listJson = JSONObject(
                post(
                    "/api/9/near/list",
                    String.format(
                        Locale.US,
                        "{\"limit\":3,\"fuelId\":%d,\"lat\":%.6f,\"lng\":%.6f}",
                        FuelTypes.baseId(target.id),
                        coordinates.latitude,
                        coordinates.longitude,
                    )
                )
            ),
            avgJson = JSONObject(
                post(
                    "/api/9/avgprices",
                    String.format(
                        Locale.US,
                        "{\"lat\":%.6f,\"lng\":%.6f}",
                        coordinates.latitude,
                        coordinates.longitude,
                    )
                )
            ),
        )
        return FuelPriceResolver.resolve(data, target)
    }

    private fun post(endpoint: String, body: String): String {
        val connection = (URL(baseUrl + endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Origin", baseUrl)
            setRequestProperty("Referer", "$baseUrl/")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36",
            )
        }
        return try {
            connection.outputStream.use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("Fuel price server returned HTTP $code")
            }
            response
        } finally {
            connection.disconnect()
        }
    }
}

data class FuelPriceData(
    val listJson: JSONObject,
    val avgJson: JSONObject,
) {
    val azsList = listJson.optJSONObject("data")?.optJSONArray("list")
    val avgPricesMap = avgJson.optJSONObject("data")?.optJSONObject("avgprice")
}

object FuelPriceResolver {
    fun resolve(data: FuelPriceData, fuel: FuelTypeOption): FuelPriceResult? {
        val exact = findExactPrice(data, fuel)
        if (exact > 0f) {
            return FuelPriceResult(fuel.id, fuel.label, exact, exact = true)
        }
        val average = findAveragePrice(data, fuel.id)
        return if (average > 0f) {
            FuelPriceResult(fuel.id, fuel.label, average, exact = false)
        } else {
            null
        }
    }

    private fun findExactPrice(data: FuelPriceData, fuel: FuelTypeOption): Float {
        val fuels = data.azsList?.optJSONObject(0)?.optJSONArray("fuels") ?: return 0f
        for (i in 0 until fuels.length()) {
            val item = fuels.optJSONObject(i) ?: continue
            if (item.optInt("fuelIdRaw") == fuel.id || fuelLabelMatches(item.optString("fuelId"), fuel.label)) {
                return item.optDouble("fuelPrice", 0.0).toFloat()
            }
        }
        return 0f
    }

    private fun findAveragePrice(data: FuelPriceData, fuelId: Int): Float {
        val avg = data.avgPricesMap ?: return 0f
        return avg.optJSONObject(FuelTypes.baseId(fuelId).toString())
            ?.optDouble("avg", 0.0)
            ?.toFloat()
            ?: 0f
    }

    internal fun fuelLabelMatches(label: String, searchName: String): Boolean {
        val cleanLabel = label.lowercase(Locale.ROOT).trim().replace(" ", "")
        val cleanSearch = searchName.lowercase(Locale.ROOT).trim().replace(" ", "")
        if (cleanSearch.contains("+") != cleanLabel.contains("+")) return false
        if (cleanSearch.contains("дт") || cleanSearch.contains("диз")) {
            return cleanLabel.contains("дт") || cleanLabel.contains("диз")
        }
        if (cleanSearch.contains("суг") || cleanSearch.contains("кпг")) {
            return cleanSearch.length >= 3 && cleanLabel.contains(cleanSearch.substring(0, 3))
        }
        val searchDigits = cleanSearch.replace("\\D".toRegex(), "")
        val labelDigits = cleanLabel.replace("\\D".toRegex(), "")
        return searchDigits.isNotEmpty() && searchDigits == labelDigits
    }
}
