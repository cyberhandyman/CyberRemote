package dev.companionremote.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.companionremote.app.i18n.LocalAppStrings
import kotlinx.coroutines.launch

/**
 * First-run tutorial shown once, right after the first successful pairing.
 * Two concise pages with lightweight, purely-programmatic animated
 * illustrations (no GIF assets, no network).
 */
@Composable
fun IntroOverlay(onDone: () -> Unit) {
    val s = LocalAppStrings.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDone) { Text(s.introSkip) }
                }
                HorizontalPager(state = pagerState) { page ->
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (page == 0) NavIllustration() else TouchIllustration()
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            if (page == 0) s.introNavTitle else s.introMoreTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (page == 0) s.introNavBody else s.introMoreBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(2) { i ->
                        val active = pagerState.currentPage == i
                        Box(
                            Modifier.padding(4.dp).size(if (active) 9.dp else 7.dp).clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (pagerState.currentPage < 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            onDone()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (pagerState.currentPage < 1) s.introNext else s.introDone)
                }
            }
        }
    }
}

/** Animated D-pad ring: the highlight steps around the four directions. */
@Composable
private fun NavIllustration() {
    val transition = rememberInfiniteTransition(label = "nav")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Restart),
        label = "phase",
    )
    val active = phase.toInt() % 4
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "ok",
    )
    val accent = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Box(
        Modifier.size(190.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Chevron(Icons.Rounded.KeyboardArrowUp, Alignment.TopCenter, if (active == 0) accent else dim)
        Chevron(Icons.AutoMirrored.Rounded.KeyboardArrowRight, Alignment.CenterEnd, if (active == 1) accent else dim)
        Chevron(Icons.Rounded.KeyboardArrowDown, Alignment.BottomCenter, if (active == 2) accent else dim)
        Chevron(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, Alignment.CenterStart, if (active == 3) accent else dim)
        Box(
            Modifier.size(74.dp).scale(pulse).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "OK",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun Chevron(icon: ImageVector, alignment: Alignment, tint: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
        Icon(icon, contentDescription = null, Modifier.padding(10.dp).size(34.dp), tint = tint)
    }
}

/** Animated touchpad with a finger dot swiping, plus type/voice glyphs. */
@Composable
private fun TouchIllustration() {
    val transition = rememberInfiniteTransition(label = "touch")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "x",
    )
    val accent = MaterialTheme.colorScheme.primary
    val dot = 34.dp
    val padEdge = 12.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier.size(width = 190.dp, height = 120.dp).clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.CenterStart,
        ) {
            val travel = maxWidth - dot - padEdge * 2
            Box(
                Modifier
                    .offset(x = padEdge + travel * x)
                    .size(dot)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.9f)),
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            GlyphChip(Icons.Rounded.TouchApp)
            GlyphChip(Icons.Rounded.Keyboard)
            GlyphChip(Icons.Rounded.Mic)
        }
    }
}

@Composable
private fun GlyphChip(icon: ImageVector) {
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
