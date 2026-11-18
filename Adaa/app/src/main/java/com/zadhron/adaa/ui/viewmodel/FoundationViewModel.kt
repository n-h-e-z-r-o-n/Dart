package com.zadhron.adaa.ui.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.data.remote.*
import com.zadhron.adaa.data.session.AndroidJourneyPreferences
import com.zadhron.adaa.ui.shared.JourneyActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FoundationState(
    val configured: Boolean = false,
    val screen: JourneyScreen = JourneyScreen.EXPLORE,
    val backTarget: JourneyScreen? = null,
    val busy: Boolean = false,
    val signedIn: Boolean = false,
    val identity: SessionIdentity? = null,
    val mode: AppMode = AppMode.CUSTOMER,
    val categories: List<BusinessCategory> = emptyList(),
    val discovered: List<BusinessSummary> = emptyList(),
    val businesses: List<BusinessSummary> = emptyList(),
    val business: BusinessDetails? = null,
    val publicBusiness: BusinessDetails? = null,
    val team: List<BusinessTeamMember> = emptyList(),
    val selectedBusinessId: String? = null,
    val message: String? = null,
    val error: String? = null,
    val query: String = "",
    val hasMore: Boolean = false,
    val email: String = "",
    val draft: BusinessDraft = BusinessDraft(),
    val editingBusinessId: String? = null,
    val fieldErrors: Map<String, String> = emptyMap()
)

class FoundationViewModel(
    private val gateway: JourneyGateway,
    private val preferences: JourneyPreferences,
    private val saved: SavedStateHandle = SavedStateHandle()
) : ViewModel(), JourneyActions {
    private val _state = MutableStateFlow(FoundationState(configured = gateway.configured,
        screen = JourneyScreen.STARTUP, mode = preferences.mode, email = saved["email"] ?: "",
        draft = BusinessDraft(requestId = saved["requestId"] ?: java.util.UUID.randomUUID().toString(),
            name = saved["name"] ?: "", categoryId = saved["category"],
            operatingModel = saved["model"] ?: "physical", location = saved["location"] ?: "",
            description = saved["description"] ?: "")))
    val state = _state.asStateFlow()
    private val lock = Mutex()
    private var authOrigin = JourneyScreen.WELCOME
    private var selectedPublicId: String? = saved["publicId"]

    init { restore() }

    private fun change(update: (FoundationState) -> FoundationState) { _state.value = update(_state.value) }
    private fun go(screen: JourneyScreen, back: JourneyScreen? = null) {
        change { it.copy(screen = screen, backTarget = back, error = null, message = null, fieldErrors = emptyMap()) }
    }
    private fun action(force: Boolean = false, block: suspend () -> Unit) {
        if (_state.value.busy && !force) return
        viewModelScope.launch {
            lock.withLock {
                change { it.copy(busy = true, error = null, message = null) }
                try { block() }
                catch (error: CancellationException) { throw error }
                catch (_: SessionExpiredException) {
                    change { it.copy(signedIn = false, identity = null, businesses = emptyList(), business = null, selectedBusinessId = null,
                        screen = JourneyScreen.SIGN_IN, backTarget = JourneyScreen.EXPLORE,
                        error = "Your session has ended. Sign in to continue.") }
                }
                catch (_: java.io.IOException) { change { it.copy(error = "You're offline or the connection was interrupted. Check your connection, then try again.") } }
                catch (error: Exception) { change { it.copy(error = error.message ?: "Something went wrong. Please try again.") } }
                finally { change { it.copy(busy = false) } }
            }
        }
    }
    private fun restore() = action {
        val identity = gateway.restoreSession()
        if (identity == null) {
            go(if (preferences.welcomed) JourneyScreen.EXPLORE else JourneyScreen.WELCOME)
            if (preferences.welcomed) loadExplore()
        } else {
            bindIdentity(identity)
            afterAuthentication(identity)
        }
    }
    private fun bindIdentity(identity: SessionIdentity) {
        val changedAccount = _state.value.identity?.id != identity.id
        if (changedAccount && identity.accountType != null) {
            preferences.mode = identity.accountType
            change { it.copy(mode = identity.accountType) }
        }
        if (_state.value.draft.name.isNotBlank() && saved.get<String>("draftOwner") != identity.id) clearDraft()
        change { it.copy(identity = identity, signedIn = true,
            businesses = if (changedAccount) emptyList() else it.businesses,
            business = if (changedAccount) null else it.business,
            selectedBusinessId = if (changedAccount) null else it.selectedBusinessId,
            team = if (changedAccount) emptyList() else it.team) }
    }
    private suspend fun afterAuthentication(identity: SessionIdentity) {
        preferences.welcomed = true
        if (identity.passwordRecovery) { go(JourneyScreen.RESET_PASSWORD); return }
        val resumePublic: String? = saved["resumePublic"]
        if (resumePublic != null && saved.get<Boolean>("resumeBookings") != true) {
            selectedPublicId = resumePublic
            go(JourneyScreen.PUBLIC_PROFILE, JourneyScreen.EXPLORE)
            val profile = gateway.publicProfile(resumePublic)
            change { it.copy(publicBusiness = profile) }
            saved.remove<String>("resumePublic")
            return
        }
        if (saved.get<Boolean>("resumeBookings") == true) {
            saved.remove<Boolean>("resumeBookings")
            saved.remove<String>("resumePublic")
            go(JourneyScreen.PERSONAL_BOOKINGS, JourneyScreen.ACCOUNT)
            return
        }
        if (preferences.mode == AppMode.BUSINESS) {
            go(JourneyScreen.BUSINESSES)
            loadMyBusinesses()
            val resume = businessToResume(_state.value.businesses, preferences.selectedBusiness(identity.id))
            if (resume != null) loadDashboard(resume)
        } else {
            go(JourneyScreen.EXPLORE)
            loadExplore()
        }
    }
    private suspend fun loadExplore() {
        val categories = gateway.categories()
        change { it.copy(categories = categories) }
        val results = gateway.businesses(_state.value.query)
        change { it.copy(discovered = results, hasMore = results.size == 20) }
    }
    private suspend fun loadMyBusinesses() {
        val businesses = gateway.businesses(mine = true)
        change { it.copy(businesses = businesses) }
    }
    private suspend fun loadDashboard(id: String) {
        change { it.copy(screen = JourneyScreen.BUSINESS_HOME, backTarget = null, selectedBusinessId = id, business = null) }
        val business = gateway.dashboard(id)
        _state.value.identity?.let { preferences.selectBusiness(it.id, business.id) }
        change { it.copy(business = business) }
    }

    override fun explore() {
        if (_state.value.busy) return
        preferences.welcomed = true; preferences.mode = AppMode.CUSTOMER
        change { it.copy(mode = AppMode.CUSTOMER) }
        go(JourneyScreen.EXPLORE)
        action { loadExplore() }
    }
    override fun businessMode() {
        if (_state.value.busy) return
        preferences.welcomed = true; preferences.mode = AppMode.BUSINESS
        change { it.copy(mode = AppMode.BUSINESS) }
        if (!_state.value.signedIn) { authOrigin = _state.value.screen; go(JourneyScreen.SIGN_IN, authOrigin) }
        else action {
            go(JourneyScreen.BUSINESSES)
            loadMyBusinesses()
            val resume = businessToResume(_state.value.businesses, preferences.selectedBusiness(_state.value.identity!!.id))
            if (resume != null) loadDashboard(resume)
        }
    }
    override fun account() {
        if (!_state.value.busy) go(JourneyScreen.ACCOUNT,
            if (_state.value.mode == AppMode.BUSINESS) JourneyScreen.BUSINESSES else JourneyScreen.EXPLORE)
    }
    override fun showSignIn() {
        if (_state.value.busy) return
        if (_state.value.screen == JourneyScreen.PUBLIC_PROFILE) saved["resumePublic"] = selectedPublicId
        if (_state.value.screen !in listOf(JourneyScreen.SIGN_UP, JourneyScreen.CONFIRM_EMAIL,
            JourneyScreen.FORGOT_PASSWORD, JourneyScreen.RESET_SENT)) authOrigin = _state.value.screen
        go(JourneyScreen.SIGN_IN, authOrigin)
    }
    override fun showSignUp() {
        if (_state.value.busy) return
        if (_state.value.screen == JourneyScreen.PUBLIC_PROFILE) saved["resumePublic"] = selectedPublicId
        if (_state.value.screen !in listOf(JourneyScreen.SIGN_IN, JourneyScreen.SIGN_UP, JourneyScreen.ACCOUNT_TYPE)) authOrigin = _state.value.screen
        go(JourneyScreen.ACCOUNT_TYPE, authOrigin)
    }
    override fun selectAccountType(type: AppMode) {
        if (_state.value.busy) return
        preferences.mode = type
        change { it.copy(mode = type) }
        go(JourneyScreen.SIGN_UP, JourneyScreen.ACCOUNT_TYPE)
    }
    override fun back() {
        if (_state.value.busy) return
        val target = _state.value.backTarget ?: return
        if (_state.value.screen == JourneyScreen.SIGN_IN) {
            saved.remove<String>("resumePublic"); saved.remove<Boolean>("resumeBookings")
        }
        go(target, when (target) {
            JourneyScreen.BUSINESS_BASICS -> if (_state.value.editingBusinessId != null) JourneyScreen.BUSINESS_HOME else JourneyScreen.BUSINESSES
            JourneyScreen.BUSINESS_LOCATION -> JourneyScreen.BUSINESS_BASICS
            JourneyScreen.SIGN_IN -> authOrigin
            JourneyScreen.PUBLIC_PROFILE -> JourneyScreen.EXPLORE
            else -> null
        })
    }
    override fun authenticate(email: String, password: String, register: Boolean) = action {
        val normalized = email.trim()
        saved["email"] = normalized
        change { it.copy(email = normalized) }
        val result = if (register) gateway.register(normalized, password, _state.value.mode)
            else gateway.authenticate(normalized, password, false)
        if (result.needsConfirmation) go(JourneyScreen.CONFIRM_EMAIL, JourneyScreen.SIGN_IN)
        else result.identity?.let { identity ->
            bindIdentity(identity)
            afterAuthentication(identity)
        }
    }
    override fun showConfirmation(email: String) {
        if (email.isBlank()) return
        saved["email"] = email.trim()
        change { it.copy(email = email.trim()) }
        go(JourneyScreen.CONFIRM_EMAIL, JourneyScreen.SIGN_IN)
    }
    override fun resendConfirmation() = action {
        gateway.resendConfirmation(_state.value.email)
        change { it.copy(message = "If your account needs confirmation, a fresh email is on its way. Check your spam folder too.") }
    }
    override fun forgotPassword() { if (!_state.value.busy) go(JourneyScreen.FORGOT_PASSWORD, JourneyScreen.SIGN_IN) }
    override fun requestPasswordReset(email: String) = action {
        saved["email"] = email.trim(); change { it.copy(email = email.trim()) }
        gateway.requestPasswordReset(email.trim())
        go(JourneyScreen.RESET_SENT, JourneyScreen.SIGN_IN)
    }
    fun handleAuthLink(link: String) = action(force = true) {
        val identity = gateway.exchangeAuthLink(link)
        bindIdentity(identity)
        afterAuthentication(identity)
    }
    override fun updatePassword(password: String) = action {
        gateway.updatePassword(password)
        val identity = _state.value.identity!!.copy(passwordRecovery = false)
        change { it.copy(identity = identity) }
        afterAuthentication(identity)
        change { it.copy(message = "Your password has been updated.") }
    }
    override fun signOut() = action {
        var disconnected = false
        try { gateway.signOut() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { disconnected = true }
        finally {
            preferences.mode = AppMode.CUSTOMER
            saved.remove<String>("resumePublic"); saved.remove<Boolean>("resumeBookings")
            clearDraft()
            change { FoundationState(configured = gateway.configured, screen = JourneyScreen.EXPLORE,
                busy = true, discovered = it.discovered, categories = it.categories) }
        }
        change { it.copy(message = if (disconnected) "You're signed out on this device. We couldn't reach the server to finish ending the session." else "You're signed out on this device.") }
    }
    override fun search(query: String) = action {
        change { it.copy(query = query.trim(), discovered = emptyList(), hasMore = false) }; loadExplore()
    }
    override fun loadMore() = action {
        val next = gateway.businesses(_state.value.query, offset = _state.value.discovered.size)
        change { it.copy(discovered = (it.discovered + next).distinctBy { b -> b.id }, hasMore = next.size == 20) }
    }
    override fun openPublicBusiness(id: String) = action {
        selectedPublicId = id
        saved["publicId"] = id
        go(JourneyScreen.PUBLIC_PROFILE, JourneyScreen.EXPLORE)
        change { it.copy(publicBusiness = null) }
        val profile = gateway.publicProfile(id)
        change { it.copy(publicBusiness = profile) }
    }
    override fun openBusiness(id: String) = action { loadDashboard(id) }
    override fun chooseBusiness() {
        if (_state.value.busy) return
        go(JourneyScreen.BUSINESSES)
        action { loadMyBusinesses() }
    }
    override fun startSetup() {
        if (_state.value.busy) return
        if (!_state.value.signedIn) { businessMode(); return }
        if (_state.value.editingBusinessId != null) clearDraft()
        go(JourneyScreen.BUSINESS_BASICS, JourneyScreen.BUSINESSES)
        if (_state.value.categories.isEmpty()) action { val categories = gateway.categories(); change { it.copy(categories = categories) } }
    }
    override fun updateDraft(value: BusinessDraft) {
        if (_state.value.busy) return
        saved["requestId"] = value.requestId; saved["name"] = value.name; saved["category"] = value.categoryId
        saved["model"] = value.operatingModel; saved["location"] = value.location; saved["description"] = value.description
        saved["draftOwner"] = _state.value.identity?.id
        change { it.copy(draft = value, fieldErrors = emptyMap()) }
    }
    override fun nextSetup() {
        val state = _state.value
        val errors = mutableMapOf<String, String>()
        when (state.screen) {
            JourneyScreen.BUSINESS_BASICS -> {
                if (state.draft.name.trim().length !in 1..160) errors["name"] = "Enter a business name, up to 160 characters."
                if (state.categories.none { it.id == state.draft.categoryId }) errors["category"] = "Choose a category to help people find you."
                if (state.draft.description.length > 4000) errors["description"] = "Keep the description under 4,000 characters."
                if (errors.isEmpty()) go(JourneyScreen.BUSINESS_LOCATION, JourneyScreen.BUSINESS_BASICS)
            }
            JourneyScreen.BUSINESS_LOCATION -> {
                if (state.draft.operatingModel != "online" && state.draft.location.trim().length !in 1..500)
                    errors["location"] = "Enter your business address or service area, up to 500 characters."
                if (errors.isEmpty()) go(JourneyScreen.BUSINESS_REVIEW, JourneyScreen.BUSINESS_LOCATION)
            }
            else -> Unit
        }
        if (errors.isNotEmpty()) change { it.copy(fieldErrors = errors) }
    }
    override fun submitBusiness() = action {
        val current = _state.value
        val id = current.editingBusinessId ?: gateway.createBusiness(current.draft)
        if (current.editingBusinessId != null) gateway.updateBusiness(id, current.draft)
        clearDraft()
        // Navigate as soon as the mutation succeeds. A failed refresh cannot cause
        // a second create; retries of an uncertain create use the same request ID.
        change { it.copy(selectedBusinessId = id, screen = JourneyScreen.BUSINESS_HOME, backTarget = null, business = null) }
        loadDashboard(id)
        loadMyBusinesses()
        change { it.copy(message = if (current.editingBusinessId == null) "Your draft is ready. Review your listing and publish when you're ready." else "Your business details have been saved.") }
    }
    override fun editBusiness() {
        val business = _state.value.business ?: return
        if (business.role !in listOf("owner", "manager") || _state.value.busy) return
        change { it.copy(editingBusinessId = business.id) }
        updateDraft(BusinessDraft(name = business.name, categoryId = business.categoryId,
            operatingModel = business.operatingModel, location = business.location, description = business.description))
        go(JourneyScreen.BUSINESS_BASICS, JourneyScreen.BUSINESS_HOME)
        if (_state.value.categories.isEmpty()) action { val categories = gateway.categories(); change { it.copy(categories = categories) } }
    }
    private fun clearDraft() {
        listOf("requestId", "name", "category", "model", "location", "description", "draftOwner").forEach { saved.remove<Any>(it) }
        change { it.copy(draft = BusinessDraft(), editingBusinessId = null, fieldErrors = emptyMap()) }
    }
    override fun previewBusiness() {
        if (!_state.value.busy && _state.value.business != null) go(JourneyScreen.BUSINESS_DETAILS, JourneyScreen.BUSINESS_HOME)
    }
    override fun personalWallet() {
        if (_state.value.busy) return
        if (!_state.value.signedIn) { showSignIn(); return }
        go(JourneyScreen.PERSONAL_WALLET, JourneyScreen.ACCOUNT)
    }
    override fun personalPayments() {
        if (_state.value.busy) return
        if (!_state.value.signedIn) { showSignIn(); return }
        go(JourneyScreen.PERSONAL_PAYMENTS, JourneyScreen.ACCOUNT)
    }
    override fun personalBookings() {
        if (_state.value.busy) return
        if (!_state.value.signedIn) { saved["resumeBookings"] = true; showSignIn(); return }
        go(JourneyScreen.PERSONAL_BOOKINGS, if (_state.value.screen == JourneyScreen.PUBLIC_PROFILE) JourneyScreen.PUBLIC_PROFILE else JourneyScreen.ACCOUNT)
    }
    override fun businessBookings() {
        if (_state.value.busy || _state.value.business == null) return
        go(JourneyScreen.BUSINESS_BOOKINGS, JourneyScreen.BUSINESS_HOME)
    }
    override fun businessWallet() {
        if (_state.value.busy || _state.value.business?.role != "owner") return
        go(JourneyScreen.BUSINESS_WALLET, JourneyScreen.BUSINESS_HOME)
    }
    override fun businessPayments() {
        if (_state.value.busy || _state.value.business == null) return
        go(JourneyScreen.BUSINESS_PAYMENTS, JourneyScreen.BUSINESS_HOME)
    }
    override fun openTeam() {
        val business = _state.value.business ?: return
        if (business.role != "owner" || _state.value.busy) return
        go(JourneyScreen.BUSINESS_TEAM, JourneyScreen.BUSINESS_HOME)
        change { it.copy(team = emptyList()) }
        action { val team = gateway.businessTeam(business.id); change { it.copy(team = team) } }
    }
    override fun setEscrowPermission(userId: String, allowed: Boolean) = action {
        val business = _state.value.business ?: return@action
        if (business.role != "owner") return@action
        gateway.setEscrowPermission(business.id, userId, allowed)
        val team = gateway.businessTeam(business.id)
        change { it.copy(team = team, message = "Escrow-release permission updated.") }
    }
    override fun publishBusiness() = action {
        val id = _state.value.selectedBusinessId ?: return@action
        gateway.publishBusiness(id)
        loadDashboard(id)
        change { it.copy(message = "You're live. Customers can now discover your business on Adaa.") }
    }
    override fun dismissMessage() { change { it.copy(error = null, message = null) } }
    override fun refresh() {
        if (_state.value.screen == JourneyScreen.STARTUP) { restore(); return }
        action {
            when (_state.value.screen) {
                JourneyScreen.BUSINESS_TEAM -> _state.value.business?.let { b -> val team = gateway.businessTeam(b.id); change { it.copy(team = team) } }
                JourneyScreen.BUSINESSES -> loadMyBusinesses()
                JourneyScreen.BUSINESS_HOME -> _state.value.selectedBusinessId?.let { loadDashboard(it) }
                JourneyScreen.PUBLIC_PROFILE -> selectedPublicId?.let { id -> val profile = gateway.publicProfile(id); change { it.copy(publicBusiness = profile) } }
                JourneyScreen.BUSINESS_BASICS -> { val categories = gateway.categories(); change { it.copy(categories = categories) } }
                else -> loadExplore()
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { FoundationViewModel(SupabaseRepository.shared(context), AndroidJourneyPreferences(context), createSavedStateHandle()) }
        }
    }
}
