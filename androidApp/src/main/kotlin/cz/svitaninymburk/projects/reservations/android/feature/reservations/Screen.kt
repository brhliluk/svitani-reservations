package cz.svitaninymburk.projects.reservations.android.feature.reservations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun MyReservationsScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Moje rezervace — připravujeme", style = MaterialTheme.typography.bodyLarge)
    }
}
