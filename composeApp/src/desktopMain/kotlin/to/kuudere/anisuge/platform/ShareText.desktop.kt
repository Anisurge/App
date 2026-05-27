package to.kuudere.anisuge.platform

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual fun shareText(text: String, title: String?): Boolean {
    return try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    } catch (_: Exception) {
        false
    }
}
