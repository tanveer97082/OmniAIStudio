package com.omniai.studio.cloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CloudUserRole(val label: String, val badgeText: String) {
    OWNER("Owner", "OWNER"),
    EDITOR("Editor", "EDITOR"),
    VIEWER("Viewer", "VIEWER"),
    PENDING("Pending Approval", "PENDING"),
    OFFLINE_LOCAL("Offline Local Mode", "OFFLINE LOCAL");

    companion object {
        fun fromServerRole(role: String?, isApproved: Boolean, isOfflineLocal: Boolean): CloudUserRole {
            if (isOfflineLocal) return OFFLINE_LOCAL
            if (!isApproved) return PENDING
            return when (role?.uppercase()?.trim()) {
                "OWNER" -> OWNER
                "EDITOR" -> EDITOR
                "VIEWER" -> VIEWER
                else -> PENDING
            }
        }

        fun fromStoredName(raw: String?): CloudUserRole =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: OFFLINE_LOCAL
    }
}

data class RoleCapabilities(
    val role: CloudUserRole,
    val canReadContent: Boolean,
    val canEditContent: Boolean,
    val canDeleteContent: Boolean,
    val canRunKaggleRender: Boolean,
    val canPushGit: Boolean,
    val canWriteVault: Boolean,
    val canManageUsers: Boolean,
    val isPendingApproval: Boolean,
    val isReadOnlyViewer: Boolean,
    val isOfflineLocal: Boolean
) {
    val statusBadge: String
        get() = when (role) {
            CloudUserRole.OWNER -> "Owner (Full Access)"
            CloudUserRole.EDITOR -> "Editor (Content & Tasks)"
            CloudUserRole.VIEWER -> "Read-Only (Viewer Role)"
            CloudUserRole.PENDING -> "Awaiting Owner Approval"
            CloudUserRole.OFFLINE_LOCAL -> "Offline Local Mode"
        }
}

/**
 * Role-Aware UI & Action Guard synchronized with Supabase server-enforced RBAC.
 * - OWNER & OFFLINE_LOCAL: Full local + cloud studio access (Offline Local stays unblocked offline).
 * - EDITOR: Unlocks Chat, Video Scripts, YT/IG/FB SEO, and Project Tasks; locks API Vault writes & User Admin.
 * - VIEWER: Strict Read-Only mode hiding/disabling Edit, Delete, Kaggle Render, Git Push, and Vault writes.
 * - PENDING: Displays "Awaiting Owner Approval" lock screen until OWNER approves via Supabase RLS.
 */
class RolePermissionGuard(initialRole: CloudUserRole = CloudUserRole.OFFLINE_LOCAL) {

    private val _activeRole = MutableStateFlow(initialRole)
    val activeRole: StateFlow<CloudUserRole> = _activeRole.asStateFlow()

    private val _capabilities = MutableStateFlow(capabilitiesFor(initialRole))
    val capabilities: StateFlow<RoleCapabilities> = _capabilities.asStateFlow()

    val currentRole: CloudUserRole
        get() = _activeRole.value

    val currentCapabilities: RoleCapabilities
        get() = _capabilities.value

    fun updateRole(role: CloudUserRole) {
        _activeRole.value = role
        _capabilities.value = capabilitiesFor(role)
    }

    fun denyReasonForEdit(actionName: String): String? {
        val caps = _capabilities.value
        return when {
            caps.isPendingApproval -> "Account pending Owner approval — $actionName blocked."
            !caps.canEditContent -> "Read-Only (Viewer Role) — $actionName is disabled."
            else -> null
        }
    }

    fun denyReasonForDelete(actionName: String): String? {
        val caps = _capabilities.value
        return when {
            caps.isPendingApproval -> "Account pending Owner approval — $actionName blocked."
            !caps.canDeleteContent -> "Read-Only (Viewer Role) — $actionName is disabled."
            else -> null
        }
    }

    fun denyReasonForVaultWrite(): String? {
        val caps = _capabilities.value
        return when {
            caps.isPendingApproval -> "Account pending Owner approval — Vault changes blocked."
            !caps.canWriteVault -> "Owner Only — ${caps.role.label} role cannot modify API Vault secrets."
            else -> null
        }
    }

    fun denyReasonForRenderOrGit(actionName: String): String? {
        val caps = _capabilities.value
        return when {
            caps.isPendingApproval -> "Account pending Owner approval — $actionName blocked."
            !caps.canRunKaggleRender || !caps.canPushGit ->
                "Read-Only (Viewer Role) — $actionName is disabled."
            else -> null
        }
    }

    fun denyReasonForAdmin(): String? {
        val caps = _capabilities.value
        return if (caps.canManageUsers) null else "Owner Only — Admin Role Manager requires OWNER role."
    }

    companion object {
        fun capabilitiesFor(role: CloudUserRole): RoleCapabilities = when (role) {
            CloudUserRole.OWNER -> RoleCapabilities(
                role = role,
                canReadContent = true,
                canEditContent = true,
                canDeleteContent = true,
                canRunKaggleRender = true,
                canPushGit = true,
                canWriteVault = true,
                canManageUsers = true,
                isPendingApproval = false,
                isReadOnlyViewer = false,
                isOfflineLocal = false
            )
            CloudUserRole.EDITOR -> RoleCapabilities(
                role = role,
                canReadContent = true,
                canEditContent = true,
                canDeleteContent = true,
                canRunKaggleRender = true,
                canPushGit = true,
                canWriteVault = false,
                canManageUsers = false,
                isPendingApproval = false,
                isReadOnlyViewer = false,
                isOfflineLocal = false
            )
            CloudUserRole.VIEWER -> RoleCapabilities(
                role = role,
                canReadContent = true,
                canEditContent = false,
                canDeleteContent = false,
                canRunKaggleRender = false,
                canPushGit = false,
                canWriteVault = false,
                canManageUsers = false,
                isPendingApproval = false,
                isReadOnlyViewer = true,
                isOfflineLocal = false
            )
            CloudUserRole.PENDING -> RoleCapabilities(
                role = role,
                canReadContent = false,
                canEditContent = false,
                canDeleteContent = false,
                canRunKaggleRender = false,
                canPushGit = false,
                canWriteVault = false,
                canManageUsers = false,
                isPendingApproval = true,
                isReadOnlyViewer = false,
                isOfflineLocal = false
            )
            CloudUserRole.OFFLINE_LOCAL -> RoleCapabilities(
                role = role,
                canReadContent = true,
                canEditContent = true,
                canDeleteContent = true,
                canRunKaggleRender = true,
                canPushGit = true,
                canWriteVault = true,
                canManageUsers = false,
                isPendingApproval = false,
                isReadOnlyViewer = false,
                isOfflineLocal = true
            )
        }
    }
}
