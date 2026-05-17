package shiro.refraction.domain

import android.content.Context
import shiro.refraction.data.local.ProfileDao
import shiro.refraction.data.local.ProfileEntity
import shiro.refraction.data.local.RefractionDatabase
import shiro.refraction.data.model.Profile

class ProfileManager(context: Context) {

    private val profileDao: ProfileDao = RefractionDatabase.getInstance(context).profileDao()
    private val cookieRepository = CookieRepository(context)

    fun observeProfiles() = profileDao.observeAll()

    suspend fun getAllProfiles() = profileDao.getAll().map { it.toModel() }

    suspend fun getProfile(id: String) = profileDao.getById(id)?.toModel()

    suspend fun getDefaultProfile() = profileDao.getDefault()?.toModel()

    suspend fun createProfile(name: String, colorHex: String): Profile {
        val entity = ProfileEntity(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            colorHex = colorHex,
            isDefault = false,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis()
        )
        profileDao.insert(entity)
        return entity.toModel()
    }

    suspend fun updateProfile(profile: Profile) {
        profileDao.update(profile.toEntity())
    }

    suspend fun deleteProfile(profile: Profile) {
        cookieRepository.delete(profile.id)
        profileDao.delete(profile.toEntity())
    }

    suspend fun setDefault(profile: Profile) {
        profileDao.clearDefault()
        profileDao.update(profile.copy(isDefault = true).toEntity())
    }

    suspend fun switchToProfile(fromProfileId: String?, toProfile: Profile) {
        fromProfileId?.let { cookieRepository.captureAndSave(it) }
        cookieRepository.clearAllCookies()
        cookieRepository.restore(toProfile.id)
        val updated = toProfile.copy(lastUsedAt = System.currentTimeMillis())
        profileDao.update(updated.toEntity())
    }

    private fun ProfileEntity.toModel() = Profile(
        id = id,
        name = name,
        colorHex = colorHex,
        isDefault = isDefault,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt
    )

    private fun Profile.toEntity() = ProfileEntity(
        id = id,
        name = name,
        colorHex = colorHex,
        isDefault = isDefault,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt
    )
}
