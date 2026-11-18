package com.zadhron.adaa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.data.remote.BusinessCategory
import com.zadhron.adaa.data.remote.BusinessSummary
import com.zadhron.adaa.ui.shared.*
import com.zadhron.adaa.ui.auth.*
import com.zadhron.adaa.ui.business.*
import com.zadhron.adaa.ui.personal.*
import com.zadhron.adaa.ui.theme.AdaaTheme
import com.zadhron.adaa.ui.viewmodel.FoundationState
import com.zadhron.adaa.ui.viewmodel.FoundationViewModel

@Composable
fun FoundationApp(viewModel: FoundationViewModel = viewModel(factory = FoundationViewModel.factory(LocalContext.current.applicationContext))) {
    val state by viewModel.state.collectAsState()
    FoundationScreen(state, viewModel)
}

/** Route host only. Each journey lives in its own screen file under ui/business, ui/personal, ui/auth and ui/shared. */
@Composable
fun FoundationScreen(state: FoundationState, actions: JourneyActions = object : JourneyActions {}) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.screen) { focus.clearFocus(); keyboard?.hide() }
    val mainScreens = setOf(JourneyScreen.EXPLORE, JourneyScreen.BUSINESSES, JourneyScreen.BUSINESS_HOME, JourneyScreen.ACCOUNT)
    BackHandler(enabled = state.busy || state.backTarget != null) { if (!state.busy) actions.back() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { if (state.screen in mainScreens) JourneyNavigation(state, actions) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                if (state.busy && state.screen != JourneyScreen.STARTUP) LinearProgressIndicator(Modifier.fillMaxWidth())
                val notice = state.error ?: state.message
                if (notice != null) Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    color = if (state.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(notice, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = if (state.error != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
                        IconButton(onClick = actions::dismissMessage) { Icon(Icons.Outlined.Close, "Dismiss message", Modifier.size(20.dp)) }
                    }
                }
                Crossfade(state.screen, Modifier.weight(1f), animationSpec = tween(160), label = "Journey destination") { screen ->
                    when (screen) {
                        JourneyScreen.STARTUP -> StartupScreen(state, actions)
                        JourneyScreen.WELCOME -> WelcomeScreen(state, actions)
                        JourneyScreen.EXPLORE -> ExploreScreen(state, actions)
                        JourneyScreen.SIGN_IN -> SignInScreen(state, actions, register = false)
                        JourneyScreen.ACCOUNT_TYPE -> AccountTypeScreen(state, actions)
                        JourneyScreen.SIGN_UP -> SignInScreen(state, actions, register = true)
                        JourneyScreen.CONFIRM_EMAIL -> EmailConfirmationScreen(state, actions, recovery = false)
                        JourneyScreen.FORGOT_PASSWORD -> ForgotPasswordScreen(state, actions)
                        JourneyScreen.RESET_SENT -> EmailConfirmationScreen(state, actions, recovery = true)
                        JourneyScreen.RESET_PASSWORD -> ResetPasswordScreen(state, actions)
                        JourneyScreen.BUSINESSES -> BusinessesScreen(state, actions)
                        JourneyScreen.BUSINESS_BASICS -> BusinessBasicsScreen(state, actions)
                        JourneyScreen.BUSINESS_LOCATION -> BusinessLocationScreen(state, actions)
                        JourneyScreen.BUSINESS_REVIEW -> BusinessReviewScreen(state, actions)
                        JourneyScreen.BUSINESS_TEAM -> BusinessTeamScreen(state, actions)
                        JourneyScreen.BUSINESS_HOME -> BusinessHomeScreen(state, actions)
                        JourneyScreen.BUSINESS_DETAILS -> BusinessProfileScreen(state, actions, ownListing = true)
                        JourneyScreen.PUBLIC_PROFILE -> {
                            val business = state.publicBusiness
                            if (business != null) BookingFeature(state.identity?.id, business, false, actions::back,
                                actions::showSignIn, actions::personalBookings, actions::personalPayments)
                            else BusinessProfileScreen(state, actions, ownListing = false)
                        }
                        JourneyScreen.PERSONAL_BOOKINGS, JourneyScreen.BUSINESS_BOOKINGS -> {
                            if (state.identity != null) BookingFeature(state.identity.id,
                                if (screen == JourneyScreen.BUSINESS_BOOKINGS) state.business else null,
                                screen == JourneyScreen.BUSINESS_BOOKINGS, actions::back, actions::showSignIn,
                                actions::personalBookings, if (screen == JourneyScreen.BUSINESS_BOOKINGS) actions::businessPayments else actions::personalPayments)
                        }
                        JourneyScreen.ACCOUNT -> AccountScreen(state, actions)
                        JourneyScreen.PERSONAL_WALLET, JourneyScreen.PERSONAL_PAYMENTS,
                        JourneyScreen.BUSINESS_WALLET, JourneyScreen.BUSINESS_PAYMENTS -> {
                            val identity = state.identity
                            val businessScope = screen in listOf(JourneyScreen.BUSINESS_WALLET, JourneyScreen.BUSINESS_PAYMENTS)
                            if (identity != null && (!businessScope || state.business != null)) PaymentFeature(
                                WalletScope(identity.id, if (businessScope) state.business?.id else null, if (businessScope) state.business?.role.orEmpty() else ""),
                                services = screen in listOf(JourneyScreen.PERSONAL_PAYMENTS, JourneyScreen.BUSINESS_PAYMENTS), onExit = actions::back)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyNavigation(state: FoundationState, actions: JourneyActions) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        NavigationBarItem(state.screen == JourneyScreen.EXPLORE, onClick = actions::explore,
            icon = { Icon(Icons.Outlined.Explore, null) }, label = { Text("Explore") }, enabled = !state.busy)
        NavigationBarItem(state.screen in listOf(JourneyScreen.BUSINESSES, JourneyScreen.BUSINESS_HOME), onClick = actions::businessMode,
            icon = { Icon(Icons.Outlined.Storefront, null) }, label = { Text("Business") }, enabled = !state.busy)
        NavigationBarItem(state.screen == JourneyScreen.ACCOUNT, onClick = actions::account,
            icon = { Icon(Icons.Outlined.PersonOutline, null) }, label = { Text("Account") }, enabled = !state.busy)
    }
}

private val previewCategory = BusinessCategory(1, "Beauty & wellness")
private val previewBusiness = BusinessDetails("preview", "The Garden Studio", 1, "Beauty & wellness", "physical", "draft",
    role = "owner", location = "24 Garden Lane, Westlands", description = "A calm little studio for your everyday rituals.", membersCount = 1,
    locationsCount = 1, canPublish = true)
private val previewState = FoundationState(configured = true, categories = listOf(previewCategory),
    business = previewBusiness, draft = BusinessDraft(name = previewBusiness.name, categoryId = 1, location = previewBusiness.location,
        description = previewBusiness.description))

@Preview(name = "01 · Welcome", showBackground = true, showSystemUi = true)
@Composable
fun WelcomePreview() { AdaaTheme { FoundationScreen(previewState.copy(screen = JourneyScreen.WELCOME)) } }

@Preview(name = "02 · Explore", showBackground = true, showSystemUi = true)
@Composable
fun ExplorePreview() { AdaaTheme { FoundationScreen(previewState.copy(discovered = listOf(
    BusinessSummary("preview", "The Garden Studio", "physical", category = "Beauty & wellness")))) } }

@Preview(name = "03 · Sign in", showBackground = true, showSystemUi = true)
@Composable
fun SignInPreview() { AdaaTheme { FoundationScreen(previewState.copy(screen = JourneyScreen.SIGN_IN, backTarget = JourneyScreen.WELCOME, mode = AppMode.BUSINESS)) } }

@Preview(name = "04 · Create account", showBackground = true, showSystemUi = true)
@Composable
fun SignUpPreview() { AdaaTheme { FoundationScreen(previewState.copy(screen = JourneyScreen.SIGN_UP, backTarget = JourneyScreen.SIGN_IN)) } }

@Preview(name = "05 · Confirm email", showBackground = true)
@Composable
fun ConfirmationPreview() { AdaaTheme { FoundationScreen(previewState.copy(screen = JourneyScreen.CONFIRM_EMAIL, email = "hello@example.com")) } }

@Preview(name = "06 · Business basics", showBackground = true, showSystemUi = true)
@Composable
fun BusinessOnboardingPreview() { AdaaTheme { FoundationScreen(previewState.copy(screen = JourneyScreen.BUSINESS_BASICS, backTarget = JourneyScreen.BUSINESSES)) } }

@Preview(name = "07 · Business location", showBackground = true, showSystemUi = true)
@Composable
fun BusinessLocationPreview() { AdaaTheme { FoundationScreen(previewState.copy(screen = JourneyScreen.BUSINESS_LOCATION, backTarget = JourneyScreen.BUSINESS_BASICS)) } }

@Preview(name = "08 · Review business", showBackground = true)
@Composable
fun BusinessReviewPreview() { AdaaTheme { FoundationScreen(previewState.copy(screen = JourneyScreen.BUSINESS_REVIEW, backTarget = JourneyScreen.BUSINESS_LOCATION)) } }

@Preview(name = "09 · Business home", showBackground = true, showSystemUi = true)
@Composable
fun BusinessHomePreview() { AdaaTheme { FoundationScreen(previewState.copy(screen = JourneyScreen.BUSINESS_HOME)) } }

@Preview(name = "10 · Dark explore", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ExploreDarkPreview() { AdaaTheme(darkTheme = true) { FoundationScreen(previewState) } }

@Preview(name = "Account type", showBackground = true, showSystemUi = true)
@Composable
fun AccountTypePreview() { AdaaTheme { FoundationScreen(previewState.copy(screen = JourneyScreen.ACCOUNT_TYPE)) } }

@Preview(name = "Business wallet dashboard", showBackground = true, showSystemUi = true)
@Composable
fun WalletDashboardPreview() { AdaaTheme { FoundationScreen(previewState.copy(screen = JourneyScreen.BUSINESS_HOME,
    business = previewBusiness.copy(wallet = WalletSummary("KES", 125000, 80000, 20000, 4, 2, 300000, false, emptyList())))) } }

@Preview(name = "Team permissions", showBackground = true, showSystemUi = true)
@Composable
fun BusinessTeamPreview() { AdaaTheme { FoundationScreen(previewState.copy(screen = JourneyScreen.BUSINESS_TEAM,
    team = listOf(BusinessTeamMember("preview-owner", "Jamie", "owner", true), BusinessTeamMember("preview-staff", "Alex", "staff", false)))) } }
