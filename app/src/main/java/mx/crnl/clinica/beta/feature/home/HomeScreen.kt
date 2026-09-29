package mx.crnl.clinica.beta.feature.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState

@Composable
fun HomeScreen() {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.nav_home)) }) { padding ->
        EmptyState(
            icon = Icons.Filled.Home,
            title = stringResource(R.string.home_placeholder_title),
            message = stringResource(R.string.home_placeholder_message),
            modifier = Modifier.padding(padding),
        )
    }
}
