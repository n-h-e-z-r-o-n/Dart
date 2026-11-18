package com.zadhron.adaa.ui.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.zadhron.adaa.ui.viewmodel.PaymentState

@Composable
fun ServicePaymentsScreen(state: PaymentState, actions: PaymentActions, onBack: () -> Unit) {
    JourneyPage(if (state.scope.isBusiness) "Services &\npayments." else "Your payment\nrequests.",
        if (state.scope.isBusiness) "Create requests, confirm completed services, and follow payment outcomes." else "A business can request payment using your customer payment code. You decide whether to pay.",
        if (state.scope.isBusiness) "BUSINESS PAYMENTS" else "PERSONAL PAYMENTS", onBack) {
        if (state.scope.canManage) PrimaryAction("Create payment request", actions::createServicePage, enabled = !state.busy)
        state.services.forEach { charge ->
            OutlinedCard(onClick = { actions.showCharge(charge.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.scope.isBusiness) charge.customerName.ifBlank { "Customer" } else charge.businessName, style = MaterialTheme.typography.titleMedium)
                    Text(charge.description, style = MaterialTheme.typography.bodyMedium)
                    Text(paymentMoney(charge.amountMinor), style = MaterialTheme.typography.headlineSmall)
                    Text(paymentStatus(charge.status), style = MaterialTheme.typography.labelLarge)
                    if (charge.externalReported && charge.status == "requested") Text("Direct payment reported / awaiting confirmation", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.services.isEmpty() && !state.busy) InfoPanel("No payment requests yet", if (state.scope.isBusiness) "Create a request for an agreed service. The customer must authorize the payment." else "Share your payment code from your wallet with the business. Their request will appear here.")
        if (state.moreServices) SecondaryAction("Older requests", { actions.more("services") }, !state.busy)
        SecondaryAction("Refresh requests", actions::refresh, !state.busy)
    }
}

@Composable
fun CreateServicePaymentScreen(state: PaymentState, actions: PaymentActions, onBack: () -> Unit) {
    var customer by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var review by remember { mutableStateOf(false) }
    JourneyPage("An agreed service.\nA clear request.", "The customer reviews and authorizes this amount. Creating a request does not charge their wallet.", "CREATE PAYMENT REQUEST", onBack) {
        FormField(customer, { customer = it }, "Customer payment code", helper = "Ask the customer to copy it from their Adaa wallet.", enabled = !state.busy, tag = "charge_customer", inputType = KeyboardType.Ascii)
        FormField(description, { description = it }, "Service description", enabled = !state.busy, tag = "charge_description")
        FormField(amount, { amount = it }, "Agreed price in KES", enabled = !state.busy, tag = "charge_amount", inputType = KeyboardType.Decimal)
        PrimaryAction("Review payment request", { review = true }, enabled = !state.busy)
    }
    if (review) AlertDialog(onDismissRequest = { review = false }, title = { Text("Create this payment request?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(description); Text(parsePaymentAmount(amount)?.let(::paymentMoney) ?: "Check the entered amount."); Text("The customer has not paid yet.") } },
        confirmButton = { TextButton(onClick = { review = false; actions.createService(customer, amount, description) }) { Text("Create request") } },
        dismissButton = { TextButton(onClick = { review = false }) { Text("Go back") } })
}

@Composable
fun ServicePaymentScreen(state: PaymentState, actions: PaymentActions, onBack: () -> Unit) {
    var confirmation by remember { mutableStateOf<String?>(null) }
    val charge = state.charge
    JourneyPage(charge?.let { paymentStatus(it.status) } ?: "Payment details.", charge?.businessName ?: "Refresh to load the latest payment state.", "SERVICE PAYMENT", onBack) {
        if (charge != null) {
            MoneyTile("Agreed service amount", charge.amountMinor)
            Text(charge.description, style = MaterialTheme.typography.titleMedium)
            Text(paymentDate(charge.createdAt), style = MaterialTheme.typography.bodyMedium)
            when (charge.status) {
                "requested" -> {
                    if (!state.scope.isBusiness) {
                        if (!charge.externalReported) {
                            PrimaryAction("Pay from my wallet", { confirmation = "pay" }, enabled = !state.busy)
                            SecondaryAction("I paid directly outside Adaa", { actions.messagePage("external") }, !state.busy)
                        } else {
                            InfoPanel("Direct payment reported", "The business must verify receipt. No money has moved through your Adaa wallet.")
                            SecondaryAction("Withdraw my payment report", { actions.messagePage("withdraw_report") }, !state.busy)
                        }
                    } else if (charge.externalReported) {
                        InfoPanel("Verify the external payment", "Check the business till or cash receipt before confirming. Adaa cannot verify money paid outside the wallet.")
                        if (charge.canRelease) PrimaryAction("Confirm external payment received", { confirmation = "confirm_external" }, enabled = !state.busy)
                        if (state.scope.canManage) SecondaryAction("Reject the payment report", { actions.messagePage("withdraw_report") }, !state.busy)
                    }
                    if (!state.scope.isBusiness || state.scope.canManage) TextButton(onClick = { confirmation = "cancel" }, enabled = !state.busy) { Text("Cancel unpaid request") }
                }
                "funded" -> {
                    InfoPanel("Payment is held in escrow", "The money is reserved for this service. It becomes available to the business after authorized completion.", Icons.Outlined.Lock)
                    if (state.scope.isBusiness && charge.canRelease) PrimaryAction("Confirm service completed", { confirmation = "complete" }, enabled = !state.busy)
                    if (state.scope.isBusiness && charge.canRefund) SecondaryAction("Refund held payment", { confirmation = "refund" }, !state.busy)
                    if (!state.scope.isBusiness) SecondaryAction("Raise a payment dispute", { actions.messagePage("dispute") }, !state.busy)
                }
                "completed" -> {
                    InfoPanel("Service confirmed", "The service payment is now available in the business wallet. This is separate from an M-Pesa withdrawal.", Icons.Outlined.CheckCircle)
                    if (state.scope.isBusiness && charge.canRefund) SecondaryAction("Refund service payment", { confirmation = "refund" }, !state.busy)
                    if (!state.scope.isBusiness) SecondaryAction("Raise a payment dispute", { actions.messagePage("dispute") }, !state.busy)
                }
                "disputed" -> {
                    InfoPanel("This payment is under review", "Escrow release is blocked and new business withdrawals are held while the dispute is open. A platform reviewer decides the outcome.")
                    charge.dispute?.let { Text(it.reason, style = MaterialTheme.typography.bodyLarge) }
                    if (!state.scope.isBusiness || state.scope.canManage) SecondaryAction("Add information to this dispute", { actions.messagePage("respond") }, !state.busy)
                }
                "external_paid" -> InfoPanel("Paid outside Adaa", "The business confirmed a direct payment. It is not part of the Adaa wallet balance or wallet statement.")
                "refunded" -> InfoPanel("Payment refunded", "The refund is recorded in the customer's Adaa wallet statement.")
            }
            charge.dispute?.resolution?.takeIf { it.isNotBlank() }?.let { InfoPanel("Review outcome", it) }
            if (charge.events.isNotEmpty()) {
                Text("Payment timeline", style = MaterialTheme.typography.titleLarge)
                charge.events.forEach { event ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(paymentStatus(event.action), style = MaterialTheme.typography.titleSmall)
                        Text(paymentDate(event.createdAt) + event.actorName.takeIf { it.isNotBlank() }?.let { " / $it" }.orEmpty(), style = MaterialTheme.typography.labelMedium)
                        if (event.message.isNotBlank()) Text(event.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        SecondaryAction("Refresh payment", actions::refresh, !state.busy)
    }
    confirmation?.let { operation ->
        val title = when (operation) { "pay" -> "Pay into escrow?"; "complete" -> "Confirm service completion?"; "refund" -> "Refund this payment?"; "cancel" -> "Cancel unpaid request?"; else -> "Confirm external receipt?" }
        val message = when (operation) {
            "pay" -> "${charge?.amountMinor?.let(::paymentMoney)} will move from your available wallet balance into escrow for this service."
            "complete" -> "Confirm only after the service is provided. This releases the held payment to your business's available balance and records your action."
            "refund" -> "The full service amount returns to the customer's Adaa wallet. A completed service refund needs enough available business funds."
            "cancel" -> "This unpaid request will be cancelled. This action does not reverse money paid outside Adaa."
            else -> "Confirm that the business actually received this payment outside Adaa. This does not add money to its Adaa wallet."
        }
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(title) }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { confirmation = null; actions.serviceAction(operation) }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Go back") } })
    }
}

@Composable
fun PaymentMessageScreen(state: PaymentState, actions: PaymentActions, onBack: () -> Unit) {
    var message by rememberSaveable(state.messageAction, state.charge?.id) { mutableStateOf("") }
    val external = state.messageAction == "external"
    JourneyPage(when (state.messageAction) { "external" -> "Report your\ndirect payment."; "respond" -> "Add your side\nof the story."; "withdraw_report" -> "Correct the\npayment report."; else -> "Let's review\nthis payment." },
        if (external) "Enter the till or cash payment reference. The business will verify it; this does not credit an Adaa wallet." else "Provide clear details for the payment record. Do not include passwords or M-Pesa PINs.",
        if (external) "EXTERNAL PAYMENT" else "PAYMENT REVIEW", onBack) {
        FormField(message, { message = it }, if (external) "Payment reference" else "Details", singleLine = external, enabled = !state.busy, tag = "payment_message")
        if (state.messageAction == "dispute") InfoPanel("What happens next", "The payment enters review. Authorized platform support decides whether to refund the customer or release funds to the business. Completed-service disputes must be within the configured window.")
        PrimaryAction(if (external) "Report payment" else "Submit details", { actions.serviceAction(state.messageAction, message) }, busy = state.busy)
    }
}
