package mx.crnl.clinica.beta.domain.patient

import mx.crnl.clinica.beta.domain.model.ContactType
import mx.crnl.clinica.beta.domain.model.DuplicateCandidate
import mx.crnl.clinica.beta.domain.model.DuplicateReason
import mx.crnl.clinica.beta.domain.model.PatientDraft
import mx.crnl.clinica.beta.domain.model.PatientRecord
import mx.crnl.clinica.beta.domain.text.TextNormalizer

/**
 * Señala posibles pacientes ya registrados antes de crear o modificar uno. Nunca bloquea: dos personas
 * distintas pueden compartir nombre o teléfono, así que el resultado es una advertencia con sus razones.
 *
 * Reglas (cualquiera basta):
 * - mismo correo normalizado;
 * - mismo teléfono (últimos 10 dígitos);
 * - mismo nombre, apellidos y fecha de nacimiento;
 * - mismo apellido paterno y fecha de nacimiento con un nombre compatible («Ana» y «Ana Lucía») cuando
 *   el apellido materno falta en alguno de los dos registros y no permite distinguirlos.
 */
object DuplicateDetector {
    fun detect(
        draft: PatientDraft,
        existing: Collection<PatientRecord>,
        excludePatientId: String? = null,
    ): List<DuplicateCandidate> {
        val email = draft.email?.let(TextNormalizer::email)?.takeIf(String::isNotEmpty)
        val phone = draft.phone?.let(TextNormalizer::phoneDigits)?.takeIf { it.length >= MIN_PHONE_DIGITS }
        val identity = Identity.of(draft.firstName, draft.paternalSurname, draft.maternalSurname)

        return existing
            .asSequence()
            .filter { it.patient.patientId != excludePatientId }
            .mapNotNull { record ->
                val reasons = buildSet {
                    if (email != null && record.hasEmail(email)) add(DuplicateReason.SAME_EMAIL)
                    if (phone != null && record.hasPhone(phone)) add(DuplicateReason.SAME_PHONE)
                    if (record.patient.birthDate == draft.birthDate) {
                        nameReason(identity, record)?.let(::add)
                    }
                }
                if (reasons.isEmpty()) null else DuplicateCandidate(record.patient, reasons)
            }
            .sortedWith(compareByDescending<DuplicateCandidate> { it.reasons.size }.thenBy { it.patient.patientNumber })
            .toList()
    }

    /** Verdadero si el cambio toca algún dato con el que se detectan duplicados (nombre, nacimiento, teléfono o correo). */
    fun changesIdentity(before: PatientDraft, after: PatientDraft): Boolean {
        val previous = Identity.of(before.firstName, before.paternalSurname, before.maternalSurname)
        val next = Identity.of(after.firstName, after.paternalSurname, after.maternalSurname)
        return previous.first != next.first ||
            previous.paternal != next.paternal ||
            previous.maternal != next.maternal ||
            before.birthDate != after.birthDate ||
            before.phone.orEmpty().let(TextNormalizer::phoneDigits) != after.phone.orEmpty().let(TextNormalizer::phoneDigits) ||
            before.email.orEmpty().let(TextNormalizer::email) != after.email.orEmpty().let(TextNormalizer::email)
    }

    private fun nameReason(draft: Identity, record: PatientRecord): DuplicateReason? {
        val other = Identity.of(record.patient.firstName, record.patient.paternalSurname, record.patient.maternalSurname)
        if (draft.paternal != other.paternal) return null
        val sameMaternal = draft.maternal == other.maternal
        val sameFirst = draft.first == other.first
        if (sameMaternal && sameFirst) return DuplicateReason.SAME_NAME_AND_BIRTH_DATE
        val maternalUnresolved = draft.maternal == null || other.maternal == null
        val compatibleFirst = sameFirst || draft.first.startsWith("${other.first} ") || other.first.startsWith("${draft.first} ")
        return if ((sameMaternal || maternalUnresolved) && compatibleFirst) DuplicateReason.SIMILAR_NAME_AND_BIRTH_DATE else null
    }

    private fun PatientRecord.hasEmail(email: String): Boolean =
        contacts.any { it.type == ContactType.EMAIL && TextNormalizer.email(it.value) == email }

    private fun PatientRecord.hasPhone(phone: String): Boolean =
        contacts.any { it.type == ContactType.PHONE && TextNormalizer.phoneDigits(it.value) == phone }

    private class Identity(val first: String, val paternal: String, val maternal: String?) {
        companion object {
            fun of(first: String, paternal: String, maternal: String?) = Identity(
                first = TextNormalizer.fold(first),
                paternal = TextNormalizer.fold(paternal),
                maternal = maternal?.let(TextNormalizer::fold)?.takeIf(String::isNotEmpty),
            )
        }
    }

    private const val MIN_PHONE_DIGITS = 7
}
