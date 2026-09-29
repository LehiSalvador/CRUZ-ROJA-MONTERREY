# Plataforma Clínica CRNL — Beta Android

Aplicación Android nativa de la plataforma clínica de Cruz Roja Nuevo León. Este repositorio contiene la base técnica de la Beta (`0.1.0-beta`): arquitectura por capas, persistencia local, navegación y sistema de diseño sobre los que se construyen los módulos.

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
  database/            ClinicalDatabase (Room, esquema v1)
  datastore/           creación del DataStore de sesión
  demo/                carga, validación y mapeo del conjunto de datos ficticios
  navigation/          rutas tipadas y destinos de la barra inferior
  ui/                  tema, componentes reutilizables, estados de UI y etiquetas de catálogos
  util/                zona horaria y formatos de fecha
data/
  local/               entidades, DAOs y mappers de Room
  repository/          implementaciones locales de los contratos
domain/
  model/               modelos y catálogos sin dependencias de Android
  repository/          contratos (PatientRepository, AppointmentRepository, SessionRepository)
feature/               splash, auth, home, patients, appointments, requests, profile
```

- La UI depende solo de los contratos de `domain/repository`. Sustituir la fuente local por una API o Supabase consiste en implementar esos contratos y cambiar el ensamblado en `app/AppContainer.kt`.
- Un solo módulo Gradle; las features se materializan como paquetes cuando tienen código.

## Datos de la Beta

- Room guarda pacientes, contactos, asignaciones (con historial), citas, encuentros, evaluaciones y su bitácora de auditoría. Convenciones del modelo: UUID técnico separado del folio humano (`CRNL-000001`), edad calculada a partir de la fecha de nacimiento, timestamps en UTC, sin borrados en cascada (las claves foráneas usan `RESTRICT`).
- En el primer arranque se cargan los JSON de `app/src/main/assets/seed_*.json` en una sola transacción. La versión aplicada se guarda en DataStore y las llaves determinísticas con `IGNORE` impiden duplicar filas aunque la carga se repita.
- Antes de cargar, `DemoSeedValidator` comprueba que el conjunto es ficticio (correos en dominios `example.*`, teléfonos en un rango no asignable, instrumentos marcadores), que las referencias son válidas y que los códigos pertenecen a los catálogos.
- Las fechas de la agenda se expresan como días relativos al primer arranque, de modo que la agenda nunca queda vencida.
- Los esquemas de Room se exportan a `app/schemas/` para las futuras migraciones.

## Decisiones que no se deducen del código

- **Versiones de Compose, Lifecycle y Navigation**: las publicaciones más recientes exigen `compileSdk 37`. Se fijaron las últimas estables compatibles con `compileSdk 36` (Compose BOM 2026.06.01, Lifecycle 2.10.0, Navigation Compose 2.9.8).
- **Navegación**: Navigation Compose estable en lugar de Navigation 3, con un back stack independiente por pestaña.
- **Acceso**: la pantalla de acceso es estructural; continúa a la shell sin verificar credenciales y guarda solo un indicador de sesión en DataStore.
- **`room-ktx`**: no se declara; en Room 2.8 esas extensiones viven en `room-runtime`.
- **Pruebas de DataStore**: corren bajo Robolectric porque DataStore reemplaza su archivo con `File.renameTo`, que en una JVM de Windows falla si el destino ya existe (en Android no).
- **Seguridad**: `allowBackup` desactivado, sin permisos, sin tráfico HTTP en claro; los secretos, llaves y `.env` están excluidos por `.gitignore`.
