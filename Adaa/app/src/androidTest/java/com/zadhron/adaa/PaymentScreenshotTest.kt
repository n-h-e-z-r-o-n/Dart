package com.zadhron.adaa

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.ui.preview.PaymentPreviewData
import com.zadhron.adaa.ui.shared.PaymentContent
import com.zadhron.adaa.ui.theme.AdaaTheme
import com.zadhron.adaa.ui.viewmodel.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class PaymentScreenshotTest(private val name: String, private val state: PaymentState, private val dark: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<JourneyTestActivity>()
    @Test fun renderPaymentDestination() {
        compose.setContent { AdaaTheme(darkTheme = dark) { PaymentContent(state) } }
        compose.waitUntil(5000) { runCatching { compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        compose.waitForIdle()
        val directory = File(ApplicationProvider.getApplicationContext<android.content.Context>().getExternalFilesDir(null), "payment-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> {
            val base = PaymentPreviewData.state
            fun item(name: String, state: PaymentState, dark: Boolean = false): Array<Any> = arrayOf(name, state, dark)
            return listOf(
                item("01-personal-wallet", base),
                item("02-personal-deposit", base.copy(page = PaymentPage.FORM)),
                item("03-withdrawal-review", base.copy(page = PaymentPage.REVIEW, direction = "withdrawal", phone = "254712345678",
                    quote = PaymentQuote(2,50000,0,50000,1000000,125000,true))),
                item("04-personal-payments", base.copy(page = PaymentPage.SERVICES)),
                item("05-business-completion", base.copy(scope = PaymentPreviewData.business, page = PaymentPage.CHARGE)),
                item("06-dispute-details", base.copy(page = PaymentPage.MESSAGE, messageAction = "dispute")),
                item("07-pending-deposit", base.copy(page = PaymentPage.OPERATION, operationId = "preview-deposit")),
                item("08-dark-business-wallet", base.copy(scope = PaymentPreviewData.business), true),
                item("09-create-service-request", base.copy(scope = PaymentPreviewData.business, page = PaymentPage.CREATE_SERVICE))
            )
        }
    }
}
