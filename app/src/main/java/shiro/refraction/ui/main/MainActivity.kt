package shiro.refraction.ui.main

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Refraction)
        setContentView(R.layout.activity_main)

        setupEdgeToEdge()
        setupViews()
        setupWebView()
        observeState()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupViews() {
        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setOnClickListener {
            openDashboard()
        }

        setSupportActionBar(findViewById(R.id.toolbar))
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
            progressBar.setProgressCompat(progress, true)
            if (progress == 100) {
                swipeRefresh.isRefreshing = false
            }
        }
        webView.webViewClient = RefractionWebViewClient { url ->
            viewModel.onPageFinished(url)
            swipeRefresh.isRefreshing = false
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
                        loadingOverlay.isVisible = loading
                    }
                }
                launch {
                    viewModel.showEmptyState.collect { empty ->
                        if (empty && supportFragmentManager.findFragmentByTag("add_profile") == null) {
                            showAddProfileDialog()
                        }
                    }
                }
            }
        }
    }

    private fun updateToolbar(profile: Profile?) {
        supportActionBar?.title = profile?.name ?: getString(R.string.app_name)
        val indicator = findViewById<android.view.View>(R.id.profileIndicator)
        indicator?.isVisible = profile != null
        profile?.let {
            indicator?.background?.setTint(Color.parseColor(it.colorHex))
        }
    }

    private fun openDashboard() {
        val sheet = DashboardBottomSheet.newInstance(
            activeProfileId = viewModel.activeProfile.value?.id
        )
        sheet.onProfileSelected = { profile ->
            val current = viewModel.activeProfile.value
            if (current?.id != profile.id) {
                clearWebViewState()
                viewModel.switchToProfile(current?.id, profile)
                webView.loadUrl(Constants.TARGET_URL)
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
            webView.clearCache(true)
            WebStorage.getInstance().deleteAllData()
            webView.clearHistory()
            webView.clearFormData()
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

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
