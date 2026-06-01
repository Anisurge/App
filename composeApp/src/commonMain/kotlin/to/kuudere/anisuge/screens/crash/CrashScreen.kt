package to.kuudere.anisuge.screens.crash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.platform.copyToClipboard
import to.kuudere.anisuge.platform.shareText
import to.kuudere.anisuge.theme.AppColors

@Serializable
data class CrashReportData(
    val appName: String = "",
    @SerialName("stackTrace") val stackTrace: String = "",
    @SerialName("deviceModel") val deviceModel: String = "",
    @SerialName("androidVersion") val osVersion: String = "",
    @SerialName("appVersion") val appVersion: String = "",
    val timestamp: String = "",
) {
    val formattedDetails: String
        get() = buildString {
            appendLine("=== Crash Report ===")
            appendLine("App: $appName v$appVersion")
            appendLine("Device: $deviceModel")
            appendLine("OS: $osVersion")
            appendLine("Time: $timestamp")
            appendLine()
            appendLine("--- Stack Trace ---")
            append(stackTrace)
        }
}

fun parseCrashReportJson(json: String): CrashReportData? {
    return runCatching {
        Json.decodeFromString<CrashReportData>(json)
    }.getOrNull()
}

@Composable
fun CrashScreen(
    crashData: CrashReportData,
    onRestart: () -> Unit,
) {
    var copied by remember { mutableStateOf(false) }
    var sentState by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "⚠",
                fontSize = 48.sp,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Something went wrong",
                color = AppColors.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "An unexpected error occurred. The details below can help the developer fix the issue.",
                color = AppColors.textMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(16.dp))

            HorizontalDivider(color = AppColors.border)

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.surfaceVariant)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = crashData.formattedDetails,
                    color = AppColors.textMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            HorizontalDivider(color = AppColors.border)

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        copyToClipboard(crashData.formattedDetails)
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.surfaceVariant,
                        contentColor = AppColors.text,
                    ),
                ) {
                    Text(
                        text = if (copied) "Copied!" else "Copy Error",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Button(
                    onClick = {
                        sentState = null
                        scope.launch {
                            val result = AppComponent.bffCrashReportService.sendReport(
                                stackTrace = crashData.stackTrace,
                                deviceModel = crashData.deviceModel,
                                osVersion = crashData.osVersion,
                                appVersion = crashData.appVersion,
                            )
                            sentState = result.isSuccess
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.surfaceVariant,
                        contentColor = AppColors.text,
                    ),
                ) {
                    Text(
                        text = when (sentState) {
                            null -> "Send to Developer"
                            true -> "Sent!"
                            false -> "Failed — tap to retry"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Button(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.accent,
                        contentColor = AppColors.onAccent,
                    ),
                ) {
                    Text(
                        text = "Restart App",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
