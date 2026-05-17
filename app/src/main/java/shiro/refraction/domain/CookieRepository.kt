package shiro.refraction.domain

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import shiro.refraction.data.local.CookieStorage
import shiro.refraction.util.Constants
import kotlin.coroutines.resume

class CookieRepository(context: Context) {

    private val cookieStorage = CookieStorage(context)

    suspend fun captureAndSave(profileId: String) {
        val cookieString = withContext(Dispatchers.Main) {
            CookieManager.getInstance().getCookie(Constants.TARGET_DOMAIN)
        } ?: return
        withContext(Dispatchers.IO) {
            cookieStorage.saveCookies(profileId, mapOf(Constants.TARGET_DOMAIN to cookieString))
        }
    }

    suspend fun restore(profileId: String) {
        val cookies = withContext(Dispatchers.IO) {
            cookieStorage.loadCookies(profileId)
        }
        withContext(Dispatchers.Main) {
            val cookieManager = CookieManager.getInstance()
            cookies.forEach { (domain, cookieString) ->
                cookieString.split(";").forEach { cookie ->
                    val trimmed = cookie.trim()
                    if (trimmed.isNotEmpty()) {
                        cookieManager.setCookie(domain, trimmed)
                    }
                }
            }
            cookieManager.flush()
        }
    }

    suspend fun delete(profileId: String) {
        cookieStorage.deleteCookies(profileId)
    }

    suspend fun clearAllCookies() = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            CookieManager.getInstance().removeAllCookies {
                continuation.resume(Unit)
            }
        }
    }

    suspend fun getCurrentCookies(): String = withContext(Dispatchers.Main) {
        CookieManager.getInstance().getCookie(Constants.TARGET_DOMAIN) ?: ""
    }
}
