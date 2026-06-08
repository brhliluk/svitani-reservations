package cz.svitaninymburk.projects.reservations.android.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cz.svitaninymburk.projects.reservations.android.R
import cz.svitaninymburk.projects.reservations.android.feature.events.EventsScreen
import cz.svitaninymburk.projects.reservations.android.feature.profile.ProfileScreen
import cz.svitaninymburk.projects.reservations.android.feature.reservations.MyReservationsScreen

private enum class MainTab { EVENTS, RESERVATIONS, PROFILE }

@Composable
fun MainScreen(onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(MainTab.EVENTS) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.EVENTS,
                    onClick = { selectedTab = MainTab.EVENTS },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_events)) },
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.RESERVATIONS,
                    onClick = { selectedTab = MainTab.RESERVATIONS },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_reservations)) },
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.PROFILE,
                    onClick = { selectedTab = MainTab.PROFILE },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_profile)) },
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                MainTab.EVENTS -> EventsScreen()
                MainTab.RESERVATIONS -> MyReservationsScreen()
                MainTab.PROFILE -> ProfileScreen(onLogout = onLogout)
            }
        }
    }
}
