package mx.crnl.clinica.beta.testing

import java.time.Instant
import java.time.ZonedDateTime

/** Identificadores del conjunto ficticio usados por las pruebas de Fase 3. */
object SeedIds {
    const val ANA = "b0000000-0000-4000-8000-000000000001" // Psicología con Mariana
    const val DIEGO = "b0000000-0000-4000-8000-000000000002" // Psicología con Rodrigo
    const val FERNANDA = "b0000000-0000-4000-8000-000000000003" // Psicología (Rodrigo) y Nutrición (Paola)
    const val JOSE = "b0000000-0000-4000-8000-000000000004" // Medicina con Eduardo
    const val REGINA = "b0000000-0000-4000-8000-000000000005" // Psicología con Rodrigo
    const val LUIS = "b0000000-0000-4000-8000-000000000006" // Nutrición (Paola) y Medicina (Eduardo)
    const val MARIA = "b0000000-0000-4000-8000-000000000007" // Medicina (Eduardo) y Nutrición (Paola)
    const val ANDRES = "b0000000-0000-4000-8000-000000000008" // Psicología: asignación cerrada, sin vigente

    const val CLAUDIA = BetaAccounts.COORDINATOR_ID
    const val HECTOR = BetaAccounts.ADMIN_ID
    const val MARIANA = BetaAccounts.PSYCHOLOGIST_ID
    const val RODRIGO = BetaAccounts.SECOND_PSYCHOLOGIST_ID
    const val PAOLA = BetaAccounts.NUTRITIONIST_ID
    const val EDUARDO = BetaAccounts.PHYSICIAN_ID
    const val VALERIA = "a0000000-0000-4000-8000-000000000007" // pendiente de aprobación
    const val GERARDO = "a0000000-0000-4000-8000-000000000008" // suspendido
    const val IVAN = "a0000000-0000-4000-8000-000000000009" // rechazado
    const val SOFIA = "a0000000-0000-4000-8000-00000000000a" // inactiva

    /** Cuenta técnica que no existe en el conjunto ficticio; las pruebas la insertan cuando la necesitan. */
    const val SYSTEM_ADMIN = "a0000000-0000-4000-8000-0000000000ff"

    const val CITA_FERNANDA_HOY_PSICOLOGIA = "e0000000-0000-4000-8000-000000000007" // hoy 10:00–10:50 confirmada, Rodrigo
    const val CITA_MARIA_HOY_MEDICINA = "e0000000-0000-4000-8000-000000000008" // hoy 13:00–13:30 programada, Eduardo
    const val CITA_LUIS_MANANA_NUTRICION = "e0000000-0000-4000-8000-000000000009" // mañana 09:30–10:15 programada, Paola
    const val CITA_ANA_PENDIENTE = "e0000000-0000-4000-8000-00000000000a" // en 3 días 11:00 pendiente, Mariana
    const val CITA_DIEGO_REPROGRAMADA = "e0000000-0000-4000-8000-00000000000b" // en 7 días 16:00 reprogramada, Rodrigo
    const val CITA_FERNANDA_NUTRICION = "e0000000-0000-4000-8000-00000000000c" // en 10 días 10:00 programada, Paola
    const val CITA_ANA_REALIZADA = "e0000000-0000-4000-8000-000000000001" // hace 21 días, realizada, con encuentro
    const val CITA_ANA_SIN_ASISTENCIA = "e0000000-0000-4000-8000-000000000005"
    const val CITA_REGINA_CANCELADA = "e0000000-0000-4000-8000-000000000006"
}

/** Instante de Monterrey: [dayOffset] días después del 2026-09-29 (el «hoy» de las pruebas) a la hora indicada. */
fun testInstant(dayOffset: Long, hour: Int, minute: Int = 0): Instant =
    ZonedDateTime.of(2026, 9, 29, hour, minute, 0, 0, TestZone).plusDays(dayOffset).toInstant()
