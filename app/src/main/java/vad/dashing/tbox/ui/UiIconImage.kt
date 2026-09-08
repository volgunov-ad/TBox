package vad.dashing.tbox.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.LauncherAppIconPaths
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.UiIconPaths
import vad.dashing.tbox.decodeFileToOwnedImageBitmap

@Immutable
private data class UiIconRuntime(
    val revision: Int = 0,
    val lookup: LauncherAppIconPaths.Lookup = LauncherAppIconPaths.Lookup.None,
    val suppressCustomIcons: Boolean = true,
)

private val LocalUiIconRuntime = compositionLocalOf { UiIconRuntime() }

/**
 * Installs the current sidecar-icon lookup for navigation and dashboard descendants.
 *
 * Overrides are deliberately hidden while a theme is being materialized, preventing a composition
 * from observing a partly replaced theme cache.
 */
@Composable
fun UiIconRuntimeProvider(
    settingsViewModel: SettingsViewModel,
    content: @Composable () -> Unit,
) {
    val revision by settingsViewModel.uiIconRevision.collectAsStateWithLifecycle()
    val themeActivationInProgress by
        settingsViewModel.themeActivationInProgress.collectAsStateWithLifecycle()
    val lookup = rememberLauncherAppIconLookup(settingsViewModel)
    val runtime = remember(revision, lookup, themeActivationInProgress) {
        UiIconRuntime(
            revision = revision,
            lookup = lookup,
            suppressCustomIcons = themeActivationInProgress,
        )
    }
    CompositionLocalProvider(LocalUiIconRuntime provides runtime, content = content)
}

@Composable
private fun rememberCustomUiIconPainter(iconKey: String): BitmapPainter? {
    val context = LocalContext.current
    val runtime = LocalUiIconRuntime.current
    return remember(iconKey, context.filesDir, runtime) {
        if (runtime.suppressCustomIcons) {
            null
        } else {
            UiIconPaths.resolveIconFile(context.filesDir, iconKey, runtime.lookup)
                ?.let(::decodeFileToOwnedImageBitmap)
                ?.let(::BitmapPainter)
        }
    }
}

@Composable
fun customizableUiPainter(
    @DrawableRes id: Int,
    iconKey: String = requireNotNull(UiIconCatalog.keyForDrawable(id)) {
        "Drawable $id is missing from UiIconCatalog"
    },
): Painter = rememberCustomUiIconPainter(iconKey) ?: painterResource(id)

@Composable
fun CustomizableUiImage(
    iconKey: String,
    @DrawableRes drawableRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
) {
    Image(
        painter = customizableUiPainter(drawableRes, iconKey),
        contentDescription = contentDescription,
        modifier = modifier,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
    )
}

@Composable
fun CustomizableUiImage(
    @DrawableRes drawableRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
) {
    CustomizableUiImage(
        iconKey = requireNotNull(UiIconCatalog.keyForDrawable(drawableRes)) {
            "Drawable $drawableRes is missing from UiIconCatalog"
        },
        drawableRes = drawableRes,
        contentDescription = contentDescription,
        modifier = modifier,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
    )
}

@Composable
fun CustomizableUiIcon(
    iconKey: String,
    fallback: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val customPainter = rememberCustomUiIconPainter(iconKey)
    if (customPainter != null) {
        Image(
            painter = customPainter,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            colorFilter = tint.asOptionalColorFilter(),
        )
    } else {
        Icon(
            imageVector = fallback,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
    }
}

@Composable
fun CustomizableUiIcon(
    iconKey: String,
    @DrawableRes drawableRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val customPainter = rememberCustomUiIconPainter(iconKey)
    Icon(
        painter = customPainter ?: painterResource(drawableRes),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun CustomizableUiIcon(
    @DrawableRes drawableRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    CustomizableUiIcon(
        iconKey = requireNotNull(UiIconCatalog.keyForDrawable(drawableRes)) {
            "Drawable $drawableRes is missing from UiIconCatalog"
        },
        drawableRes = drawableRes,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

private fun Color.asOptionalColorFilter(): ColorFilter? =
    if (this == Color.Unspecified) null else ColorFilter.tint(this)
