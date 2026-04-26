package to.kuudere.anisuge

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import to.kuudere.anisuge.notifications.NotificationChannels
import to.kuudere.anisuge.notifications.NotificationTopicManager
import to.kuudere.anisuge.platform.androidAppContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidAppContext = applicationContext
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        NotificationChannels.createAll(this)
        initNotificationTopics()

        setContent {
            App(onAppExit = { finishAffinity() })
        }
    }

    private fun initNotificationTopics() {
        lifecycleScope.launch {
            val enabled = AppComponent.settingsStore.notificationsEnabledFlow.first()
            if (enabled) {
                NotificationTopicManager.subscribeToDefaultTopics()
            }
        }
    }
}
