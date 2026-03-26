package vad.dashing.tbox

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class TboxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val appDataManager = AppDataManager(this)
        runBlocking {
            val saved = appDataManager.motorHoursFlow.first()
            CarDataRepository.setMotorHours(saved)
            CarDataRepository.markPersisted(saved)
        }
    }
}
