package com.zadhron.adaa.ui.business

import androidx.compose.runtime.Composable
import com.zadhron.adaa.ui.shared.*
import com.zadhron.adaa.ui.viewmodel.PaymentState

@Composable
fun BusinessWalletScreen(state: PaymentState, actions: PaymentActions, onBack: () -> Unit) =
    WalletOverview(state, actions, onBack, business = true)
