package com.zadhron.adaa.ui.shared

import com.zadhron.adaa.data.model.BusinessDraft
import com.zadhron.adaa.data.model.AppMode

/** Presentation callbacks keep individual screens independent of ViewModels and transport. */
interface JourneyActions {
    fun explore() {}
    fun businessMode() {}
    fun account() {}
    fun back() {}
    fun showSignIn() {}
    fun showSignUp() {}
    fun selectAccountType(type: AppMode) {}
    fun authenticate(email: String, password: String, register: Boolean) {}
    fun showConfirmation(email: String) {}
    fun resendConfirmation() {}
    fun forgotPassword() {}
    fun requestPasswordReset(email: String) {}
    fun updatePassword(password: String) {}
    fun signOut() {}
    fun refresh() {}
    fun search(query: String) {}
    fun loadMore() {}
    fun openPublicBusiness(id: String) {}
    fun openBusiness(id: String) {}
    fun chooseBusiness() {}
    fun startSetup() {}
    fun updateDraft(value: BusinessDraft) {}
    fun nextSetup() {}
    fun submitBusiness() {}
    fun editBusiness() {}
    fun previewBusiness() {}
    fun publishBusiness() {}
    fun openTeam() {}
    fun personalWallet() {}
    fun personalPayments() {}
    fun personalBookings() {}
    fun businessBookings() {}
    fun businessWallet() {}
    fun businessPayments() {}
    fun setEscrowPermission(userId: String, allowed: Boolean) {}
    fun dismissMessage() {}
}
