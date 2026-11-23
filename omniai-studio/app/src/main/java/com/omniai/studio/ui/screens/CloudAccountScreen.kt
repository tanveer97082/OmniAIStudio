package com.omniai.studio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniai.studio.cloud.CloudUserProfile
import com.omniai.studio.cloud.CloudUserRole
import com.omniai.studio.ui.guides.PlatformGuideBottomSheet
import com.omniai.studio.ui.guides.PlatformGuideId
import com.omniai.studio.ui.guides.PlatformGuideTriggerButton
import com.omniai.studio.ui.theme.AmberWarning
import com.omniai.studio.ui.theme.AmoledBlack
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.OmniAccentBadge
import com.omniai.studio.ui.theme.OmniDesignTokens
import com.omniai.studio.ui.theme.OmniSurfaceCard
import com.omniai.studio.ui.theme.TextSecondary
import com.omniai.studio.viewmodel.OmniAiViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun roleAccentColor(role: CloudUserRole): Color = when (role) {
    CloudUserRole.OWNER -> EmeraldAccent
    CloudUserRole.EDITOR -> Color(0xFF38BDF8)
    CloudUserRole.VIEWER -> AmberWarning
    CloudUserRole.PENDING -> CrimsonDanger
    CloudUserRole.OFFLINE_LOCAL -> Color(0xFFA1A1AA)
}

private fun formatRelativeSyncTime(epochMs: Long): String {
    if (epochMs <= 0L) return "Never"
    val diffSec = ((System.currentTimeMillis() - epochMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Just now"
        diffSec < 3600L -> "${diffSec / 60L}m ago"
        diffSec < 86400L -> "${diffSec / 3600L}h ago"
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(epochMs))
    }
}

/**
 * Cloud Sync & Server-Enforced RBAC Role Management Screen (v5.5).
 * Accessible from ModalNavigationDrawer ("Cloud DB & Roles") and Settings Hub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAccountScreen(
    viewModel: OmniAiViewModel,
    onBack: () -> Unit,
    onRunGuideCommandInTerminal: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    val session by viewModel.cloudSessionState.collectAsState()
    val capabilities by viewModel.roleCapabilities.collectAsState()
    val syncStatus by viewModel.cloudSyncStatus.collectAsState()
    val teamProfiles by viewModel.cloudTeamProfiles.collectAsState()
    val isLoadingTeam by viewModel.isLoadingCloudTeam.collectAsState()
    val syncIntervalMinutes by viewModel.cloudSyncIntervalMinutes.collectAsState()
    val syncWifiOnly by viewModel.cloudSyncWifiOnly.collectAsState()

    var supabaseUrlInput by remember { mutableStateOf(viewModel.vault.supabaseUrl) }
    var supabaseAnonKeyInput by remember { mutableStateOf(viewModel.vault.supabaseAnonKey) }
    var showSupabaseGuide by remember { mutableStateOf(false) }

    LaunchedEffect(capabilities.canManageUsers, session.isSignedIn) {
        if (capabilities.canManageUsers && session.isSignedIn) {
            viewModel.loadCloudTeamProfiles()
        }
    }

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Cloud DB & Roles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Supabase PostgreSQL · GZIP Text-Only · RLS RBAC",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    OmniAccentBadge(
                        text = session.role.badgeText,
                        accent = roleAccentColor(session.role)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = OmniDesignTokens.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Google Sign-In & Active Session Card
            item {
                OmniSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (session.isSignedIn) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = roleAccentColor(session.role),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = if (session.isSignedIn) {
                                        session.email.ifBlank { "Signed-In Account" }
                                    } else {
                                        "Offline Local Mode"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Device: ${session.deviceName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OmniAccentBadge(
                            text = session.role.badgeText,
                            accent = roleAccentColor(session.role)
                        )
                    }

                    Surface(
                        color = colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, colorScheme.outline),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Last synced: ${formatRelativeSyncTime(syncStatus.lastSyncedAtMs)} | Compression saved: ${syncStatus.lastSavedPercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldAccent,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = syncStatus.lastMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Primary Sign-In / Sync / Sign-Out Actions
                    if (!session.isSignedIn) {
                        Button(
                            onClick = { viewModel.startGoogleCloudSignIn(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign in with Google (PKCE S256)")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.syncCloudNow() },
                                enabled = !syncStatus.isSyncing && !capabilities.isPendingApproval,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (syncStatus.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (syncStatus.isSyncing) "Syncing..." else "Sync Now")
                            }
                            OutlinedButton(
                                onClick = { viewModel.signOutCloud() },
                                modifier = Modifier.weight(0.85f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign Out")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.refreshCloudApprovalStatus() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Refresh Role")
                            }
                            OutlinedButton(
                                onClick = { viewModel.enableOfflineLocalMode() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Offline Local")
                            }
                        }
                    }
                }
            }

            // 2. Auto Delta Sync Cadence (WorkManager)
            item {
                OmniSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.CloudSync, contentDescription = null, tint = colorScheme.primary)
                            Column {
                                Text("Multi-Device GZIP Delta Sync", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Text-only JSON (<=256 KB/row, zero MP4/images)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15 to "15 Min", 60 to "1 Hour", 0 to "Manual").forEach { (mins, label) ->
                            FilterChip(
                                selected = syncIntervalMinutes == mins,
                                onClick = { viewModel.setCloudSyncSchedule(mins, syncWifiOnly) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sync on Unmetered Wi-Fi Only", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = syncWifiOnly,
                            onCheckedChange = { viewModel.setCloudSyncSchedule(syncIntervalMinutes, it) }
                        )
                    }
                }
            }

            // 3. Supabase Project Connection & Setup Guide
            item {
                OmniSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = EmeraldAccent)
                            Text("Supabase Project Config", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        PlatformGuideTriggerButton(
                            onClick = { showSupabaseGuide = true }
                        )
                    }

                    OutlinedTextField(
                        value = supabaseUrlInput,
                        onValueChange = { supabaseUrlInput = it },
                        label = { Text("SUPABASE_URL (https://<ref>.supabase.co)") },
                        singleLine = true,
                        enabled = capabilities.canWriteVault,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = supabaseAnonKeyInput,
                        onValueChange = { supabaseAnonKeyInput = it },
                        label = { Text("SUPABASE_ANON_KEY (anon public key)") },
                        singleLine = true,
                        enabled = capabilities.canWriteVault,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!capabilities.canWriteVault) {
                        Text(
                            text = "Owner Only: ${session.role.label} role cannot modify Supabase project credentials.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberWarning
                        )
                    } else {
                        Button(
                            onClick = {
                                viewModel.saveSupabaseConfig(supabaseUrlInput, supabaseAnonKeyInput)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Encrypted Supabase Config")
                        }
                    }
                }
            }

            // 4. Owner-Only Team & Device Role Manager
            item {
                OmniSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = EmeraldAccent)
                            Column {
                                Text("Team & Device Role Manager", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Server-enforced PostgreSQL RLS (Owner Only)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (capabilities.canManageUsers) {
                            IconButton(
                                onClick = { viewModel.loadCloudTeamProfiles() },
                                enabled = !isLoadingTeam
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh Team Profiles")
                            }
                        }
                    }

                    if (!capabilities.canManageUsers) {
                        Surface(
                            color = colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, colorScheme.outline),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                                Text(
                                    text = if (session.isOfflineLocalMode) {
                                        "Sign in as the Supabase OWNER account to manage team roles and device approvals."
                                    } else {
                                        "Owner Only — Your current role (${session.role.badgeText}) cannot modify user roles."
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (isLoadingTeam) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Loading user_profiles...", style = MaterialTheme.typography.labelMedium)
                        }
                    } else if (teamProfiles.isEmpty()) {
                        Text(
                            text = "No user profiles returned yet. Tap Refresh after running schema_and_rls.sql.",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            teamProfiles.forEach { profile ->
                                TeamMemberRoleRow(
                                    profile = profile,
                                    isSelf = profile.id == session.userId,
                                    onUpdateRole = { role, approved ->
                                        viewModel.updateCloudUserRole(profile.id, role, approved)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showSupabaseGuide) {
        PlatformGuideBottomSheet(
            guideId = PlatformGuideId.SUPABASE_CLOUD,
            onDismiss = { showSupabaseGuide = false },
            onToast = { viewModel.postToast(it) },
            onRunInTerminal = { cmd ->
                showSupabaseGuide = false
                onRunGuideCommandInTerminal(cmd)
            }
        )
    }
}

@Composable
private fun TeamMemberRoleRow(
    profile: CloudUserProfile,
    isSelf: Boolean,
    onUpdateRole: (String, Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var roleMenuExpanded by remember { mutableStateOf(false) }
    val parsedRole = CloudUserRole.fromServerRole(profile.role, profile.isApproved, isOfflineLocal = false)

    Surface(
        color = colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, colorScheme.outline),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.email + if (isSelf) " (You)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Devices,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${profile.lastDevice?.ifBlank { "Unknown Device" } ?: "Unknown Device"} · Seen ${profile.lastSeenAt?.take(16)?.replace('T', ' ') ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                OmniAccentBadge(text = profile.role, accent = roleAccentColor(parsedRole))
            }

            HorizontalDivider(color = colorScheme.outline)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    OutlinedButton(
                        onClick = { roleMenuExpanded = true },
                        enabled = !isSelf,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Role: ${profile.role}", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = roleMenuExpanded,
                        onDismissRequest = { roleMenuExpanded = false }
                    ) {
                        listOf("OWNER", "EDITOR", "VIEWER", "PENDING").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    roleMenuExpanded = false
                                    val autoApprove = option != "PENDING"
                                    onUpdateRole(option, autoApprove)
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (profile.isApproved) "Approved" else "Revoked",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (profile.isApproved) EmeraldAccent else CrimsonDanger
                    )
                    Switch(
                        checked = profile.isApproved,
                        enabled = !isSelf,
                        onCheckedChange = { checked ->
                            val nextRole = if (!checked) "PENDING" else if (profile.role == "PENDING") "VIEWER" else profile.role
                            onUpdateRole(nextRole, checked)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Full-screen RBAC gate shown when an authenticated Google user has role = PENDING or is_approved = false.
 * Includes "Refresh Approval Status", "Sign Out", and an explicit "Offline Local Mode" fallback.
 */
@Composable
fun PendingApprovalLockScreen(
    email: String,
    deviceName: String,
    isRefreshing: Boolean,
    onRefreshStatus: () -> Unit,
    onUseOfflineLocal: () -> Unit,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = AmoledSurface,
            border = BorderStroke(1.dp, AmoledBorder),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.HourglassTop,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Awaiting Owner Approval",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Signed in as ${email.ifBlank { "Google Account" }} ($deviceName). The workspace OWNER must approve your account and assign a role (EDITOR / VIEWER / OWNER) inside Supabase RBAC before cloud content unlocks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Button(
                    onClick = onRefreshStatus,
                    enabled = !isRefreshing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Approval Status")
                }
                OutlinedButton(
                    onClick = onUseOfflineLocal,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch to Offline Local Mode")
                }
                OutlinedButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonDanger),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
    }
}

/**
 * Compact Read-Only (Viewer Role) or Owner-Only Vault Banner rendered cleanly inside guarded screens.
 */
@Composable
fun RoleGuardBanner(
    role: CloudUserRole,
    modifier: Modifier = Modifier,
    onOpenCloudRoles: () -> Unit = {}
) {
    if (role != CloudUserRole.VIEWER && role != CloudUserRole.EDITOR) return
    val isViewer = role == CloudUserRole.VIEWER
    val accent = if (isViewer) AmberWarning else Color(0xFF38BDF8)
    Surface(
        onClick = onOpenCloudRoles,
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isViewer) Icons.Filled.Visibility else Icons.Filled.Security,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isViewer) {
                    "Read-Only (Viewer Role) — Edit, Delete, Render, Git Push & Vault writes are disabled"
                } else {
                    "Editor Role — Content & Tasks unlocked · API Vault & User Admin are Owner Only"
                },
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
