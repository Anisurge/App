package to.kuudere.anisuge.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

actual fun copyToClipboard(text: String) {
    val context = androidAppContext
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Anisurge", text))
}
