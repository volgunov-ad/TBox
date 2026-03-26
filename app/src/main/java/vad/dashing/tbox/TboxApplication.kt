package vad.dashing.tbox

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import vad.dashing.tbox.mbcan.MbCanNative

class TboxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MbCanNative.librariesLoaded
        val appDataManager = AppDataManager(this)
        runBlocking {
            val saved = appDataManager.motorHoursFlow.first()
            CarDataRepository.setMotorHours(saved)
            CarDataRepository.markPersisted(saved)
        }
    }
}
