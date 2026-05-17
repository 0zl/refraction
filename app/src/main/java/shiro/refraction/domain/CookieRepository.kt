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

    suspend fun captureAndSave(profileId: String) = withContext(Dispatchers.IO) {
        val cookieManager = CookieManager.getInstance()
        val domain = Constants.TARGET_DOMAIN
        val cookieString = cookieManager.getCookie(domain) ?: return@withContext
        val cookies = mapOf(domain to cookieString)
        cookieStorage.saveCookies(profileId, cookies)
    }

    suspend fun restore(profileId: String) = withContext(Dispatchers.IO) {
        val cookies = cookieStorage.loadCookies(profileId)
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

    suspend fun delete(profileId: String) {
        cookieStorage.deleteCookies(profileId)
    }

    suspend fun clearAllCookies() = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            CookieManager.getInstance().removeAllCookies {
                continuation.resume(Unit)
            }
        }
    }

    suspend fun getCurrentCookies(): String = withContext(Dispatchers.IO) {
        CookieManager.getInstance().getCookie(Constants.TARGET_DOMAIN) ?: ""
    }
}
