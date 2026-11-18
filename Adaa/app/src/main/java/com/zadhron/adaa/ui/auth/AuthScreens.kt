package com.zadhron.adaa.ui.auth

import com.zadhron.adaa.ui.shared.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.data.model.AppMode
import com.zadhron.adaa.ui.viewmodel.FoundationState

@Composable
fun SignInScreen(state: FoundationState, actions: JourneyActions, register: Boolean) {
    var email by rememberSaveable { mutableStateOf(state.email) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val validEmail = email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
    val focus = LocalFocusManager.current
    JourneyPage(if (register) "Your next chapter\nstarts here." else "Welcome back.",
        if (state.mode == AppMode.BUSINESS) "${if (register) "Create an account" else "Sign in"} to set up or manage your business."
        else if (register) "Create your personal account to discover local businesses." else "Sign in to pick up where you left off.",
        eyebrow = if (register) "CREATE ACCOUNT / 2 OF 2" else "SIGN IN", onBack = actions::back) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField(email, { email = it }, "Email address", email = true, enabled = !state.busy,
                error = if (submitted && !validEmail) "Enter a valid email address." else null, tag = "auth_email")
            FormField(password, { password = it }, "Password", password = true, enabled = !state.busy,
                helper = if (register) "Use at least 8 characters. A longer password is stronger." else null,
                error = if (submitted && (password.isBlank() || register && password.length < 8))
                    if (register) "Use at least 8 characters." else "Enter your password." else null, tag = "auth_password")
            if (register) FormField(confirmation, { confirmation = it }, "Confirm password", password = true,
                enabled = !state.busy, error = if (submitted && confirmation != password) "Your passwords don't match." else null,
                tag = "auth_confirm_password")
            else TextButton(onClick = actions::forgotPassword, enabled = !state.busy, modifier = Modifier.align(Alignment.End)) {
                Text("Forgot password?")
            }
        }
        PrimaryAction(if (register) "Create account" else "Sign in", onClick = {
            submitted = true
            if (validEmail && password.isNotBlank() && (!register || password.length >= 8 && confirmation == password)) {
                focus.clearFocus(); actions.authenticate(email, password, register)
                submitted = false
            }
        }, busy = state.busy)
        if (register) {
            InfoPanel(if (state.mode == AppMode.BUSINESS) "Business account" else "Personal account",
                if (state.mode == AppMode.BUSINESS) "Next: set up your business. Team permissions are managed separately." else "Your space to discover businesses. Booking features are coming later.", Icons.Outlined.PersonOutline)
            TextButton(onClick = actions::showSignUp, enabled = !state.busy) { Text("Change account type") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(if (register) "Already with us?" else "New to Adaa?", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            TextButton(onClick = if (register) actions::showSignIn else actions::showSignUp, enabled = !state.busy) {
                Text(if (register) "Sign in" else "Create account")
            }
        }
        if (!register) TextButton(onClick = { submitted = true; if (validEmail) actions.showConfirmation(email) },
            enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Need to confirm your email?") }
    }
}

@Composable
fun EmailConfirmationScreen(state: FoundationState, actions: JourneyActions, recovery: Boolean) {
    JourneyPage("Check your inbox.",
        if (recovery) "If an account exists for ${state.email}, we've sent a password reset link."
        else "We've sent a confirmation link to ${state.email}. Open it to activate your account.",
        eyebrow = if (recovery) "RESET PASSWORD" else "CONFIRM YOUR EMAIL", onBack = actions::back) {
        Surface(Modifier.align(Alignment.CenterHorizontally).size(100.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MarkEmailRead, null, Modifier.size(40.dp)) }
        }
        InfoPanel("Open the link on this phone", "Return to Adaa from the email to continue securely. Check your spam folder if it doesn't arrive.", Icons.Outlined.PhoneAndroid)
        if (!recovery) PrimaryAction("I've confirmed my email · Sign in", actions::showSignIn, enabled = !state.busy)
        else InfoPanel("Didn't return to the app?", "Request a fresh link here and open it on this phone. Your reset link works only with the device that requested it.", Icons.Outlined.LockReset)
        SecondaryAction(if (recovery) "Send another reset email" else "Resend confirmation email",
            if (recovery) ({ actions.requestPasswordReset(state.email) }) else actions::resendConfirmation, enabled = !state.busy)
        TextButton(onClick = actions::showSignIn, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (recovery) "Back to sign in" else "Use a different email")
        }
    }
}

@Composable
fun ForgotPasswordScreen(state: FoundationState, actions: JourneyActions) {
    var email by rememberSaveable { mutableStateOf(state.email) }
    var submitted by remember { mutableStateOf(false) }
    val valid = email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
    JourneyPage("Let's get you\nback in.", "Enter your account email and we'll send you a secure reset link.",
        "ACCOUNT RECOVERY", actions::back) {
        FormField(email, { email = it }, "Email address", email = true, enabled = !state.busy,
            error = if (submitted && !valid) "Enter a valid email address." else null)
        PrimaryAction("Send reset link", { submitted = true; if (valid) actions.requestPasswordReset(email) }, busy = state.busy)
        TextButton(onClick = actions::showSignIn, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
    }
}

@Composable
fun ResetPasswordScreen(state: FoundationState, actions: JourneyActions) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    JourneyPage("A fresh start.", "Choose a new password for your Adaa account.", "NEW PASSWORD") {
        FormField(password, { password = it }, "New password", helper = "Use at least 8 characters.", password = true,
            enabled = !state.busy, error = if (submitted && password.length < 8) "Use at least 8 characters." else null)
        FormField(confirmation, { confirmation = it }, "Confirm new password", password = true,
            enabled = !state.busy, error = if (submitted && password != confirmation) "Your passwords don't match." else null)
        PrimaryAction("Save new password", {
            submitted = true
            if (password.length >= 8 && confirmation == password) actions.updatePassword(password)
        }, busy = state.busy)
        TextButton(onClick = actions::signOut, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel and sign out") }
    }
}
