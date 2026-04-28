package to.kuudere.anisuge.i18n

enum class AppLocale(
    val code: String,
    val displayName: String,
    val nativeName: String,
) {
    English("en", "English", "English"),
    Hindi("hi", "Hindi", "हिन्दी"),
    Spanish("es", "Spanish", "Español"),
    Portuguese("pt", "Portuguese", "Português"),
    Arabic("ar", "Arabic", "العربية"),
    Indonesian("id", "Indonesian", "Bahasa Indonesia"),
    French("fr", "French", "Français"),
    German("de", "German", "Deutsch");

    companion object {
        val default = English

        fun fromCode(code: String?): AppLocale = entries.firstOrNull { it.code == code } ?: default
    }
}
