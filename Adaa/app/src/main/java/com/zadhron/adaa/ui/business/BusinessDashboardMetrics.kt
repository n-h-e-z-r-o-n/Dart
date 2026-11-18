package com.zadhron.adaa.ui.business

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.data.model.BusinessDetails
import com.zadhron.adaa.ui.shared.InfoPanel

/** Only authoritative workspace counts are shown. Payment totals require a reconciled ledger. */
@Composable
fun BusinessDashboardMetrics(business: BusinessDetails) {
    if (business.role == "owner") {
        val wallet = business.wallet
        if (wallet == null) InfoPanel("Business wallet", business.walletIssue ?: "Not connected yet. Available funds, money in escrow and pending withdrawals will appear here when your wallet is activated.", Icons.Outlined.AccountBalanceWallet)
        else {
            Text("Your business wallet", style = MaterialTheme.typography.titleLarge)
            WorkspaceMetric("Available balance", walletMoney(wallet.availableMinor), Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkspaceMetric("Held in escrow", walletMoney(wallet.escrowMinor), Modifier.weight(1f))
                WorkspaceMetric("Pending withdrawals", walletMoney(wallet.withdrawalPendingMinor), Modifier.weight(1f))
            }
            Text("Escrow becomes available when an owner or authorized employee confirms service completion. Withdrawals move available funds out through M-Pesa.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkspaceMetric("Completed services / all time", wallet.completedServices.toString(), Modifier.weight(1f))
                WorkspaceMetric("Services in escrow", wallet.heldServices.toString(), Modifier.weight(1f))
            }
            WorkspaceMetric("Completed service value / all time", walletMoney(wallet.completedServiceValueMinor), Modifier.fillMaxWidth())
            if (!wallet.paymentsActive) InfoPanel("Payments are not active", "Deposits and withdrawals will become available once wallet payments are activated.", Icons.Outlined.AccountBalanceWallet)
            if (wallet.recentTransactions.isNotEmpty()) {
                Text("Recent wallet movements", style = MaterialTheme.typography.titleLarge)
                wallet.recentTransactions.forEach { entry ->
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.kind.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                            Text("${entry.bucket.replace('_', ' ')} / ${walletMoney(entry.amountMinor)}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
    Text("Workspace overview", style = MaterialTheme.typography.titleLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WorkspaceMetric("Team members", business.membersCount.toString(), Modifier.weight(1f))
        WorkspaceMetric("Business locations", business.locationsCount.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun WorkspaceMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = if (value.length > 8) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun walletMoney(minor: Long): String = NumberFormat.getCurrencyInstance().apply {
    currency = Currency.getInstance("KES")
}.format(BigDecimal.valueOf(minor, 2))
