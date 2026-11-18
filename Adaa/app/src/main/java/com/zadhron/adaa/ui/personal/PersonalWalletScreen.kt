package com.zadhron.adaa.ui.personal

import androidx.compose.runtime.Composable
import com.zadhron.adaa.ui.shared.*
import com.zadhron.adaa.ui.viewmodel.PaymentState

@Composable
fun PersonalWalletScreen(state: PaymentState, actions: PaymentActions, onBack: () -> Unit) =
    WalletOverview(state, actions, onBack, business = false)
