package com.zadhron.adaa.ui.personal

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zadhron.adaa.ui.preview.PaymentPreviewData
import com.zadhron.adaa.ui.shared.PaymentContent
import com.zadhron.adaa.ui.theme.AdaaTheme
import com.zadhron.adaa.ui.viewmodel.PaymentPage

@Preview(name = "Personal wallet", showBackground = true, showSystemUi = true)
@Composable
fun PersonalWalletPreview() { AdaaTheme { PaymentContent(PaymentPreviewData.state) } }

@Preview(name = "Personal service payment", showBackground = true, showSystemUi = true)
@Composable
fun PersonalServicePaymentPreview() { AdaaTheme { PaymentContent(PaymentPreviewData.state.copy(page = PaymentPage.CHARGE)) } }

@Preview(name = "Personal deposit", showBackground = true, showSystemUi = true)
@Composable
fun PersonalDepositPreview() { AdaaTheme { PaymentContent(PaymentPreviewData.state.copy(page = PaymentPage.FORM)) } }
