package com.bihstudio.madafim.presentation.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
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
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bihstudio.madafim.presentation.legal.LegalUrls
import com.bihstudio.madafim.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
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

    BackHandler(enabled = uiState.modeHistory.isNotEmpty()) {
        viewModel.onBack()
    }

    LaunchedEffect(uiState.isSignedIn) {
        if (uiState.isSignedIn) onAuthSuccess()
    }

    // ── Google Sign-In via Credential Manager ─────────────────────────────────
    val credentialManager = remember { CredentialManager.create(context) }

    fun launchGoogleSignIn() {
        if (!viewModel.onGoogleSignInStarted()) return
        if (googleWebClientId.isBlank()) {
            viewModel.onGoogleCredentialFailure("configuration_missing", "Google sign-in is unavailable right now. Please sign in with email.")
            return
        }
        scope.launch {
            try {
                val googleIdOption = GetSignInWithGoogleOption.Builder(googleWebClientId)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.onGoogleIdTokenReceived(googleCredential.idToken)
                } else {
                    viewModel.onGoogleCredentialFailure("invalid_credential", "Google did not return a usable sign-in credential. Please try again.")
                }
            } catch (e: GetCredentialCancellationException) {
                // Google Play services also reports provider failures (including failed
                // account reauthentication) as cancellation. Do not silently dismiss them.
                viewModel.onGoogleCredentialFailure(
                    "cancelled",
                    "Google sign-in wasn't completed. Try again or choose another account. If this keeps happening after selecting an account, please contact support.",
                )
            } catch (e: NoCredentialException) {
                viewModel.onGoogleCredentialFailure("no_credential", "No Google account is available. Add a Google account on your device and try again.")
            } catch (e: GetCredentialException) {
                viewModel.onGoogleCredentialFailure("credential_failure", "Google sign-in could not start. Please try again or sign in with email.")
            } catch (e: CancellationException) {
                viewModel.onGoogleCredentialFailure("cancelled")
                throw e
            } catch (e: Exception) {
                viewModel.onGoogleCredentialFailure("credential_failure", "Google sign-in failed. Please try again.")
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showPass    by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val authTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        cursorColor = Color.Black,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = Color(0xFF3F484A),
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        // Shrink decoration and spacing when the keyboard reduces the available height.
        val compact = maxHeight < 600.dp
        val showBranding = maxHeight >= 500.dp
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = if (compact) 8.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
        ) {

            if (showBranding) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = "MaDaFim logo",
                    modifier = Modifier
                        .size(if (compact) 56.dp else 80.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Fit,
                )

                // Title
                Text(
                    text = if (uiState.mode == AuthMode.SIGN_IN) "Welcome back" else "Create account",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "MaDaFim — your personal page library",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // Display name (register only)
            if (uiState.mode == AuthMode.REGISTER) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = authTextFieldColors,
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
                colors = authTextFieldColors,
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
                colors = authTextFieldColors,
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
            if (uiState.error != null) {
                uiState.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

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
                if (uiState.isLoading && !uiState.isGoogleSignInInProgress) {
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
                if (uiState.isGoogleSignInInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    if (uiState.isGoogleSignInInProgress) {
                        "Connecting to Google…"
                    } else if (googleWebClientId.isBlank()) {
                        "Google sign-in not configured"
                    } else {
                        "Continue with Google"
                    }
                )
            }

            // Toggle mode
            TextButton(onClick = viewModel::toggleMode, enabled = !uiState.isLoading) {
                Text(
                    if (uiState.mode == AuthMode.SIGN_IN)
                        "Don't have an account? Register"
                    else
                        "Already have an account? Sign in"
                )
            }

            if (showBranding) {
                Text(
                    "By continuing, you agree to the BI.H Terms and acknowledge the Privacy Policy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { viewModel.trackExternalLink("terms"); context.openLegalUrl(LegalUrls.TERMS) }) { Text("Terms") }
                    TextButton(onClick = { viewModel.trackExternalLink("privacy"); context.openLegalUrl(LegalUrls.PRIVACY) }) { Text("Privacy") }
                }
            }
        }
    }
}

private fun Context.openLegalUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun Context.googleWebClientId(): String {
    val resourceId = resources.getIdentifier("default_web_client_id", "string", packageName)
    if (resourceId == 0) return ""
    return runCatching { getString(resourceId) }.getOrDefault("")
}
