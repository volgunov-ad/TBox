package vad.dashing.tbox.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.PlaceCallFailureReason
import vad.dashing.tbox.PlaceCallResult
import vad.dashing.tbox.R
import vad.dashing.tbox.WIDGET_TITLE_POSITION_BOTTOM
import vad.dashing.tbox.hasCallPhonePermission
import vad.dashing.tbox.isHfpClientConnected
import vad.dashing.tbox.normalizeDialPhoneNumber
import vad.dashing.tbox.normalizeWidgetTitlePosition
import vad.dashing.tbox.phone.PhoneCallControlResult
import vad.dashing.tbox.phone.PhoneCallState
import vad.dashing.tbox.phone.PhoneCallStateRepository
import vad.dashing.tbox.phone.PhoneDialCallActivity
import vad.dashing.tbox.phoneDialFailureMessage
import vad.dashing.tbox.placeCallFailureUserMessage
import vad.dashing.tbox.placeOutgoingCall

@Composable
internal fun DashboardPhoneDialWidgetItem(
    widget: DashboardWidget,
    phoneNumber: String,
    showNumber: Boolean,
    confirmBeforeCall: Boolean,
    showTitle: Boolean,
    titleOverride: String = "",
    defaultTitle: String,
    isEditMode: Boolean,
    onEditClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val callSnap by PhoneCallStateRepository.snapshot.collectAsStateWithLifecycle()

    val dialNumber = remember(phoneNumber) { normalizeDialPhoneNumber(phoneNumber) }
    val displayNumber = dialNumber.ifBlank { phoneNumber.trim() }
    val titleLine = titleOverride.trim().ifBlank { defaultTitle }
    val centerOnlyTitle = !showNumber

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    // Permission and confirm UI live in PhoneDialCallActivity: on floating panels this tile is
    // composed in a Service context (no ActivityResultRegistry, no Dialog window token).
    // During dialing / active / held / incoming, tap ends the call (hangup / reject) instead of
    // placing another outgoing call.
    fun requestCall() {
        when (callSnap.callState) {
            PhoneCallState.DIALING,
            PhoneCallState.ACTIVE,
            PhoneCallState.HELD,
            PhoneCallState.INCOMING,
            -> {
                when (val hangup = PhoneCallStateRepository.hangupCall()) {
                    is PhoneCallControlResult.Success -> Unit
                    is PhoneCallControlResult.Failure -> showToast(hangup.message)
                }
                return
            }
            PhoneCallState.IDLE,
            PhoneCallState.UNKNOWN,
            -> Unit
        }
        if (dialNumber.isEmpty()) {
            showToast(resources.getString(R.string.widget_phone_dial_empty_number))
            return
        }
        val granted = hasCallPhonePermission(context)
        if (granted && !isHfpClientConnected()) {
            showToast(resources.getString(R.string.widget_phone_dial_no_hfp))
            return
        }
        if (granted && !confirmBeforeCall) {
            val result = placeOutgoingCall(context, dialNumber)
            if (result is PlaceCallResult.Failure) {
                showToast(phoneDialFailureMessage(resources, result))
            }
            return
        }
        val confirmLabel = if (showNumber) dialNumber else titleLine
        if (!PhoneDialCallActivity.start(context, dialNumber, confirmLabel, confirmBeforeCall)) {
            showToast(placeCallFailureUserMessage(PlaceCallFailureReason.START_FAILED))
        }
    }

    val indicatorColor = when (callSnap.callState) {
        PhoneCallState.DIALING -> Color(0xFFFFC107)
        PhoneCallState.ACTIVE -> Color(0xFF4CAF50)
        PhoneCallState.INCOMING -> Color(0xFF2196F3)
        PhoneCallState.HELD -> Color(0xFFFF9800)
        PhoneCallState.IDLE, PhoneCallState.UNKNOWN -> null
    }

    DashboardWidgetScaffold(
        modifier = Modifier.fillMaxSize(),
        onClick = {
            if (isEditMode) {
                onEditClick()
            } else {
                requestCall()
            }
        },
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        val titleAtBottom =
            normalizeWidgetTitlePosition(LocalWidgetTitlePosition.current) == WIDGET_TITLE_POSITION_BOTTOM
        Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            if (centerOnlyTitle) {
                val titleStyle = calculateResponsiveTextStyle(
                    containerHeight = availableHeight,
                    textType = TextType.TITLE,
                    forWidgetTitle = true,
                )
                Text(
                    text = titleLine,
                    style = titleStyle,
                    color = resolvedTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = widgetColumnHorizontalAlignment(LocalWidgetTextAlign.current),
                ) {
                    if (showTitle && titleLine.isNotEmpty() && !titleAtBottom) {
                        val titleStyle = calculateResponsiveTextStyle(
                            containerHeight = availableHeight,
                            textType = TextType.TITLE,
                            forWidgetTitle = true,
                        )
                        Text(
                            text = titleLine,
                            style = titleStyle,
                            color = resolvedTextColor,
                            textAlign = LocalWidgetTextAlign.current,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val numberStyle = calculateResponsiveTextStyle(
                            containerHeight = availableHeight,
                            textType = TextType.VALUE,
                        )
                        Text(
                            text = displayNumber.ifBlank {
                                stringResource(R.string.widget_phone_dial_number_placeholder)
                            },
                            style = numberStyle,
                            color = resolvedTextColor.copy(
                                alpha = if (displayNumber.isBlank()) 0.45f else 1f,
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (showTitle && titleLine.isNotEmpty() && titleAtBottom) {
                        val titleStyle = calculateResponsiveTextStyle(
                            containerHeight = availableHeight,
                            textType = TextType.TITLE,
                            forWidgetTitle = true,
                        )
                        Text(
                            text = titleLine,
                            style = titleStyle,
                            color = resolvedTextColor,
                            textAlign = LocalWidgetTextAlign.current,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                        )
                    }
                }
            }
            if (indicatorColor != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(10.dp)
                        .background(indicatorColor, CircleShape),
                )
            }
        }
    }
}
