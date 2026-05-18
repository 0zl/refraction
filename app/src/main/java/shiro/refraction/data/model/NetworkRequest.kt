package shiro.refraction.data.model

import org.json.JSONObject

data class NetworkRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val responseCode: Int = 0,
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: String? = null,
    val source: RequestSource = RequestSource.RESOURCE,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toCurl(): String {
        val parts = mutableListOf("curl -X $method '$url'")
        headers.forEach { (k, v) -> parts.add("  -H '$k: $v'") }
        requestBody?.takeIf { it.isNotEmpty() }?.let { parts.add("  -d '$it'") }
        return parts.joinToString(" \\\n")
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("method", method)
        put("url", url)
        put("headers", JSONObject(headers))
        requestBody?.let { put("requestBody", it) }
        put("responseCode", responseCode)
        put("responseHeaders", JSONObject(responseHeaders))
        responseBody?.let { put("responseBody", it) }
        put("source", source.name)
        put("timestamp", timestamp)
    }
}

enum class RequestSource { API, RESOURCE }
