package com.zadhron.adaa

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.data.remote.BusinessCategory
import com.zadhron.adaa.data.remote.BusinessSummary
import com.zadhron.adaa.ui.FoundationScreen
import com.zadhron.adaa.ui.shared.JourneyActions
import com.zadhron.adaa.ui.theme.AdaaTheme
import com.zadhron.adaa.ui.viewmodel.FoundationState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JourneyUiTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyTestActivity>()
    private val category = BusinessCategory(1, "Beauty & wellness")
    private val business = BusinessDetails("sample", "The Garden Studio", 1, category.name, "physical", "draft",
        role = "owner", location = "24 Garden Lane, Westlands", description = "A calm little studio for your everyday rituals.",
        membersCount = 1, locationsCount = 1, canPublish = true)
    private val base = FoundationState(configured = true, categories = listOf(category), business = business,
        identity = SessionIdentity("sample", "hello@example.com"), signedIn = true,
        draft = BusinessDraft(name = business.name, categoryId = 1, location = business.location, description = business.description))

    @Test fun welcomeHasDistinctGuestBusinessAndReturningUserActions() {
        var explore = 0; var setup = 0; var signIn = 0
        compose.setContent { AdaaTheme { FoundationScreen(base.copy(screen = JourneyScreen.WELCOME), object : JourneyActions {
            override fun explore() { explore++ }
            override fun businessMode() { setup++ }
            override fun showSignIn() { signIn++ }
        }) } }
        compose.onNodeWithText("Explore businesses").performScrollTo().performClick()
        compose.onNodeWithText("Set up my business").performScrollTo().performClick()
        compose.onNodeWithText("Sign in", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(1, explore); assertEquals(1, setup); assertEquals(1, signIn)
    }

    @Test fun invalidSignUpExplainsWhatToFixBeforeSendingAnything() {
        var submissions = 0
        compose.setContent { AdaaTheme { FoundationScreen(base.copy(screen = JourneyScreen.SIGN_UP), object : JourneyActions {
            override fun authenticate(email: String, password: String, register: Boolean) { submissions++ }
        }) } }
        compose.onNodeWithText("Create account", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(0, submissions)
        compose.onNodeWithText("Enter a valid email address.").assertExists()
        compose.onNodeWithText("Use at least 8 characters.", substring = false).assertExists()
    }

    @Test fun staffHomeDoesNotOfferPublishOrEdit() {
        compose.setContent { AdaaTheme { FoundationScreen(base.copy(screen = JourneyScreen.BUSINESS_HOME,
            business = business.copy(role = "staff", canPublish = false))) } }
        compose.onNodeWithText("Publish business").assertDoesNotExist()
        compose.onNodeWithText("Edit business details").assertDoesNotExist()
        compose.onNodeWithText("Review my listing").assertExists()
    }

}
