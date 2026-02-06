package vad.dashing.tbox.utils

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

class MotorHoursBuffer(private val maxDif: Float = 0.02f) {
    private var lastTime = System.currentTimeMillis()
    private var lastRPM = 0f

    fun updateValue(rpm: Float): Float {
        if (rpm < 200f && lastRPM < 200f) {
            lastRPM = rpm
            lastTime = System.currentTimeMillis()
            return 0f
        }
        lastRPM = rpm
        val newHours = (System.currentTimeMillis() - lastTime) / 3600000.0f
        if (newHours > 10 * maxDif) {
            lastTime = System.currentTimeMillis()
            return 0f
        } else if (newHours >= maxDif) {
            lastTime = System.currentTimeMillis()
            return newHours
        } else {
            return 0f
        }
    }
}