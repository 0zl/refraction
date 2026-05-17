package shiro.refraction.ui.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch
import shiro.refraction.R
import shiro.refraction.data.model.Profile
import shiro.refraction.ui.dashboard.DashboardBottomSheet
import shiro.refraction.ui.dialog.AddProfileDialog
import shiro.refraction.ui.dialog.CookieBottomSheet
import shiro.refraction.util.Constants

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var loadingOverlay: android.view.View
    private lateinit var tvToolbarTitle: TextView
    private lateinit var profileIndicator: android.view.View
    private lateinit var profileSwitcher: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Refraction)
        setContentView(R.layout.activity_main)

        setupViews()
        setupWebView()
        observeState()
    }

    private fun setupViews() {
        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle)
        profileIndicator = findViewById(R.id.profileIndicator)
        profileSwitcher = findViewById(R.id.profileSwitcher)

        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }

        profileSwitcher.setOnClickListener { openDashboard() }

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
        }
        webView.webChromeClient = RefractionWebChromeClient { progress ->
            viewModel.onProgressChanged(progress)
            if (progress == 100) {
                progressBar.progress = 0
                progressBar.isVisible = false
                swipeRefresh.isRefreshing = false
            } else {
                progressBar.isVisible = true
                progressBar.setProgressCompat(progress, true)
            }
        }
        webView.webViewClient = RefractionWebViewClient { url ->
            viewModel.onPageFinished(url)
            swipeRefresh.isRefreshing = false
            if (webView.alpha < 1f) {
                ObjectAnimator.ofFloat(webView, "alpha", 0f, 1f).apply {
                    duration = 200
                    start()
                }
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.activeProfile.collect { profile ->
                        updateToolbar(profile)
                        if (profile != null && webView.url == null) {
                            webView.loadUrl(Constants.TARGET_URL)
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        if (loading) {
                            loadingOverlay.isVisible = true
                            loadingOverlay.alpha = 0f
                            ObjectAnimator.ofFloat(loadingOverlay, "alpha", 0f, 1f).apply {
                                duration = 150
                                start()
                            }
                        } else {
                            ObjectAnimator.ofFloat(loadingOverlay, "alpha", 1f, 0f).apply {
                                duration = 150
                                addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        loadingOverlay.isVisible = false
                                    }
                                })
                                start()
                            }
                        }
                    }
                }
                launch {
                    viewModel.showEmptyState.collect { empty ->
                        if (empty && supportFragmentManager.findFragmentByTag("add_profile") == null) {
                            showAddProfileDialog()
                        }
                    }
                }
                launch {
                    viewModel.switchEvent.collect {
                        clearWebViewState()
                        webView.alpha = 0f
                        webView.loadUrl(Constants.TARGET_URL)
                    }
                }
            }
        }
    }

    private fun updateToolbar(profile: Profile?) {
        tvToolbarTitle.text = profile?.name ?: getString(R.string.app_name)
        profileIndicator.isVisible = profile != null
        profile?.let {
            profileIndicator.background?.setTint(Color.parseColor(it.colorHex))
        }
    }

    private fun openDashboard() {
        val sheet = DashboardBottomSheet.newInstance(
            activeProfileId = viewModel.activeProfile.value?.id
        )
        sheet.onProfileSelected = { profile ->
            val current = viewModel.activeProfile.value
            if (current?.id != profile.id) {
                viewModel.switchToProfile(current?.id, profile)
            }
        }
        sheet.onAddProfile = {
            showAddProfileDialog()
        }
        sheet.onEditProfile = { profile ->
            showEditProfileDialog(profile)
        }
        sheet.onDeleteProfile = { profile ->
            confirmDelete(profile)
        }
        sheet.show(supportFragmentManager, "dashboard")
    }

    private fun clearWebViewState() {
        webView.clearCache(true)
        WebStorage.getInstance().deleteAllData()
        webView.clearHistory()
        webView.clearFormData()
    }

    private fun showAddProfileDialog() {
        AddProfileDialog().show(supportFragmentManager, "add_profile")
    }

    private fun showEditProfileDialog(profile: Profile) {
        AddProfileDialog.newInstance(profile).show(supportFragmentManager, "edit_profile")
    }

    private fun confirmDelete(profile: Profile) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_profile_title)
            .setMessage(R.string.delete_profile_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteProfile(profile)
                if (viewModel.profiles.value.size <= 1) {
                    webView.loadUrl("about:blank")
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_profile -> {
                showAddProfileDialog()
                true
            }
            R.id.action_refresh -> {
                webView.reload()
                true
            }
            R.id.action_extract_cookies -> {
                extractCookies()
                true
            }
            R.id.action_clear_cookies -> {
                clearCookies()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun extractCookies() {
        lifecycleScope.launch {
            val cookies = viewModel.getCurrentCookieString()
            CookieBottomSheet.newInstance(cookies).show(supportFragmentManager, "cookies")
        }
    }

    private fun clearCookies() {
        lifecycleScope.launch {
            viewModel.clearCurrentProfileCookies()
            clearWebViewState()
            webView.loadUrl(Constants.TARGET_URL)
            Toast.makeText(this@MainActivity, R.string.cookies_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.captureCookies()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
