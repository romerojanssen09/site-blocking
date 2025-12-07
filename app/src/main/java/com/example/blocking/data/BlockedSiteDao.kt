package com.example.blocking.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedSiteDao {
    @Query("SELECT * FROM blocked_sites ORDER BY addedAt DESC")
    fun getAllBlocked(): Flow<List<BlockedSite>>
    
    @Query("SELECT * FROM blocked_sites WHERE type = :type")
    fun getBlockedByType(type: SiteType): Flow<List<BlockedSite>>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(site: BlockedSite)
    
    @Delete
    suspend fun delete(site: BlockedSite)
    
    @Query("DELETE FROM blocked_sites WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)
    
    @Query("DELETE FROM blocked_sites")
    suspend fun deleteAll()
    
    @Query("SELECT EXISTS(SELECT 1 FROM blocked_sites WHERE domain = :domain LIMIT 1)")
    suspend fun exists(domain: String): Boolean
}
