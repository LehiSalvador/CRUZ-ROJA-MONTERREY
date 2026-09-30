# Recorridos de demostración de la Beta

Cuentas y contraseña ficticia: [`BETA_ACCESS.md`](BETA_ACCESS.md). Todos los datos son ficticios y locales; no hay red.

## Qué cuenta usar para cada rol

| Para probar | Cuenta | Nota |
| --- | --- | --- |
| Coordinación de Psicología | `claudia.benavides@example.org` | Asigna en Psicología y gestiona sus citas |
| Administración clínica | `hector.montemayor@example.org` | Las tres áreas |
| Profesional de Psicología | `mariana.elizondo@example.org` | Solo sus citas y su área |
| Profesional de Psicología (otra agenda) | `rodrigo.villarreal@example.org` | Tiene una cita hoy con Fernanda |
| Profesional de Nutrición / Medicina | `paola.garza@example.org` / `eduardo.trevino@example.org` | Ven de otras áreas solo su existencia; piden acceso de lectura |

El conjunto ficticio no incluye una cuenta de administración del sistema; su comportamiento (Inicio sin contenido clínico, solo cuentas en Solicitudes, usuarios y auditoría en solo lectura, sin restablecer) se verifica en las pruebas automáticas.

## Recorrido 1 · Coordinación: asignar, agendar, atender

1. Entra como coordinación de Psicología → **Pacientes** → *Luis Fernando Mireles Cortés* (no tiene profesional de Psicología).
2. Pestaña **Psicología** → **Asignar profesional** → elegir *Mariana Elizondo Cantú* → **Asignar**.
3. **Nueva cita** → fecha `dd/mm/aaaa` y hora (o los selectores) → **Guardar cita**. Se abre el detalle.
4. **Confirmar cita** → **Contactar por WhatsApp** (abre la otra aplicación con el texto neutro; sin ella se muestra un aviso).
5. **Marcar realizada** → confirmar → **Registrar atención** → tipo → **Registrar atención**.
6. El expediente se abre en Psicología con la atención en **Historial de atención**.

## Recorrido 2 · Profesional: sus citas y su atención

1. Entra como *Mariana* → **Citas**: solo aparecen sus citas. Abre una y observa que solo se ofrecen las acciones válidas para su estado.
2. **Pacientes → Mis pacientes** → *Ana Lucía Cavazos Ibarra* → **Psicología** → **Registrar atención**.

## Recorrido 3 · Otras áreas

1. Como coordinación de Psicología abre *Fernanda Guerra Domínguez* (atendida en Psicología y en Nutrición).
2. **Psicología** muestra el detalle; **Nutrición** solo dice que el paciente cuenta con atención registrada.

## Recorrido 4 · Reprogramar y persistencia

1. **Citas** → abre una cita → **Reprogramar** → cambia la hora → **Reprogramar**. La cita conserva su identidad y queda *Reprogramada*.
2. Cierra la aplicación desde recientes y ábrela: la cita y su estado se conservan.
3. **Cancelar cita** pide confirmación y razón opcional; la cita pasa al filtro **Historial**.

## Recorrido 5 · Aprobar una cuenta

1. En la pantalla de acceso elige **Solicitar cuenta**, captura los datos (rol *Profesional*, área *Psicología*) y envía. Intentar entrar responde que la solicitud está pendiente.
2. Entra como coordinación de Psicología (`claudia.benavides@example.org`) → **Solicitudes → Cuentas** (pendientes primero) → abre la solicitud → **Aprobar cuenta** (o **Rechazar solicitud**, con confirmación).
3. Cierra sesión y entra con la cuenta nueva y la contraseña que eligió. Una cuenta rechazada muestra que su solicitud fue rechazada. La solicitud de Valeria (Nutrición) solo la ve la administración clínica.

## Recorrido 6 · Acceso de lectura a otra área

1. Entra como `paola.garza@example.org` (Nutrición) → **Pacientes → Fernanda Guerra Domínguez** → en la tarjeta de Psicología, **Solicitar acceso de lectura** → motivo administrativo → **Enviar solicitud** (queda *Ver solicitud pendiente*).
2. Entra como `claudia.benavides@example.org` (Psicología, área propietaria) → **Solicitudes → Acceso interárea** → abre la solicitud → elige la vigencia (1, 7 o 30 días) → **Aprobar acceso de lectura**.
3. Vuelve como Paola: la pestaña **Psicología** de Fernanda muestra *Acceso temporal de lectura, válido hasta…* con el profesional, las citas, la atención y las evaluaciones, pero ninguna acción de escritura.
4. Como coordinación, abre la solicitud → **Revocar acceso**: la lectura se retira. También se retira sola al vencer la vigencia.

## Recorrido 7 · Cambio de profesional

1. Entra como `rodrigo.villarreal@example.org` → **Pacientes → Fernanda Guerra Domínguez → Psicología** → **Solicitar cambio de profesional** → elige a *Mariana Elizondo Cantú* y escribe el motivo → **Solicitar cambio**.
2. Entra como coordinación de Psicología → **Solicitudes → Cambio de profesional** → abre la solicitud (avisa de la cita de hoy que no se reasigna) → **Aprobar cambio** → **Confirmar cambio**.
3. En el expediente, la pestaña **Psicología** muestra a Mariana como profesional vigente y el **Historial de asignaciones** conserva a Rodrigo y a Mariana (la asignación anterior queda cerrada). La cita de hoy sigue a nombre de Rodrigo.

## Recorrido 8 · Evaluaciones

Como `mariana.elizondo@example.org` abre **Ana Lucía Cavazos Ibarra → Psicología → Evaluaciones → Instrumento A**: versión, modo (*Captura profesional*), fechas, profesional y el puntaje registrado con *Sin clasificación*. Los instrumentos son marcadores ficticios: no hay reactivos ni interpretación. *Instrumento B* de Diego aparece como *Cancelada · Sin resultado*. Nutrición no ve ninguna evaluación de Psicología.

## Recorrido 9 · Modo supervisado

Como `rodrigo.villarreal@example.org` abre **Diego Alejandro Treviño Salinas → Psicología → Evaluaciones → Instrumento A** (*Aplicación supervisada*) → **Ver modo supervisado**. La pantalla es aislada (sin barra inferior ni datos del paciente) y no muestra contenido del instrumento. **Volver con profesional** pide confirmación; no se crea ninguna aplicación ni resultado.

## Recorrido 10 · Usuarios y auditoría

Como `hector.montemayor@example.org` → **Perfil → Administración**. **Usuarios** agrupa por Pendientes, Activos, Suspendidos o rechazados e Inactivos; una cuenta activa se puede **Suspender** (con confirmación) y una suspendida **Reactivar**. **Auditoría** lista las acciones más recientes (inicio de sesión, aprobaciones, solicitudes…) con filtros por categoría y periodo, sin datos clínicos.

## Recorrido 11 · Restablecer los datos de Beta

Como administración clínica → **Perfil → Herramientas de Beta → Restablecer datos de Beta** → **Continuar** → **Restablecer ahora**. Se eliminan los cambios locales, se restauran los datos ficticios iniciales y se vuelve a la pantalla de acceso.

## Conflictos de horario

Agendar a *Diego Alejandro Treviño Salinas* hoy a las 10:30: su profesional (Rodrigo) atiende de 10:00 a 10:50, así que el formulario no guarda y muestra la cita con la que choca.

## Pruebas

```bash
./gradlew :app:testDebugUnitTest --tests "*ClinicalAppFlowsTest"   # recorridos de citas y atención
./gradlew :app:testDebugUnitTest --tests "*Phase4FlowsTest"          # solicitudes, acceso interárea, cambio, evaluaciones, administración
./gradlew :app:testDebugUnitTest                                   # suite completa
```
