package shiro.refraction.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import shiro.refraction.data.local.ProfileEntity
import shiro.refraction.data.model.NetworkRequest
import shiro.refraction.data.model.Profile
import shiro.refraction.data.model.ProxySettings
import shiro.refraction.domain.CookieRepository
import shiro.refraction.domain.ProfileManager
import shiro.refraction.domain.ProxyManager
import shiro.refraction.domain.RequestRecorder

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val profileManager = ProfileManager(application)
    private val cookieRepository = CookieRepository(application)
    private val proxyManager = ProxyManager(application)
    val requestRecorder = RequestRecorder()

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _desktopMode = MutableStateFlow(prefs.getBoolean(KEY_DESKTOP_MODE, false))
    val desktopMode: StateFlow<Boolean> = _desktopMode.asStateFlow()

    private val _uaChangedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val uaChangedEvent: SharedFlow<Unit> = _uaChangedEvent.asSharedFlow()

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<Profile?>(null)
    val activeProfile: StateFlow<Profile?> = _activeProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _showEmptyState = MutableStateFlow(false)
    val showEmptyState: StateFlow<Boolean> = _showEmptyState.asStateFlow()

    private val _switchEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val switchEvent: SharedFlow<Unit> = _switchEvent.asSharedFlow()

    private val _proxySettings = MutableStateFlow(proxyManager.load())
    val proxySettings: StateFlow<ProxySettings> = _proxySettings.asStateFlow()

    val isRecording: StateFlow<Boolean> = requestRecorder.isRecording
    val recordedRequests: StateFlow<List<NetworkRequest>> = requestRecorder.requests

    init {
        viewModelScope.launch {
            profileManager.observeProfiles().collect { entities ->
                val models = entities.map { it.toModel() }
                _profiles.value = models
                _showEmptyState.value = models.isEmpty()
                if (_activeProfile.value == null && models.isNotEmpty()) {
                    loadInitialProfile(models)
                }
            }
        }
    }

    private suspend fun loadInitialProfile(models: List<Profile>) {
        val default = models.find { it.isDefault } ?: models.first()
        switchToProfile(null, default)
    }

    fun onPageFinished(url: String) {
        viewModelScope.launch {
            _activeProfile.value?.let { profile ->
                cookieRepository.captureAndSave(profile.id)
            }
        }
    }

    fun onProgressChanged(progress: Int) {
        _progress.value = progress
    }

    fun switchToProfile(fromProfileId: String?, toProfile: Profile) {
        viewModelScope.launch {
            _isLoading.value = true
            profileManager.switchToProfile(fromProfileId, toProfile)
            _activeProfile.value = toProfile
            _isLoading.value = false
            _switchEvent.tryEmit(Unit)
        }
    }

    fun captureCookies() {
        viewModelScope.launch {
            _activeProfile.value?.let { profile ->
                cookieRepository.captureAndSave(profile.id)
            }
        }
    }

    fun createProfile(name: String, colorHex: String) {
        viewModelScope.launch {
            val profile = profileManager.createProfile(name, colorHex)
            if (_activeProfile.value == null) {
                switchToProfile(null, profile)
            }
        }
    }

    fun updateProfile(profile: Profile) {
        viewModelScope.launch {
            profileManager.updateProfile(profile)
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            profileManager.deleteProfile(profile)
            if (_activeProfile.value?.id == profile.id) {
                _activeProfile.value = null
            }
        }
    }

    fun setDefaultProfile(profile: Profile) {
        viewModelScope.launch {
            profileManager.setDefault(profile)
        }
    }

    suspend fun clearCurrentProfileCookies() {
        _activeProfile.value?.let { profile ->
            cookieRepository.clearAllCookies()
            cookieRepository.delete(profile.id)
        }
    }

    suspend fun getCurrentCookieString(): String {
        return cookieRepository.getCurrentCookies()
    }

    fun toggleDesktopMode() {
        val newValue = !_desktopMode.value
        prefs.edit().putBoolean(KEY_DESKTOP_MODE, newValue).apply()
        _desktopMode.value = newValue
        _uaChangedEvent.tryEmit(Unit)
    }

    fun saveProxySettings(settings: ProxySettings) {
        proxyManager.save(settings)
        _proxySettings.value = settings
    }

    fun startRecording() {
        requestRecorder.startRecording()
    }

    fun stopRecording() {
        requestRecorder.stopRecording()
    }

    fun clearRecordedRequests() {
        requestRecorder.clear()
    }

    fun recordRequest(request: NetworkRequest) {
        requestRecorder.record(request)
    }

    private companion object {
        private const val PREFS_NAME = "refraction_prefs"
        private const val KEY_DESKTOP_MODE = "desktop_mode"
    }

    private fun ProfileEntity.toModel() = Profile(
        id = id,
        name = name,
        colorHex = colorHex,
        isDefault = isDefault,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt
    )
}
