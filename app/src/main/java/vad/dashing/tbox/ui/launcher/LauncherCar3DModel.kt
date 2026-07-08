package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

private const val DRIVE_SPEED_THRESHOLD_KMH = 3f
private const val MODEL_YAW_DEG = -90f
private const val SETTINGS_YAW_DEG = 87f

private val TOP_CAMERA_POS = Float3(0f, 4.8f, 0.08f)
private val TOP_CAMERA_TARGET = Float3(0f, 0f, 0f)
private val DRIVE_CAMERA_POS = Float3(0f, 1.05f, 3.7f)
private val DRIVE_CAMERA_TARGET = Float3(0f, 0.25f, -2.2f)
private val SETTINGS_CAMERA_POS = Float3(2.1f, 1.25f, 2.65f)
private val SETTINGS_CAMERA_TARGET = Float3(0f, 0.22f, 0f)

/** Offline Filament 3D car (Dashing 720) with pivot rig, paint colors and drive camera. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherCar3DModel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modelRevision: Int = 0,
    paintRevision: Int = 0,
    paintId: String = LauncherCarPaint.defaultId,
    rigState: LauncherCarRigState = LauncherCarRigState(),
    speedKmh: Float = 0f,
    steeringDeg: Float = 0f,
    steerPreview: Boolean = false,
    showRoad: Boolean = true,
    settingsView: Boolean = false,
    settingsProgress: Float = 1f,
    settingsUserYawDeg: Float = 0f,
) {
    val interactionModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onClick?.invoke() },
            onLongClick = { onLongClick?.invoke() },
        )
    } else {
        Modifier
    }

    Box(modifier = modifier) {
        if (showRoad) {
            LauncherVirtualRoad(
                speedKmh = speedKmh,
                steerAngleDeg = steeringDeg,
                steerPreview = steerPreview,
                modifier = Modifier.fillMaxSize(),
            )
        }
        LauncherCarFilamentModel(
            modifier = Modifier.fillMaxSize(),
            paintId = paintId,
            rigState = rigState,
            speedKmh = speedKmh,
            steeringDeg = steeringDeg,
            steerPreview = steerPreview,
            settingsView = settingsView,
            settingsProgress = settingsProgress,
            settingsUserYawDeg = settingsUserYawDeg,
        )
        if (onClick != null || onLongClick != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(interactionModifier),
            )
        }
    }
}

@Composable
private fun LauncherCarFilamentModel(
    modifier: Modifier = Modifier,
    paintId: String,
    rigState: LauncherCarRigState,
    speedKmh: Float,
    steeringDeg: Float,
    steerPreview: Boolean,
    settingsView: Boolean,
    settingsProgress: Float,
    settingsUserYawDeg: Float,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val modelInstance = rememberModelInstance(modelLoader, LAUNCHER_CAR_MODEL_ASSET)

    val speedRef = rememberUpdatedState(speedKmh)
    val steerRef = rememberUpdatedState(steeringDeg)
    var lastFrameNs by remember { mutableLongStateOf(0L) }
    var driveBlend by remember { mutableFloatStateOf(0f) }
    var steerVisual by remember { mutableFloatStateOf(0f) }
    val modelNodeRef = remember { mutableStateOf<ModelNode?>(null) }
    val paintMaterialsRef = remember { mutableStateOf<List<com.google.android.filament.MaterialInstance>>(emptyList()) }

    LaunchedEffect(modelInstance, paintId) {
        val instance = modelInstance ?: return@LaunchedEffect
        val materials = LauncherCarPaint.bindMaterials(instance)
        paintMaterialsRef.value = materials
        LauncherCarPaint.apply(materials, paintId)
    }

    Box(modifier = modifier) {
        if (modelInstance != null) {
            SceneView(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { false },
                engine = engine,
                modelLoader = modelLoader,
                cameraNode = cameraNode,
                cameraManipulator = null,
                isOpaque = false,
                autoFitContent = false,
                onFrame = { frameNs ->
                    val node = modelNodeRef.value ?: return@SceneView
                    val dt = if (lastFrameNs == 0L) {
                        0.016f
                    } else {
                        ((frameNs - lastFrameNs) / 1_000_000_000f).coerceAtMost(0.05f)
                    }
                    lastFrameNs = frameNs

                    val transition = settingsProgress.coerceIn(0f, 1f)
                    val driveTarget = when {
                        settingsView -> 0f
                        speedRef.value > DRIVE_SPEED_THRESHOLD_KMH -> 1f
                        steerPreview -> 1f
                        else -> 0f
                    }
                    driveBlend += (driveTarget - driveBlend) * (1f - kotlin.math.exp(-dt * 2.0f))

                    val steerNorm = LauncherSteerVisual.visualSteerNorm(steerRef.value)
                    steerVisual += (steerNorm - steerVisual) * (1f - kotlin.math.exp(-dt * LauncherSteerVisual.STEER_SMOOTH_RATE))
                    if (settingsView) {
                        cameraNode.worldPosition = Position(
                            SETTINGS_CAMERA_POS.x,
                            SETTINGS_CAMERA_POS.y,
                            SETTINGS_CAMERA_POS.z,
                        )
                        cameraNode.lookAt(
                            Position(
                                SETTINGS_CAMERA_TARGET.x,
                                SETTINGS_CAMERA_TARGET.y,
                                SETTINGS_CAMERA_TARGET.z,
                            ),
                        )
                    } else {
                        updateDriveCamera(
                            cameraNode = cameraNode,
                            blend = driveBlend,
                            steerNorm = steerVisual,
                        )
                    }
                    val steerBlend = driveBlend
                    val driveYaw = MODEL_YAW_DEG - steerVisual * 4.8f * steerBlend
                    val modelYaw = if (settingsView) {
                        lerp(MODEL_YAW_DEG, SETTINGS_YAW_DEG, transition) + settingsUserYawDeg
                    } else {
                        driveYaw
                    }
                    node.rotation = Rotation(y = modelYaw)
                    if (settingsView) {
                        node.position = Position(
                            x = lerp(0f, -0.34f, transition),
                            y = lerp(-0.1f, -0.08f, transition),
                            z = lerp(0f, -0.06f, transition),
                        )
                        node.scale = Scale(lerp(0.36f, 0.44f, transition))
                    } else {
                        node.position = Position(y = -0.1f)
                        node.scale = Scale(0.36f)
                    }
                },
            ) {
                ModelNode(
                    modelInstance = modelInstance,
                    autoAnimate = false,
                    scale = Scale(0.36f),
                    position = Position(y = -0.1f),
                    rotation = Rotation(y = MODEL_YAW_DEG),
                    apply = { modelNodeRef.value = this },
                )
            }
        }
    }
}

private fun updateDriveCamera(
    cameraNode: CameraNode,
    blend: Float,
    steerNorm: Float,
) {
    val topPos = TOP_CAMERA_POS
    val drivePos = DRIVE_CAMERA_POS
    val topTarget = TOP_CAMERA_TARGET
    val driveTarget = DRIVE_CAMERA_TARGET

    val pos = lerp3(topPos, drivePos, blend)
    val target = lerp3(topTarget, driveTarget, blend)
    cameraNode.worldPosition = Position(pos.x, pos.y, pos.z)
    cameraNode.lookAt(Position(target.x, target.y, target.z))
}

private fun lerp3(a: Float3, b: Float3, t: Float): Float3 = Float3(
    a.x + (b.x - a.x) * t,
    a.y + (b.y - a.y) * t,
    a.z + (b.z - a.z) * t,
)

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
