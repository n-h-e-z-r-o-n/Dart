package com.zadhron.adaa

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.data.remote.BusinessCategory
import com.zadhron.adaa.data.remote.BusinessSummary
import com.zadhron.adaa.ui.FoundationScreen
import com.zadhron.adaa.ui.theme.AdaaTheme
import com.zadhron.adaa.ui.viewmodel.FoundationState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Each visual fixture gets its own Activity; navigation is tested separately. */
@RunWith(Parameterized::class)
class JourneyScreenshotTest(private val name: String, private val state: FoundationState, private val dark: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<JourneyTestActivity>()

    @Test fun renderDestination() {
        compose.setContent { AdaaTheme(darkTheme = dark) { FoundationScreen(state) } }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.getExternalFilesDir(null), "journey-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> {
            val category = BusinessCategory(1, "Beauty & wellness")
            val business = BusinessDetails("sample", "The Garden Studio", 1, category.name, "physical", "draft",
                role = "owner", location = "24 Garden Lane, Westlands", description = "A calm little studio for your everyday rituals.",
                membersCount = 1, locationsCount = 1, canPublish = true)
            val base = FoundationState(configured = true, categories = listOf(category), business = business,
                identity = SessionIdentity("sample", "hello@example.com"), signedIn = true,
                draft = BusinessDraft(name = business.name, categoryId = 1, location = business.location, description = business.description))
            fun sample(name: String, state: FoundationState, dark: Boolean = false): Array<Any> = arrayOf(name, state, dark)
            return listOf(
                sample("17-wallet-dashboard", base.copy(screen = JourneyScreen.BUSINESS_HOME,
                    business = business.copy(wallet = WalletSummary("KES", 125000, 80000, 20000, 4, 2, 300000, false, emptyList())))),
                sample("18-team-permissions", base.copy(screen = JourneyScreen.BUSINESS_TEAM,
                    team = listOf(BusinessTeamMember("owner", "Jamie", "owner", true), BusinessTeamMember("staff", "Alex", "staff", false)))),
                sample("19-account-type", base.copy(screen = JourneyScreen.ACCOUNT_TYPE)),
                sample("01-welcome", base.copy(screen = JourneyScreen.WELCOME)),
                sample("02-explore", base.copy(discovered = listOf(BusinessSummary("sample", business.name, "physical", category = category.name)))),
                sample("03-sign-in", base.copy(screen = JourneyScreen.SIGN_IN, mode = AppMode.BUSINESS, backTarget = JourneyScreen.WELCOME)),
                sample("04-sign-up", base.copy(screen = JourneyScreen.SIGN_UP, backTarget = JourneyScreen.SIGN_IN)),
                sample("05-email", base.copy(screen = JourneyScreen.CONFIRM_EMAIL, email = "hello@example.com", backTarget = JourneyScreen.SIGN_IN)),
                sample("06-business-intro", base.copy(screen = JourneyScreen.BUSINESSES)),
                sample("07-business-basics", base.copy(screen = JourneyScreen.BUSINESS_BASICS, backTarget = JourneyScreen.BUSINESSES)),
                sample("08-business-location", base.copy(screen = JourneyScreen.BUSINESS_LOCATION, backTarget = JourneyScreen.BUSINESS_BASICS)),
                sample("09-business-review", base.copy(screen = JourneyScreen.BUSINESS_REVIEW, backTarget = JourneyScreen.BUSINESS_LOCATION)),
                sample("10-business-home", base.copy(screen = JourneyScreen.BUSINESS_HOME)),
                sample("11-public-profile", base.copy(screen = JourneyScreen.PUBLIC_PROFILE, publicBusiness = business.copy(status = "published"), backTarget = JourneyScreen.EXPLORE)),
                sample("12-account", base.copy(screen = JourneyScreen.ACCOUNT)),
                sample("13-empty-explore", base.copy(screen = JourneyScreen.EXPLORE, signedIn = false)),
                sample("14-reset-password", base.copy(screen = JourneyScreen.RESET_PASSWORD)),
                sample("15-dark-business-home", base.copy(screen = JourneyScreen.BUSINESS_HOME), true),
                sample("16-dark-welcome", base.copy(screen = JourneyScreen.WELCOME), true)
            )
        }
    }
}
