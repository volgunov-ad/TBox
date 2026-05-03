package vad.dashing.tbox.fuel

import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    val sourceName: String,
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
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
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
        val request = Request.Builder()
            .url(baseUrl + endpoint)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .header("Origin", baseUrl)
            .header("Referer", "$baseUrl/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (response.code != 200) {
                throw IOException("Fuel price server returned HTTP ${response.code}")
            }
            return responseBody
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
    const val AVERAGE_PRICE_SOURCE_NAME = "средняя цена"

    fun resolve(data: FuelPriceData, fuel: FuelTypeOption): FuelPriceResult? {
        val exact = findExactPrice(data, fuel)
        if (exact != null) {
            return FuelPriceResult(fuel.id, fuel.label, exact.price, exact = true, sourceName = exact.sourceName)
        }
        val average = findAveragePrice(data, fuel.id)
        return if (average > 0f) {
            FuelPriceResult(fuel.id, fuel.label, average, exact = false, sourceName = AVERAGE_PRICE_SOURCE_NAME)
        } else {
            null
        }
    }

    private data class ExactPrice(val price: Float, val sourceName: String)

    private fun findExactPrice(data: FuelPriceData, fuel: FuelTypeOption): ExactPrice? {
        val azs = data.azsList?.optJSONObject(0) ?: return null
        val fuels = azs.optJSONArray("fuels") ?: return null
        for (i in 0 until fuels.length()) {
            val item = fuels.optJSONObject(i) ?: continue
            if (item.optInt("fuelIdRaw") == fuel.id || fuelLabelMatches(item.optString("fuelId"), fuel.label)) {
                val price = item.optDouble("fuelPrice", 0.0).toFloat()
                if (price > 0f) return ExactPrice(price, fuelStationName(azs))
            }
        }
        return null
    }

    private fun fuelStationName(azs: JSONObject): String {
        val brand = azs.optJSONObject("brand")?.optString("name").orEmpty().trim()
        val address = azs.optString("address").trim()
        return when {
            brand.isNotEmpty() && address.isNotEmpty() -> "$brand, $address"
            brand.isNotEmpty() -> brand
            address.isNotEmpty() -> address
            else -> "АЗС"
        }
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
