package com.bihstudio.madafim.presentation.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bihstudio.madafim.domain.model.AppResult
import com.bihstudio.madafim.domain.usecase.auth.DeleteAccountUseCase
import com.bihstudio.madafim.domain.usecase.auth.GetCurrentUserUseCase
import com.bihstudio.madafim.domain.usecase.auth.SignOutUseCase
import com.bihstudio.madafim.presentation.legal.LegalUrls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val displayName: String = "",
    val email: String = "",
    val isDeleting: Boolean = false,
    val error: String? = null,
    val signedOut: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    getCurrentUser: GetCurrentUserUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {
    private val user = getCurrentUser()
    private val _state = MutableStateFlow(AccountUiState(user?.displayName.orEmpty(), user?.email.orEmpty()))
    val state = _state.asStateFlow()

    fun signOut() = viewModelScope.launch {
        when (val result = signOutUseCase()) {
            is AppResult.Success -> _state.update { it.copy(signedOut = true) }
            is AppResult.Error -> _state.update { it.copy(error = result.message) }
        }
    }

    fun deletePermanently() = viewModelScope.launch {
        _state.update { it.copy(isDeleting = true, error = null) }
        when (val result = deleteAccount()) {
            is AppResult.Success -> _state.update { it.copy(isDeleting = false, signedOut = true) }
            is AppResult.Error -> _state.update { it.copy(isDeleting = false, error = result.message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account & privacy") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
            Text(state.displayName.ifBlank { "MaDaFim account" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (state.email.isNotBlank()) Text(state.email, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))

            AccountAction(Icons.Default.WorkspacePremium, "MaDaFim Pro", "View plans and benefits", onUpgrade)
            AccountAction(Icons.Default.ManageAccounts, "Manage subscription", "Cancel or manage billing in Google Play", onClick = { context.openUrl(LegalUrls.MANAGE_SUBSCRIPTIONS) })
            HorizontalDivider()
            AccountAction(Icons.Default.PrivacyTip, "Privacy Policy", "How BI.H handles your data", onClick = { context.openUrl(LegalUrls.PRIVACY) })
            AccountAction(Icons.Default.Description, "Terms and Conditions", "Rules for using MaDaFim", onClick = { context.openUrl(LegalUrls.TERMS) })
            AccountAction(Icons.Default.DeleteOutline, "Online deletion information", "Delete without access to the app", onClick = { context.openUrl(LegalUrls.DELETE_ACCOUNT) })
            HorizontalDivider()
            AccountAction(Icons.Default.Logout, "Sign out", "Keep cloud data and sign out on this device", viewModel::signOut)
            AccountAction(Icons.Default.DeleteForever, "Delete account and data", "Permanently removes your account, books, pages and cloud files", { confirmDelete = true }, destructive = true)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { if (!state.isDeleting) confirmDelete = false },
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Permanently delete account?") },
        text = { Text("This permanently deletes your BI.H MaDaFim account, owned books, pages, uploaded files, covers and invitations. Shared access will be removed. This cannot be undone. If Google requires recent authentication, sign in again and retry.") },
        confirmButton = {
            Button(onClick = viewModel::deletePermanently, enabled = !state.isDeleting, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                if (state.isDeleting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Delete permanently")
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !state.isDeleting) { Text("Cancel") } },
    )
}

@Composable
private fun AccountAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, destructive: Boolean = false) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
