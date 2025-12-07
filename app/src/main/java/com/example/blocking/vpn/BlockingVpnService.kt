package com.example.blocking.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import com.example.blocking.MainActivity
import com.example.blocking.data.BlockingRulesManager
import com.example.blocking.data.LogManager
import kotlinx.coroutines.*
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import kotlin.coroutines.coroutineContext

class BlockingVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectivityManager by lazy {
        getSystemService(android.net.ConnectivityManager::class.java)
    }
    private var rulesObserverJob: Job? = null

    companion object {
        private const val TAG = "BlockingVPN"
        private const val CHANNEL_ID = "BlockingVpnChannel"
        private const val BLOCK_CHANNEL_ID = "BlockingNotifications"
        private const val NOTIFICATION_ID = 1
        private const val BLOCK_NOTIFICATION_ID = 2
        const val ACTION_START = "com.example.blocking.START_VPN"
        const val ACTION_STOP = "com.example.blocking.STOP_VPN"
    }

    private var lastBlockedDomain = ""
    private var lastBlockTime = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeBlockingRules()
    }
    
    private fun observeBlockingRules() {
        var isFirstCollection = true
        rulesObserverJob = scope.launch {
            BlockingRulesManager.blockedDomains.collect { domains ->
                if (vpnInterface != null && !isFirstCollection) {
                    log("🔄 Blocking rules updated: ${domains.size} domains")
                    log("🔄 Restarting VPN to clear DNS cache...")
                    restartVpn()
                }
                isFirstCollection = false
            }
        }
    }
    
    private fun restartVpn() {
        log("Stopping VPN...")
        serviceJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        
        // Small delay to ensure cleanup
        Thread.sleep(100)
        
        log("Restarting VPN...")
        startVpn()
        
        showRulesUpdatedNotification()
    }
    
    private fun showRulesUpdatedNotification() {
        val notification = Notification.Builder(this, BLOCK_CHANNEL_ID)
            .setContentTitle("Blocking Rules Updated")
            .setContentText("VPN restarted - new rules active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setTimeoutAfter(3000)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(BLOCK_NOTIFICATION_ID + 1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        log("Starting VPN service...")

        try {
            val builder = Builder()
                .setSession("Blocking VPN")
                .addAddress("10.0.0.2", 24)
                // Set DNS server - this makes system use our VPN for DNS
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                // Route only DNS servers through VPN to intercept queries
                .addRoute("8.8.8.8", 32)
                .addRoute("8.8.4.4", 32)
                .addRoute("1.1.1.1", 32)
                .addRoute("1.0.0.1", 32)
                .setBlocking(false)
                .setMtu(1500)

            // Try to set underlying network for proper routing
            try {
                val underlyingNetwork = connectivityManager?.activeNetwork
                if (underlyingNetwork != null) {
                    builder.setUnderlyingNetworks(arrayOf(underlyingNetwork))
                    log("Using underlying network for routing")
                }
            } catch (e: SecurityException) {
                log("Warning: Could not set underlying network")
            }

            vpnInterface = builder.establish()
            
            if (vpnInterface == null) {
                log("ERROR: Failed to establish VPN interface")
                return
            }
            
            log("VPN interface established - DNS-only blocking mode")
            
            startForeground(NOTIFICATION_ID, createNotification())

            serviceJob = scope.launch {
                processPackets()
            }
        } catch (e: Exception) {
            log("ERROR: VPN setup failed - ${e.message}")
            Log.e(TAG, "VPN setup error", e)
        }
    }

    private fun stopVpn() {
        serviceJob?.cancel()
        rulesObserverJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun processPackets() {
        val vpnInput = FileInputStream(vpnInterface?.fileDescriptor)
        val vpnOutput = FileOutputStream(vpnInterface?.fileDescriptor)
        val packet = ByteArray(32767)

        log("Packet processing started")

        try {
            while (coroutineContext.isActive && vpnInterface != null) {
                val length = vpnInput.read(packet)
                if (length > 0) {
                    handlePacket(packet, length, vpnOutput)
                }
            }
        } catch (e: Exception) {
            log("ERROR: Packet processing failed - ${e.message}")
            Log.e(TAG, "Error in packet processing", e)
        }
    }

    private fun handlePacket(packet: ByteArray, length: Int, vpnOutput: FileOutputStream) {
        try {
            if (length < 20) return

            val version = (packet[0].toInt() shr 4) and 0xF
            if (version != 4) return

            val protocol = packet[9].toInt() and 0xFF
            val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4

            // Check destination IP - block if it's 0.0.0.0 (our blocked response)
            val destIp = String.format(
                "%d.%d.%d.%d",
                packet[16].toInt() and 0xFF,
                packet[17].toInt() and 0xFF,
                packet[18].toInt() and 0xFF,
                packet[19].toInt() and 0xFF
            )

            // Drop packets to 0.0.0.0 (blocked domains)
            if (destIp == "0.0.0.0") {
                return // Silently drop
            }
            
            if (BlockingRulesManager.isBlocked(destIp)) {
                log("BLOCKED IP: $destIp")
                return // Drop blocked IP packets
            }

            // Handle DNS queries (UDP port 53)
            if (protocol == 17 && length >= ipHeaderLength + 8) {
                val destPort = ((packet[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or
                              (packet[ipHeaderLength + 3].toInt() and 0xFF)

                if (destPort == 53) {
                    val domain = parseDnsDomain(packet, ipHeaderLength + 8, length)
                    
                    if (domain != null) {
                        if (BlockingRulesManager.isDomainBlocked(domain)) {
                            log("BLOCKED: $domain → 0.0.0.0")
                            showBlockNotification(domain)
                            val response = createBlockedDnsResponse(packet, length, ipHeaderLength)
                            vpnOutput.write(response)
                            return
                        } else {
                            // Only log unique domains to reduce noise
                            scope.launch {
                                forwardDnsQuery(packet, length, ipHeaderLength, vpnOutput, domain)
                            }
                            return
                        }
                    }
                }
            }

            // Non-DNS packets shouldn't reach here since we only route DNS servers
            // But if they do, just ignore them

        } catch (e: Exception) {
            Log.e(TAG, "Error handling packet", e)
            e.printStackTrace()
        }
    }

    private suspend fun forwardPacketToNetwork(
        packet: ByteArray,
        length: Int,
        vpnOutput: FileOutputStream
    ) {
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                val version = (packet[0].toInt() shr 4) and 0xF
                if (version != 4) return@withContext

                val protocol = packet[9].toInt() and 0xFF
                val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4

                // Only handle UDP packets (TCP requires more complex handling)
                if (protocol != 17) return@withContext
                
                if (length < ipHeaderLength + 8) return@withContext

                // Extract destination IP and port
                val destIp = InetAddress.getByAddress(byteArrayOf(
                    packet[16], packet[17], packet[18], packet[19]
                ))
                val destPort = ((packet[ipHeaderLength].toInt() and 0xFF) shl 8) or
                              (packet[ipHeaderLength + 1].toInt() and 0xFF)

                // Skip DNS packets (already handled)
                if (destPort == 53) return@withContext

                // Extract UDP payload
                val udpHeaderLength = 8
                val payloadOffset = ipHeaderLength + udpHeaderLength
                val payloadLength = length - payloadOffset
                
                if (payloadLength <= 0) return@withContext

                val payload = ByteArray(payloadLength)
                System.arraycopy(packet, payloadOffset, payload, 0, payloadLength)

                // Create socket and protect it
                socket = DatagramSocket()
                if (!protect(socket)) {
                    socket.close()
                    return@withContext
                }

                socket.soTimeout = 5000

                // Forward packet
                val outPacket = DatagramPacket(payload, payloadLength, destIp, destPort)
                socket.send(outPacket)

                // Receive response
                val responseBuffer = ByteArray(32767)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)

                // Create response packet
                val response = createUdpResponsePacket(
                    packet,
                    ipHeaderLength,
                    responseBuffer,
                    responsePacket.length,
                    responsePacket.address,
                    responsePacket.port
                )
                vpnOutput.write(response)

            } catch (e: Exception) {
                // Silently ignore - most packets will timeout
            } finally {
                socket?.close()
            }
        }
    }

    private fun createUdpResponsePacket(
        originalPacket: ByteArray,
        ipHeaderLength: Int,
        payload: ByteArray,
        payloadLength: Int,
        sourceAddress: InetAddress,
        sourcePort: Int
    ): ByteArray {
        val udpHeaderLength = 8
        val responseSize = ipHeaderLength + udpHeaderLength + payloadLength
        val response = ByteArray(responseSize)

        // Copy IP header
        System.arraycopy(originalPacket, 0, response, 0, ipHeaderLength)

        // Swap IP addresses
        for (i in 0 until 4) {
            val temp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = temp
        }

        // Copy UDP header
        System.arraycopy(originalPacket, ipHeaderLength, response, ipHeaderLength, udpHeaderLength)

        // Swap UDP ports
        for (i in 0 until 2) {
            val temp = response[ipHeaderLength + i]
            response[ipHeaderLength + i] = response[ipHeaderLength + 2 + i]
            response[ipHeaderLength + 2 + i] = temp
        }

        // Copy payload
        System.arraycopy(payload, 0, response, ipHeaderLength + udpHeaderLength, payloadLength)

        // Update IP total length
        response[2] = (responseSize shr 8).toByte()
        response[3] = responseSize.toByte()

        // Update UDP length
        val udpLength = udpHeaderLength + payloadLength
        response[ipHeaderLength + 4] = (udpLength shr 8).toByte()
        response[ipHeaderLength + 5] = udpLength.toByte()

        // Clear and recalculate IP checksum
        response[10] = 0
        response[11] = 0
        val ipChecksum = calculateChecksum(response, 0, ipHeaderLength)
        response[10] = (ipChecksum shr 8).toByte()
        response[11] = ipChecksum.toByte()

        // Clear UDP checksum
        response[ipHeaderLength + 6] = 0
        response[ipHeaderLength + 7] = 0

        return response
    }

    private suspend fun forwardDnsQuery(
        packet: ByteArray,
        length: Int,
        ipHeaderLength: Int,
        vpnOutput: FileOutputStream,
        domain: String
    ) {
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                // Create a new unbound socket
                socket = DatagramSocket()
                
                // CRITICAL: Protect the socket from going through VPN
                if (!protect(socket)) {
                    log("ERROR: Failed to protect socket for $domain")
                    socket.close()
                    return@withContext
                }

                // Extract DNS query
                val udpHeaderLength = 8
                val dnsOffset = ipHeaderLength + udpHeaderLength
                val dnsLength = length - dnsOffset
                val dnsQuery = ByteArray(dnsLength)
                System.arraycopy(packet, dnsOffset, dnsQuery, 0, dnsLength)

                // Try multiple DNS servers
                val dnsServers = listOf("8.8.8.8", "8.8.4.4", "1.1.1.1")
                var success = false

                for ((index, dnsServer) in dnsServers.withIndex()) {
                    try {
                        // Create new socket for each attempt
                        if (index > 0) {
                            socket?.close()
                            socket = DatagramSocket()
                            if (!protect(socket!!)) {
                                continue
                            }
                        }

                        val dnsAddress = InetAddress.getByName(dnsServer)
                        val dnsPacket = DatagramPacket(
                            dnsQuery,
                            dnsLength,
                            dnsAddress,
                            53
                        )
                        
                        socket!!.soTimeout = 3000
                        socket.send(dnsPacket)

                        // Receive response
                        val responseBuffer = ByteArray(512)
                        val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                        socket.receive(responsePacket)

                        log("DNS OK: $domain")

                        // Create IP/UDP packet with DNS response
                        val response = createDnsResponsePacket(
                            packet,
                            ipHeaderLength,
                            responseBuffer,
                            responsePacket.length
                        )
                        vpnOutput.write(response)
                        success = true
                        break
                    } catch (e: Exception) {
                        Log.d(TAG, "DNS attempt failed for $domain via $dnsServer: ${e.message}")
                        continue
                    }
                }

                if (!success) {
                    log("ERROR: DNS timeout for $domain")
                }

            } catch (e: Exception) {
                log("ERROR: DNS exception for $domain - ${e.message}")
                Log.e(TAG, "Error forwarding DNS query for $domain", e)
            } finally {
                socket?.close()
            }
        }
    }

    private fun createDnsResponsePacket(
        originalPacket: ByteArray,
        ipHeaderLength: Int,
        dnsResponse: ByteArray,
        dnsResponseLength: Int
    ): ByteArray {
        val udpHeaderLength = 8
        val responseSize = ipHeaderLength + udpHeaderLength + dnsResponseLength
        val response = ByteArray(responseSize)

        // Copy IP header
        System.arraycopy(originalPacket, 0, response, 0, ipHeaderLength)

        // Swap IP addresses
        for (i in 0 until 4) {
            val temp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = temp
        }

        // Copy UDP header
        System.arraycopy(originalPacket, ipHeaderLength, response, ipHeaderLength, udpHeaderLength)

        // Swap UDP ports
        for (i in 0 until 2) {
            val temp = response[ipHeaderLength + i]
            response[ipHeaderLength + i] = response[ipHeaderLength + 2 + i]
            response[ipHeaderLength + 2 + i] = temp
        }

        // Copy DNS response
        System.arraycopy(dnsResponse, 0, response, ipHeaderLength + udpHeaderLength, dnsResponseLength)

        // Update IP total length
        response[2] = (responseSize shr 8).toByte()
        response[3] = responseSize.toByte()

        // Update UDP length
        val udpLength = udpHeaderLength + dnsResponseLength
        response[ipHeaderLength + 4] = (udpLength shr 8).toByte()
        response[ipHeaderLength + 5] = udpLength.toByte()

        // Clear and recalculate IP checksum
        response[10] = 0
        response[11] = 0
        val ipChecksum = calculateChecksum(response, 0, ipHeaderLength)
        response[10] = (ipChecksum shr 8).toByte()
        response[11] = ipChecksum.toByte()

        // Clear UDP checksum
        response[ipHeaderLength + 6] = 0
        response[ipHeaderLength + 7] = 0

        return response
    }



    private fun parseDnsDomain(packet: ByteArray, dnsOffset: Int, length: Int): String? {
        try {
            if (length < dnsOffset + 12) return null

            var pos = dnsOffset + 12 // Skip DNS header
            val domain = StringBuilder()

            while (pos < length) {
                val labelLength = packet[pos].toInt() and 0xFF
                if (labelLength == 0) break
                if (labelLength >= 192) break // Compression pointer
                if (pos + labelLength >= length) break

                pos++
                if (domain.isNotEmpty()) domain.append('.')
                
                for (i in 0 until labelLength) {
                    if (pos >= length) break
                    domain.append(packet[pos++].toInt().toChar())
                }
            }

            return if (domain.isNotEmpty()) domain.toString() else null
        } catch (e: Exception) {
            return null
        }
    }

    private fun createBlockedDnsResponse(query: ByteArray, queryLength: Int, ipHeaderLength: Int): ByteArray {
        // Calculate response size
        val udpHeaderLength = 8
        val dnsHeaderLength = 12
        val answerLength = 16 // Pointer(2) + Type(2) + Class(2) + TTL(4) + DataLen(2) + IP(4)
        
        val responseSize = queryLength + answerLength
        val response = ByteArray(responseSize)
        
        // Copy original query
        System.arraycopy(query, 0, response, 0, queryLength)
        
        // Swap IP addresses
        for (i in 0 until 4) {
            val temp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = temp
        }
        
        // Swap UDP ports
        for (i in 0 until 2) {
            val temp = response[ipHeaderLength + i]
            response[ipHeaderLength + i] = response[ipHeaderLength + 2 + i]
            response[ipHeaderLength + 2 + i] = temp
        }
        
        val dnsOffset = ipHeaderLength + udpHeaderLength
        
        // Set DNS response flags
        response[dnsOffset + 2] = 0x81.toByte()
        response[dnsOffset + 3] = 0x80.toByte()
        
        // Set answer count to 1
        response[dnsOffset + 6] = 0x00
        response[dnsOffset + 7] = 0x01
        
        // Add answer at the end
        var pos = queryLength
        response[pos++] = 0xC0.toByte() // Name pointer
        response[pos++] = 0x0C.toByte()
        response[pos++] = 0x00 // Type A
        response[pos++] = 0x01
        response[pos++] = 0x00 // Class IN
        response[pos++] = 0x01
        response[pos++] = 0x00 // TTL
        response[pos++] = 0x00
        response[pos++] = 0x00
        response[pos++] = 0x3C
        response[pos++] = 0x00 // Data length
        response[pos++] = 0x04
        response[pos++] = 0x00 // IP: 0.0.0.0
        response[pos++] = 0x00
        response[pos++] = 0x00
        response[pos++] = 0x00
        
        // Update IP total length
        response[2] = (responseSize shr 8).toByte()
        response[3] = responseSize.toByte()
        
        // Update UDP length
        val udpLength = responseSize - ipHeaderLength
        response[ipHeaderLength + 4] = (udpLength shr 8).toByte()
        response[ipHeaderLength + 5] = udpLength.toByte()
        
        // Clear and recalculate IP checksum
        response[10] = 0
        response[11] = 0
        val ipChecksum = calculateChecksum(response, 0, ipHeaderLength)
        response[10] = (ipChecksum shr 8).toByte()
        response[11] = ipChecksum.toByte()
        
        // Clear UDP checksum (optional for IPv4)
        response[ipHeaderLength + 6] = 0
        response[ipHeaderLength + 7] = 0
        
        return response
    }



    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < offset + length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv().toInt() and 0xFFFF
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        
        // VPN service channel
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "VPN Blocking Service",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(serviceChannel)
        
        // Block notifications channel
        val blockChannel = NotificationChannel(
            BLOCK_CHANNEL_ID,
            "Blocked Sites",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when sites are blocked"
        }
        manager.createNotificationChannel(blockChannel)
    }

    private fun showBlockNotification(domain: String) {
        // Throttle notifications - only show once per domain per 5 seconds
        val now = System.currentTimeMillis()
        if (domain == lastBlockedDomain && now - lastBlockTime < 5000) {
            return
        }
        lastBlockedDomain = domain
        lastBlockTime = now

        val notification = Notification.Builder(this, BLOCK_CHANNEL_ID)
            .setContentTitle("Site Blocked")
            .setContentText(domain)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setAutoCancel(true)
            .setTimeoutAfter(3000)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(BLOCK_NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VPN Blocking Active")
            .setContentText("System-wide blocking is enabled")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }





    private fun log(message: String) {
        Log.d(TAG, message)
        LogManager.addLog(message)
    }

    override fun onDestroy() {
        stopVpn()
        scope.cancel()
        super.onDestroy()
    }
}
