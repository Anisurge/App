package to.kuudere.anisuge.screens.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun formatBytesOneDecimalPreferredUnit(bytes: Long): String =
    when {
        bytes <= 0L -> "—"
        bytes >= 1024L * 1024 * 1024 -> String.format("%.1f GB", bytes.toDouble() / (1024 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
        else -> String.format("%.0f KB", bytes.toDouble() / 1024)
    }

/** Progressive MP4 options: concurrent HEAD for size, Download triggers [onDownloadRequested]. */
@Composable
fun DirectMp4QualityPicker(
    httpClient: HttpClient,
    options: List<Triple<String, String, Map<String, String>>>,
    modifier: Modifier = Modifier,
    onDownloadRequested: (url: String, headers: Map<String, String>, quality: String) -> Unit,
) {
    val sizeByQuality = remember { mutableStateMapOf<String, String>() }
    val loadingByQuality = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(options.map { "${it.first}\u001f${it.second}" }.joinToString("|")) {
        sizeByQuality.clear()
        loadingByQuality.clear()
        if (options.isEmpty()) return@LaunchedEffect
        options.forEach { (q, _, _) ->
            loadingByQuality[q] = true
        }
        // Sequential HEAD avoids piling buffers on OkHttp / reduces OOM risk on tight heaps.
        withContext(Dispatchers.IO) {
            options.forEach { (qualityLabel, url, hdrs) ->
                val display: String =
                    try {
                        val response = httpClient.head(url) {
                            hdrs.forEach { (k, v) -> header(k, v) }
                        }
                        val len = response.headers["Content-Length"]?.toLongOrNull()
                        if (response.status.isSuccess() && len != null && len > 0L) {
                            formatBytesOneDecimalPreferredUnit(len)
                        } else {
                            "—"
                        }
                    } catch (_: Exception) {
                        "—"
                    }
                withContext(Dispatchers.Main) {
                    sizeByQuality[qualityLabel] = display
                    loadingByQuality.remove(qualityLabel)
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Quality (MP4)", color = Color.Gray, fontSize = 14.sp)
        // Non-lazy Column: nested LazyColumn inside ModalBottomSheet often measures to 0 height.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (quality, url, hdrs) ->
                val sizeText = sizeByQuality[quality]
                val stillLoading = loadingByQuality.containsKey(quality)
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            quality,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (stillLoading && sizeText == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("…", color = Color.Gray, fontSize = 13.sp)
                                Spacer(Modifier.size(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.Gray,
                                    strokeWidth = 2.dp,
                                )
                            }
                        } else {
                            Text(
                                sizeText ?: "…",
                                color = Color.Gray,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    Button(
                        onClick = { onDownloadRequested(url, hdrs, quality) },
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .padding(start = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Download", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
