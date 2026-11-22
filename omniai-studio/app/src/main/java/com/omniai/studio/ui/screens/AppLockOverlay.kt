package com.omniai.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.omniai.studio.security.PinVerificationResult
import com.omniai.studio.ui.theme.AmoledBlack
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary

@Composable
fun AppLockOverlay(
    hasPinConfigured: Boolean = false,
    onUnlockClick: () -> Unit,
    onVerifyPin: (String) -> PinVerificationResult = { PinVerificationResult.NOT_CONFIGURED }
) {
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Fingerprint,
            contentDescription = "Locked",
            tint = EmeraldAccent,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "OmniAI-Studio Locked",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Authenticate with Fingerprint, Face, or PIN to access encrypted sessions and vault credentials.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        if (hasPinConfigured) {
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = pinInput,
                onValueChange = { raw ->
                    pinInput = raw.filter { it.isDigit() }.take(10)
                    pinError = null
                },
                label = { Text("Enter Access PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (pinError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = pinError.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = CrimsonDanger
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        when (onVerifyPin(pinInput)) {
                            PinVerificationResult.MASTER, PinVerificationResult.DECOY -> {
                                pinInput = ""
                                pinError = null
                            }
                            PinVerificationResult.INVALID -> {
                                pinError = "Incorrect PIN. Attempt logged."
                                pinInput = ""
                            }
                            PinVerificationResult.AUTO_WIPE_TRIGGERED -> {
                                pinError = "Max failed PIN attempts reached — emergency auto-wipe executed."
                                pinInput = ""
                            }
                            PinVerificationResult.NOT_CONFIGURED -> {
                                pinError = "No PIN configured."
                            }
                        }
                    },
                    enabled = pinInput.length >= 4,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unlock PIN")
                }
                OutlinedButton(
                    onClick = onUnlockClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Biometric")
                }
            }
        } else {
            Spacer(modifier = Modifier.height(28.dp))
            Button(onClick = onUnlockClick) {
                Text("Unlock Studio")
            }
        }
    }
}
