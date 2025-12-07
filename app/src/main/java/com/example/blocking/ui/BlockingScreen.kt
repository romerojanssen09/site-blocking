package com.example.blocking.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.blocking.data.BlockingRulesManager
import com.example.blocking.vpn.BlockingVpnService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockingScreen() {
    val context = LocalContext.current
    val prefs = remember { 
        context.getSharedPreferences("vpn_state", android.content.Context.MODE_PRIVATE) 
    }
    var isVpnActive by remember { mutableStateOf(prefs.getBoolean("is_running", false)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val blockedDomains by BlockingRulesManager.blockedDomains.collectAsState()
    val blockedIps by BlockingRulesManager.blockedIps.collectAsState()
    
    // Double-tap back to exit
    var backPressedTime by remember { mutableStateOf(0L) }
    var showExitToast by remember { mutableStateOf(false) }

    // Request notification permission
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled
    }

    LaunchedEffect(Unit) {
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
//        }
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(context, BlockingVpnService::class.java).apply {
                action = BlockingVpnService.ACTION_START
            }
            context.startService(intent)
            isVpnActive = true
        }
    }

    // Handle system back button when viewing logs
    androidx.activity.compose.BackHandler(enabled = showLogs) {
        showLogs = false
    }
    
    // Handle double-tap back to exit on main screen
    androidx.activity.compose.BackHandler(enabled = !showLogs) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000) {
            // Double tap detected - exit app
            (context as? Activity)?.finish()
        } else {
            // First tap - show toast
            backPressedTime = currentTime
            android.widget.Toast.makeText(
                context,
                "Press back again to exit",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    if (showLogs) {
        LogScreen(onBack = { showLogs = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System-Wide Blocking") },
                actions = {
                    TextButton(onClick = { showLogs = true }) {
                        Text("View Logs")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add rule")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "VPN Status",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isVpnActive) "Active" else "Inactive",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (isVpnActive) {
                                val blockedCount by BlockingRulesManager.blockedCount.collectAsState()
                                val allowedCount by BlockingRulesManager.allowedCount.collectAsState()
                                Text(
                                    text = "Blocked: $blockedCount | Allowed: $allowedCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = {
                                if (isVpnActive) {
                                    val intent = Intent(context, BlockingVpnService::class.java).apply {
                                        action = BlockingVpnService.ACTION_STOP
                                    }
                                    context.startService(intent)
                                    isVpnActive = false
                                    BlockingRulesManager.resetStats()
                                } else {
                                    val intent = VpnService.prepare(context)
                                    if (intent != null) {
                                        vpnLauncher.launch(intent)
                                    } else {
                                        val serviceIntent = Intent(context, BlockingVpnService::class.java).apply {
                                            action = BlockingVpnService.ACTION_START
                                        }
                                        context.startService(serviceIntent)
                                        isVpnActive = true
                                    }
                                }
                            }
                        ) {
                            Text(if (isVpnActive) "Stop VPN" else "Start VPN")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Blocked Domains & IPs",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${blockedDomains.size + blockedIps.size} rules",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(blockedDomains.toList()) { domain ->
                    BlockedItemCard(
                        text = domain,
                        onDelete = { BlockingRulesManager.removeBlockedDomain(domain) }
                    )
                }
                items(blockedIps.toList()) { ip ->
                    BlockedItemCard(
                        text = ip,
                        onDelete = { BlockingRulesManager.removeBlockedIp(ip) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddBlockingRuleDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { rule ->
                if (rule.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                    BlockingRulesManager.addBlockedIp(rule)
                } else {
                    BlockingRulesManager.addBlockedDomain(rule)
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun BlockedItemCard(text: String, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete")
            }
        }
    }
}

@Composable
fun AddBlockingRuleDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Blocking Rule") },
        text = {
            Column {
                Text("Enter domain or IP address to block:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Domain or IP") },
                    placeholder = { Text("example.com or 1.2.3.4") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onAdd(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper function to check if VPN is running using modern API
private fun isVpnRunning(context: android.content.Context): Boolean {
    return try {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) 
            as? android.net.ConnectivityManager
        val network = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
    } catch (e: Exception) {
        false
    }
}
