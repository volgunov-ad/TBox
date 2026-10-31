package vad.dashing.mqtt.wireguard

import org.junit.Assert.assertEquals
import org.junit.Test

class WgFailureTest {
    @Test
    fun classNameAfterFailedInitBecomesALibraryMessage() {
        val error = NoClassDefFoundError("wgstack.Wgstack")
        assertEquals("Не удалось загрузить библиотеку WireGuard", wireguardFailure(error))
    }

    @Test
    fun linkerTextIsKept() {
        val error = ExceptionInInitializerError(UnsatisfiedLinkError("dlopen failed: library \"libgojni.so\" not found"))
        assertEquals(
            "Не удалось загрузить библиотеку WireGuard: dlopen failed: library \"libgojni.so\" not found",
            wireguardFailure(error),
        )
    }

    @Test
    fun goErrorStaysAsIs() {
        assertEquals("конфигурация WireGuard отклонена", wireguardFailure(IllegalStateException("конфигурация WireGuard отклонена")))
    }
}
