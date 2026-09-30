# Plataforma Clínica CRNL — Beta Android

Aplicación Android nativa de la plataforma clínica de Cruz Roja Nuevo León (`0.1.0-beta`). Incluye acceso local con cuentas ficticias, solicitud de cuenta, Inicio con indicadores reales, el núcleo de pacientes (listado y búsqueda, alta guiada con detección de posibles duplicados, edición de datos generales y de contacto) y el flujo operativo de atención: asignación inicial de profesional por área, agenda de citas, contacto por WhatsApp (solo enlace externo), registro base de atención y línea de atención por área en el expediente.

## Stack

| Área | Tecnología |
| --- | --- |
| Lenguaje / UI | Kotlin 2.4, Jetpack Compose, Material 3 |
| Build | Gradle 9.6 (Kotlin DSL), Android Gradle Plugin 9.4, catálogo de versiones en `gradle/libs.versions.toml` |
| Android | `minSdk 26` · `compileSdk 36` · `targetSdk 36` · `applicationId mx.crnl.clinica.beta` |
| Datos | Room (KSP) para datos estructurados; Preferences DataStore solo para sesión y configuración simple |
| Asincronía | Coroutines, Flow y StateFlow |
| Navegación | Navigation Compose con rutas tipadas (kotlinx.serialization) |
| Sin | Backend, red, permisos de Android, inyección de dependencias con framework |

La Beta funciona sin conexión: todo el almacenamiento es local y los datos precargados son **ficticios**.

## Requisitos para compilar

- JDK 17.
- Android SDK con `platforms;android-36` y `build-tools;36.0.0`, indicado con `ANDROID_HOME` o con `sdk.dir` en `local.properties` (archivo local, fuera de Git).
- Conexión a Internet solo para que Gradle descargue dependencias la primera vez.

## Comandos

```bash
./gradlew :app:testDebugUnitTest   # pruebas unitarias (JVM + Robolectric)
./gradlew :app:lintDebug           # Android Lint
./gradlew assembleDebug            # genera el APK debug
```

En Windows usa `gradlew.bat`. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. La primera corrida de pruebas descarga el JAR de Android que usa Robolectric.

## Arquitectura

```text
Compose UI → ViewModel → contrato de repository → implementación local → Room / DataStore
```

```text
app/                   MainActivity, ClinicalApplication, AppContainer (ensamblado manual), shell de navegación
core/
  database/            ClinicalDatabase (Room, esquema v2) y migraciones
  datastore/           creación del DataStore de sesión
  demo/                carga, validación y mapeo del conjunto de datos ficticios
  navigation/          rutas tipadas y destinos de la barra inferior
  security/            derivación y verificación de contraseñas (PBKDF2)
  ui/                  tema, componentes reutilizables, estados de UI y etiquetas de catálogos
  util/                zona horaria y formatos de fecha
data/
  local/               entidades, DAOs y mappers de Room
  repository/          implementaciones locales de los contratos
domain/
  model/               modelos y catálogos sin dependencias de Android
  patient/ account/ home/ text/   reglas puras: búsqueda, duplicados, folio, validaciones, indicadores de Inicio
  appointment/         máquina de estados, conflictos de horario, reglas y agenda de citas
  access/              política de acceso de la Beta (roles y áreas)
  clinical/            expediente visible por rol, capacidades por área, reglas del encuentro
  contact/             texto neutro y enlace de WhatsApp
  repository/          contratos (Auth, Session, Patient, Appointment, ProfessionalAssignment, Encounter, Home)
feature/               splash, auth, session, home, patients (listado, expediente, alta, edición), appointments (agenda, detalle, formularios), assignments, encounters, requests, profile
```

- La UI depende solo de los contratos de `domain/repository`. Sustituir la fuente local por una API o Supabase consiste en implementar esos contratos y cambiar el ensamblado en `app/AppContainer.kt`.
- Un solo módulo Gradle; las features se materializan como paquetes cuando tienen código.

## Acceso y sesión

- El acceso es **local y de Beta**: verifica correo y contraseña contra cuentas ficticias guardadas en Room. No es seguridad de producción ni usa credenciales institucionales.
- Las contraseñas no se guardan: la tabla `demo_credentials` (1:1 con `demo_users`) conserva solo sal, algoritmo, costo y el resultado de PBKDF2-HMAC-SHA256. Las cuentas de prueba y sus contraseñas ficticias están en [`docs/BETA_ACCESS.md`](docs/BETA_ACCESS.md).
- La decisión de acceso vive en `AuthRepository`: solo una cuenta `ACTIVE` con contraseña correcta abre sesión; `PENDING_APPROVAL`, `SUSPENDED`, `REJECTED` e `INACTIVE` se rechazan con su motivo, y un correo o contraseña incorrectos reciben el mismo mensaje.
- DataStore guarda `session_active` y `logged_user_id`. Al arrancar, la sesión solo se restaura si el usuario existe y sigue activo; en otro caso se limpia y se vuelve al acceso. Cerrar sesión (o dejar de estar activa) regresa al acceso y vacía la pila de navegación autenticada.
- **Solicitar cuenta** crea una cuenta `PENDING_APPROVAL` sin iniciar sesión y sin poder autoaprobarse; solo se piden roles clínicos.

## Pacientes

- Alta guiada en seis pasos (identificación, contacto, población, consentimientos, coincidencias y confirmación). Los consentimientos institucionales no están configurados, por lo que el paso no registra aceptación alguna.
- El folio `CRNL-000001` se genera al guardar como el siguiente al mayor folio válido; el identificador técnico es un UUID distinto. La edad nunca se almacena.
- Antes de crear (y al editar identidad o contacto) se buscan posibles duplicados por correo, teléfono y nombre con fecha de nacimiento. Es una advertencia con sus razones, nunca un bloqueo.
- La búsqueda cubre folio, nombre, apellidos, teléfono, correo y fecha de nacimiento, sin distinguir mayúsculas ni acentos.
- Alta, edición y consulta del expediente quedan en la bitácora (`audit_entries`) junto con inicio y cierre de sesión y solicitudes de cuenta, sin contraseñas ni datos clínicos.

## Asignación, citas y atención base

- **Asignación inicial**: desde el expediente, en la pestaña del área, quien tiene permiso elige un profesional activo del área y, si quiere, una razón administrativa. Solo existe la asignación inicial: si el área ya tiene profesional vigente no se ofrece cambiarlo (el cambio pertenece a un módulo posterior) y el historial nunca se sobrescribe. Cada asignación guarda quién la hizo.
- **Citas**: agenda con filtros *Próximas / Hoy / Historial*, detalle, alta, edición de datos administrativos y reprogramación. Una cita es una entidad administrativa distinta del encuentro clínico; no se borra (cancelar cambia su estado) y no captura contenido clínico. La cita se agenda con el profesional asignado al paciente en el área; si no lo hay, el formulario lo indica y ofrece ir a asignarlo, pero nunca crea una asignación por su cuenta. Los instantes se guardan en UTC y se muestran en hora de Monterrey. Se bloquea guardar una cita que se solapa con otra del mismo profesional o del mismo paciente (se muestra con cuál choca); las canceladas no ocupan horario.
- **Estados**: `AppointmentStatePolicy` es una regla operativa y reversible de la Beta, no la matriz institucional final. Realizada, no asistió y cancelada son terminales. Desviación de la matriz recomendada: una cita ya reprogramada puede reprogramarse de nuevo. Marcar realizada no crea ningún encuentro; el repositorio rechaza cualquier transición que la política no permita, aunque la interfaz oculte el botón.
- **WhatsApp**: solo un enlace externo (`whatsapp://send`) que abre la aplicación de WhatsApp con un texto neutro (nombre, fecha y hora; sin área, folio, profesional ni datos clínicos). No hay cliente HTTP, no hay permiso de Internet, no se envía nada automáticamente y no se comprueba si el número tiene WhatsApp. Sin la aplicación instalada se muestra un aviso y no hay constancia. La bitácora solo registra que se abrió (sin teléfono ni texto).
- **Atención base**: `ClinicalEncounter` registra tipo (Inicial, Seguimiento, Intervención, Evaluación, Cierre, Otro), cuándo ocurrió (`eventAt`), cuándo se capturó (`recordedAt`), profesional, área y, si aplica, la cita relacionada. No hay campos clínicos: los formularios oficiales de cada área todavía no existen. Un encuentro nace completo y es de solo lectura; un doble toque no crea dos idénticos. Se registra desde el detalle de una cita realizada o desde la pestaña del área.
- **Línea de atención por área**: cada pestaña de área muestra el profesional vigente, el historial de asignaciones, la próxima cita, las citas recientes y la línea de atención (encuentros por `eventAt`, del más reciente al más antiguo).
- **Visibilidad entre áreas**: de un área que la persona no puede ver solo se muestra que el paciente cuenta con atención registrada (con conteo y última fecha): nunca profesional, tipo, citas ni evaluaciones. El recorte lo hace el dominio (`PatientDetailAssembler`) antes de que llegue a las pantallas.

### Política de acceso de la Beta

`BetaClinicalAccessPolicy` es una defensa local, pequeña y reemplazable para las operaciones nuevas. **No es la matriz institucional final ni seguridad de producción** (no hay backend que la respalde).

| Rol | Ve el detalle de | Asigna | Gestiona citas | Registra atención |
| --- | --- | --- | --- | --- |
| Profesional | su área | no | solo en las que es el profesional | de pacientes asignados a él, en su área |
| Coordinación de área | su área | su área | su área | su área (atribuida al profesional asignado) |
| Administración clínica | las tres áreas | las tres áreas | las tres áreas | las tres áreas |
| Administración del sistema | ninguna | no | no | no |

Los repositorios vuelven a comprobar la política contra la cuenta vigente al escribir; las pantallas solo ocultan lo que no corresponde.

## Datos de la Beta

- Room guarda usuarios, credenciales, pacientes, contactos, asignaciones (con historial), citas, encuentros, evaluaciones y su bitácora de auditoría. Convenciones del modelo: UUID técnico separado del folio humano, timestamps en UTC, sin borrados en cascada (las claves foráneas usan `RESTRICT`) y sin borrado físico de pacientes.
- El esquema es la versión 2 y `MIGRATION_1_2` actualiza instalaciones de la primera versión sin perder datos (nunca se recrea la base). Los esquemas se exportan a `app/schemas/` y una prueba de migración los verifica.
- El conjunto ficticio (`app/src/main/assets/seed_*.json`) es un arranque, no la base viva: se carga en una sola transacción, es aditivo e idempotente (llaves determinísticas con `IGNORE`) y su versión aplicada se guarda en DataStore; una instalación anterior recibe solo lo que le falta.
- Antes de cargar, `DemoSeedValidator` comprueba que el conjunto es ficticio (correos en dominios `example.*`, teléfonos y cédulas en rangos no asignables, instrumentos marcadores), que las referencias son válidas y que los códigos pertenecen a los catálogos.
- Las fechas de la agenda se expresan como días relativos al primer arranque, de modo que la agenda nunca queda vencida.

## Decisiones que no se deducen del código

- **Versiones de Compose, Lifecycle y Navigation**: las publicaciones más recientes exigen `compileSdk 37`. Se fijaron las últimas estables compatibles con `compileSdk 36` (Compose BOM 2026.06.01, Lifecycle 2.10.0, Navigation Compose 2.9.8).
- **Navegación**: Navigation Compose estable en lugar de Navigation 3, con un back stack independiente por pestaña; la pestaña Pacientes es un grafo anidado que contiene listado, expediente, alta y edición. Los formularios ocultan la barra inferior.
- **Pantallas de distinto tamaño**: el contenido se limita a una columna centrada de 640 dp (barra superior, botón flotante y barra de acciones se alinean con ella); en un teléfono horizontal (alto < 480 dp) la barra inferior se cambia por un riel lateral y las pantallas de formulario ocupan toda la pantalla.
- **Costo de PBKDF2**: 120 000 iteraciones, guardadas con cada credencial para poder subirlas sin invalidar cuentas.
- **Mis pacientes**: son los pacientes con una asignación vigente al profesional; crear un paciente no crea una asignación.
- **`room-ktx`**: no se declara; en Room 2.8 esas extensiones viven en `room-runtime`.
- **Prueba de migración**: crea la base de la versión 1 a partir del esquema exportado (`app/schemas/…/1.json`) y la abre con Room y `MIGRATION_1_2`; `MigrationTestHelper` no localiza los assets de esquemas bajo Robolectric, y así no se añade `room-testing`.
- **Pruebas de DataStore**: corren bajo Robolectric porque DataStore reemplaza su archivo con `File.renameTo`, que en una JVM de Windows falla si el destino ya existe (en Android no).
- **Seguridad**: `allowBackup` desactivado, sin permisos, sin tráfico HTTP en claro; los secretos, llaves y `.env` están excluidos por `.gitignore`.
