package com.zadhron.adaa

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.data.remote.BusinessCategory
import com.zadhron.adaa.data.remote.BusinessSummary
import com.zadhron.adaa.ui.FoundationApp
import com.zadhron.adaa.ui.FoundationScreen
import com.zadhron.adaa.ui.theme.AdaaTheme
import com.zadhron.adaa.ui.viewmodel.FoundationState
import com.zadhron.adaa.ui.viewmodel.FoundationViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real UI/navigation with a deterministic gateway; never creates hosted accounts/listings. */
@RunWith(AndroidJUnit4::class)
class CompleteBusinessJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyTestActivity>()

    @Test fun newBusinessUserCanRegisterConfirmSetUpReviewAndPublish() {
        val gateway = TestGateway()
        val prefs = object : JourneyPreferences {
            override var welcomed = false
            override var mode = AppMode.CUSTOMER
            private var business: String? = null
            override fun selectedBusiness(userId: String) = business
            override fun selectBusiness(userId: String, businessId: String) { business = businessId }
        }
        val model = FoundationViewModel(gateway, prefs)
        val store = ViewModelStore().apply { put("test", model) }
        try {
            compose.setContent { AdaaTheme { FoundationApp(model) } }
            compose.waitUntil(5000) { model.state.value.screen == JourneyScreen.WELCOME }
            compose.onNodeWithText("Set up my business").performScrollTo().performClick()
            compose.onNodeWithText("Create account").performScrollTo().performClick()
            compose.onNodeWithText("Continue with Business").performScrollTo().performClick()
            compose.onNodeWithTag("auth_email").performScrollTo().performTextInput("journey@example.com")
            compose.onNodeWithTag("auth_password").performScrollTo().performTextInput("test-password-123")
            compose.onNodeWithTag("auth_confirm_password").performScrollTo().performTextInput("test-password-123")
            compose.onNodeWithText("Create account", useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(5000) { model.state.value.screen == JourneyScreen.CONFIRM_EMAIL }
            compose.onNodeWithText("I've confirmed my email · Sign in").performScrollTo().performClick()
            compose.onNodeWithTag("auth_password").performScrollTo().performTextInput("test-password-123")
            compose.onNodeWithText("Sign in", useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(5000) { model.state.value.screen == JourneyScreen.BUSINESSES }
            compose.onNodeWithText("Set up my business").performScrollTo().performClick()
            compose.waitUntil(5000) { model.state.value.categories.isNotEmpty() }
            compose.onNodeWithTag("business_name").performScrollTo().performTextInput("Test journey studio")
            compose.onNodeWithText("Choose your category").performScrollTo().performClick()
            compose.onNodeWithText("Other").performClick()
            compose.onNodeWithText("Continue to location").performScrollTo().performClick()
            compose.onNodeWithTag("business_location").performScrollTo().performTextInput("12 Test Lane")
            compose.onNodeWithText("Review my business").performScrollTo().performClick()
            assertEquals(0, gateway.created)
            compose.onNodeWithText("Create my business draft").performScrollTo().performClick()
            compose.waitUntil(5000) { model.state.value.business != null }
            compose.onNodeWithText("Review my listing").performScrollTo().performClick()
            compose.onNodeWithText("Publish business").performScrollTo().performClick()
            compose.onNodeWithText("Ready to go live?").assertExists()
            compose.onNode(hasText("Publish business") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(5000) { model.state.value.business?.status == "published" }
            compose.onNodeWithText("Live").assertExists()
            assertEquals(1, gateway.created); assertEquals(1, gateway.published)
        } finally { compose.runOnIdle { store.clear() } }
    }

    @Test fun signUpPrimaryActionRemainsReachableAtDoubleTextSize() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AdaaTheme { FoundationScreen(FoundationState(configured = true, screen = JourneyScreen.SIGN_UP,
                    backTarget = JourneyScreen.SIGN_IN)) }
            }
        }
        compose.onNodeWithText("Create account", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Go back").assertIsDisplayed()
    }

    private class TestGateway : JourneyGateway {
        override val configured = true
        var created = 0
        var published = 0
        private var draft: BusinessDraft? = null
        private var live = false
        override suspend fun restoreSession(): SessionIdentity? = null
        override suspend fun authenticate(email: String, password: String, register: Boolean) =
            if (register) AuthenticationResult(needsConfirmation = true) else AuthenticationResult(SessionIdentity("test", email))
        override suspend fun resendConfirmation(email: String) {}
        override suspend fun requestPasswordReset(email: String) {}
        override suspend fun exchangeAuthLink(link: String) = SessionIdentity("test", "journey@example.com")
        override suspend fun updatePassword(password: String) {}
        override suspend fun signOut() {}
        override suspend fun categories() = listOf(BusinessCategory(1, "Other"))
        override suspend fun businesses(query: String, mine: Boolean, offset: Int) = if (mine && draft != null)
            listOf(BusinessSummary("test-business", draft!!.name, draft!!.operatingModel, if (live) "published" else "draft", "owner")) else emptyList()
        override suspend fun createBusiness(draft: BusinessDraft): String { created++; this.draft = draft; return "test-business" }
        override suspend fun updateBusiness(id: String, draft: BusinessDraft) { this.draft = draft }
        override suspend fun dashboard(id: String) = BusinessDetails(id, draft!!.name, 1, "Other", draft!!.operatingModel,
            if (live) "published" else "draft", role = "owner", location = draft!!.location, membersCount = 1, canPublish = !live)
        override suspend fun publicProfile(id: String) = dashboard(id)
        override suspend fun publishBusiness(id: String) { published++; live = true }
    }
}
