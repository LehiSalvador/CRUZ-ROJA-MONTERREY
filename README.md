# Plataforma Clínica CRNL — Beta Android

Aplicación Android nativa de la plataforma clínica de Cruz Roja Nuevo León (`0.1.0-beta`). Incluye acceso local con cuentas ficticias, solicitud y aprobación de cuentas, Inicio con indicadores reales por rol, el núcleo de pacientes (listado y búsqueda, alta guiada con detección de posibles duplicados, edición de datos generales y de contacto), el flujo operativo de atención (asignación de profesional por área, agenda de citas, contacto por WhatsApp como enlace externo, registro base de atención y línea de atención por área), el acceso de lectura entre áreas con vigencia, el cambio formal de profesional, el historial estructural de evaluaciones, la vista previa aislada del modo supervisado y las herramientas de administración de la Beta (usuarios, auditoría y restablecimiento de datos).

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

## Versión y distribución

`0.1.0-beta` (`versionCode 1`). Las notas de la versión están en [`docs/RELEASE_NOTES_0.1.0-beta.md`](docs/RELEASE_NOTES_0.1.0-beta.md) y el APK firmado se publica como *pre-release* en GitHub Releases junto con su `.sha256`; el APK nunca se versiona en Git.

```bash
./gradlew :app:assembleRelease :app:lintRelease
```

- La firma de `release` se lee **solo** de cuatro variables de entorno del proceso de compilación: `CRNL_KEYSTORE_FILE`, `CRNL_KEYSTORE_PASSWORD`, `CRNL_KEY_ALIAS` y `CRNL_KEY_PASSWORD`. Si falta alguna, el `release` se genera sin firmar y `debug` no cambia.
- El almacén de llaves y sus contraseñas viven fuera del repositorio y no se copian a Gradle, a los recursos, al APK ni a la documentación (`*.jks`, `release.env` y `signing.properties` están en `.gitignore`).
- Un APK firmado con la llave de la Beta no se instala sobre uno de depuración: se desinstala el anterior primero (los datos locales se pierden).
- Verificación: `apksigner verify --print-certs app-release.apk` y comparar `sha256` con el archivo publicado.

## Arquitectura

```text
Compose UI → ViewModel → contrato de repository → implementación local → Room / DataStore
```

```text
app/                   MainActivity, ClinicalApplication, AppContainer (ensamblado manual), shell de navegación
core/
  database/            ClinicalDatabase (Room, esquema v3) y migraciones
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
  access/              políticas de la Beta: acceso clínico por rol, administración de cuentas, acceso interárea de lectura, cambio de profesional y herramientas de administración
  request/             bandejas de la pestaña Solicitudes por rol
  clinical/            expediente visible por rol, capacidades por área, reglas del encuentro
  contact/             texto neutro y enlace de WhatsApp
  repository/          contratos (Auth, Session, Patient, Appointment, ProfessionalAssignment, Encounter, Home, AccountAdministration, AccessRequest, ProfessionalChange, Assessment, Audit, BetaMaintenance)
feature/               splash, auth, session, home, patients (listado, expediente, alta, edición), appointments (agenda, detalle, formularios), assignments, encounters, requests (bandejas, detalles y formularios de solicitud), assessments (historial, detalle y modo supervisado), admin (usuarios, auditoría, herramientas de Beta), profile
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

- **Asignación inicial**: desde el expediente, en la pestaña del área, quien tiene permiso elige un profesional activo del área y, si quiere, una razón administrativa. La asignación inicial es directa; con un profesional vigente, cambiarlo es una solicitud formal que otra persona resuelve (ver *Cambio de profesional*). El historial nunca se sobrescribe y cada asignación guarda quién la hizo.
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

## Solicitudes, acceso interárea, evaluaciones y administración

- **Solicitudes** (pestaña): una bandeja por categoría y por rol. Profesionales ven sus solicitudes de *Acceso interárea* y de *Cambio de profesional*; coordinación y administración clínica ven además *Cuentas*; el administrador del sistema solo ve cuentas (lo demás está ligado a pacientes). Lo pendiente va primero y el historial después; las tarjetas nunca llevan datos clínicos.
- **Cuentas**: quien solicita una cuenta la deja `PENDING_APPROVAL` (con su contraseña ya derivada). Coordinación aprueba o rechaza únicamente profesionales de su propia área; administración clínica, profesionales y coordinación de cualquiera de las áreas clínicas; el administrador del sistema consulta pero no aprueba, porque aprobar concede acceso clínico. Nadie actúa sobre su propia cuenta. Aprobar no crea ni muestra contraseñas ni abre sesión; rechazar no borra nada. Administración clínica puede además suspender y reactivar (no hay borrado). La acción exige que la cuenta siga en el estado que la persona veía: dos revisiones simultáneas no duplican el cambio ni su auditoría.
- **Acceso interárea (solo lectura)**: desde un área que la persona no ve, *Solicitar acceso de lectura* con un motivo administrativo. Revisa la coordinación del área propietaria o administración clínica, nunca quien lo pidió. Al aprobar se elige la vigencia (1, 7 o 30 días; la propuesta de 7 días es solo una conveniencia de la Beta) y se crea una concesión (`access_grants`) de lectura para esa persona, ese paciente y esa área. La concesión rige mientras esté activa y dentro de su vigencia, vence sola (sin programador: la lectura se reevalúa en cada cambio y en cada instante de inicio o vencimiento) y puede revocarse, sin borrarse. Añade lectura del profesional vigente, citas, atención y evaluaciones del área, marcada como *Acceso temporal de lectura, válido hasta…*; **nunca** habilita asignar, agendar, registrar atención, cancelar, editar, cambiar profesional ni abrir el modo supervisado.
- **Cambio de profesional**: el profesional asignado, la coordinación del área o administración clínica piden el cambio; otra persona (coordinación del área o administración clínica) lo resuelve, nunca quien lo pidió. Aprobar cierra la asignación vigente (`ENDED`) y abre otra con el profesional propuesto, en una sola transacción y solo si la vigente sigue siendo la que se veía; el historial de asignaciones, los encuentros y las citas pasadas conservan a su profesional. Las citas por ocurrir con el profesional anterior **no se reasignan solas**: se avisa antes de aprobar y quedan a su nombre para revisión administrativa.
- **Evaluaciones**: historial estructural y de solo lectura por área (Psicología en el conjunto ficticio). Los códigos `DEV_PLACEHOLDER_A/B` se muestran como *Instrumento A/B* con versión, modo (*Captura profesional* / *Aplicación supervisada*), fecha, profesional y el puntaje ya registrado con *Sin clasificación*; no se calcula, clasifica ni interpreta nada, y no existen reactivos, respuestas ni instrumentos reales (BAI/BDI). Un código no reconocido se muestra como *Instrumento institucional no configurado*. Solo si hay tres o más registros comparables del mismo instrumento y versión se lista su evolución (fecha y valor), sin mezclar instrumentos.
- **Modo supervisado**: vista previa a pantalla completa, aislada (sin barra inferior, sin datos del paciente, sin contenido del instrumento), con salida confirmada (*Volver con profesional*). No crea aplicaciones ni resultados; solo la abre quien puede por su rol, y queda una constancia en la bitácora.
- **Auditoría**: consulta de solo lectura para administración clínica y del sistema, filtrable por categoría y periodo; muestra fecha, actor, acción, tipo de entidad, área y resultado, sin metadata ni identificadores. La bitácora nunca guarda el motivo de una solicitud ni contenido clínico.
- **Restablecer datos de Beta** (administración clínica, en *Perfil → Herramientas de Beta*): con dos confirmaciones, vacía la base local y recarga el conjunto ficticio inicial en una sola transacción, cierra la sesión y deja constancia. No toca archivos externos ni configuración fuera de la base.
- **Navegación por rol**: las mismas cinco pestañas para todos; las rutas de administración vuelven a validar la política y, sin permiso, no consultan ni muestran datos. Volver a tocar la pestaña abierta regresa a su pantalla raíz. Con fuentes muy grandes la barra inferior no corta etiquetas: si alguna no cabe, muestra solo iconos con su nombre accesible.

| Rol | Cuentas | Acceso interárea | Cambio de profesional | Auditoría / Usuarios | Restablecer |
| --- | --- | --- | --- | --- | --- |
| Profesional | no | pide (áreas que no ve) | pide (sus pacientes) | no | no |
| Coordinación de área | profesionales de su área | pide; revisa lo de su área | pide; resuelve lo de su área | no | no |
| Administración clínica | profesionales y coordinación | revisa y revoca todo | pide y resuelve | sí (gestiona) | sí |
| Administración del sistema | solo consulta | no | no | sí (consulta) | no |

Esta matriz es provisional y local: **no es la matriz institucional final ni seguridad de producción**.

## Datos de la Beta

- Room guarda usuarios, credenciales, pacientes, contactos, asignaciones (con historial), citas, encuentros, evaluaciones y su bitácora de auditoría. Convenciones del modelo: UUID técnico separado del folio humano, timestamps en UTC, sin borrados en cascada (las claves foráneas usan `RESTRICT`) y sin borrado físico de pacientes.
- El esquema es la versión 3. `MIGRATION_1_2` y `MIGRATION_2_3` actualizan instalaciones anteriores sin perder datos (nunca se recrea la base); la 2→3 solo crea `access_grants` y `professional_override_requests`, con claves foráneas `RESTRICT` e índices. Los esquemas se exportan a `app/schemas/` y pruebas de migración los verifican (v1→v2→v3 y v2→v3).
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
- **Prueba de migración**: crea la base de la versión anterior a partir del esquema exportado (`app/schemas/…/N.json`) y la abre con Room y las migraciones; `MigrationTestHelper` no localiza los assets de esquemas bajo Robolectric, y así no se añade `room-testing`.
- **Concesiones como filas propias**: aprobar una solicitud crea una fila en `access_grants` (única por solicitud) en lugar de reutilizar el estado `APPROVED`, para que la vigencia y la revocación no dependan de la solicitud. Solo se guarda `ACTIVE` o `REVOKED`; vencida se deduce de la vigencia.
- **Restablecimiento atómico**: el vaciado y la recarga del conjunto ficticio ocurren en una transacción, de modo que ninguna pantalla observa la base vacía; una prueba comprueba que el vaciado cubre todas las tablas del esquema.
- **Pruebas de DataStore**: corren bajo Robolectric porque DataStore reemplaza su archivo con `File.renameTo`, que en una JVM de Windows falla si el destino ya existe (en Android no).
- **Seguridad**: `allowBackup` desactivado, sin permisos, sin tráfico HTTP en claro; los secretos, llaves y `.env` están excluidos por `.gitignore`.
