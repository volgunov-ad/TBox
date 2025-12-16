package vad.dashing.tbox

class FuelLevelBuffer(private val bufferSize: Int = 10) {
    private val buffer = ArrayDeque<UInt>()

    fun addValue(newValue: UInt): Boolean {
        // Добавляем новое значение в буфер
        buffer.addLast(newValue)

        // Если буфер превысил размер, удаляем самое старое значение
        if (buffer.size > bufferSize) {
            buffer.removeFirst()
        }

        // Проверяем, можно ли записать значение (буфер заполнен и все значения одинаковы)
        return shouldWriteToStorage()
    }

    private fun shouldWriteToStorage(): Boolean {
        // Если буфер еще не заполнен, не записываем
        if (buffer.size < bufferSize) return false

        // Проверяем, что все значения в буфере равны первому
        val firstValue = buffer.first()
        return buffer.all { it == firstValue }
    }
}