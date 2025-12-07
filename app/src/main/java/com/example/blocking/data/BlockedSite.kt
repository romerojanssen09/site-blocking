package com.example.blocking.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_sites")
data class BlockedSite(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val domain: String,
    val type: SiteType, // DOMAIN or IP
    val addedAt: Long = System.currentTimeMillis()
)

enum class SiteType {
    DOMAIN,
    IP
}
