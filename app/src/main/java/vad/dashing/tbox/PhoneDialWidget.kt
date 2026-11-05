package vad.dashing.tbox

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat

/** Dashboard widget: one-tap HFP outgoing call via Telecom `ACTION_CALL`. */
const val PHONE_DIAL_WIDGET_DATA_KEY = "phoneDialWidget"

/** BluetoothProfile.HEADSET_CLIENT — system API constant (not in public SDK stubs). */
internal const val BLUETOOTH_PROFILE_HEADSET_CLIENT = 16

fun isPhoneDialWidgetDataKey(dataKey: String): Boolean =
    dataKey == PHONE_DIAL_WIDGET_DATA_KEY

/**
 * Minimal cleanup for a dial string: trim; drop spaces / dashes / parentheses;
 * keep digits, leading `+`, and `*` / `#`. Empty / no digits → invalid.
 */
fun normalizeDialPhoneNumber(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val out = StringBuilder(trimmed.length)
    var sawDigit = false
    for (c in trimmed) {
        when {
            c.isDigit() -> {
                out.append(c)
                sawDigit = true
            }
            c == '+' && out.isEmpty() -> out.append(c)
            c == '*' || c == '#' -> out.append(c)
            c == ' ' || c == '-' || c == '(' || c == ')' || c == '\u00A0' -> Unit
            // Other characters dropped (minimal cleanup, not a full E.164 parser).
        }
    }
    return if (sawDigit) out.toString() else ""
}

fun hasCallPhonePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Best-effort HFP Client connected check via [BluetoothAdapter.getProfileConnectionState]
 * for profile id [BLUETOOTH_PROFILE_HEADSET_CLIENT].
 */
fun isHfpClientConnected(): Boolean {
    val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull() ?: return false
    if (!adapter.isEnabled) return false
    return runCatching {
        adapter.getProfileConnectionState(BLUETOOTH_PROFILE_HEADSET_CLIENT) ==
            BluetoothProfile.STATE_CONNECTED
    }.getOrDefault(false)
}

sealed class PlaceCallResult {
    data object Success : PlaceCallResult()
    data class Failure(val reason: PlaceCallFailureReason, val detail: String? = null) : PlaceCallResult()
}

enum class PlaceCallFailureReason {
    EMPTY_NUMBER,
    NO_CALL_PHONE_PERMISSION,
    NO_HFP,
    ACTIVITY_NOT_FOUND,
    START_FAILED,
}

/**
 * Fire-and-forget outgoing call: `ACTION_CALL` + `tel:` via Telecom → HfpClientConnectionService.
 * Does not wait for ACTIVE. Caller must hold [Manifest.permission.CALL_PHONE].
 */
fun placeOutgoingCall(context: Context, rawNumber: String): PlaceCallResult {
    val number = normalizeDialPhoneNumber(rawNumber)
    if (number.isEmpty()) {
        return PlaceCallResult.Failure(PlaceCallFailureReason.EMPTY_NUMBER)
    }
    if (!hasCallPhonePermission(context)) {
        return PlaceCallResult.Failure(PlaceCallFailureReason.NO_CALL_PHONE_PERMISSION)
    }
    if (!isHfpClientConnected()) {
        return PlaceCallResult.Failure(PlaceCallFailureReason.NO_HFP)
    }
    val uri = Uri.fromParts("tel", number, null)
    val intent = Intent(Intent.ACTION_CALL, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val resolve = context.packageManager.resolveActivity(intent, 0)
    if (resolve == null) {
        return PlaceCallResult.Failure(PlaceCallFailureReason.ACTIVITY_NOT_FOUND)
    }
    return runCatching {
        context.startActivity(intent)
        PlaceCallResult.Success
    }.getOrElse { error ->
        PlaceCallResult.Failure(
            PlaceCallFailureReason.START_FAILED,
            error.message?.takeIf { it.isNotBlank() },
        )
    }
}

fun placeCallFailureUserMessage(reason: PlaceCallFailureReason, detail: String? = null): String =
    when (reason) {
        PlaceCallFailureReason.EMPTY_NUMBER -> "Задайте номер телефона"
        PlaceCallFailureReason.NO_CALL_PHONE_PERMISSION -> "Нет разрешения на звонки"
        PlaceCallFailureReason.NO_HFP -> "Телефон не подключён по Bluetooth"
        PlaceCallFailureReason.ACTIVITY_NOT_FOUND -> "Нет обработчика звонка"
        PlaceCallFailureReason.START_FAILED ->
            detail?.takeIf { it.isNotBlank() } ?: "Не удалось начать звонок"
    }

/** True when we should request runtime BT connect permission before probing HFP (API 31+). */
fun needsBluetoothConnectPermission(): Boolean = Build.VERSION.SDK_INT >= 31

fun hasBluetoothConnectPermission(context: Context): Boolean {
    if (!needsBluetoothConnectPermission()) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
}
