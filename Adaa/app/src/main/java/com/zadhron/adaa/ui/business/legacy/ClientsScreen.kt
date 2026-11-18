package com.zadhron.adaa.ui.business.legacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.data.model.Client
import com.zadhron.adaa.ui.components.ClientDialog
import com.zadhron.adaa.ui.theme.PrimaryRose
import com.zadhron.adaa.ui.viewmodel.AdaaViewModel

@Composable
fun ClientsScreen(viewModel: AdaaViewModel) {
    val clients by viewModel.clients.collectAsState()
    val searchQuery by viewModel.clientSearchQuery.collectAsState()
    val isClientDialogVisible by viewModel.isClientDialogVisible.collectAsState()
    ClientsScreen(clients, searchQuery, isClientDialogVisible,
        { viewModel.isClientDialogVisible.value = it }, viewModel::setClientSearchQuery,
        viewModel::deleteClient, viewModel::addClient)
}

@Composable
fun ClientsScreen(
    clients: List<Client>, searchQuery: String = "", isClientDialogVisible: Boolean = false,
    onDialogVisible: (Boolean) -> Unit = {}, onSearch: (String) -> Unit = {},
    onDelete: (Long) -> Unit = {}, onAdd: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> }
) {

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onDialogVisible(true) },
                containerColor = PrimaryRose,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Client")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = "Client Book",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${clients.size} active clients",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { onSearch(it) },
                    placeholder = { Text("Search clients by name, phone...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearch("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (clients.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryRose.copy(alpha = 0.12f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = PrimaryRose,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Text(
                            text = if (searchQuery.isBlank()) "No Clients Yet" else "No matching clients",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "Tap the + button to add your first client profile." else "Try a different search term.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(clients, key = { it.id }) { client ->
                        ClientCard(
                            client = client,
                            onDelete = { onDelete(client.id) }
                        )
                    }
                }
            }
        }
    }

    if (isClientDialogVisible) {
        ClientDialog(
            onDismiss = { onDialogVisible(false) },
            onConfirm = { name, phone, email, notes, colorHex ->
                onAdd(name, phone, email, notes, colorHex)
            }
        )
    }
}

@Composable
fun ClientCard(
    client: Client,
    onDelete: () -> Unit
) {
    val avatarColor = try {
        Color(android.graphics.Color.parseColor(client.colorHex))
    } catch (e: Exception) {
        PrimaryRose
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circle Avatar with initials
                Surface(
                    shape = CircleShape,
                    color = avatarColor,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = client.initials,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = client.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = client.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (client.email.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Mail,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = client.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (client.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = client.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete client",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Clients Screen · List", showBackground = true, showSystemUi = true)
@Composable
fun ClientsScreenListPreview() {
    com.zadhron.adaa.ui.theme.AdaaTheme {
        ClientsScreen(
            clients = com.zadhron.adaa.ui.preview.PreviewData.sampleClients,
            searchQuery = ""
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Clients Screen · Empty", showBackground = true, showSystemUi = true)
@Composable
fun ClientsScreenEmptyPreview() {
    com.zadhron.adaa.ui.theme.AdaaTheme {
        ClientsScreen(
            clients = emptyList(),
            searchQuery = ""
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Client Card", showBackground = true)
@Composable
fun ClientCardItemPreview() {
    com.zadhron.adaa.ui.theme.AdaaTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ClientCard(
                client = com.zadhron.adaa.ui.preview.PreviewData.sampleClients[0],
                onDelete = {}
            )
        }
    }
}