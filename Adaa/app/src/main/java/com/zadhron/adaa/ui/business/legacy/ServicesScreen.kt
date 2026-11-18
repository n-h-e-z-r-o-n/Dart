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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.zadhron.adaa.data.model.Service
import com.zadhron.adaa.data.model.ServiceCategory
import com.zadhron.adaa.ui.components.ServiceDialog
import com.zadhron.adaa.ui.theme.PrimaryRose
import com.zadhron.adaa.ui.viewmodel.AdaaViewModel

@Composable
fun ServicesScreen(viewModel: AdaaViewModel) {
    val services by viewModel.services.collectAsState()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsState()
    val isServiceDialogVisible by viewModel.isServiceDialogVisible.collectAsState()
    ServicesScreen(services, selectedCategory, isServiceDialogVisible,
        { viewModel.isServiceDialogVisible.value = it }, viewModel::setCategoryFilter,
        viewModel::deleteService, viewModel::addService)
}

@Composable
fun ServicesScreen(
    services: List<Service>, selectedCategory: ServiceCategory? = null, isServiceDialogVisible: Boolean = false,
    onDialogVisible: (Boolean) -> Unit = {}, onCategory: (ServiceCategory?) -> Unit = {},
    onDelete: (Long) -> Unit = {}, onAdd: (String, ServiceCategory, Double, Int) -> Unit = { _, _, _, _ -> }
) {

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onDialogVisible(true) },
                containerColor = PrimaryRose,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Service")
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
                    text = "Service Catalog",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${services.size} services available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Category Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { onCategory(null) },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryRose.copy(alpha = 0.2f),
                            selectedLabelColor = PrimaryRose
                        )
                    )
                }
                items(ServiceCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { onCategory(cat) },
                        label = { Text(cat.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryRose.copy(alpha = 0.2f),
                            selectedLabelColor = PrimaryRose
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (services.isEmpty()) {
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
                                    Icons.Rounded.Spa,
                                    contentDescription = null,
                                    tint = PrimaryRose,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Text(
                            text = "No Services in this Category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap the + button to add a custom service to your catalog.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(services, key = { it.id }) { service ->
                        ServiceCard(
                            service = service,
                            onDelete = { onDelete(service.id) }
                        )
                    }
                }
            }
        }
    }

    if (isServiceDialogVisible) {
        ServiceDialog(
            onDismiss = { onDialogVisible(false) },
            onConfirm = { title, cat, price, duration ->
                onAdd(title, cat, price, duration)
            }
        )
    }
}

@Composable
fun ServiceCard(
    service: Service,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = service.category.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryRose,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = service.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${service.durationMinutes} minutes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$${service.price}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete service",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Services Screen · Catalog", showBackground = true, showSystemUi = true)
@Composable
fun ServicesScreenCatalogPreview() {
    com.zadhron.adaa.ui.theme.AdaaTheme {
        ServicesScreen(
            services = com.zadhron.adaa.ui.preview.PreviewData.sampleServices,
            selectedCategory = null
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Services Screen · Nails Filter", showBackground = true, showSystemUi = true)
@Composable
fun ServicesScreenNailsFilterPreview() {
    com.zadhron.adaa.ui.theme.AdaaTheme {
        ServicesScreen(
            services = com.zadhron.adaa.ui.preview.PreviewData.sampleServices.filter { it.category == com.zadhron.adaa.data.model.ServiceCategory.NAILS },
            selectedCategory = com.zadhron.adaa.data.model.ServiceCategory.NAILS
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Service Card", showBackground = true)
@Composable
fun ServiceCardItemPreview() {
    com.zadhron.adaa.ui.theme.AdaaTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ServiceCard(
                service = com.zadhron.adaa.ui.preview.PreviewData.sampleServices[0],
                onDelete = {}
            )
        }
    }
}