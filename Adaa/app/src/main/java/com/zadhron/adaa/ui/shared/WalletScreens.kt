package com.zadhron.adaa.ui.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.zadhron.adaa.ui.viewmodel.PaymentState

@Composable
fun WalletOverview(state: PaymentState, actions: PaymentActions, onBack: () -> Unit, business: Boolean) {
    val clipboard = LocalClipboardManager.current
    JourneyPage(if (business) "Your business wallet." else "Your money.\nYour wallet.",
        "Add money with M-Pesa, pay inside Adaa, and track every movement.", if (business) "BUSINESS WALLET" else "PERSONAL WALLET", onBack) {
        val wallet = state.wallet
        if (wallet != null) {
            MoneyTile("Available balance", wallet.availableMinor)
            if (business) MoneyTile("Held in escrow", wallet.escrowMinor) else MoneyTile("Held for services", wallet.heldForServicesMinor)
            MoneyTile("Pending withdrawals", wallet.withdrawalPendingMinor)
        } else if (!state.busy) InfoPanel("Balances unavailable", "Refresh to load your wallet. Amounts are never estimated.")
        val policy = state.policy
        if (policy?.active != true) InfoPanel("Payments are not active", "Deposits and withdrawals are unavailable until the payment rules and gateway are activated. Your history and existing service requests remain accessible.", Icons.Outlined.Lock)
        if (!business) PrimaryAction("Deposit with M-Pesa", { actions.beginOperation("deposit") }, enabled = !state.busy && policy?.active == true)
        if (state.scope.canWithdraw) SecondaryAction("Withdraw to M-Pesa", { actions.beginOperation("withdrawal") }, enabled = !state.busy && policy?.active == true && wallet?.withdrawalsBlocked != true)
        if (wallet?.withdrawalsBlocked == true) InfoPanel("Withdrawals are on hold", "An open payment dispute needs platform review. You can follow the case from Service payments.", Icons.Outlined.Lock)
        if (policy?.configured == true) InfoPanel("Your payment rules", "Per request: ${paymentMoney(policy.minMinor ?: 0)} to ${paymentMoney(policy.maxMinor ?: 0)}. Daily external limit: ${paymentMoney(policy.dailyMinor ?: 0)}. Adaa fee: ${paymentMoney(policy.feeMinor)}. Your M-Pesa provider may charge separately.")
        SecondaryAction(if (business) "Service payments" else "My payment requests", actions::showServices, !state.busy)
        SecondaryAction("Wallet statement", actions::showActivity, !state.busy)
        if (!business) {
            InfoPanel("Your customer payment code", "Share this code with a business to receive a service payment request. Sharing it does not authorize a charge.")
            Text(state.scope.userId, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { clipboard.setText(AnnotatedString(state.scope.userId)) }) { Text("Copy payment code") }
        }
        Text("Deposits & withdrawals", style = MaterialTheme.typography.titleLarge)
        if (state.operations.isEmpty() && !state.busy) Text("No wallet requests yet.", style = MaterialTheme.typography.bodyMedium)
        state.operations.forEach { operation ->
            OutlinedCard(onClick = { actions.showOperation(operation.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${operation.direction.replaceFirstChar { it.uppercase() }} / ${paymentMoney(operation.amountMinor)}", style = MaterialTheme.typography.titleMedium)
                    Text(paymentStatus(operation.status), style = MaterialTheme.typography.bodyMedium)
                    Text(paymentDate(operation.createdAt), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (state.moreOperations) SecondaryAction("Older requests", { actions.more("operations") }, !state.busy)
        SecondaryAction("Refresh wallet", actions::refresh, !state.busy)
    }
}

@Composable
fun MoneyTile(label: String, amount: Long) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(paymentMoney(amount), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun WalletOperationForm(state: PaymentState, actions: PaymentActions, onBack: () -> Unit) {
    var amount by rememberSaveable(state.direction) { mutableStateOf(state.amount) }
    var phone by rememberSaveable(state.direction) { mutableStateOf(state.phone) }
    val deposit = state.direction == "deposit"
    JourneyPage(if (deposit) "Add money\nto your wallet." else "Move money\nto M-Pesa.",
        if (deposit) "You'll approve the deposit on your phone. Your wallet is credited only after payment is confirmed." else "Only available funds can be withdrawn. Review the destination carefully.",
        if (deposit) "DEPOSIT / 1 OF 2" else "WITHDRAW / 1 OF 2", onBack) {
        FormField(amount, { amount = it }, "Amount in KES", helper = "Use whole shillings for M-Pesa.", enabled = !state.busy, tag = "wallet_amount", inputType = KeyboardType.Decimal)
        FormField(phone, { phone = it }, if (deposit) "M-Pesa phone number" else "Recipient M-Pesa number", helper = "For example: 0712345678 or +254712345678", enabled = !state.busy, tag = "wallet_phone", inputType = KeyboardType.Phone)
        PrimaryAction("Review ${if (deposit) "deposit" else "withdrawal"}", { actions.reviewOperation(amount, phone) }, busy = state.busy)
        InfoPanel("Check existing requests first", "If a previous request is still processing, open it from your wallet rather than starting another one.")
    }
}

@Composable
fun WalletOperationReview(state: PaymentState, actions: PaymentActions, onBack: () -> Unit) {
    val quote = state.quote ?: return
    val deposit = state.direction == "deposit"
    JourneyPage("Review before\nyou confirm.", "Make sure the amount and M-Pesa number are correct.", "PAYMENT / 2 OF 2", onBack) {
        MoneyTile(if (deposit) "Deposit amount" else "Withdrawal amount", quote.amountMinor)
        DetailRow("M-Pesa number", state.phone, Icons.Outlined.PhoneAndroid)
        DetailRow("Adaa fee", paymentMoney(quote.feeMinor), Icons.Outlined.Receipt)
        if (!deposit) DetailRow("Total reserved", paymentMoney(quote.totalDebitMinor), Icons.Outlined.AccountBalanceWallet)
        InfoPanel(if (deposit) "Approve on your phone" else "Funds are reserved first", if (deposit) "After you confirm, approve the M-Pesa prompt. A pending prompt is not a confirmed deposit." else "This amount leaves your available balance while the payout processes. An uncertain payout remains on hold for review.")
        PrimaryAction(if (deposit) "Send M-Pesa deposit prompt" else "Confirm withdrawal", actions::confirmOperation, busy = state.busy)
    }
}

@Composable
fun WalletOperationScreen(state: PaymentState, actions: PaymentActions, onBack: () -> Unit) {
    val operation = state.selectedOperation ?: state.operations.firstOrNull { it.id == state.operationId }
    JourneyPage(operation?.let { paymentStatus(it.status) } ?: "Request submitted.",
        "Your request has its own reference. Refresh its status instead of submitting another payment.", "WALLET REQUEST", onBack) {
        if (operation != null) {
            MoneyTile(operation.direction.replaceFirstChar { it.uppercase() }, operation.amountMinor)
            DetailRow("M-Pesa number", "Ending ${operation.phoneSuffix}", Icons.Outlined.PhoneAndroid)
            Text(paymentDate(operation.createdAt), style = MaterialTheme.typography.bodyMedium)
            if (operation.receipt.isNotBlank()) InfoPanel("M-Pesa receipt", operation.receipt, Icons.Outlined.Receipt)
            InfoPanel("What happens next", when (operation.status) {
                "succeeded" -> "This transaction is confirmed and recorded in your wallet statement."
                "failed" -> "This request did not complete. Check your statement before trying again."
                "review" -> "This request needs reconciliation. Withdrawal funds remain reserved until the outcome is verified."
                else -> "We're waiting for the payment provider. An accepted request is not a confirmed payment."
            })
        }
        PrimaryAction("Refresh status", actions::refresh, busy = state.busy)
    }
}

@Composable
fun WalletActivityScreen(state: PaymentState, actions: PaymentActions, onBack: () -> Unit) {
    JourneyPage("Every movement.\nOne statement.", "Transfers between available funds and escrow are shown together.", "WALLET STATEMENT", onBack) {
        state.movements.forEach { movement ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(paymentStatus(movement.kind), style = MaterialTheme.typography.titleMedium)
                    Text(paymentDate(movement.createdAt), style = MaterialTheme.typography.labelMedium)
                    movement.entries.forEach { Text("${paymentStatus(it.bucket)}: ${paymentMoney(it.amountMinor)}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        if (state.movements.isEmpty() && !state.busy) InfoPanel("No wallet movements yet", "Confirmed deposits, service payments, releases, refunds and withdrawals will appear here.")
        if (state.moreActivity) SecondaryAction("Older movements", { actions.more("activity") }, !state.busy)
        SecondaryAction("Refresh statement", actions::refresh, !state.busy)
    }
}
