package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIDevice

@Composable
actual fun rememberDeviceControls(): DeviceControls = remember {
    object : DeviceControls {
        override val currentVolume: Float = 1f
        override val currentBrightness: Float = UIDevice.currentDevice.batteryLevel
        override fun setVolume(volume: Float) {
            // iOS volume controlled by hardware buttons
        }
        override fun setBrightness(brightness: Float) {
            // TODO: Use UIScreen.mainScreen.brightness
        }
    }
}
