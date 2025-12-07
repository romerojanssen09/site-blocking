package com.example.blocking.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BlockingRulesManager {
    private val _blockedDomains = MutableStateFlow<Set<String>>(emptySet())
    val blockedDomains: StateFlow<Set<String>> = _blockedDomains.asStateFlow()

    private val _blockedIps = MutableStateFlow<Set<String>>(emptySet())
    val blockedIps: StateFlow<Set<String>> = _blockedIps.asStateFlow()

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    private val _allowedCount = MutableStateFlow(0)
    val allowedCount: StateFlow<Int> = _allowedCount.asStateFlow()

    fun addBlockedDomain(domain: String) {
        _blockedDomains.value = _blockedDomains.value + domain.lowercase()
    }

    fun removeBlockedDomain(domain: String) {
        _blockedDomains.value = _blockedDomains.value - domain.lowercase()
    }

    fun addBlockedIp(ip: String) {
        _blockedIps.value = _blockedIps.value + ip
    }

    fun removeBlockedIp(ip: String) {
        _blockedIps.value = _blockedIps.value - ip
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
    }
}
