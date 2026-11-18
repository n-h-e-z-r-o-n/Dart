package com.zadhron.adaa.ui.shared

interface PaymentActions {
    fun refresh() {}
    fun showServices() {}
    fun showActivity() {}
    fun more(kind: String) {}
    fun beginOperation(direction: String) {}
    fun reviewOperation(amount: String, phone: String) {}
    fun confirmOperation() {}
    fun showOperation(id: String) {}
    fun showCharge(id: String) {}
    fun createServicePage() {}
    fun createService(customer: String, amount: String, description: String) {}
    fun messagePage(action: String) {}
    fun serviceAction(action: String, message: String = "") {}
}
