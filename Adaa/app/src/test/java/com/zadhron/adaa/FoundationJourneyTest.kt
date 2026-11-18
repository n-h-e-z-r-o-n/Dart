package com.zadhron.adaa

import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.data.remote.BusinessCategory
import com.zadhron.adaa.data.remote.BusinessSummary
import com.zadhron.adaa.ui.viewmodel.FoundationViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FoundationJourneyTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = SessionIdentity("owner", "owner@example.com")
    private lateinit var gateway: FakeGateway
    private lateinit var preferences: MemoryPreferences

    @Before fun prepare() { Dispatchers.setMain(dispatcher); gateway = FakeGateway(); preferences = MemoryPreferences() }
    @After fun finish() { Dispatchers.resetMain() }
    private fun model() = FoundationViewModel(gateway, preferences).also { dispatcher.scheduler.advanceUntilIdle() }
    private fun settle() = dispatcher.scheduler.advanceUntilIdle()

    @Test fun firstVisitOffersGuestDiscoveryWithoutAuthentication() {
        val vm = model()
        assertEquals(JourneyScreen.WELCOME, vm.state.value.screen)
        vm.explore(); settle()
        assertEquals(JourneyScreen.EXPLORE, vm.state.value.screen)
        assertFalse(vm.state.value.signedIn)
        assertTrue(preferences.welcomed)
        assertEquals(0, gateway.authCalls)
    }
    @Test fun newBusinessUserGetsAuthenticationThenASeparateWorkspaceEntry() {
        val vm = model(); vm.businessMode(); settle()
        assertEquals(JourneyScreen.SIGN_IN, vm.state.value.screen)
        assertEquals(AppMode.BUSINESS, preferences.mode)
        vm.showSignUp(); vm.selectAccountType(AppMode.BUSINESS); gateway.authResult = AuthenticationResult(needsConfirmation = true)
        vm.authenticate(owner.email, "long password", true); settle()
        assertEquals(JourneyScreen.CONFIRM_EMAIL, vm.state.value.screen)
        assertFalse(vm.state.value.signedIn)
        vm.showSignIn(); gateway.authResult = AuthenticationResult(owner)
        vm.authenticate(owner.email, "long password", false); settle()
        assertEquals(JourneyScreen.BUSINESSES, vm.state.value.screen)
        vm.startSetup(); settle()
        assertEquals(JourneyScreen.BUSINESS_BASICS, vm.state.value.screen)
    }
    @Test fun returningCustomerResumesExploreWithRestoredIdentity() {
        preferences.welcomed = true; gateway.restored = owner
        val vm = model()
        assertEquals(JourneyScreen.EXPLORE, vm.state.value.screen)
        assertEquals(owner, vm.state.value.identity)
        assertTrue(vm.state.value.signedIn)
    }
    @Test fun returningBusinessMemberResumesOnlyAnAuthorizedRememberedBusiness() {
        preferences.mode = AppMode.BUSINESS; preferences.selectBusiness(owner.id, "two")
        gateway.restored = owner
        gateway.mine = listOf(summary("one"), summary("two"))
        val vm = model()
        assertEquals(JourneyScreen.BUSINESS_HOME, vm.state.value.screen)
        assertEquals("two", vm.state.value.business?.id)
    }
    @Test fun removedRememberedMembershipNeverLoadsItsPrivateDashboard() {
        preferences.mode = AppMode.BUSINESS; preferences.selectBusiness(owner.id, "revoked")
        gateway.restored = owner; gateway.mine = listOf(summary("one"), summary("two"))
        val vm = model()
        assertEquals(JourneyScreen.BUSINESSES, vm.state.value.screen)
        assertTrue(gateway.dashboardRequests.isEmpty())
        assertNull(vm.state.value.business)
    }
    @Test fun businessSelectionIsScopedToTheSignedInAccount() {
        preferences.mode = AppMode.BUSINESS; preferences.selectBusiness("someone-else", "one")
        gateway.restored = owner; gateway.mine = listOf(summary("one"), summary("two"))
        assertEquals(JourneyScreen.BUSINESSES, model().state.value.screen)
    }
    @Test fun temporaryRestoreFailureOffersRetryWithoutErasingSession() {
        gateway.restoreError = IOException("offline")
        val vm = model()
        assertEquals(JourneyScreen.STARTUP, vm.state.value.screen)
        assertNotNull(vm.state.value.error)
        assertEquals(0, gateway.signOutCalls)
        gateway.restoreError = null; gateway.restored = owner
        vm.refresh(); settle()
        assertEquals(owner, vm.state.value.identity)
        assertEquals(JourneyScreen.EXPLORE, vm.state.value.screen)
    }
    @Test fun expiredSessionRoutesToSignInWithoutPrivateBusinessData() {
        gateway.restoreError = SessionExpiredException()
        val state = model().state.value
        assertEquals(JourneyScreen.SIGN_IN, state.screen)
        assertFalse(state.signedIn); assertNull(state.business)
        assertTrue(state.businesses.isEmpty())
    }
    @Test fun setupValidatesEachStepAndCreatesOnlyAfterReview() {
        gateway.restored = owner; preferences.mode = AppMode.BUSINESS
        val vm = model(); vm.startSetup(); settle()
        vm.nextSetup()
        assertEquals(JourneyScreen.BUSINESS_BASICS, vm.state.value.screen)
        assertTrue(vm.state.value.fieldErrors.containsKey("name"))
        vm.updateDraft(vm.state.value.draft.copy(name = "New studio", categoryId = 1))
        vm.nextSetup()
        assertEquals(JourneyScreen.BUSINESS_LOCATION, vm.state.value.screen)
        vm.nextSetup()
        assertTrue(vm.state.value.fieldErrors.containsKey("location"))
        vm.updateDraft(vm.state.value.draft.copy(location = "12 Garden Lane")); vm.nextSetup()
        assertEquals(JourneyScreen.BUSINESS_REVIEW, vm.state.value.screen)
        assertEquals(0, gateway.createRequests.size)
        vm.submitBusiness(); settle()
        assertEquals(JourneyScreen.BUSINESS_HOME, vm.state.value.screen)
        assertEquals("created", vm.state.value.business?.id)
        assertEquals(1, gateway.createRequests.size)
    }
    @Test fun retryAfterUncertainCreateKeepsTheSameIdempotencyIdentifier() {
        gateway.restored = owner; preferences.mode = AppMode.BUSINESS
        val vm = model(); vm.startSetup(); settle()
        vm.updateDraft(vm.state.value.draft.copy(name = "Studio", categoryId = 1, operatingModel = "online"))
        vm.nextSetup(); vm.nextSetup()
        gateway.createError = IOException("response lost")
        vm.submitBusiness(); settle()
        assertEquals(JourneyScreen.BUSINESS_REVIEW, vm.state.value.screen)
        gateway.createError = null; vm.submitBusiness(); settle()
        assertEquals(2, gateway.createRequests.size)
        assertEquals(gateway.createRequests.first(), gateway.createRequests.last())
    }
    @Test fun dashboardRefreshFailureAfterCreationCannotCreateAnotherBusiness() {
        gateway.restored = owner; preferences.mode = AppMode.BUSINESS
        val vm = model(); vm.startSetup(); settle()
        vm.updateDraft(vm.state.value.draft.copy(name = "Studio", categoryId = 1, operatingModel = "online"))
        vm.nextSetup(); vm.nextSetup(); gateway.dashboardError = IOException("offline")
        vm.submitBusiness(); settle()
        assertEquals(JourneyScreen.BUSINESS_HOME, vm.state.value.screen)
        assertEquals("created", vm.state.value.selectedBusinessId)
        gateway.dashboardError = null; vm.refresh(); settle()
        assertEquals(1, gateway.createRequests.size)
        assertEquals("created", vm.state.value.business?.id)
    }
    @Test fun staffCannotEnterTheBusinessEditor() {
        gateway.restored = owner; preferences.mode = AppMode.BUSINESS; gateway.mine = listOf(summary("staff"))
        gateway.role = "staff"
        val vm = model(); vm.editBusiness()
        assertEquals(JourneyScreen.BUSINESS_HOME, vm.state.value.screen)
        assertNull(vm.state.value.editingBusinessId)
    }
    @Test fun passwordRecoveryCannotEnterBusinessHomeBeforePasswordIsUpdated() {
        gateway.restored = owner.copy(passwordRecovery = true)
        val vm = model()
        assertEquals(JourneyScreen.RESET_PASSWORD, vm.state.value.screen)
        vm.updatePassword("new secure password"); settle()
        assertEquals(1, gateway.passwordUpdates)
        assertEquals(JourneyScreen.EXPLORE, vm.state.value.screen)
        assertFalse(vm.state.value.identity!!.passwordRecovery)
    }
    @Test fun signingOutOfflineStillClearsThePrivateUi() {
        gateway.restored = owner; preferences.mode = AppMode.BUSINESS; gateway.mine = listOf(summary("one"))
        val vm = model(); gateway.logoutError = IOException("offline")
        vm.signOut(); settle()
        assertEquals(JourneyScreen.EXPLORE, vm.state.value.screen)
        assertFalse(vm.state.value.signedIn)
        assertNull(vm.state.value.identity); assertNull(vm.state.value.business)
        assertTrue(vm.state.value.businesses.isEmpty())
    }
    @Test fun createAccountFromAccountPageHasAClearBackDestination() {
        val vm = model(); vm.explore(); settle(); vm.account(); vm.showSignUp(); vm.back()
        assertEquals(JourneyScreen.ACCOUNT, vm.state.value.screen)
    }
    @Test fun emailCallbackForAnotherAccountClearsPrivateDraftAndBusinessState() {
        gateway.restored = owner; preferences.mode = AppMode.BUSINESS
        val vm = model(); vm.startSetup(); settle()
        vm.updateDraft(vm.state.value.draft.copy(name = "Private first-account draft", categoryId = 1))
        gateway.callbackIdentity = SessionIdentity("other-user", "other@example.com", passwordRecovery = true)
        vm.handleAuthLink("test-only-callback"); settle()
        assertEquals(JourneyScreen.RESET_PASSWORD, vm.state.value.screen)
        assertEquals("", vm.state.value.draft.name)
        assertNull(vm.state.value.business)
        assertTrue(vm.state.value.businesses.isEmpty())
    }
    @Test fun taskRestorationCannotExposeADraftFromAnotherAccount() {
        gateway.restored = owner; preferences.mode = AppMode.BUSINESS
        val vm = FoundationViewModel(gateway, preferences, SavedStateHandle(mapOf(
            "name" to "Other account's private draft", "draftOwner" to "another-owner"
        )))
        settle()
        assertEquals("", vm.state.value.draft.name)
        assertEquals(owner, vm.state.value.identity)
    }

    @Test fun signupRequiresExplicitPersonalOrBusinessChoice() {
        val vm = model()
        vm.showSignUp()
        assertEquals(JourneyScreen.ACCOUNT_TYPE, vm.state.value.screen)
        vm.selectAccountType(AppMode.CUSTOMER)
        assertEquals(JourneyScreen.SIGN_UP, vm.state.value.screen)
        assertEquals(AppMode.CUSTOMER, vm.state.value.mode)
        vm.back()
        assertEquals(JourneyScreen.ACCOUNT_TYPE, vm.state.value.screen)
        vm.selectAccountType(AppMode.BUSINESS)
        gateway.authResult = AuthenticationResult(owner.copy(accountType = AppMode.BUSINESS))
        vm.authenticate(owner.email, "long-password", true); settle()
        assertEquals(AppMode.BUSINESS, gateway.registeredType)
        assertEquals(JourneyScreen.BUSINESSES, vm.state.value.screen)
    }
    @Test fun accountTypeFromServerOverridesAnotherDevicesLastMode() {
        preferences.mode = AppMode.BUSINESS
        gateway.restored = owner.copy(accountType = AppMode.CUSTOMER)
        val vm = model()
        assertEquals(AppMode.CUSTOMER, vm.state.value.mode)
        assertEquals(JourneyScreen.EXPLORE, vm.state.value.screen)
    }

    @Test fun storefrontSignInReturnsToTheSameBusiness() {
        val vm = model(); vm.explore(); settle(); vm.openPublicBusiness("chosen"); settle()
        vm.showSignIn(); gateway.authResult = AuthenticationResult(owner.copy(accountType = AppMode.BUSINESS))
        vm.authenticate(owner.email, "long-password", false); settle()
        assertEquals(JourneyScreen.PUBLIC_PROFILE, vm.state.value.screen)
        assertEquals("chosen", vm.state.value.publicBusiness?.id)
    }
    @Test fun guestBookingsEntryContinuesToPersonalBookingsAfterAuthentication() {
        val vm = model(); vm.personalBookings()
        gateway.authResult = AuthenticationResult(owner)
        vm.authenticate(owner.email, "long-password", false); settle()
        assertEquals(JourneyScreen.PERSONAL_BOOKINGS, vm.state.value.screen)
    }
    private fun summary(id: String) = BusinessSummary(id, "Business $id", "online", role = "owner", status = "draft")
    private class MemoryPreferences : JourneyPreferences {
        override var welcomed = false
        override var mode = AppMode.CUSTOMER
        private val selected = mutableMapOf<String, String>()
        override fun selectedBusiness(userId: String) = selected[userId]
        override fun selectBusiness(userId: String, businessId: String) { selected[userId] = businessId }
    }
    private class FakeGateway : JourneyGateway {
        override val configured = true
        var restored: SessionIdentity? = null
        var restoreError: Exception? = null
        var authResult = AuthenticationResult()
        var authCalls = 0
        var registeredType: AppMode? = null
        override suspend fun register(email: String, password: String, accountType: AppMode): AuthenticationResult { registeredType = accountType; return authenticate(email, password, true) }
        var callbackIdentity = SessionIdentity("owner", "owner@example.com")
        var mine = emptyList<BusinessSummary>()
        var role = "owner"
        var createError: Exception? = null
        var dashboardError: Exception? = null
        var logoutError: Exception? = null
        var signOutCalls = 0
        var passwordUpdates = 0
        val createRequests = mutableListOf<String>()
        val dashboardRequests = mutableListOf<String>()
        override suspend fun restoreSession(): SessionIdentity? { restoreError?.let { throw it }; return restored }
        override suspend fun authenticate(email: String, password: String, register: Boolean): AuthenticationResult { authCalls++; return authResult }
        override suspend fun resendConfirmation(email: String) {}
        override suspend fun requestPasswordReset(email: String) {}
        override suspend fun exchangeAuthLink(link: String) = callbackIdentity
        override suspend fun updatePassword(password: String) { passwordUpdates++ }
        override suspend fun signOut() { signOutCalls++; restored = null; logoutError?.let { throw it } }
        override suspend fun categories() = listOf(BusinessCategory(1, "Other"))
        override suspend fun businesses(query: String, mine: Boolean, offset: Int) = if (mine) this.mine else emptyList()
        override suspend fun createBusiness(draft: BusinessDraft): String { createRequests += draft.requestId; createError?.let { throw it }; return "created" }
        override suspend fun updateBusiness(id: String, draft: BusinessDraft) {}
        override suspend fun dashboard(id: String): BusinessDetails {
            dashboardRequests += id; dashboardError?.let { throw it }
            return BusinessDetails(id, "Business $id", 1, "Other", "online", "draft", role = role, membersCount = 1, canPublish = role == "owner")
        }
        override suspend fun publicProfile(id: String) = dashboard(id)
        override suspend fun publishBusiness(id: String) {}
    }
}
