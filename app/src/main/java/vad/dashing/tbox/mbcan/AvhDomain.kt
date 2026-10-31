package vad.dashing.tbox.mbcan

/**
 * Auto Hold cfg **142** on Android 9.
 *
 * Journal `tbox_app_log_20261010_214807`: mark «выключаю автохолд» landed on raw **1**
 * (21:48:44), mark «включаю автохолд» landed on raw **2** (21:49:44). The later mark
 * «держу на автохолд» left cfg 142 at **2**. Stopping with the function off did not
 * produce another code. So **2** is the function on (including while the car is held)
 * and **1** is the function off. Write policy is the same pair.
 *
 * The widget and the settings switch both follow [decodeEnabled] via `avhState`.
 * A10 `R_0400_ESP_3_AVHSts` still uses [MbCanSignalStateEngine.decodeAvhHdcStatusRaw].
 */
object AvhDomain {
    /** **2** function on / **1** function off. Other raw values are off for the widget. */
    fun decodeEnabled(raw: Int): MbCanBinaryState = when (raw) {
        2 -> MbCanBinaryState.On
        else -> MbCanBinaryState.Off
    }
}
