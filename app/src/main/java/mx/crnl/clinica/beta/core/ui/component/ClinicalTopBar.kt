package mx.crnl.clinica.beta.core.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicalTopBar(title: String, modifier: Modifier = Modifier) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
