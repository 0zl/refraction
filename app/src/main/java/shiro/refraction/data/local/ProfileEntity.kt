package shiro.refraction.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val lastUsedAt: Long
)
