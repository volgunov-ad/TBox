package vad.dashing.usbaudio

data class UsbDeviceInfo(
    val name: String,
    val manufacturer: String?,
    val vendorId: Int,
    val productId: Int,
    val classDescription: String,
    val isAudio: Boolean,
)

object UsbDeviceCatalog {
    fun describe(deviceClass: Int, interfaceClasses: List<Int>): String = when {
        deviceClass != 0x00 -> classText(deviceClass)
        interfaceClasses.isEmpty() -> "Неизвестный класс"
        else -> interfaceClasses.distinct().let { distinct ->
            if (distinct.size == 1) classText(distinct.single())
            else distinct.joinToString(" + ") { classText(it) }
        }
    }

    fun isAudio(deviceClass: Int, interfaceClasses: List<Int>): Boolean =
        deviceClass == 0x01 || interfaceClasses.contains(0x01)

    fun formatId(id: Int): String = "%04x".format(id).uppercase()

    private fun classText(code: Int): String = when (code) {
        0x00 -> "Класс задаётся интерфейсами"
        0x01 -> "Аудио"
        0x02 -> "Связь (CDC)"
        0x03 -> "HID (клавиатура/мышь)"
        0x05 -> "Физическое устройство (джойстик)"
        0x06 -> "Изображения"
        0x07 -> "Принтер"
        0x08 -> "Накопитель"
        0x09 -> "USB-хаб"
        0x0a -> "CDC-данные"
        0x0b -> "Смарт-карта"
        0x0d -> "Шифрование контента"
        0x0e -> "Видео"
        0x0f -> "Медицинское"
        0x10 -> "Аудио/видео"
        0x11 -> "Bluetooth"
        0x12 -> "USB-мост"
        0xdc -> "Диагностика"
        0xe0 -> "Беспроводной контроллер"
        0xef -> "Составное устройство"
        0xfe -> "Прикладной класс"
        0xff -> "Фирменный (vendor-specific)"
        else -> "Класс 0x%02x".format(code)
    }
}
