package mx.crnl.clinica.beta.feature.patients.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ChoiceChipGroup
import mx.crnl.clinica.beta.core.ui.component.ClinicalTextField
import mx.crnl.clinica.beta.core.ui.component.DateOfBirthField
import mx.crnl.clinica.beta.core.ui.component.DropdownField
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.domain.patient.FieldIssue
import mx.crnl.clinica.beta.domain.patient.PatientField

/** Eventos de edición de un formulario de paciente; los ViewModels de alta y edición los implementan por igual. */
class PatientFormCallbacks(
    val onText: (PatientField, String) -> Unit,
    val onSex: (Sex) -> Unit,
    val onPopulationType: (PopulationType) -> Unit,
)

@Composable
fun PatientIdentityFields(form: PatientFormState, callbacks: PatientFormCallbacks, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val moveDown = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    val nameOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Next)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ClinicalTextField(
            value = form.input.firstName,
            onValueChange = { callbacks.onText(PatientField.FIRST_NAME, it) },
            label = stringResource(R.string.field_first_name),
            error = form.errorFor(PatientField.FIRST_NAME),
            keyboardOptions = nameOptions,
            keyboardActions = moveDown,
        )
        ClinicalTextField(
            value = form.input.paternalSurname,
            onValueChange = { callbacks.onText(PatientField.PATERNAL_SURNAME, it) },
            label = stringResource(R.string.field_paternal_surname),
            error = form.errorFor(PatientField.PATERNAL_SURNAME),
            keyboardOptions = nameOptions,
            keyboardActions = moveDown,
        )
        ClinicalTextField(
            value = form.input.maternalSurname,
            onValueChange = { callbacks.onText(PatientField.MATERNAL_SURNAME, it) },
            label = stringResource(R.string.field_maternal_surname),
            error = form.errorFor(PatientField.MATERNAL_SURNAME),
            keyboardOptions = nameOptions,
            keyboardActions = moveDown,
        )
        DateOfBirthField(
            digits = form.input.birthDate,
            onDigitsChange = { callbacks.onText(PatientField.BIRTH_DATE, it) },
            label = stringResource(R.string.field_birth_date),
            helper = stringResource(R.string.field_birth_date_hint),
            error = form.errorFor(PatientField.BIRTH_DATE),
        )
        ClinicalTextField(
            value = form.input.birthPlace,
            onValueChange = { callbacks.onText(PatientField.BIRTH_PLACE, it) },
            label = stringResource(R.string.field_birth_place),
            error = form.errorFor(PatientField.BIRTH_PLACE),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
        ChoiceChipGroup(
            label = stringResource(R.string.field_sex),
            options = Sex.entries,
            selected = form.input.sex,
            onSelected = callbacks.onSex,
            optionLabel = { stringResource(it.labelRes()) },
            error = form.errorFor(PatientField.SEX),
        )
    }
}

@Composable
fun PatientContactFields(form: PatientFormState, callbacks: PatientFormCallbacks, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val moveDown = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ClinicalTextField(
            value = form.input.phone,
            onValueChange = { callbacks.onText(PatientField.PHONE, it) },
            label = stringResource(R.string.field_phone),
            error = form.errorFor(PatientField.PHONE),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            keyboardActions = moveDown,
        )
        ClinicalTextField(
            value = form.input.email,
            onValueChange = { callbacks.onText(PatientField.EMAIL, it) },
            label = stringResource(R.string.field_email),
            error = form.errorFor(PatientField.EMAIL),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = moveDown,
        )
        ClinicalTextField(
            value = form.input.municipality,
            onValueChange = { callbacks.onText(PatientField.MUNICIPALITY, it) },
            label = stringResource(R.string.field_municipality),
            error = form.errorFor(PatientField.MUNICIPALITY),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
    }
}

@Composable
fun PatientPopulationFields(form: PatientFormState, callbacks: PatientFormCallbacks, modifier: Modifier = Modifier) {
    DropdownField(
        label = stringResource(R.string.field_population),
        options = PopulationType.entries,
        selected = form.input.populationType,
        onSelected = callbacks.onPopulationType,
        optionLabel = { stringResource(it.labelRes()) },
        modifier = modifier,
        error = form.errorFor(PatientField.POPULATION_TYPE),
    )
}

@Composable
private fun PatientFormState.errorFor(field: PatientField): String? = issues[field]?.let { issue ->
    stringResource(issue.messageRes(field))
}

private fun FieldIssue.messageRes(field: PatientField): Int = when (this) {
    FieldIssue.REQUIRED -> if (field == PatientField.SEX || field == PatientField.POPULATION_TYPE) {
        R.string.issue_select
    } else {
        R.string.issue_required
    }
    FieldIssue.TOO_LONG -> R.string.issue_too_long
    FieldIssue.FUTURE_DATE -> R.string.issue_future_date
    FieldIssue.TOO_OLD -> R.string.issue_too_old
    FieldIssue.INVALID -> when (field) {
        PatientField.BIRTH_DATE -> R.string.issue_invalid_date
        PatientField.PHONE -> R.string.issue_invalid_phone
        PatientField.EMAIL -> R.string.issue_invalid_email
        PatientField.FIRST_NAME, PatientField.PATERNAL_SURNAME, PatientField.MATERNAL_SURNAME -> R.string.issue_invalid_name
        else -> R.string.issue_invalid
    }
}
