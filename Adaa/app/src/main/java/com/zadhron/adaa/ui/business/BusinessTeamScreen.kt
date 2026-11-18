package com.zadhron.adaa.ui.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.ui.shared.*
import com.zadhron.adaa.ui.viewmodel.FoundationState

@Composable
fun BusinessTeamScreen(state: FoundationState, actions: JourneyActions) {
    var pendingUser by remember { mutableStateOf<String?>(null) }
    var pendingAllowed by remember { mutableStateOf(false) }
    JourneyPage("Your people.\nClear permissions.", "Choose which team members can confirm service completion and release escrow.", "TEAM PERMISSIONS", actions::back) {
        InfoPanel("Release permission is separate", "Only the business owner can withdraw wallet funds. Granting escrow release never grants withdrawal access.", Icons.Outlined.Lock)
        state.team.forEach { member ->
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(member.displayName.ifBlank { "Team member / ${member.userId.takeLast(6)}" }, style = MaterialTheme.typography.titleMedium)
                    Text(member.role.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge)
                    if (member.role == "owner") Text("Can release escrow and withdraw funds.", style = MaterialTheme.typography.bodyMedium)
                    else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Can release escrow", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(member.canReleaseEscrow, onCheckedChange = { pendingAllowed = it; pendingUser = member.userId }, enabled = !state.busy)
                    }
                }
            }
        }
        if (state.team.isEmpty() && !state.busy) Text("Team permissions couldn't be loaded. Refresh to try again.")
        SecondaryAction("Refresh team", actions::refresh, !state.busy)
    }
    if (pendingUser != null) AlertDialog(onDismissRequest = { pendingUser = null },
        title = { Text(if (pendingAllowed) "Allow escrow release?" else "Remove escrow release?") },
        text = { Text(if (pendingAllowed) "This member will be able to confirm service completion and make the held payment available to your business. They cannot withdraw funds." else "This member will no longer be able to release held payments. Their other business access stays the same.") },
        confirmButton = { TextButton(onClick = { pendingUser?.let { actions.setEscrowPermission(it, pendingAllowed) }; pendingUser = null }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = { pendingUser = null }) { Text("Cancel") } })
}
