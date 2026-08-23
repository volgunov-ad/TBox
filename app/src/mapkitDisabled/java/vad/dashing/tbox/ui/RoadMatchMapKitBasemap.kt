package vad.dashing.tbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import vad.dashing.tbox.location.roadmatch.RoadMatchCanvasViewport

/**
 * No-op while Yandex MapKit is unbundled (`mapkitEnabled = false` in app/build.gradle.kts).
 * Real implementation lives in `src/mapkitEnabled/`.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun RoadMatchMapKitBasemap(
    viewport: RoadMatchCanvasViewport,
    viewHeightPx: Int,
    transparencyPercent: Int,
    userMapkitApiKey: String,
    modifier: Modifier = Modifier,
) = Unit
