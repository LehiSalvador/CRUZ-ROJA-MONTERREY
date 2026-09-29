package mx.crnl.clinica.beta.domain.patient

/** Folio humano del paciente: `CRNL-` seguido de un consecutivo con al menos seis dígitos. */
object PatientNumber {
    private const val PREFIX = "CRNL-"
    private const val MIN_DIGITS = 6
    private val FORMAT = Regex("^CRNL-(\\d{$MIN_DIGITS,})$")

    /** El siguiente folio después del mayor válido; los valores con otro formato se ignoran. */
    fun next(existing: Iterable<String>): String {
        val highest = existing.mapNotNull { FORMAT.matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() }.maxOrNull() ?: 0L
        return PREFIX + (highest + 1).toString().padStart(MIN_DIGITS, '0')
    }
}
