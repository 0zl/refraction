package shiro.refraction.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CookieStorage(context: Context) {

    private val appContext = context.applicationContext

    private fun getPreferences(profileId: String): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            appContext,
            "cookies_$profileId",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun saveCookies(profileId: String, cookies: Map<String, String>) = withContext(Dispatchers.IO) {
        val prefs = getPreferences(profileId)
        val json = JSONObject()
        cookies.forEach { (key, value) ->
            json.put(key, value)
        }
        prefs.edit().putString(KEY_COOKIES, json.toString()).apply()
    }

    suspend fun loadCookies(profileId: String): Map<String, String> = withContext(Dispatchers.IO) {
        val prefs = getPreferences(profileId)
        val jsonString = prefs.getString(KEY_COOKIES, null) ?: return@withContext emptyMap()
        val json = JSONObject(jsonString)
        val map = mutableMapOf<String, String>()
        json.keys().forEach { key ->
            map[key] = json.getString(key)
        }
        map
    }

    suspend fun deleteCookies(profileId: String) = withContext(Dispatchers.IO) {
        val prefs = getPreferences(profileId)
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_COOKIES = "cookie_bundle"
    }
}
