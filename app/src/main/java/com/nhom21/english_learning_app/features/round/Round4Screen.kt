package com.nhom21.english_learning_app.features.round

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.ui.components.Owla3DButton
import com.nhom21.english_learning_app.domain.model.QuestionContent
import com.nhom21.english_learning_app.domain.model.TranslationDirection
import com.nhom21.english_learning_app.features.review.RoundResultScreen
import com.nhom21.english_learning_app.features.round.components.RoundCloseButton
import com.nhom21.english_learning_app.features.round.components.RoundExitDialog
import com.nhom21.english_learning_app.features.round.components.RoundLoadingScreen
import com.nhom21.english_learning_app.features.round.components.RoundNeutralButton
import com.nhom21.english_learning_app.ui.theme.LocalRoundColors
import com.nhom21.english_learning_app.ui.theme.OwlaType
import com.nhom21.english_learning_app.ui.theme.RoundColors

private val Round4Cream = Color(0xFFFCF9F0)
private val Round4Teal = Color(0xFF007C72)
private val Round4TealBase = Color(0xFF006158)
private val Round4TealInk = Color(0xFF005E57)
private val Round4Ink = Color(0xFF183439)
private val Round4Muted = Color(0xFF4F6766)
private val Round4Mint = Color(0xFFE7F3EB)
private val Round4MintBorder = Color(0xFFC9DFD2)
private val Round4PromptColor = Color(0xFFF4EEDD)
private val Round4ChipBorder = Color(0xFFD8D2C1)
private val Round4Success = Color(0xFF226747)
private val Round4Danger = Color(0xFF9A4242)
private val Round4FeedbackGreen = Color(0xFFE8F5EA)
private val Round4FeedbackRed = Color(0xFFF8E9E4)
private val Round4Light = RoundColors(
    background = Color(0xFFEAF4F3),
    surface = Color.White,
    ink = Round4Ink,
    accentInk = Round4TealInk,
    border = Round4MintBorder,
    primaryFace = Round4Teal,
    primaryBase = Round4TealBase,
    primaryLabel = Color.White,
    successInk = Round4Success,
    dangerInk = Round4Danger,
)

@Composable
internal fun Round4Screen(
    state: Round4UiState,
    onToken: (String, String) -> Unit,
    onText: (String, String) -> Unit,
    onMode: (String) -> Unit,
    onCheck: (String) -> Unit,
    onRetryAnswer: (String) -> Unit,
    onNext: (String) -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    imageContent: @Composable (String, Modifier) -> Unit = { url, modifier -> RoundWordImage(url, modifier) },
) {
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity) {
        val light = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.DKGRAY)
        activity?.enableEdgeToEdge(statusBarStyle = light, navigationBarStyle = light)
        onDispose { activity?.enableEdgeToEdge() }
    }
    BackHandler(enabled = !confirmExit) {
        if (state is Round4UiState.Completed || state is Round4UiState.Message) onExit()
        else confirmExit = true
    }
    CompositionLocalProvider(LocalRoundColors provides Round4Light) {
        when (state) {
            Round4UiState.Loading -> Box(Modifier.fillMaxSize().background(Round4Light.background).safeDrawingPadding()) {
                RoundLoadingScreen(
                    onRequestExit = { confirmExit = true },
                    description = stringResource(R.string.round4_loading),
                    mascot = R.drawable.owla_mascot,
                )
            }
            is Round4UiState.Question -> key(state.question.id) {
                val id = state.question.id
                Round4Question(
                    state,
                    onToken = { onToken(id, it) },
                    onText = { onText(id, it) },
                    onMode = { onMode(id) },
                    onCheck = { onCheck(id) },
                    onRetryAnswer = { onRetryAnswer(id) },
                    onNext = { onNext(id) },
                    onExit = { confirmExit = true },
                    imageContent = imageContent,
                )
            }
            is Round4UiState.Message -> RoundStatus(state.reason, onExit, onRetry)
            is Round4UiState.Submitting -> if (state.error == null) {
                Box(Modifier.fillMaxSize().background(Round4Light.background).safeDrawingPadding()) {
                    RoundLoadingScreen(
                        onRequestExit = { confirmExit = true },
                        message = stringResource(R.string.round2_submitting),
                        description = "",
                        mascot = R.drawable.owla_mascot,
                    )
                }
            } else {
                RoundStatusContent(stringResource(R.string.round3_submit_error_body), onExit, onRetry)
            }
            is Round4UiState.Completed -> {
                val result = state.result
                RoundResultScreen(
                    resultId = state.resultId,
                    summary = stringResource(if (result.passed) R.string.round3_passed else R.string.round3_failed),
                    stars = result.stars,
                    actionLabel = stringResource(if (result.passed) R.string.round_back_to_lesson else R.string.round_retry),
                    onAction = if (result.passed) onExit else onRetry,
                    details = {
                        Text(
                            stringResource(
                                if (result.passed) R.string.round2_passed else R.string.round2_failed,
                                result.scorePercent,
                            ),
                            color = Round4Muted, style = OwlaType.body,
                        )
                    },
                    secondaryAction = if (result.passed) null else {
                        { RoundNeutralButton(stringResource(R.string.round_back_to_lesson), onExit) }
                    },
                )
            }
        }

        if (confirmExit && state !is Round4UiState.Completed && state !is Round4UiState.Message) {
            RoundExitDialog(onKeepLearning = { confirmExit = false }, onLeave = onExit)
        }
    }
}

@Composable
private fun Round4Question(
    state: Round4UiState.Question,
    onToken: (String) -> Unit,
    onText: (String) -> Unit,
    onMode: () -> Unit,
    onCheck: () -> Unit,
    onRetryAnswer: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    imageContent: @Composable (String, Modifier) -> Unit,
) {
    val content = state.question.content
    val feedback = state.feedback
    val editable = !state.checking && feedback == null
    val canCheck = editable && state.answer() != null
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val check = {
        if (canCheck) {
            focusManager.clearFocus()
            keyboard?.hide()
            onCheck()
        }
    }
    val title = when (content) {
        is QuestionContent.FillBlank -> R.string.round4_fill_title
        is QuestionContent.Translate -> R.string.round4_translate_title
        else -> R.string.round4_order_title
    }
    val hint = when (content) {
        is QuestionContent.FillBlank -> if (state.keyboard) R.string.round4_fill_type else R.string.round4_fill_select
        is QuestionContent.Translate -> when {
            content.direction == TranslationDirection.EN_TO_VI && state.keyboard -> R.string.round4_translate_type_vi
            content.direction == TranslationDirection.EN_TO_VI -> R.string.round4_translate_select_vi
            state.keyboard -> R.string.round4_translate_type
            else -> R.string.round4_translate_select
        }
        else -> R.string.round4_order_hint
    }
    Box(
        Modifier.fillMaxSize().background(Round4Cream).safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxSize().padding(horizontal = 22.dp, vertical = 10.dp)) {
            Round4Header(state.number, state.total, onExit, compact = imeVisible)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(title), Modifier.semantics { heading() },
                        style = OwlaType.h1, color = Round4Ink,
                    )
                    Text(stringResource(hint), style = OwlaType.caption, color = Round4Muted)
                }
                val image = state.question.imageUrl?.takeIf { it.isNotBlank() }
                if (image != null) {
                    Row(
                        Modifier.fillMaxWidth().height(if (imeVisible) 62.dp else 92.dp)
                            .background(Round4Mint, RoundedCornerShape(18.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    ) {
                        if (!state.keyboard) Round4Mascot(48)
                        imageContent(image, Modifier.size(if (imeVisible) 50.dp else 70.dp))
                    }
                }
                when (content) {
                    is QuestionContent.FillBlank -> {
                        val word = state.tokens.firstOrNull { it.id == state.selectedIds.firstOrNull() }?.text
                        Round4Prompt(if (word == null) content.sentence else content.sentence.replaceFirst(
                            BlankMarker, Regex.escapeReplacement(word),
                        ))
                    }
                    is QuestionContent.Translate -> Round4Prompt(
                        if (content.direction == TranslationDirection.VI_TO_EN) "🇻🇳  ${content.sourceText}" else content.sourceText
                    )
                    else -> Unit
                }
                if (state.keyboard) {
                    Round4TextInput(state, editable, onText, check)
                } else {
                    if (content !is QuestionContent.FillBlank) {
                        Round4AnswerTray(state, editable, onToken)
                    }
                    if (feedback == null || content is QuestionContent.FillBlank) {
                        Round4WordBank(state, editable, onToken)
                    }
                }
                if (content is QuestionContent.Translate && editable) {
                    TextButton(
                        onClick = {
                            if (state.keyboard) {
                                focusManager.clearFocus()
                                keyboard?.hide()
                            }
                            onMode()
                        },
                    ) {
                        Text(
                            stringResource(if (state.keyboard) R.string.round4_chip_mode else R.string.round4_keyboard_mode),
                            color = Round4TealInk, style = OwlaType.caption,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (feedback != null) {
                    Round4Feedback(feedback.isCorrect, content is QuestionContent.SentenceOrder)
                    Round4Button(
                        label = stringResource(if (feedback.isCorrect) R.string.round3_continue else R.string.round_retry),
                        onClick = if (feedback.isCorrect) onNext else onRetryAnswer,
                        danger = !feedback.isCorrect,
                    )
                    if (!feedback.isCorrect) {
                        TextButton(onClick = onNext, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(stringResource(R.string.round3_continue), color = Round4TealInk)
                        }
                    }
                } else {
                    state.error?.let {
                        Text(
                            stringResource(
                                if (it == ApiErrorType.NETWORK) R.string.round2_network_error
                                else R.string.round2_request_failed
                            ),
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = Round4Danger, style = OwlaType.caption,
                        )
                    }
                    Round4Button(
                        stringResource(if (state.checking) R.string.round2_checking else R.string.round4_check),
                        check, enabled = canCheck, checking = state.checking,
                    )
                }
            }
        }
    }
}

private val BlankMarker = Regex("_{2,}")

@Composable
private fun Round4Header(number: Int, total: Int, onExit: () -> Unit, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!compact) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Round4Mascot(25)
                Text(stringResource(R.string.round4_brand), style = OwlaType.h2, color = Round4TealInk)
            }
        }
        val progress = number.toFloat().div(total.coerceAtLeast(1)).coerceIn(0f, 1f)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundCloseButton(onExit)
            Box(
                Modifier.weight(1f).height(10.dp).clip(CircleShape).background(Round4MintBorder)
                    .progressSemantics(progress),
            ) {
                Box(Modifier.fillMaxWidth(progress).height(10.dp).background(Round4Teal, CircleShape))
            }
            Text(
                stringResource(R.string.round4_steps, number, total),
                Modifier.background(Round4Mint, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 7.dp),
                color = Round4TealInk, style = OwlaType.caption,
            )
        }
    }
}

@Composable
private fun Round4Prompt(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().background(Round4PromptColor, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFECE2CD), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        style = OwlaType.h2, color = Round4Ink,
    )
}

@Composable
private fun Round4TextInput(
    state: Round4UiState.Question,
    editable: Boolean,
    onText: (String) -> Unit,
    onCheck: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    val inspection = LocalInspectionMode.current
    LaunchedEffect(state.keyboard) {
        if (!inspection && editable) requester.requestFocus()
    }
    OutlinedTextField(
        value = state.draft,
        onValueChange = onText,
        modifier = Modifier.fillMaxWidth().focusRequester(requester),
        readOnly = !editable,
        label = { Text(stringResource(R.string.round4_answer)) },
        textStyle = OwlaType.body,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Round4Teal, unfocusedBorderColor = Round4Teal,
            focusedTextColor = Round4Ink, unfocusedTextColor = Round4Ink,
            focusedLabelColor = Round4TealInk, unfocusedLabelColor = Round4Muted,
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
            cursorColor = Round4Teal,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onCheck() }),
    )
}

@Composable
private fun Round4AnswerTray(state: Round4UiState.Question, editable: Boolean, onToken: (String) -> Unit) {
    val correct = state.feedback?.isCorrect
    val label = if (state.question.content is QuestionContent.Translate) R.string.round4_translation else R.string.round4_answer
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Round4Label(label)
        FlowRow(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .background(
                    when (correct) {
                        true -> Round4FeedbackGreen
                        false -> Round4FeedbackRed
                        null -> Round4Mint
                    },
                    RoundedCornerShape(16.dp),
                ).border(
                    1.dp, if (correct == false) Color(0xFFE8C1B8) else Round4MintBorder, RoundedCornerShape(16.dp),
                ).padding(horizontal = 9.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val options = state.tokens.associateBy { it.id }
            state.selectedIds.forEach { id ->
                val option = options.getValue(id)
                key(id) { Round4Chip(option.text, true, editable, correct) { onToken(id) } }
            }
        }
    }
}

@Composable
private fun Round4WordBank(state: Round4UiState.Question, editable: Boolean, onToken: (String) -> Unit) {
    val fill = state.question.content is QuestionContent.FillBlank
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Round4Label(R.string.round4_word_bank)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.tokens.forEach { token ->
                val selected = token.id in state.selectedIds
                val available = editable && (fill || !selected)
                key(token.id) {
                    Round4Chip(token.text, fill && selected, available, state.feedback?.isCorrect.takeIf { selected }) {
                        onToken(token.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun Round4Chip(label: String, selected: Boolean, enabled: Boolean, correct: Boolean?, onClick: () -> Unit) {
    val face = when {
        correct == false -> Color(0xFFF4DAD6)
        correct == true -> Color(0xFFD6EFE1)
        selected -> Color(0xFFC7ECE1)
        enabled -> Color.White
        else -> Color(0xFFEEEEE8)
    }
    val edge = when {
        correct == false -> Color(0xFFDBA5A1)
        selected -> Color(0xFF93C9B9)
        else -> Round4ChipBorder
    }
    val ink = when {
        correct == false -> Round4Danger
        correct == true -> Round4Success
        selected -> Round4TealInk
        enabled -> Round4Ink
        else -> Round4Muted
    }
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier.heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            Modifier.background(edge, shape).padding(bottom = 3.dp).background(face, shape)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            style = OwlaType.caption, color = ink, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Round4Label(label: Int) {
    Text(stringResource(label), style = OwlaType.caption, color = Round4Muted)
}

@Composable
private fun Round4Button(label: String, onClick: () -> Unit, enabled: Boolean = true, checking: Boolean = false, danger: Boolean = false) {
    val color = when {
        danger -> Round4Danger
        enabled -> Round4Teal
        checking -> Color(0xFF8EC7BD)
        else -> Color(0xFFB4C5BC)
    }
    Owla3DButton(
        label, onClick, enabled = enabled,
        baseColor = color, shadowColor = if (enabled) {
            if (danger) Color(0xFF713131) else Round4TealBase
        } else color,
        textColor = if (enabled) Color.White else Round4TealInk,
        height = 48.dp, pressDepth = 4.dp, shape = RoundedCornerShape(16.dp),
        leadingIcon = if (checking) {
            { CircularProgressIndicator(Modifier.size(16.dp), color = Round4TealInk, strokeWidth = 2.dp) }
        } else null,
    )
}

@Composable
private fun Round4Feedback(correct: Boolean, ordering: Boolean) {
    val ink = if (correct) Round4Success else Round4Danger
    Row(
        Modifier.fillMaxWidth().background(
            if (correct) Round4FeedbackGreen else Round4FeedbackRed, RoundedCornerShape(18.dp),
        ).padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                stringResource(if (correct) R.string.round4_correct else R.string.round4_wrong),
                style = OwlaType.h2, color = ink,
            )
            Text(
                stringResource(
                    when {
                        correct && ordering -> R.string.round4_correct_order
                        correct -> R.string.round4_correct_answer
                        ordering -> R.string.round4_wrong_order
                        else -> R.string.round4_wrong_answer
                    }
                ),
                style = OwlaType.caption, color = ink,
            )
        }
        Round4Mascot(50)
    }
}

@Composable
private fun Round4Mascot(size: Int) {
    Image(painterResource(R.drawable.owla_mascot), contentDescription = null, modifier = Modifier.size(size.dp))
}
