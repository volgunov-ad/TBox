import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

// Класс для структуры JSON-файла
data class CalibrationData(
    val realLiters: DoubleArray,
    val sensorLiters: DoubleArray
)

class FuelStorage(private val fileName: String = "calibration.json") {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // Сохранение данных
    fun save(real: DoubleArray, sensor: DoubleArray) {
        val data = CalibrationData(real, sensor)
        File(fileName).writeText(gson.toJson(data))
    }

    // Загрузка данных
    fun load(): CalibrationData? {
        val file = File(fileName)
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), CalibrationData::class.java)
        } catch (e: Exception) {
            println("Ошибка чтения файла: ${e.message}")
            null
        }
    }
}