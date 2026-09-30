package mx.crnl.clinica.beta.feature.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.InfoBanner
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.feature.appointments.messageRes

private fun ReviewOutcome.messageRes(): Int = when (this) {
    ReviewOutcome.ACCOUNT_APPROVED -> R.string.review_done_account_approved
    ReviewOutcome.ACCOUNT_REJECTED -> R.string.review_done_account_rejected
    ReviewOutcome.ACCOUNT_SUSPENDED -> R.string.review_done_account_suspended
    ReviewOutcome.ACCOUNT_REACTIVATED -> R.string.review_done_account_reactivated
    ReviewOutcome.ACCESS_APPROVED -> R.string.review_done_access_approved
    ReviewOutcome.ACCESS_REJECTED -> R.string.review_done_access_rejected
    ReviewOutcome.GRANT_REVOKED -> R.string.review_done_grant_revoked
    ReviewOutcome.CHANGE_APPROVED -> R.string.review_done_change_approved
    ReviewOutcome.CHANGE_REJECTED -> R.string.review_done_change_rejected
}

/** Resultado de una revisión: confirmación de lo hecho o el motivo por el que no se pudo, sin datos técnicos. */
@Composable
fun ReviewFeedback(review: ReviewState, modifier: Modifier = Modifier) {
    when (review) {
        ReviewState.Idle, ReviewState.Working -> Unit
        is ReviewState.Done -> Row(
            modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
            Text(text = stringResource(review.outcome.messageRes()), style = MaterialTheme.typography.bodyLarge)
        }
        is ReviewState.Failed -> ErrorLine(stringResource(review.error.messageRes()), modifier)
        ReviewState.Unavailable -> ErrorLine(stringResource(R.string.review_unavailable), modifier)
    }
}

@Composable
private fun ErrorLine(message: String, modifier: Modifier) {
    Row(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

/** Nota fija bajo las acciones de una revisión: recuerda el alcance de la Beta sin presentarlo como seguridad de producción. */
@Composable
fun BetaScopeNote(text: String, modifier: Modifier = Modifier) {
    InfoBanner(text = text, modifier = modifier)
}
