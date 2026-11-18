package com.zadhron.adaa

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.zadhron.adaa.ui.preview.PaymentPreviewData
import com.zadhron.adaa.ui.shared.PaymentContent
import com.zadhron.adaa.ui.theme.AdaaTheme
import org.junit.Rule
import org.junit.Test

class PaymentPermissionUiTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyTestActivity>()
    @Test fun disputedBusinessCannotStartAWithdrawal() {
        val state = PaymentPreviewData.state.copy(scope = PaymentPreviewData.business,
            policy = PaymentPreviewData.policy.copy(active = true), wallet = PaymentPreviewData.wallet.copy(withdrawalsBlocked = true))
        compose.setContent { AdaaTheme { PaymentContent(state) } }
        compose.onNodeWithText("Withdraw to M-Pesa").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Withdrawals are on hold").performScrollTo().assertIsDisplayed()
    }
}
