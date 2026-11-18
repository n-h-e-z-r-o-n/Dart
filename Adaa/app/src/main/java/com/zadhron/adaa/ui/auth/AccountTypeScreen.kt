package com.zadhron.adaa.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Storefront
import com.zadhron.adaa.data.model.AppMode
import com.zadhron.adaa.ui.shared.*
import com.zadhron.adaa.ui.viewmodel.FoundationState

@Composable
fun AccountTypeScreen(state: FoundationState, actions: JourneyActions) {
    JourneyPage("How will you use\nAdaa?", "Choose your account type before creating your account.",
        "CREATE ACCOUNT / 1 OF 2", actions::back) {
        InfoPanel("Personal account", "Discover businesses and explore their listings. For your everyday needs.", Icons.Outlined.PersonOutline)
        PrimaryAction("Continue with Personal", { actions.selectAccountType(AppMode.CUSTOMER) }, enabled = !state.busy)
        InfoPanel("Business account", "Set up your business, manage your listing and give your team access with separate permissions.", Icons.Outlined.Storefront)
        SecondaryAction("Continue with Business", { actions.selectAccountType(AppMode.BUSINESS) }, enabled = !state.busy)
    }
}
