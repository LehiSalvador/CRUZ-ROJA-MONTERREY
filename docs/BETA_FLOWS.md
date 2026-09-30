# Recorridos de demostración de la Beta

Cuentas y contraseña ficticia: [`BETA_ACCESS.md`](BETA_ACCESS.md). Todos los datos son ficticios y locales; no hay red.

## Qué cuenta usar para cada rol

| Para probar | Cuenta | Nota |
| --- | --- | --- |
| Coordinación de Psicología | `claudia.benavides@example.org` | Asigna en Psicología y gestiona sus citas |
| Administración clínica | `hector.montemayor@example.org` | Las tres áreas |
| Profesional de Psicología | `mariana.elizondo@example.org` | Solo sus citas y su área |
| Profesional de Psicología (otra agenda) | `rodrigo.villarreal@example.org` | Tiene una cita hoy con Fernanda |
| Profesional de Nutrición / Medicina | `paola.garza@example.org` / `eduardo.trevino@example.org` | Ven de otras áreas solo su existencia |

El conjunto ficticio no incluye una cuenta de administración del sistema; su comportamiento (sin detalle clínico ni agenda) se verifica en las pruebas automáticas.

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

## Conflictos de horario

Agendar a *Diego Alejandro Treviño Salinas* hoy a las 10:30: su profesional (Rodrigo) atiende de 10:00 a 10:50, así que el formulario no guarda y muestra la cita con la que choca.

## Pruebas

```bash
./gradlew :app:testDebugUnitTest --tests "*ClinicalAppFlowsTest"   # recorridos completos
./gradlew :app:testDebugUnitTest                                   # suite completa
```
