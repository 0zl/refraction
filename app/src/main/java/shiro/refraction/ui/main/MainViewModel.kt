package shiro.refraction.ui.main

import android.app.Application
import android.webkit.WebStorage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import shiro.refraction.data.local.ProfileEntity
import shiro.refraction.data.model.Profile
import shiro.refraction.domain.CookieRepository
import shiro.refraction.domain.ProfileManager

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val profileManager = ProfileManager(application)
    private val cookieRepository = CookieRepository(application)

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

    private fun ProfileEntity.toModel() = Profile(
        id = id,
        name = name,
        colorHex = colorHex,
        isDefault = isDefault,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt
    )
}
