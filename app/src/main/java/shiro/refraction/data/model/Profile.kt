package shiro.refraction.data.model

data class Profile(
    val id: String,
    val name: String,
    val colorHex: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val lastUsedAt: Long
)
