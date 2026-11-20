package vad.dashing.tbox

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import vad.dashing.tbox.phone.PhoneCallStateRepository

/** Dashboard widget: one-tap HFP outgoing call via Telecom `ACTION_CALL`. */
const val PHONE_DIAL_WIDGET_DATA_KEY = "phoneDialWidget"

/** BluetoothProfile.HEADSET_CLIENT — system API constant (not in public SDK stubs). */
internal const val BLUETOOTH_PROFILE_HEADSET_CLIENT = 16

fun isPhoneDialWidgetDataKey(dataKey: String): Boolean =
    dataKey == PHONE_DIAL_WIDGET_DATA_KEY

/**
 * Minimal cleanup for a dial string: trim; drop spaces / dashes / dots / parentheses;
 * keep ASCII digits, a leading `+`, and `*` / `#`. Empty, no digits, or any other character
 * (letters, a `+` in the middle) → invalid, so a typo never dials a different number.
 */
fun normalizeDialPhoneNumber(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val out = StringBuilder(trimmed.length)
    var sawDigit = false
    for (c in trimmed) {
        when {
            c in '0'..'9' -> {
                out.append(c)
                sawDigit = true
            }
            c == '+' && out.isEmpty() -> out.append(c)
            c == '*' || c == '#' -> out.append(c)
            c == ' ' || c == '-' || c == '.' || c == '(' || c == ')' || c == '\u00A0' -> Unit
            else -> return ""
        }
    }
    return if (sawDigit) out.toString() else ""
}

fun hasCallPhonePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Whether HFP Client (car role, profile [BLUETOOTH_PROFILE_HEADSET_CLIENT]) looks connected
 * enough for `ACTION_CALL` → Telecom → `HfpClientConnectionService`.
 *
 * Combines three signals (any one is enough):
 * 1. [BluetoothAdapter.getProfileConnectionState] for HEADSET_CLIENT
 * 2. `BluetoothHeadsetClient.connectedDevices` from the bound proxy in
 *    [vad.dashing.tbox.phone.PhoneCallStateRepository]
 * 3. Last HEADSET_CLIENT `CONNECTION_STATE_CHANGED` broadcast (`EXTRA_STATE`)
 *
 * On A9/A10 OEM stacks (nFore), (1) alone can stay DISCONNECTED while the stock dialer and
 * Telecom HFP path are Connected — that was the false «Телефон не подключён» toast (S8).
 * Bonded ≠ connected; ACL/`BluetoothHeadset` (profile 1) is not used here.
 */
fun isHfpClientConnected(): Boolean =
    resolveHfpClientConnected(
        adapterProfileConnected = readAdapterHfpProfileConnected(),
        headsetClientConnectedDevices = PhoneCallStateRepository.connectedHeadsetClientDeviceCount(),
        connectionBroadcastConnected = PhoneCallStateRepository.lastHeadsetClientConnectionBroadcast(),
    )

/**
 * Pure HFP-connected decision used by [isHfpClientConnected] and the call-state observer.
 * Prefer the same inputs Telecom's HFP Client path effectively needs: a live HEADSET_CLIENT
 * device (proxy / broadcast), not merely a bonded phone.
 */
internal fun resolveHfpClientConnected(
    adapterProfileConnected: Boolean,
    headsetClientConnectedDevices: Int,
    connectionBroadcastConnected: Boolean?,
): Boolean {
    if (adapterProfileConnected) return true
    if (headsetClientConnectedDevices > 0) return true
    if (connectionBroadcastConnected == true) return true
    return false
}

/** Adapter-only HEADSET_CLIENT profile state (may be stale on OEM Bluetooth). */
internal fun readAdapterHfpProfileConnected(): Boolean {
    val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull() ?: return false
    return try {
        adapter.isEnabled &&
            adapter.getProfileConnectionState(BLUETOOTH_PROFILE_HEADSET_CLIENT) ==
            BluetoothAdapter.STATE_CONNECTED
    } catch (_: SecurityException) {
        false
    } catch (_: RuntimeException) {
        false
    }
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

/** Toast text for the dial tile (localized for the reasons a user can fix). */
fun phoneDialFailureMessage(resources: Resources, failure: PlaceCallResult.Failure): String =
    when (failure.reason) {
        PlaceCallFailureReason.EMPTY_NUMBER ->
            resources.getString(R.string.widget_phone_dial_empty_number)
        PlaceCallFailureReason.NO_CALL_PHONE_PERMISSION ->
            resources.getString(R.string.widget_phone_dial_no_permission)
        PlaceCallFailureReason.NO_HFP -> resources.getString(R.string.widget_phone_dial_no_hfp)
        else -> placeCallFailureUserMessage(failure.reason, failure.detail)
    }

fun hasAnswerPhoneCallsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) ==
        PackageManager.PERMISSION_GRANTED

/** True when we should request runtime BT connect permission before probing HFP (API 31+). */
fun needsBluetoothConnectPermission(): Boolean = Build.VERSION.SDK_INT >= 31

fun hasBluetoothConnectPermission(context: Context): Boolean {
    if (!needsBluetoothConnectPermission()) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
}
