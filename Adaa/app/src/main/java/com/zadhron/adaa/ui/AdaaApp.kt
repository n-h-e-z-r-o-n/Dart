package com.zadhron.adaa.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zadhron.adaa.ui.components.AdaaBottomNavBar
import com.zadhron.adaa.ui.components.AdaaScreen
import com.zadhron.adaa.ui.preview.PreviewData
import com.zadhron.adaa.ui.business.legacy.ClientsScreen
import com.zadhron.adaa.ui.business.legacy.FinanceScreen
import com.zadhron.adaa.ui.business.legacy.ScheduleScreen
import com.zadhron.adaa.ui.business.legacy.ServicesScreen
import com.zadhron.adaa.ui.theme.AdaaTheme
import com.zadhron.adaa.ui.viewmodel.AdaaViewModel
import java.time.LocalDate

@Composable
fun AdaaApp(viewModel: AdaaViewModel = viewModel()) {
    var currentScreen by remember { mutableStateOf(AdaaScreen.SCHEDULE) }

    AdaaAppScaffold(
        currentScreen = currentScreen,
        onScreenSelected = { currentScreen = it }
    ) {
        when (currentScreen) {
            AdaaScreen.SCHEDULE -> ScheduleScreen(viewModel = viewModel)
            AdaaScreen.CLIENTS -> ClientsScreen(viewModel = viewModel)
            AdaaScreen.SERVICES -> ServicesScreen(viewModel = viewModel)
            AdaaScreen.FINANCES -> FinanceScreen(viewModel = viewModel)
        }
    }
}

@Composable
fun AdaaAppScaffold(
    currentScreen: AdaaScreen,
    onScreenSelected: (AdaaScreen) -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        bottomBar = {
            AdaaBottomNavBar(
                currentScreen = currentScreen,
                onScreenSelected = onScreenSelected
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            content()
        }
    }
}

@Preview(name = "Adaa Full App · Schedule (Light)", showBackground = true, showSystemUi = true)
@Composable
fun AdaaAppSchedulePreview() {
    AdaaTheme {
        AdaaAppScaffold(
            currentScreen = AdaaScreen.SCHEDULE,
            onScreenSelected = {}
        ) {
            ScheduleScreen(
                selectedDate = LocalDate.now(),
                appointments = PreviewData.sampleAppointments,
                rawClients = PreviewData.sampleClients,
                rawServices = PreviewData.sampleServices
            )
        }
    }
}

@Preview(name = "Adaa Full App · Schedule (Dark)", showBackground = true, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AdaaAppScheduleDarkPreview() {
    AdaaTheme(darkTheme = true) {
        AdaaAppScaffold(
            currentScreen = AdaaScreen.SCHEDULE,
            onScreenSelected = {}
        ) {
            ScheduleScreen(
                selectedDate = LocalDate.now(),
                appointments = PreviewData.sampleAppointments,
                rawClients = PreviewData.sampleClients,
                rawServices = PreviewData.sampleServices
            )
        }
    }
}

@Preview(name = "Adaa Full App · Clients", showBackground = true, showSystemUi = true)
@Composable
fun AdaaAppClientsPreview() {
    AdaaTheme {
        AdaaAppScaffold(
            currentScreen = AdaaScreen.CLIENTS,
            onScreenSelected = {}
        ) {
            ClientsScreen(
                clients = PreviewData.sampleClients,
                searchQuery = ""
            )
        }
    }
}

@Preview(name = "Adaa Full App · Finances", showBackground = true, showSystemUi = true)
@Composable
fun AdaaAppFinancesPreview() {
    AdaaTheme {
        AdaaAppScaffold(
            currentScreen = AdaaScreen.FINANCES,
            onScreenSelected = {}
        ) {
            FinanceScreen(
                metrics = PreviewData.sampleMetrics,
                allAppointments = PreviewData.sampleAppointments
            )
        }
    }
}
