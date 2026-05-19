package to.kuudere.anisuge

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import to.kuudere.anisuge.notifications.NotificationChannels
import to.kuudere.anisuge.notifications.NotificationIntentParser
import to.kuudere.anisuge.notifications.NotificationTopicManager
import to.kuudere.anisuge.navigation.NotificationLaunch
import to.kuudere.anisuge.platform.androidAppContext
import to.kuudere.anisuge.platform.isAndroidTvPlatform

class MainActivity : ComponentActivity() {
    private var notificationLaunch by mutableStateOf<NotificationLaunch?>(null)
    private var skipSplash by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initNotificationTopics()
        } else {
            openAppNotificationSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidAppContext = applicationContext
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        NotificationChannels.createAll(this)
        initNotificationTopics()
        android.util.Log.d("MainActivity", "onCreate intent=${intent?.data}")
        notificationLaunch = NotificationIntentParser.parse(intent)
        skipSplash = NotificationIntentParser.shouldSkipSplash(intent, savedInstanceState)
        handleTrackingDeepLink(intent)

        setContent {
            App(
                skipSplash = skipSplash,
                notificationLaunch = notificationLaunch,
                onNotificationLaunchConsumed = { notificationLaunch = null },
                onAppExit = { finishAffinity() }
            )
        }

        window.decorView.post {
            showNotificationPromptIfNeeded()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val parsed = NotificationIntentParser.parse(intent)
        android.util.Log.d(
            "MainActivity",
            "onNewIntent data=${intent.data} anime=${parsed?.animeId} ep=${parsed?.episodeNumber}",
        )
        skipSplash = true
        notificationLaunch = parsed
        handleTrackingDeepLink(intent)
    }

    private fun handleTrackingDeepLink(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "anisurge") return
        lifecycleScope.launch {
            when (data.host) {
                "mal" -> {
                    val accessToken = data.getQueryParameter("access_token") ?: return@launch
                    val refreshToken = data.getQueryParameter("refresh_token") ?: ""
                    val expiresIn = data.getQueryParameter("expires_in")?.toLongOrNull() ?: 0L
                    AppComponent.settingsStore.saveMalTokens(accessToken, refreshToken, expiresIn)
                    try {
                        val username: String? = AppComponent.trackingService.fetchMalUsername()
                        if (username != null) AppComponent.settingsStore.saveMalUsername(username)
                    } catch (_: Exception) {}
                    AppComponent.integrationsSyncService.pushFromLocal()
                }
                "anilist" -> {
                    val accessToken = data.getQueryParameter("access_token") ?: return@launch
                    val expiresIn = data.getQueryParameter("expires_in")?.toLongOrNull() ?: 0L
                    AppComponent.settingsStore.saveAnilistTokens(accessToken, expiresIn)
                    try {
                        val username: String? = AppComponent.trackingService.fetchAnilistUsername()
                        if (username != null) AppComponent.settingsStore.saveAnilistUsername(username)
                    } catch (_: Exception) {}
                    AppComponent.integrationsSyncService.pushFromLocal()
                }
            }
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

    private fun showNotificationPromptIfNeeded() {
        if (isAndroidTvPlatform || hasNotificationPermission()) return

        AlertDialog.Builder(this)
            .setTitle("Turn on notifications")
            .setMessage("Allow Anisurge to notify you about new episodes, announcements, and download progress.")
            .setPositiveButton("Turn on") { _, _ ->
                requestOrOpenNotificationPermission()
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun hasNotificationPermission(): Boolean {
        val appNotificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        return appNotificationsEnabled && runtimePermissionGranted
    }

    private fun requestOrOpenNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAppNotificationSettings()
        }
    }

    private fun openAppNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        }

        runCatching { startActivity(intent) }
    }
}
