package com.nhom21.english_learning_app.features.round.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as BitmapCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Base64
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.features.round.Round3UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

@Composable
internal fun MatchingLoading(onExit: () -> Unit) {
    val colors = matchingColors
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        val contentHeight = maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = contentHeight)
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth()) { MatchingBackButton(onExit, close = true) }
            Spacer(Modifier.height((contentHeight * 0.20f).coerceIn(38.dp, 120.dp)))
            MatchingMascot(204, framed = true)
            Spacer(Modifier.height(15.dp))
            Text(stringResource(R.string.round_loading), style = matchingTextStyle(28, 850), color = colors.accent)
            Spacer(Modifier.height(26.dp))
            LinearProgressIndicator(
                modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth().height(9.dp).clip(CircleShape),
                color = colors.accent, trackColor = colors.track,
            )
            Spacer(Modifier.height(15.dp))
            Text(
                stringResource(R.string.round3_loading_body), style = matchingTextStyle(14, 620),
                color = colors.muted, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun MatchingStatusScreen(
    title: Int,
    description: Int,
    action: Int?,
    onAction: () -> Unit,
    onExit: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(matchingColors.background).safeDrawingPadding()) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) { MatchingBackButton(onExit) }
        Box(
            Modifier.align(Alignment.Center).padding(horizontal = 22.dp).widthIn(max = 382.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            MatchingStatusCard(title, description, action, onAction)
        }
    }
}

@Composable
internal fun MatchingStatusCard(title: Int, description: Int, action: Int?, onAction: () -> Unit) {
    val colors = matchingColors
    val loading = action == null
    Column(
        Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(17.dp))
            .border(1.dp, colors.border, RoundedCornerShape(17.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).background(if (loading) colors.selectedFace else colors.wrongFace, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(15.dp), color = colors.selectedInk, strokeWidth = 2.dp)
                } else {
                    Text("!", style = matchingTextStyle(16, 800), color = colors.wrongInk)
                }
            }
            Text(stringResource(title), style = matchingTextStyle(16, 740), color = colors.ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(description), Modifier.weight(1f), style = matchingTextStyle(13, 570),
                color = colors.muted,
            )
            if (action != null) {
                MatchingButton(
                    stringResource(action), onAction, Modifier.width(95.dp), tone = MatchingButtonTone.Yellow, compact = true,
                )
            }
        }
    }
}

@Composable
internal fun MatchingExitDialog(repairing: Boolean, onStay: () -> Unit, onLeave: () -> Unit) {
    Dialog(onDismissRequest = onStay) { MatchingExitPanel(repairing, onStay, onLeave) }
}

@Composable
internal fun MatchingExitPanel(repairing: Boolean, onStay: () -> Unit, onLeave: () -> Unit) {
    val colors = matchingColors
    Column(
        Modifier.widthIn(max = 324.dp).fillMaxWidth().background(colors.border, RoundedCornerShape(25.dp))
            .padding(bottom = 6.dp)
            .background(colors.surface, RoundedCornerShape(25.dp)).verticalScroll(rememberScrollState())
            .padding(horizontal = 23.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MatchingMascot(96, framed = true)
        Spacer(Modifier.height(17.dp))
        Text(
            stringResource(if (repairing) R.string.round3_leave_repair else R.string.round_leave_title),
            style = matchingTextStyle(25, 800), color = colors.accent, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(if (repairing) R.string.round3_leave_repair_body else R.string.round3_exit_body),
            style = matchingTextStyle(14, 550), color = colors.muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(29.dp))
        MatchingButton(stringResource(R.string.round_keep_learning), onStay)
        Spacer(Modifier.height(15.dp))
        MatchingButton(
            stringResource(if (repairing) R.string.round3_view_result else R.string.round_leave),
            onLeave, tone = MatchingButtonTone.Neutral,
        )
    }
}

@Composable
internal fun MatchingRepairIntro(total: Int, onStart: () -> Unit, onExit: () -> Unit) {
    val colors = matchingColors
    Box(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) { MatchingBackButton(onExit, close = true) }
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 382.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MatchingMascot(160)
            Text(
                stringResource(R.string.round3_repair_intro, total),
                style = matchingTextStyle(26, 800), textAlign = TextAlign.Center, color = colors.accent,
            )
            Text(
                stringResource(R.string.round3_repair_hint),
                style = matchingTextStyle(14), textAlign = TextAlign.Center, color = colors.muted,
            )
            MatchingButton(stringResource(R.string.round3_start_repair), onStart)
        }
    }
}

@Composable
internal fun MatchingResult(
    state: Round3UiState.Completed,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    onRepair: () -> Unit,
) {
    val result = state.result
    val colors = matchingColors
    val outcome = stringResource(if (result.passed) R.string.round3_passed else R.string.round3_failed)
    val score = stringResource(
        if (result.passed) R.string.round2_passed else R.string.round2_failed, result.scorePercent,
    )
    BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.background).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val topGap = (maxHeight * 0.12f).coerceIn(36.dp, 135.dp)
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(topGap))
            MatchingMascot(180)
            Spacer(Modifier.height(21.dp))
            Text(
                stringResource(R.string.round_complete),
                style = matchingTextStyle(30, 850), textAlign = TextAlign.Center, color = colors.accent,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                outcome,
                Modifier.semantics { contentDescription = score },
                style = matchingTextStyle(15, 660), color = colors.muted,
            )
            Spacer(Modifier.height(30.dp))
            MatchingStars(result.stars, state.resultId)
            Spacer(Modifier.height(53.dp))
            MatchingButton(
                stringResource(if (result.passed) R.string.round_back_to_lesson else R.string.round_retry),
                if (result.passed) onExit else onRetry,
            )
            if (!result.passed) {
                Spacer(Modifier.height(12.dp))
                MatchingButton(stringResource(R.string.round_back_to_lesson), onExit, tone = MatchingButtonTone.Neutral)
            }
            if (state.repairedCount < state.repairTotal) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRepair, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.round3_repair_action), style = matchingTextStyle(14, 790), color = colors.accent)
                }
            } else if (state.repairTotal > 0) {
                Spacer(Modifier.height(17.dp))
                Text(
                    stringResource(R.string.round3_repaired, state.repairedCount, state.repairTotal),
                    style = matchingTextStyle(14), color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun MatchingMascot(size: Int, framed: Boolean = false) {
    val colors = matchingColors
    Box(
        Modifier.size(size.dp).then(
            if (framed) Modifier.background(colors.surface, CircleShape)
                .border(1.5.dp, colors.border, CircleShape) else Modifier,
        ),
        contentAlignment = Alignment.Center,
    ) {
        val resources = LocalContext.current.resources
        val owl = remember(resources) {
            resources.openRawResource(R.raw.round3_figma_owla).bufferedReader().use { reader ->
                val bytes = Base64.decode(reader.readLine(), Base64.DEFAULT)
                val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                original?.let {
                    val outline = PathParser().parsePathString(reader.readLine()).toPath().asAndroidPath()
                    val bounds = RectF().also { rect -> outline.computeBounds(rect, true) }
                    val crop = Bitmap.createBitmap(
                        ceil(bounds.width()).toInt(), ceil(bounds.height()).toInt(), Bitmap.Config.ARGB_8888,
                    )
                    BitmapCanvas(crop).apply {
                        translate(-bounds.left, -bounds.top)
                        clipPath(outline)
                        drawBitmap(original, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
                    }
                    crop.asImageBitmap()
                }
            }
        }
        val modifier = Modifier.fillMaxSize(if (framed) 0.70f else 0.96f)
        if (owl == null) Image(painterResource(R.drawable.owla_mascot), null, modifier)
        else Image(owl, null, modifier)
    }
}

private val StarPoints = listOf(
    25f to 0f, 32.34f to 15.9f, 49.73f to 17.97f, 36.87f to 29.86f, 40.28f to 47.03f,
    25f to 38.48f, 9.72f to 47.03f, 13.13f to 29.86f, 0.27f to 17.97f, 17.66f to 15.9f,
)

@Composable
private fun MatchingStars(stars: Int, resultId: Long) {
    val colors = matchingColors
    val earned = stars.coerceIn(0, 3)
    val scales = remember(resultId, earned) { List(earned) { Animatable(1f) } }
    LaunchedEffect(scales) {
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
        scales.forEachIndexed { index, scale ->
            launch {
                delay(index * 110L)
                scale.animateTo(1f, keyframes {
                    durationMillis = 220
                    0.8f at 0
                    1.12f at 100
                })
            }
        }
    }
    val label = stringResource(R.string.round_result_stars, earned)
    Row(
        Modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        repeat(3) { index ->
            Canvas(Modifier.size(50.dp, 48.dp).graphicsLayer {
                val scale = scales.getOrNull(index)?.value ?: 1f
                scaleX = scale
                scaleY = scale
            }) {
                val path = Path().apply {
                    StarPoints.forEachIndexed { point, (x, y) ->
                        val px = x / 50f * size.width
                        val py = y / 48f * size.height
                        if (point == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                if (index < earned) {
                    drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFE18A), Color(0xFFF7AE36))))
                } else {
                    drawPath(path, colors.track)
                }
                drawPath(
                    path, if (index < earned) Color(0xFFDCA045) else colors.border,
                    style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round),
                )
            }
        }
    }
}
