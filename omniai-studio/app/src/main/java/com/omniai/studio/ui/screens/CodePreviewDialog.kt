package com.omniai.studio.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omniai.studio.ui.theme.AmberWarning
import com.omniai.studio.ui.theme.AmoledBlack
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary
import java.io.ByteArrayInputStream

/**
 * Full-screen Compose Dialog hosting a hardened Android WebView for offline execution
 * of AI-generated HTML/CSS/JS mini-apps and tools via loadDataWithBaseURL().
 *
 * Security hardening:
 * - allowFileAccess = false
 * - allowContentAccess = false
 * - allowFileAccessFromFileURLs = false
 * - allowUniversalAccessFromFileURLs = false
 * - External network requests are blocked by default unless explicitly toggled on.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodePreviewDialog(
    htmlCode: String,
    onDismiss: () -> Unit
) {
    var allowNetwork by remember { mutableStateOf(false) }
    var reloadNonce by remember { mutableIntStateOf(0) }

    val normalizedHtml = remember(htmlCode) {
        val trimmed = htmlCode.trim()
        if (trimmed.contains("<html", ignoreCase = true) || trimmed.contains("<!doctype", ignoreCase = true)) {
            trimmed
        } else {
            """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <style>
                body { background: #0A0A0F; color: #F4F4F5; font-family: sans-serif; padding: 14px; margin: 0; }
              </style>
            </head>
            <body>
            $trimmed
            </body>
            </html>
            """.trimIndent()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = AmoledBlack,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AmoledSurface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = if (allowNetwork) AmberWarning else EmeraldAccent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Live HTML/CSS/JS Preview", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (allowNetwork) "Sandbox JS · Network Allowed" else "Hardened Offline Sandbox (No Network/File Access)",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (allowNetwork) AmberWarning else EmeraldAccent
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { reloadNonce++ }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reload Preview")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Preview")
                        }
                    }
                }

                // Network Isolation Toggle Strip
                Surface(
                    color = AmoledSurface,
                    border = BorderStroke(1.dp, AmoledBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Allow External CDN / Network Requests",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Switch(
                            checked = allowNetwork,
                            onCheckedChange = {
                                allowNetwork = it
                                reloadNonce++
                            }
                        )
                    }
                }

                HorizontalDivider(color = AmoledBorder)

                // Hardened Sandboxed WebView
                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            @Suppress("DEPRECATION")
                            settings.allowFileAccessFromFileURLs = false
                            @Suppress("DEPRECATION")
                            settings.allowUniversalAccessFromFileURLs = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        }
                    },
                    update = { webView ->
                        val networkAllowed = allowNetwork
                        webView.settings.blockNetworkLoads = !networkAllowed
                        webView.webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val uri = request?.url ?: return null
                                val host = uri.host.orEmpty()
                                val isLocalOrigin = host.equals("omniai.local", ignoreCase = true) ||
                                    uri.scheme.equals("data", ignoreCase = true) ||
                                    uri.scheme.equals("about", ignoreCase = true)
                                if (!networkAllowed && !isLocalOrigin) {
                                    return WebResourceResponse(
                                        "text/plain",
                                        "utf-8",
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        if (webView.tag != reloadNonce) {
                            webView.tag = reloadNonce
                            webView.loadDataWithBaseURL(
                                "https://omniai.local/",
                                normalizedHtml,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    }
                )
            }
        }
    }
}
