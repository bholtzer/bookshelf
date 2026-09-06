package com.bihstudio.bookshelf.presentation.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bihstudio.bookshelf.BuildConfig
import com.bihstudio.bookshelf.presentation.legal.LegalUrls
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val googleWebClientId = remember(context) { context.googleWebClientId() }
    var googleSignInError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.isSignedIn) {
        if (uiState.isSignedIn) onAuthSuccess()
    }

    // ── Google Sign-In via Credential Manager ─────────────────────────────────
    val credentialManager = remember { CredentialManager.create(context) }

    fun launchGoogleSignIn() {
        if (googleWebClientId.isBlank()) {
            googleSignInError = "Google sign-in needs a Web OAuth client ID. Add GOOGLE_WEB_CLIENT_ID to local.properties or download an updated google-services.json from Firebase after enabling Google sign-in."
            return
        }
        scope.launch {
            try {
                googleSignInError = null
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(googleWebClientId)
                    .setFilterByAuthorizedAccounts(false)   // show all accounts, not just previously used
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(context as Activity, request)
                val credential = result.credential
                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.onGoogleIdTokenReceived(googleCredential.idToken)
                } else {
                    googleSignInError = "Google did not return a usable sign-in credential. Try again with another Google account."
                }
            } catch (e: GetCredentialException) {
                googleSignInError = e.localizedMessage ?: "Google sign-in was cancelled or no Google account is available."
            } catch (e: Exception) {
                googleSignInError = e.localizedMessage ?: "Google sign-in failed. Please try again."
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showPass    by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // Title
            Text(
                text = if (uiState.mode == AuthMode.SIGN_IN) "Welcome back" else "Create account",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "BookShelf — your personal page library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            // Display name (register only)
            AnimatedVisibility(visible = uiState.mode == AuthMode.REGISTER) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                )
            }

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            imageVector = if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPass) "Hide password" else "Show password",
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )

            // Error message
            AnimatedVisibility(visible = uiState.error != null) {
                uiState.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            AnimatedVisibility(visible = googleSignInError != null) {
                googleSignInError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Primary CTA
            Button(
                onClick = {
                    if (uiState.mode == AuthMode.SIGN_IN) {
                        viewModel.onSignIn(email, password)
                    } else {
                        viewModel.onRegister(email, password, displayName)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading,
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(if (uiState.mode == AuthMode.SIGN_IN) "Sign in" else "Create account")
                }
            }

            // Divider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    "  or  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // Google Sign-In
            OutlinedButton(
                onClick = ::launchGoogleSignIn,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading,
            ) {
                Text(
                    if (googleWebClientId.isBlank()) {
                        "Google sign-in not configured"
                    } else {
                        "Continue with Google"
                    }
                )
            }

            // Toggle mode
            TextButton(onClick = viewModel::toggleMode) {
                Text(
                    if (uiState.mode == AuthMode.SIGN_IN)
                        "Don't have an account? Register"
                    else
                        "Already have an account? Sign in"
                )
            }

            Text(
                "By continuing, you agree to the BI.H Terms and acknowledge the Privacy Policy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { context.openLegalUrl(LegalUrls.TERMS) }) { Text("Terms") }
                TextButton(onClick = { context.openLegalUrl(LegalUrls.PRIVACY) }) { Text("Privacy") }
            }
        }
    }
}

private fun Context.openLegalUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun Context.googleWebClientId(): String {
    if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
        return BuildConfig.GOOGLE_WEB_CLIENT_ID
    }
    val resourceId = resources.getIdentifier("default_web_client_id", "string", packageName)
    if (resourceId == 0) return ""
    return runCatching { getString(resourceId) }.getOrDefault("")
}
