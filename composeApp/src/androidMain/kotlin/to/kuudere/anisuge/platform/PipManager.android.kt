package to.kuudere.anisuge.platform

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPipManager(): PipManager {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val isAvailable = remember(activity) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
    }

    return remember(activity, isAvailable) {
        PipManager(
            isAvailable = isAvailable,
            isActive = false,
            requestPip = { width, height ->
                val act = activity
                if (act != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val safeWidth = width.coerceAtLeast(1)
                    val safeHeight = height.coerceAtLeast(1)
                    val ratio = safeWidth.toFloat() / safeHeight.toFloat()
                    val aspect = when {
                        ratio < 1.0f / 2.39f -> Rational(100, 239)
                        ratio > 2.39f -> Rational(239, 100)
                        else -> Rational(safeWidth, safeHeight)
                    }
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(aspect)
                        .build()
                    act.enterPictureInPictureMode(params)
                }
            },
        )
    }
}
