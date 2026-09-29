package mx.crnl.clinica.beta.feature.requests

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState

@Composable
fun RequestsScreen() {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.requests_title)) }) { padding ->
        EmptyState(
            icon = Icons.Filled.Email,
            title = stringResource(R.string.requests_placeholder_title),
            message = stringResource(R.string.requests_placeholder_message),
            modifier = Modifier.padding(padding),
        )
    }
}
