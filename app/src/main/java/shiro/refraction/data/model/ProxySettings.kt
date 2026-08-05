package shiro.refraction.data.model

enum class ProxyType { HTTP, SOCKS5 }

data class ProxySettings(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.SOCKS5,
    val host: String = "",
    val port: Int = 1080,
    val username: String = "",
    val password: String = ""
) {
    val isValid: Boolean
        get() = host.isNotBlank() && port in 1..65535
}
