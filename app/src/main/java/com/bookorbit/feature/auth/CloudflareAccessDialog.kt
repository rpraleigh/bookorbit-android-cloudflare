package com.bookorbit.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bookorbit.R

/** Why the token dialog is open; decides the explanation shown and whether a new secret is required. */
enum class CloudflareDialogReason { MANAGE, REJECTED, BLOCKED }

/**
 * Listens for Cloudflare Access turning requests away and offers to fix the token. Place once in the
 * signed-in part of the app. (While signed out, the login screen offers a link to the server screen.)
 */
@Composable
fun CloudflareAccessPrompt(vm: CloudflareAccessViewModel = hiltViewModel()) {
    var reason by remember { mutableStateOf<CloudflareDialogReason?>(null) }
    LaunchedEffect(vm) {
        vm.rejections.collect { configured ->
            reason = if (configured) CloudflareDialogReason.REJECTED else CloudflareDialogReason.BLOCKED
        }
    }
    reason?.let { CloudflareAccessDialog(reason = it, onDismiss = { reason = null }, vm = vm) }
}

@Composable
fun CloudflareAccessDialog(
    reason: CloudflareDialogReason,
    onDismiss: () -> Unit,
    vm: CloudflareAccessViewModel = hiltViewModel(),
) {
    val savedId by vm.clientId.collectAsState()
    var clientId by remember { mutableStateOf(savedId.orEmpty()) }
    // Never saved to instance state; a blank secret keeps the saved one (except after a rejection).
    var secret by remember { mutableStateOf("") }
    var secretMissing by remember { mutableStateOf(false) }
    val requireNewSecret = reason == CloudflareDialogReason.REJECTED

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cf_access_settings_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        when (reason) {
                            CloudflareDialogReason.MANAGE -> R.string.cf_access_dialog_manage
                            CloudflareDialogReason.REJECTED -> R.string.cf_access_dialog_rejected
                            CloudflareDialogReason.BLOCKED -> R.string.cf_access_dialog_blocked
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.cf_access_client_id)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = {
                        secret = it
                        secretMissing = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.cf_access_client_secret)) },
                    placeholder = if (savedId != null && !requireNewSecret) {
                        { Text(stringResource(R.string.cf_access_secret_keep)) }
                    } else {
                        null
                    },
                    isError = secretMissing,
                    supportingText = if (secretMissing) {
                        { Text(stringResource(R.string.cf_access_dialog_secret_required)) }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                )
                if (savedId != null && reason == CloudflareDialogReason.MANAGE) {
                    TextButton(onClick = {
                        vm.remove()
                        onDismiss()
                    }) { Text(stringResource(R.string.cf_access_remove)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = clientId.isNotBlank(),
                onClick = {
                    if (vm.save(clientId, secret, requireNewSecret)) onDismiss() else secretMissing = true
                },
            ) { Text(stringResource(R.string.cf_access_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(
                        if (reason == CloudflareDialogReason.MANAGE) R.string.cancel else R.string.cf_access_not_now,
                    ),
                )
            }
        },
    )
}
