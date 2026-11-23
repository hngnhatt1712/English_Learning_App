package com.nhom21.english_learning_app.core.ui.components

import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.Group
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.RenderVectorGroup
import androidx.compose.ui.graphics.vector.VectorConfig
import androidx.compose.ui.graphics.vector.VectorProperty
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/** Stable names from owla-design-system/references/mascot.md, including future Tier B art. */
enum class OwlaExpression {
    Idle, Think, Happy, Celebrate, Encourage, Surprised, Sad, Sleepy, Offline, Teach,
}

/**
 * Owla Tier A. The caller owns sizing; ContentScale.Fit preserves the 1197:1519
 * proportions even in a square/non-proportional slot. Default slot: Home's 96dp.
 * Set [animationsEnabled] to false for static content or an app reduced-motion setting.
 * System animator scale and the host lifecycle are also respected automatically.
 * Decorative by default; give [contentDescription] only when Owla conveys information.
 */
@Composable
fun OwlaMascot(
    expression: OwlaExpression = OwlaExpression.Idle,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    animationsEnabled: Boolean = true,
) {
    val inspection = LocalInspectionMode.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val systemMotion = rememberOwlaSystemMotionEnabled()
    val moving = animationsEnabled && systemMotion && !inspection &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val breath = remember { Animatable(0f) }
    val blink = remember { Animatable(0f) }
    val sleepy = expression == OwlaExpression.Sleepy

    // Exactly two repeating jobs, both cancelled on disposal, pause or reduced motion.
    LaunchedEffect(moving, sleepy) {
        breath.snapTo(0f)
        if (moving) {
            val halfCycle = if (sleepy) 2000 else 1500
            while (isActive) {
                // Also support hosts overriding Compose's motion scale independently.
                val scale = coroutineContext[MotionDurationScale]
                if (scale != null && scale.scaleFactor <= 0f) {
                    snapshotFlow { scale.scaleFactor }.first { it > 0f }
                }
                breath.animateTo(1f, tween(halfCycle, easing = FastOutSlowInEasing))
                breath.animateTo(0f, tween(halfCycle, easing = FastOutSlowInEasing))
            }
        }
    }
    LaunchedEffect(moving) {
        blink.snapTo(0f)
        if (moving) {
            while (isActive) {
                delay(Random.nextLong(3000L, 5001L))
                blink.animateTo(1f, tween(80, easing = FastOutSlowInEasing))
                blink.animateTo(0f, tween(80, easing = FastOutSlowInEasing))
            }
        }
    }

    val target = expression.pose()
    val transition = updateTransition(target, label = "Owla expression")
    val gazeX by transition.animateFloat(
        transitionSpec = { if (moving) tween(ExpressionMillis) else snap() }, label = "Gaze x",
    ) { it.gazeX }
    val gazeY by transition.animateFloat(
        transitionSpec = { if (moving) tween(ExpressionMillis) else snap() }, label = "Gaze y",
    ) { it.gazeY }
    val tilt by transition.animateFloat(
        transitionSpec = { if (moving) tween(ExpressionMillis) else snap() }, label = "Tilt",
    ) { it.tilt }
    val wings by transition.animateFloat(
        transitionSpec = { if (moving) tween(ExpressionMillis) else snap() }, label = "Wings",
    ) { it.wings }
    val lids by transition.animateFloat(
        transitionSpec = { if (moving) tween(ExpressionMillis) else snap() }, label = "Lids",
    ) { it.lids }
    val lower by transition.animateFloat(
        transitionSpec = { if (moving) tween(ExpressionMillis) else snap() }, label = "Lower body",
    ) { it.lower }
    val bounce by transition.animateFloat(
        transitionSpec = { if (moving) tween(ExpressionMillis) else snap() }, label = "Bounce",
    ) { it.bounce }
    val surprise by transition.animateFloat(
        transitionSpec = { if (moving) tween(ExpressionMillis) else snap() }, label = "Surprise",
    ) { it.surprise }
    val phase = if (moving) breath.value else 0f
    val closure = maxOf(if (moving) blink.value else 0f, if (moving) lids else target.lids)
    // Read the target directly when stopped: no transient pose from an interrupted transition.
    val pose = if (moving) OwlaPose(gazeX, gazeY, tilt, wings, lids, lower, bounce, surprise) else target
    val vector = ImageVector.vectorResource(R.drawable.owla_mascot)
    val configs = layerConfigs(pose, phase, closure)
    val painter = rememberVectorPainter(
        defaultWidth = vector.defaultWidth,
        defaultHeight = vector.defaultHeight,
        viewportWidth = vector.viewportWidth,
        viewportHeight = vector.viewportHeight,
        autoMirror = false,
    ) { _, _ ->
        Group(
            name = "owla_pose",
            pivotX = 598.5f, pivotY = 759.5f,
            // Inset leaves room for breathing/tilt without clipping the source silhouette.
            scaleX = 0.94f + phase * 0.009f,
            scaleY = 0.94f + phase * 0.009f,
            rotation = pose.tilt,
            translationY = pose.lower - phase * pose.bounce,
        ) {
            RenderVectorGroup(vector.root, configs)
        }
    }
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(96.dp).owlaHalo(),
        contentScale = ContentScale.Fit,
    )
}

/** Static, untinted head-only logo. Default slot is 48dp; the caller can override it. */
@Composable
fun OwlaHeaderLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(R.drawable.owla_head),
        contentDescription = contentDescription,
        modifier = modifier.size(48.dp).owlaHalo(),
        contentScale = ContentScale.Fit,
    )
}

private const val ExpressionMillis = 350

private data class OwlaPose(
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    val tilt: Float = 0f,
    val wings: Float = 0f,
    val lids: Float = 0f,
    val lower: Float = 0f,
    val bounce: Float = 0f,
    val surprise: Float = 0f,
)

/**
 * Central fallback table from the design system. Tier B eyes/props are not available.
 * Celebrate keeps raised wings + a gentle bounce; no confetti/props in this component.
 * Offline uses its documented Idle base, without the out-of-scope wifi prop.
 * Teach uses Idle until the book prop exists. Surprised shrinks pupils and lifts tufts.
 */
private fun OwlaExpression.pose(): OwlaPose = when (this) {
    OwlaExpression.Idle -> OwlaPose()
    OwlaExpression.Think -> OwlaPose(gazeX = -22f, gazeY = -24f, tilt = -4f)
    // Keep Tier A wing motion gentle and inside the shared source viewport.
    OwlaExpression.Happy -> OwlaPose(wings = 3f, bounce = 10f)
    OwlaExpression.Celebrate -> OwlaPose(wings = 5f, bounce = 16f)
    OwlaExpression.Encourage -> OwlaPose(gazeY = -16f, tilt = 4f, wings = -3f)
    OwlaExpression.Surprised -> OwlaPose(lower = -8f, surprise = 1f)
    OwlaExpression.Sad -> OwlaPose(gazeY = 30f, wings = -10f, lower = 12f)
    OwlaExpression.Sleepy -> OwlaPose(tilt = 4f, lids = 0.85f)
    OwlaExpression.Offline, OwlaExpression.Teach -> OwlaPose()
}

private class OwlaVectorConfig(
    vararg values: Pair<VectorProperty<*>, Any>,
) : VectorConfig {
    private val overrides = values.toMap()

    @Suppress("UNCHECKED_CAST")
    override fun <T> getOrDefault(property: VectorProperty<T>, defaultValue: T): T =
        overrides[property] as? T ?: defaultValue
}

private fun rectangle(top: Float, bottom: Float): List<PathNode> = listOf(
    PathNode.MoveTo(0f, top), PathNode.LineTo(1197f, top),
    PathNode.LineTo(1197f, bottom), PathNode.LineTo(0f, bottom), PathNode.Close,
)

private fun layerConfigs(pose: OwlaPose, phase: Float, closure: Float): Map<String, VectorConfig> {
    val result = mutableMapOf<String, VectorConfig>()
    result["head_tufts_motion"] = OwlaVectorConfig(VectorProperty.TranslateY to -16f * pose.surprise)
    for (side in listOf("left", "right")) {
        result["eye_${side}_look"] = OwlaVectorConfig(
            VectorProperty.TranslateX to pose.gazeX,
            VectorProperty.TranslateY to pose.gazeY,
        )
        val pupilScale = 1f - 0.25f * pose.surprise
        result["pupil_${side}_motion"] = OwlaVectorConfig(
            VectorProperty.PivotX to if (side == "left") 367f else 821f,
            VectorProperty.PivotY to 480f,
            VectorProperty.ScaleX to pupilScale,
            VectorProperty.ScaleY to pupilScale,
        )
        val centerY = if (side == "left") 468f else 467f
        // Cover using the source's outer eye contour and its own cream colour.
        // No screen-colour mask, duplicate eyes, or scaling of glasses/mascot for blinking.
        result["lid_${side}_top_clip"] = OwlaVectorConfig(
            VectorProperty.PathData to rectangle(0f, 250f + (centerY - 250f) * closure),
        )
        result["lid_${side}_bottom_clip"] = OwlaVectorConfig(
            VectorProperty.PathData to rectangle(690f - (690f - centerY) * closure, 1519f),
        )
        for (lid in listOf("top", "bottom")) {
            result["lid_${side}_${lid}_fill"] = OwlaVectorConfig(
                VectorProperty.FillAlpha to if (closure > 0f) 1f else 0f,
            )
        }
        // Flatten the existing crescent shadow into a closed eyelid crease.
        result["lid_${side}_crease"] = OwlaVectorConfig(
            VectorProperty.PivotY to centerY,
            VectorProperty.ScaleY to 0.045f,
            VectorProperty.TranslateY to 5f,
        )
        result["lid_${side}_crease_fill"] = OwlaVectorConfig(
            VectorProperty.FillAlpha to ((closure - 0.65f) / 0.35f).coerceIn(0f, 1f),
        )
        val direction = if (side == "left") 1f else -1f
        result["wing_${side}_motion"] = OwlaVectorConfig(
            VectorProperty.PivotX to if (side == "left") 150f else 1045f,
            VectorProperty.PivotY to 720f,
            VectorProperty.Rotation to direction * (pose.wings + phase * pose.bounce * 0.1f),
        )
    }
    return result
}

@Composable
private fun rememberOwlaSystemMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val inspection = LocalInspectionMode.current
    fun readScale() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    var scale by remember(resolver, inspection) { mutableFloatStateOf(if (inspection) 0f else readScale()) }
    DisposableEffect(resolver, inspection) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scale = readScale() }
        }
        if (!inspection) {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
            )
            scale = readScale()
        }
        onDispose { if (!inspection) resolver.unregisterContentObserver(observer) }
    }
    return scale > 0f
}

@Composable
private fun Modifier.owlaHalo(): Modifier {
    if (!isSystemInDarkTheme()) return this
    val halo = lerp(OwlaColors.DarkSurface, OwlaColors.LightSurface, 0.08f)
    return drawBehind { drawCircle(halo, radius = size.minDimension / 2f) }
}

@Preview(name = "Idle - animated", showBackground = true)
@Composable
private fun OwlaIdlePreview() { OwlaMascot(modifier = Modifier.size(160.dp)) }

@Preview(name = "Think - static", showBackground = true)
@Composable
private fun OwlaThinkPreview() {
    OwlaMascot(OwlaExpression.Think, Modifier.size(160.dp), animationsEnabled = false)
}

@Preview(name = "Head - 40dp and 48dp", showBackground = true)
@Composable
private fun OwlaHeaderPreview() {
    Row { OwlaHeaderLogo(Modifier.size(40.dp)); OwlaHeaderLogo() }
}

@Preview(name = "All expressions - Light", showBackground = true, widthDp = 360, heightDp = 800)
@Preview(name = "All expressions - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 800)
@Composable
private fun OwlaExpressionsPreview() {
    val dark = isSystemInDarkTheme()
    Column(
        Modifier.background(if (dark) OwlaColors.DarkBackground else OwlaColors.LightBackground).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OwlaExpression.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { expression ->
                    Column {
                        OwlaMascot(expression, Modifier.size(104.dp), animationsEnabled = false)
                        Text(expression.name, color = if (dark) OwlaColors.DarkTextPrimary else OwlaColors.LightTextPrimary)
                    }
                }
            }
        }
        Row { OwlaHeaderLogo(Modifier.size(40.dp)); OwlaHeaderLogo() }
        Row {
            OwlaMascot(modifier = Modifier.size(32.dp), animationsEnabled = false)
            OwlaMascot(OwlaExpression.Think, Modifier.size(200.dp), animationsEnabled = false)
        }
    }
}
