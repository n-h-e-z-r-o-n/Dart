package com.zadhron.adaa.ui.preview

import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.ui.viewmodel.PaymentState

/** Design-time fixtures only. No preview values are used by repositories or runtime navigation. */
object PaymentPreviewData {
    val personal = WalletScope("11111111-1111-1111-1111-111111111111")
    val business = WalletScope("22222222-2222-2222-2222-222222222222", "preview-business", "owner")
    val policy = WalletPolicy(2, true, false, 10000, 5000000, 10000000, 72, 0)
    val wallet = WalletSummary("KES", 125000, 80000, 20000, 4, 2, 300000, false, emptyList(), 80000)
    val charge = ServicePayment("preview-charge", "preview-business", "The Garden Studio", personal.userId, "Alex",
        "Studio haircut & styling", 150000, "funded", "2026-11-04T10:00:00Z", true, true, false,
        events = listOf(PaymentEvent("funded", "", "2026-11-04T10:05:00Z", "Alex")))
    val state = PaymentState(personal, ready = true, policy = policy, wallet = wallet, services = listOf(charge), charge = charge,
        chargeId = charge.id, operations = listOf(GatewayOperation("preview-deposit", "deposit", 50000, "5678", "pending", "", "2026-11-04T10:00:00Z", "")))
}
