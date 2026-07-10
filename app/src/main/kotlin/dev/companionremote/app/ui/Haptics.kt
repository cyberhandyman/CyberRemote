package dev.companionremote.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.companionremote.app.data.HapticStrength

private fun vibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

private fun amplitudeFor(strength: HapticStrength): Int = when (strength) {
    HapticStrength.Light -> 70
    HapticStrength.Medium -> 150
    HapticStrength.Strong -> 255
}

private fun durationFor(strength: HapticStrength): Long = when (strength) {
    HapticStrength.Light -> 10
    HapticStrength.Medium -> 15
    HapticStrength.Strong -> 22
}

private fun buzz(vibrator: Vibrator?, strength: HapticStrength) {
    if (vibrator?.hasVibrator() == true) {
        runCatching {
            vibrator.vibrate(
                VibrationEffect.createOneShot(durationFor(strength), amplitudeFor(strength)),
            )
        }
    }
}

/**
 * Returns a `() -> Unit` that fires a short button-press vibration whose
 * amplitude follows [strength], or does nothing when [enabled] is false.
 * A single reusable [Vibrator] is captured for the composition.
 */
@Composable
fun rememberHaptic(enabled: Boolean, strength: HapticStrength): () -> Unit {
    val context = LocalContext.current
    val vibrator = remember(context) { vibrator(context) }
    return { if (enabled) buzz(vibrator, strength) }
}

/**
 * Returns a `(HapticStrength) -> Unit` that vibrates once at the given
 * strength — used to preview a level the moment the user selects it.
 */
@Composable
fun rememberHapticPreview(): (HapticStrength) -> Unit {
    val context = LocalContext.current
    val vibrator = remember(context) { vibrator(context) }
    return { strength -> buzz(vibrator, strength) }
}
