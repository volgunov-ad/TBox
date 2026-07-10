package vad.dashing.tbox.mbcan

import android.content.Context
import androidx.annotation.StringRes
import com.mengbo.mbCan.defines.MBAudioProperty
import vad.dashing.tbox.R

/** How the value semantics were obtained. */
enum class MbCanAudioPropertyConfidence {
    /** Explicitly used in stock apps or firmware config (AdayoAudioConfig, SystemSettings, Multimedia). */
    CONFIRMED_STOCK,
    /** Derived from SDK names / VHAL property mapping on Android 10. */
    INFERRED,
    /** No reliable stock reference found yet. */
    UNKNOWN,
}

data class MbCanAudioPropertyHelpEntry(
    val propertyId: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val confidence: MbCanAudioPropertyConfidence,
)

object MbCanAudioPropertyHelp {
  private val byPropertyId: Map<Int, MbCanAudioPropertyHelpEntry> = listOf(
        entry(MBAudioProperty.eAUDIO_PROPERTY_SOURCE, R.string.audio_prop_1_title, R.string.audio_prop_1_desc, MbCanAudioPropertyConfidence.INFERRED),
        entry(MBAudioProperty.eAUDIO_PROPERTY_VOLUME, R.string.audio_prop_2_title, R.string.audio_prop_2_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_BALANCE_BALANCE, R.string.audio_prop_3_title, R.string.audio_prop_3_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_BALANCE_FADER, R.string.audio_prop_4_title, R.string.audio_prop_4_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_EQBAND_BASS, R.string.audio_prop_5_title, R.string.audio_prop_5_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_EQBAND_MIDDLE, R.string.audio_prop_6_title, R.string.audio_prop_6_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_EQBAND_TREBLE, R.string.audio_prop_7_title, R.string.audio_prop_7_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_SOFTMUTE, R.string.audio_prop_8_title, R.string.audio_prop_8_desc, MbCanAudioPropertyConfidence.UNKNOWN),
        entry(MBAudioProperty.eAUDIO_PROPERTY_AMPMUTE, R.string.audio_prop_9_title, R.string.audio_prop_9_desc, MbCanAudioPropertyConfidence.UNKNOWN),
        entry(MBAudioProperty.eAUDIO_PROPERTY_EQMODE, R.string.audio_prop_10_title, R.string.audio_prop_10_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_VOLUME_RADAS, R.string.audio_prop_11_title, R.string.audio_prop_11_desc, MbCanAudioPropertyConfidence.UNKNOWN),
        entry(MBAudioProperty.eAUDIO_PROPERTY_VOLUME_INSTRCUMENT, R.string.audio_prop_12_title, R.string.audio_prop_12_desc, MbCanAudioPropertyConfidence.UNKNOWN),
        entry(MBAudioProperty.eAUDIO_PROPERTY_VOLUME_SPEED, R.string.audio_prop_13_title, R.string.audio_prop_13_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_MIX, R.string.audio_prop_14_title, R.string.audio_prop_14_desc, MbCanAudioPropertyConfidence.UNKNOWN),
        entry(MBAudioProperty.eVEHICLE_PROPERTY_XFMIC_MODE, R.string.audio_prop_15_title, R.string.audio_prop_15_desc, MbCanAudioPropertyConfidence.UNKNOWN),
        entry(MBAudioProperty.eAUDIO_PROPERTY_LOUNDNESS_MODE, R.string.audio_prop_16_title, R.string.audio_prop_16_desc, MbCanAudioPropertyConfidence.UNKNOWN),
        entry(MBAudioProperty.eAUDIO_PROPERTY_VOLUME_KEY, R.string.audio_prop_17_title, R.string.audio_prop_17_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_VOLUME_PHONE, R.string.audio_prop_18_title, R.string.audio_prop_18_desc, MbCanAudioPropertyConfidence.INFERRED),
        entry(MBAudioProperty.eAUDIO_PROPERTY_VOLUME_NAVI, R.string.audio_prop_19_title, R.string.audio_prop_19_desc, MbCanAudioPropertyConfidence.INFERRED),
        entry(MBAudioProperty.eAUDIO_PROPERTY_VOLUME_VOICE, R.string.audio_prop_20_title, R.string.audio_prop_20_desc, MbCanAudioPropertyConfidence.INFERRED),
        entry(MBAudioProperty.eAUDIO_PROPERTY_ACOUSTIC_FIELD_MODE, R.string.audio_prop_21_title, R.string.audio_prop_21_desc, MbCanAudioPropertyConfidence.INFERRED),
        entry(MBAudioProperty.eAUDIO_PROPERTY_SOUNDWAVE_MODE, R.string.audio_prop_22_title, R.string.audio_prop_22_desc, MbCanAudioPropertyConfidence.UNKNOWN),
        entry(MBAudioProperty.eAUDIO_PROPERTY_SURROUND, R.string.audio_prop_23_title, R.string.audio_prop_23_desc, MbCanAudioPropertyConfidence.INFERRED),
        entry(MBAudioProperty.eAUDIO_PROPERTY_ACOUSTIC_MODE, R.string.audio_prop_24_title, R.string.audio_prop_24_desc, MbCanAudioPropertyConfidence.UNKNOWN),
        entry(MBAudioProperty.eAUDIO_PROPERTY_LOUDNESS, R.string.audio_prop_25_title, R.string.audio_prop_25_desc, MbCanAudioPropertyConfidence.INFERRED),
        entry(MBAudioProperty.eAUDIO_PROPERTY_RESET, R.string.audio_prop_26_title, R.string.audio_prop_26_desc, MbCanAudioPropertyConfidence.UNKNOWN),
        entry(MBAudioProperty.eAUDIO_PROPERTY_MUSICLOUDNESS_120HZ, R.string.audio_prop_27_title, R.string.audio_prop_27_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_MUSICLOUDNESS_250HZ, R.string.audio_prop_28_title, R.string.audio_prop_28_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_MUSICLOUDNESS_500HZ, R.string.audio_prop_29_title, R.string.audio_prop_29_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_MUSICLOUDNESS_1000HZ, R.string.audio_prop_30_title, R.string.audio_prop_30_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_MUSICLOUDNESS_2000HZ, R.string.audio_prop_31_title, R.string.audio_prop_31_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_MUSICLOUDNESS_6000HZ, R.string.audio_prop_32_title, R.string.audio_prop_32_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_MUSICLOUDNESS_1500HZ, R.string.audio_prop_33_title, R.string.audio_prop_33_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_DEFAULT_MAX_VOLUME, R.string.audio_prop_34_title, R.string.audio_prop_34_desc, MbCanAudioPropertyConfidence.CONFIRMED_STOCK),
        entry(MBAudioProperty.eAUDIO_PROPERTY_FACTORY, R.string.audio_prop_35_title, R.string.audio_prop_35_desc, MbCanAudioPropertyConfidence.UNKNOWN),
        entry(MBAudioProperty.eAUDIO_PROPERTY_VOLUME_BCALL, R.string.audio_prop_36_title, R.string.audio_prop_36_desc, MbCanAudioPropertyConfidence.INFERRED),
        entry(MBAudioProperty.eAUDIO_AUDIO_HEADREST_SPEAKER, R.string.audio_prop_37_title, R.string.audio_prop_37_desc, MbCanAudioPropertyConfidence.UNKNOWN),
    ).associateBy { it.propertyId }

    fun get(propertyId: Int): MbCanAudioPropertyHelpEntry? = byPropertyId[propertyId]

    @StringRes
    fun confidenceLabelRes(confidence: MbCanAudioPropertyConfidence): Int = when (confidence) {
        MbCanAudioPropertyConfidence.CONFIRMED_STOCK -> R.string.audio_help_confirmed
        MbCanAudioPropertyConfidence.INFERRED -> R.string.audio_help_inferred
        MbCanAudioPropertyConfidence.UNKNOWN -> R.string.audio_help_unknown
    }

    /**
     * Human-readable meaning for known discrete values.
     * Returns null when the raw value has no documented mapping.
     */
    fun decodeValue(context: Context, propertyId: Int, raw: Int): String? = when (propertyId) {
        MBAudioProperty.eAUDIO_PROPERTY_SOURCE.value -> when (raw) {
            0 -> context.getString(R.string.audio_value_source_null)
            1 -> context.getString(R.string.audio_value_source_media)
            2 -> context.getString(R.string.audio_value_source_radio)
            3 -> context.getString(R.string.audio_value_source_handsfree)
            4 -> context.getString(R.string.audio_value_source_bcall)
            5 -> context.getString(R.string.audio_value_source_vr)
            else -> null
        }
        MBAudioProperty.eAUDIO_PROPERTY_VOLUME_SPEED.value -> when (raw) {
            1 -> context.getString(R.string.audio_value_vsc_off)
            2 -> context.getString(R.string.audio_value_vsc_low)
            3 -> context.getString(R.string.audio_value_vsc_mid)
            4 -> context.getString(R.string.audio_value_vsc_high)
            else -> null
        }
        MBAudioProperty.eAUDIO_PROPERTY_LOUDNESS.value,
        MBAudioProperty.eAUDIO_PROPERTY_SOFTMUTE.value,
        MBAudioProperty.eAUDIO_PROPERTY_AMPMUTE.value,
        MBAudioProperty.eAUDIO_PROPERTY_SURROUND.value,
        -> when (raw) {
            0, 1 -> context.getString(if (raw == 1) R.string.value_yes else R.string.value_no)
            else -> null
        }
        else -> null
    }

    fun formatCurrentValue(
        context: Context,
        propertyId: Int,
        raw: Int?,
        noDataLabel: String,
    ): String {
        if (raw == null) return noDataLabel
        val decoded = decodeValue(context, propertyId, raw)
        return if (decoded != null) {
            context.getString(R.string.audio_settings_current_value_decoded, raw, decoded)
        } else {
            raw.toString()
        }
    }

    private fun entry(
        property: MBAudioProperty,
        @StringRes titleRes: Int,
        @StringRes descriptionRes: Int,
        confidence: MbCanAudioPropertyConfidence,
    ): MbCanAudioPropertyHelpEntry = MbCanAudioPropertyHelpEntry(
        propertyId = property.value,
        titleRes = titleRes,
        descriptionRes = descriptionRes,
        confidence = confidence,
    )
}
