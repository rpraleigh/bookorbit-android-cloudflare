package com.bookorbit.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.R
import com.bookorbit.ui.asString

/** Self-service account creation (shown only when the server reports that registration is open). */
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: RegisterViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords are kept out of saved instance state.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }

    val usernameProblem = RegistrationRules.usernameProblem(username)
    val nameProblem = RegistrationRules.nameProblem(name)
    val emailProblem = RegistrationRules.emailProblem(email)
    val passwordProblem = RegistrationRules.passwordProblem(password)
    val confirmProblem = RegistrationRules.confirmProblem(password, confirm)
    val formValid = listOf(usernameProblem, nameProblem, emailProblem, passwordProblem, confirmProblem).all { it == null }
    val editable = !ui.submitting && !ui.accountCreated

    fun submit() {
        attempted = true
        if (formValid) vm.register(username, name, email, password)
    }

    fun problemText(problem: Int?): (@Composable () -> Unit)? =
        if (attempted && problem != null) {
            { Text(stringResource(problem)) }
        } else {
            null
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.register_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.register_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = editable,
            label = { Text(stringResource(R.string.username)) },
            isError = attempted && usernameProblem != null,
            supportingText = problemText(usernameProblem),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = editable,
            label = { Text(stringResource(R.string.register_display_name)) },
            isError = attempted && nameProblem != null,
            supportingText = problemText(nameProblem),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = editable,
            label = { Text(stringResource(R.string.register_email)) },
            isError = attempted && emailProblem != null,
            supportingText = problemText(emailProblem),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = editable,
            label = { Text(stringResource(R.string.password)) },
            isError = attempted && passwordProblem != null,
            supportingText = if (attempted && passwordProblem != null) {
                { Text(stringResource(passwordProblem)) }
            } else {
                { Text(stringResource(R.string.register_password_rules)) }
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = editable,
            label = { Text(stringResource(R.string.register_confirm_password)) },
            isError = attempted && confirmProblem != null,
            supportingText = problemText(confirmProblem),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )

        ui.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it.asString(),
                color = if (ui.accountCreated) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        if (!ui.accountCreated) {
            Button(
                onClick = { submit() },
                enabled = !ui.submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (ui.submitting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.register_create_account))
                }
            }
        }
        TextButton(
            onClick = onBack,
            enabled = !ui.submitting,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text(stringResource(R.string.register_back_to_sign_in)) }
    }
}
