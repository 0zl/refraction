package shiro.refraction.util

object Constants {
    const val TARGET_DOMAIN = "https://www.facebook.com"
    const val TARGET_URL = "https://www.facebook.com"

    val MATERIAL_COLORS = listOf(
        "#2196F3",
        "#F44336",
        "#4CAF50",
        "#9C27B0",
        "#FF9800",
        "#009688",
        "#E91E63",
        "#3F51B5",
        "#00BCD4",
        "#FFC107",
        "#CDDC39",
        "#FF5722"
    )
}

object UserAgent {
    private const val MOBILE_CHROME =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.6478.71 Mobile Safari/537.36"

    private const val DESKTOP_CHROME =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.6478.71 Safari/537.36"

    fun forDesktop(desktop: Boolean): String =
        if (desktop) DESKTOP_CHROME else MOBILE_CHROME
}
