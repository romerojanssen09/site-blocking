package com.example.blocking.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object BlockingRulesManager {
    private val _blockedDomains = MutableStateFlow<Set<String>>(emptySet())
    val blockedDomains: StateFlow<Set<String>> = _blockedDomains.asStateFlow()

    private val _blockedIps = MutableStateFlow<Set<String>>(emptySet())
    val blockedIps: StateFlow<Set<String>> = _blockedIps.asStateFlow()

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    private val _allowedCount = MutableStateFlow(0)
    val allowedCount: StateFlow<Int> = _allowedCount.asStateFlow()

    private var database: BlockingDatabase? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize(context: Context) {
        database = BlockingDatabase.getDatabase(context)
        loadFromDatabase()
    }

    private fun loadFromDatabase() {
        scope.launch {
            database?.blockedSiteDao()?.getAllBlocked()?.collect { sites ->
                val domains = sites.filter { it.type == SiteType.DOMAIN }.map { it.domain }.toSet()
                val ips = sites.filter { it.type == SiteType.IP }.map { it.domain }.toSet()
                _blockedDomains.value = domains
                _blockedIps.value = ips
            }
        }
    }

    fun addBlockedDomain(domain: String) {
        val lowerDomain = domain.lowercase()
        _blockedDomains.value = _blockedDomains.value + lowerDomain
        scope.launch {
            database?.blockedSiteDao()?.insert(
                BlockedSite(domain = lowerDomain, type = SiteType.DOMAIN)
            )
        }
    }

    fun removeBlockedDomain(domain: String) {
        _blockedDomains.value = _blockedDomains.value - domain.lowercase()
        scope.launch {
            database?.blockedSiteDao()?.deleteByDomain(domain.lowercase())
        }
    }

    fun addBlockedIp(ip: String) {
        _blockedIps.value = _blockedIps.value + ip
        scope.launch {
            database?.blockedSiteDao()?.insert(
                BlockedSite(domain = ip, type = SiteType.IP)
            )
        }
    }

    fun removeBlockedIp(ip: String) {
        _blockedIps.value = _blockedIps.value - ip
        scope.launch {
            database?.blockedSiteDao()?.deleteByDomain(ip)
        }
    }

    fun isBlocked(ip: String): Boolean {
        return _blockedIps.value.contains(ip)
    }

    fun isDomainBlocked(domain: String): Boolean {
        val lowerDomain = domain.lowercase()
        val isBlocked = _blockedDomains.value.any { blockedDomain ->
            lowerDomain == blockedDomain || lowerDomain.endsWith(".$blockedDomain")
        }
        
        if (isBlocked) {
            _blockedCount.value++
        } else {
            _allowedCount.value++
        }
        
        return isBlocked
    }

    fun resetStats() {
        _blockedCount.value = 0
        _allowedCount.value = 0
    }

    fun clearAll() {
        _blockedDomains.value = emptySet()
        _blockedIps.value = emptySet()
        scope.launch {
            database?.blockedSiteDao()?.deleteAll()
        }
    }
}
