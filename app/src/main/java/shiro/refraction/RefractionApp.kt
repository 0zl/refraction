package shiro.refraction

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import shiro.refraction.data.local.RefractionDatabase

class RefractionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        RefractionDatabase.getInstance(this)
    }
}
