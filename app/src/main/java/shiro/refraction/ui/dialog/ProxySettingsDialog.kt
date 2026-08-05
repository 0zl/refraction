package shiro.refraction.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import shiro.refraction.R
import shiro.refraction.data.model.ProxySettings
import shiro.refraction.data.model.ProxyType
import shiro.refraction.domain.ProxyManager
import shiro.refraction.ui.main.MainViewModel

class ProxySettingsDialog : DialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var proxyManager: ProxyManager

    private lateinit var switchEnabled: MaterialSwitch
    private lateinit var toggleMode: MaterialButtonToggleGroup
    private lateinit var etHost: TextInputEditText
    private lateinit var etPort: TextInputEditText
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnTest: Button
    private lateinit var tvTestResult: TextView
    private var testing = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        proxyManager = ProxyManager(requireContext())
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_proxy, null)

        switchEnabled = view.findViewById(R.id.switchEnabled)
        toggleMode = view.findViewById(R.id.toggleMode)
        etHost = view.findViewById(R.id.etHost)
        etPort = view.findViewById(R.id.etPort)
        etUsername = view.findViewById(R.id.etUsername)
        etPassword = view.findViewById(R.id.etPassword)
        btnTest = view.findViewById(R.id.btnTest)
        tvTestResult = view.findViewById(R.id.tvTestResult)

        val current = viewModel.proxySettings.value
        switchEnabled.isChecked = current.enabled
        toggleMode.check(
            if (current.type == ProxyType.HTTP) R.id.btnHttp else R.id.btnSocks5
        )
        etHost.setText(current.host)
        etPort.setText(if (current.port > 0) current.port.toString() else "")
        etUsername.setText(current.username)
        etPassword.setText(current.password)
        updateFieldState(current.enabled)

        switchEnabled.setOnCheckedChangeListener { _, checked -> updateFieldState(checked) }
        btnTest.setOnClickListener { runTest() }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.proxy_settings)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ -> save() }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun updateFieldState(enabled: Boolean) {
        toggleMode.isEnabled = enabled
        etHost.isEnabled = enabled
        etPort.isEnabled = enabled
        etUsername.isEnabled = enabled
        etPassword.isEnabled = enabled
        btnTest.isEnabled = enabled && !testing
    }

    private fun collectSettings(): ProxySettings {
        val type =
            if (toggleMode.checkedButtonId == R.id.btnHttp) ProxyType.HTTP else ProxyType.SOCKS5
        return ProxySettings(
            enabled = switchEnabled.isChecked,
            type = type,
            host = etHost.text?.toString()?.trim().orEmpty(),
            port = etPort.text?.toString()?.toIntOrNull() ?: 0,
            username = etUsername.text?.toString()?.trim().orEmpty(),
            password = etPassword.text?.toString().orEmpty()
        )
    }

    private fun save() {
        val settings = collectSettings()
        if (settings.enabled && !settings.isValid) {
            tvTestResult.text = getString(R.string.proxy_invalid_input)
            return
        }
        viewModel.saveProxySettings(settings)
    }

    private fun runTest() {
        if (testing) return
        val settings = collectSettings()
        if (!settings.isValid) {
            tvTestResult.text = getString(R.string.proxy_invalid_input)
            return
        }
        testing = true
        btnTest.isEnabled = false
        tvTestResult.text = getString(R.string.proxy_testing)
        lifecycleScope.launch {
            val result = proxyManager.test(settings)
            tvTestResult.text = if (result.ok) {
                getString(
                    R.string.proxy_test_ok,
                    result.exitIp ?: getString(R.string.proxy_test_ok_no_ip)
                )
            } else {
                getString(R.string.proxy_test_failed, result.message)
            }
            btnTest.isEnabled = true
            testing = false
        }
    }
}
