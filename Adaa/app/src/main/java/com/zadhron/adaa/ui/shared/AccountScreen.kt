package com.zadhron.adaa.ui.shared

import com.zadhron.adaa.data.model.AppMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.data.remote.BusinessSummary
import com.zadhron.adaa.ui.viewmodel.FoundationState

@Composable
fun AccountScreen(state: FoundationState, actions: JourneyActions) {
    JourneyPage("A little more you.", "Your profile and account preferences.", "ACCOUNT") {
        if (!state.signedIn) {
            InfoPanel("Make yourself at home", "Sign in to manage your businesses. You can keep exploring without an account.", Icons.Outlined.PersonOutline)
            PrimaryAction("Sign in", actions::showSignIn, enabled = !state.busy)
            SecondaryAction("Create an account", actions::showSignUp, enabled = !state.busy)
        } else {
            InfoPanel("You're signed in", state.identity?.email.orEmpty(), Icons.Outlined.VerifiedUser)
            PrimaryAction("My personal wallet", actions::personalWallet, enabled = !state.busy)
            SecondaryAction("My bookings & inquiries", actions::personalBookings, enabled = !state.busy)
            SecondaryAction("My payment requests", actions::personalPayments, enabled = !state.busy)
            SecondaryAction("Go to my businesses", actions::businessMode, enabled = !state.busy)
            SecondaryAction("Explore as a customer", actions::explore, enabled = !state.busy, icon = Icons.Outlined.Explore)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(if (state.identity?.accountType == AppMode.BUSINESS) "Business account" else if (state.identity?.accountType == AppMode.CUSTOMER) "Personal account" else "Your Adaa account", style = MaterialTheme.typography.titleMedium)
            Text("Your account type sets your starting experience. Business access is controlled separately by your team membership.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var confirm by remember { mutableStateOf(false) }
            TextButton(onClick = { confirm = true }, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.Logout, null); Spacer(Modifier.width(10.dp)); Text("Sign out") }
            if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Sign out of Adaa?") },
                text = { Text("You'll still be able to explore. Sign in again to manage your businesses.") },
                confirmButton = { TextButton(onClick = { confirm = false; actions.signOut() }) { Text("Sign out") } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("Stay signed in") } })
        }
    }
}

