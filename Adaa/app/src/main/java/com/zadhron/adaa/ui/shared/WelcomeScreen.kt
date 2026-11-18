package com.zadhron.adaa.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.ui.viewmodel.FoundationState

@Composable
fun WelcomeScreen(state: FoundationState, actions: JourneyActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("welcome"),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BrandWordmark(); Spacer(Modifier.weight(1f))
            if (LocalDensity.current.fontScale <= 1.3f) Text("LOCAL CONNECTIONS", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(30.dp)).background(BrandPlum)) {
            StorefrontArtwork(Modifier.fillMaxSize())
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Good things.\nClose to you.", style = MaterialTheme.typography.displayMedium)
            Text("Find businesses you'll love. Or create a place for people to discover yours.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryAction("Explore businesses", actions::explore, enabled = !state.busy)
            SecondaryAction("Set up my business", actions::businessMode, enabled = !state.busy, icon = Icons.Outlined.Storefront)
            Text("You can browse without an account.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account?", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            TextButton(onClick = actions::showSignIn, enabled = !state.busy) { Text("Sign in") }
        }
    }
}

@Composable
fun StartupScreen(state: FoundationState, actions: JourneyActions) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            BrandWordmark()
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                Text("Getting things ready…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Let's reconnect", style = MaterialTheme.typography.headlineSmall)
                Text("We couldn't restore your account right now. Try again, or keep exploring without signing in.",
                    style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                PrimaryAction("Try again", actions::refresh)
                TextButton(onClick = actions::explore) { Text("Browse without signing in") }
            }
        }
    }
}
