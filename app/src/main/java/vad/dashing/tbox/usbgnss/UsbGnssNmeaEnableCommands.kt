package vad.dashing.tbox.usbgnss

/**
 * Optional post-open commands to request VTG/ZDA from the GNSS module.
 * Default is off in Settings — many modules already emit these sentences.
 *
 * Unicore (UM980): rate arg 1 = 1 Hz. Does not SAVECONFIG.
 * SiRF path is reserved for true SiRF receivers; not used for CP210x+UM980.
 */
object UsbGnssNmeaEnableCommands {
    fun unicoreEnableVtg(rate: String = "1"): String = "GPVTG $rate"
    fun unicoreEnableZda(rate: String = "1"): String = "GPZDA $rate"

    fun buildUnicoreLines(requestVtg: Boolean, requestZda: Boolean): List<String> {
        val out = ArrayList<String>(2)
        if (requestVtg) out.add(unicoreEnableVtg())
        if (requestZda) out.add(unicoreEnableZda())
        return out
    }
}
