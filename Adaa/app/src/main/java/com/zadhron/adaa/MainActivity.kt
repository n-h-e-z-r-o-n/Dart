package com.zadhron.adaa

import android.content.res.Configuration
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.zadhron.adaa.ui.viewmodel.FoundationViewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zadhron.adaa.data.remote.BusinessSummary
import com.zadhron.adaa.ui.FoundationApp
import com.zadhron.adaa.ui.FoundationScreen
import com.zadhron.adaa.ui.theme.AdaaTheme
import com.zadhron.adaa.ui.viewmodel.FoundationState

class MainActivity : ComponentActivity() {
    private val journeyModel by lazy {
        ViewModelProvider(this, FoundationViewModel.factory(applicationContext))[FoundationViewModel::class.java]
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setOnExitAnimationListener { provider ->
            // Respect the device's animator setting; never wait for network access.
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                provider.view.animate().alpha(0f).setDuration(240L)
                    .withEndAction { provider.remove() }.start()
            } else {
                provider.remove()
            }
        }
        enableEdgeToEdge()
        setContent {
            AdaaTheme {
                FoundationApp(journeyModel)
            }
        }
        if (savedInstanceState == null) intent?.data?.toString()?.let(journeyModel::handleAuthLink)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.let(journeyModel::handleAuthLink)
    }
}

@Preview(name = "MainActivity · Explore (Light)", showBackground = true, showSystemUi = true)
@Composable
fun MainActivityExploreLightPreview() {
    AdaaTheme {
        FoundationScreen(
            state = FoundationState(
                configured = true,
                discovered = listOf(
                    BusinessSummary("1", "Adaa Beauty Studio", "physical", category = "Nails & Esthetics"),
                    BusinessSummary("2", "Luxe Balayage Lounge", "physical", category = "Hair Styling")
                )
            )
        )
    }
}

@Preview(name = "MainActivity · Explore (Dark)", showBackground = true, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MainActivityExploreDarkPreview() {
    AdaaTheme(darkTheme = true) {
        FoundationScreen(
            state = FoundationState(
                configured = true,
                discovered = listOf(
                    BusinessSummary("1", "Adaa Beauty Studio", "physical", category = "Nails & Esthetics")
                )
            )
        )
    }
}
