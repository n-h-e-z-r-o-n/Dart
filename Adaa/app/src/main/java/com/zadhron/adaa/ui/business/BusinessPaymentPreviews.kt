package com.zadhron.adaa.ui.business

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zadhron.adaa.ui.preview.PaymentPreviewData
import com.zadhron.adaa.ui.shared.PaymentContent
import com.zadhron.adaa.ui.theme.AdaaTheme
import com.zadhron.adaa.ui.viewmodel.PaymentPage

@Preview(name = "Business wallet", showBackground = true, showSystemUi = true)
@Composable
fun BusinessPaymentsWalletPreview() { AdaaTheme { PaymentContent(PaymentPreviewData.state.copy(scope = PaymentPreviewData.business)) } }

@Preview(name = "Confirm business service", showBackground = true, showSystemUi = true)
@Composable
fun BusinessServiceCompletionPreview() { AdaaTheme { PaymentContent(PaymentPreviewData.state.copy(scope = PaymentPreviewData.business, page = PaymentPage.CHARGE)) } }
