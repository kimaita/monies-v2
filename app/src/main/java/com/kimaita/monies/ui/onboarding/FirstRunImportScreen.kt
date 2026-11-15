package com.kimaita.monies.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.time.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val CAPTION_INTERVAL_MS = 1300L

private val yearLines: List<(Int) -> String> = listOf(
    { y -> "Retrieving $y" },
    { y -> "Unpacking your $y" },
    { y -> "Sorting through $y" },
    { y -> "Catching up on $y" },
    { y -> "Bado tuko $y" },
    { y -> "Tallying up $y" },
)

private val creativeLines: List<String> = listOf(
    "Counting every bob",
    "Following the money",
    "Matching merchants",
    "Reading your M-PESA texts",
    "Pole pole…",
    "Sorting sent from received",
    "Tukiendelea…",
    "Crunching the numbers",
)

/** Before any batch reports a year. */
private val readingLines: List<String> = listOf(
    "Reading your message history",
    "Tunasoma messages zako",
    "Going through your texts",
    "Opening your inbox",
)

private val finishingLines: List<String> = listOf(
    "Almost there",
    "Bado kidogo",
    "Tunamaliza",
    "Nearly done",
)

/**
 * Deterministic from (tick, year, fraction). Even ticks show a year line, odd ticks a
 * creative line, so the year is woven through rather than parked on screen.
 */
private fun importCaption(tick: Int, year: Int?, fraction: Float?): String = when {
    fraction != null && fraction >= 0.92f -> finishingLines[Math.floorMod(tick, finishingLines.size)]
    year == null -> readingLines[Math.floorMod(tick, readingLines.size)]
    tick % 2 == 0 -> yearLines[Math.floorMod(tick / 2, yearLines.size)](year)
    else -> creativeLines[Math.floorMod(tick / 2, creativeLines.size)]
}

@Composable
fun FirstRunImportScreen(
    processed: Int,
    total: Int?,
    year: Int?,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    if (failed) {
        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Couldn't import your messages",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
        return
    }
    val fractionTarget =
        total?.takeIf { it > 0 }?.let { (processed.toFloat() / it).coerceIn(0f, 1f) }
    val fraction = animateFloatAsState(
        targetValue = fractionTarget ?: 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "fraction",
    )
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CAPTION_INTERVAL_MS.milliseconds)
            tick++
        }
    }
    val caption = importCaption(tick, year, fractionTarget)

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Setting up Monies",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(48.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                if (fractionTarget != null) {
                    CircularProgressIndicator(
                        progress = { fraction.value },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 16.dp,
                        strokeCap = StrokeCap.Round,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 16.dp,
                        strokeCap = StrokeCap.Round,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%,d".format(processed),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (total != null) "of %,d".format(total) else "messages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
            AnimatedContent(
                targetState = caption,
                transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
                label = "caption",
            ) { text ->
                Text(
                    text, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (fractionTarget != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${(fractionTarget * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}