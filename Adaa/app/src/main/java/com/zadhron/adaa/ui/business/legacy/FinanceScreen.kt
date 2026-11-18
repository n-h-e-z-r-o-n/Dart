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
import com.zadhron.adaa.ui.theme.AdaaIcons
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.data.model.AppointmentStatus
import com.zadhron.adaa.data.model.ServiceCategory
import com.zadhron.adaa.ui.theme.CatBrows
import com.zadhron.adaa.ui.theme.CatHair
import com.zadhron.adaa.ui.theme.CatMakeup
import com.zadhron.adaa.ui.theme.CatNails
import com.zadhron.adaa.ui.theme.CatSkincare
import com.zadhron.adaa.ui.theme.PrimaryRose
import com.zadhron.adaa.ui.theme.StatusCompleted
import com.zadhron.adaa.ui.theme.TertiaryBronze
import com.zadhron.adaa.ui.viewmodel.AdaaViewModel

@Composable
fun FinanceScreen(viewModel: AdaaViewModel) {
    val metrics by viewModel.metrics.collectAsState()
    val allAppointments by viewModel.allAppointments.collectAsState()
    FinanceScreen(metrics, allAppointments)
}

@Composable
fun FinanceScreen(
    metrics: com.zadhron.adaa.data.model.FinancialMetrics,
    allAppointments: List<com.zadhron.adaa.data.model.Appointment> = emptyList()
) {

    val completedAppointments = allAppointments.filter { it.status == AppointmentStatus.COMPLETED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "Business & Finances",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Revenue analytics and performance overview",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Main Big Revenue Banner Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryRose)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Lifetime Revenue",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        AdaaIcons.TrendingUp,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "$${String.format("%.2f", metrics.totalRevenue)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "This Month",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                                Text(
                                    text = "$${String.format("%.2f", metrics.monthRevenue)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = "Today",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                                Text(
                                    text = "$${String.format("%.2f", metrics.todayRevenue)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = "Completed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                                Text(
                                    text = "${metrics.completedCount} visits",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Secondary Quick KPI Cards Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricMiniCard(
                        title = "Active Clients",
                        value = "${metrics.totalClients}",
                        icon = Icons.Rounded.People,
                        color = TertiaryBronze,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCard(
                        title = "Upcoming",
                        value = "${metrics.upcomingCount}",
                        icon = AdaaIcons.EventNote,
                        color = Color(0xFF2B79C2),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Revenue by Category Progress Breakdown
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Revenue by Specialty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        val totalRev = if (metrics.totalRevenue > 0) metrics.totalRevenue else 1.0

                        val categoriesWithRevenue = ServiceCategory.entries.map { cat ->
                            val catRev = metrics.categoryBreakdown[cat] ?: 0.0
                            val pct = (catRev / totalRev).toFloat()
                            val color = when (cat) {
                                ServiceCategory.NAILS -> CatNails
                                ServiceCategory.HAIR -> CatHair
                                ServiceCategory.BROWS_LASHES -> CatBrows
                                ServiceCategory.MAKEUP -> CatMakeup
                                ServiceCategory.SKINCARE -> CatSkincare
                                ServiceCategory.OTHER -> Color.Gray
                            }
                            Triple(cat.displayName, catRev, Pair(pct, color))
                        }.sortedByDescending { it.second }

                        categoriesWithRevenue.forEach { (catName, rev, pctAndColor) ->
                            val (pct, color) = pctAndColor
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = catName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "$${String.format("%.2f", rev)} (${(pct * 100).toInt()}%)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { if (pct > 0f) pct else 0.02f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = color,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Recent Completed Visits History
            item {
                Text(
                    text = "Recent Completed Visits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (completedAppointments.isEmpty()) {
                item {
                    Text(
                        text = "No completed visits yet. Mark appointments as completed from the Schedule screen to see income logged here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(completedAppointments.take(10), key = { it.id }) { appt ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = StatusCompleted.copy(alpha = 0.15f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = StatusCompleted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = appt.clientName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${appt.serviceTitle} â€¢ ${appt.formattedDate}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = "+$${appt.price}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusCompleted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricMiniCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Finances · Revenue & KPIs", showBackground = true, showSystemUi = true)
@Composable
fun FinanceScreenOverviewPreview() {
    com.zadhron.adaa.ui.theme.AdaaTheme {
        FinanceScreen(
            metrics = com.zadhron.adaa.ui.preview.PreviewData.sampleMetrics,
            allAppointments = com.zadhron.adaa.ui.preview.PreviewData.sampleAppointments
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Finances · Dark Mode", showBackground = true, showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun FinanceScreenDarkPreview() {
    com.zadhron.adaa.ui.theme.AdaaTheme(darkTheme = true) {
        FinanceScreen(
            metrics = com.zadhron.adaa.ui.preview.PreviewData.sampleMetrics,
            allAppointments = com.zadhron.adaa.ui.preview.PreviewData.sampleAppointments
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Metric KPI Mini Card", showBackground = true)
@Composable
fun MetricMiniCardPreview() {
    com.zadhron.adaa.ui.theme.AdaaTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            MetricMiniCard(
                title = "Active Clients",
                value = "24",
                icon = androidx.compose.material.icons.Icons.Rounded.People,
                color = com.zadhron.adaa.ui.theme.TertiaryBronze
            )
        }
    }
}