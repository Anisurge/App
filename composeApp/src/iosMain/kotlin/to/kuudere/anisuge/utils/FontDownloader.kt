package to.kuudere.anisuge.utils

import to.kuudere.anisuge.data.models.FontData

actual suspend fun downloadFontsAndGetDir(fonts: List<FontData>, onProgress: ((String) -> Unit)?): String? {
    // TODO: Download fonts to iOS documents directory
    return null
}
