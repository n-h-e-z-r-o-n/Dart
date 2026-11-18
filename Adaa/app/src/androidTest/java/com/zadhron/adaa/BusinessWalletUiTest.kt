package com.zadhron.adaa

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.ui.FoundationScreen
import com.zadhron.adaa.ui.shared.JourneyActions
import com.zadhron.adaa.ui.theme.AdaaTheme
import com.zadhron.adaa.ui.viewmodel.FoundationState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BusinessWalletUiTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyTestActivity>()
    private val wallet = WalletSummary("KES", 125000, 80000, 20000, 4, 2, 300000, false, emptyList())
    private val business = BusinessDetails("test", "Test Studio", 1, "Other", "online", "published", role = "owner", wallet = wallet)

    @Test fun ownerSeesSeparateAvailableEscrowAndWithdrawalBalances() {
        compose.setContent { AdaaTheme { FoundationScreen(FoundationState(screen = JourneyScreen.BUSINESS_HOME, business = business)) } }
        compose.onNodeWithText("Available balance").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Held in escrow").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pending withdrawals").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Payments are not active").performScrollTo().assertIsDisplayed()
    }
    @Test fun staffCannotSeeWalletTotalsOrPermissionManagement() {
        compose.setContent { AdaaTheme { FoundationScreen(FoundationState(screen = JourneyScreen.BUSINESS_HOME, business = business.copy(role = "staff"))) } }
        compose.onNodeWithText("Available balance").assertDoesNotExist()
        compose.onNodeWithText("Team permissions").assertDoesNotExist()
    }
    @Test fun grantingEscrowReleaseRequiresConfirmation() {
        var granted = false
        compose.setContent { AdaaTheme { FoundationScreen(FoundationState(screen = JourneyScreen.BUSINESS_TEAM,
            team = listOf(BusinessTeamMember("employee", "Alex", "staff", false))), object : JourneyActions {
            override fun setEscrowPermission(userId: String, allowed: Boolean) { assertEquals("employee", userId); granted = allowed }
        }) } }
        compose.onNode(isToggleable()).performScrollTo().performClick()
        assertFalse(granted)
        compose.onNodeWithText("Allow escrow release?").assertIsDisplayed()
        compose.onNodeWithText("Confirm").performClick()
        assertTrue(granted)
    }
}
