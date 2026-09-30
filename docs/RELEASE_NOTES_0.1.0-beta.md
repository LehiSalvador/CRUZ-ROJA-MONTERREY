# CRNL Clínica 0.1.0-beta

Beta Android de la plataforma clínica de Cruz Roja Nuevo León. Funciona sin conexión y con datos locales ficticios.

## Qué incluye

- **Acceso por rol**: inicio de sesión local, solicitud de cuenta y aprobación por quien corresponde (profesional, coordinación de área, administración clínica y administración del sistema).
- **Inicio**: indicadores reales por rol, con los pendientes de cada persona.
- **Pacientes**: listado y búsqueda, alta guiada con aviso de posibles duplicados, edición de datos generales y de contacto, y expediente de lectura.
- **Atención**: asignación de profesional por área, agenda de citas, contacto por WhatsApp como enlace externo, registro base de atención y línea de atención por área.
- **Solicitudes**: cuentas, acceso de lectura entre áreas con vigencia y revocación, y cambio formal de profesional con historial íntegro.
- **Evaluaciones**: historial estructural de solo lectura y vista previa aislada del modo supervisado.
- **Administración**: directorio de usuarios, consulta de auditoría y restablecimiento de los datos de la Beta.

## Instalación

1. Requiere Android 8.0 (API 26) o superior.
2. Comprueba la integridad con `CRNL-Clinica-0.1.0-beta.apk.sha256` (`sha256sum -c` o `Get-FileHash -Algorithm SHA256`).
3. Android puede pedir permiso para instalar desde el origen donde se descargó el archivo.
4. Las cuentas de prueba están en [`BETA_ACCESS.md`](BETA_ACCESS.md).

El APK está firmado con la llave de la Beta. Una compilación firmada con otra llave (por ejemplo, la de depuración) no se instala encima: hay que desinstalarla antes, y los datos locales de esa instalación se pierden.

## Alcance de esta Beta

- Los datos son locales y ficticios. No hay servidor ni permiso de Internet; la aplicación no envía nada fuera del dispositivo.
- Los roles y las concesiones de acceso son una protección local de la Beta. No constituyen seguridad de producción ni la matriz institucional final.
- Instrumento institucional no configurado: no existen instrumentos, reactivos, puntajes, rangos ni clasificaciones reales. Los registros de evaluación son marcadores estructurales.
- Los formularios oficiales de Nutrición y Medicina, los consentimientos institucionales, la exportación y los documentos aún no existen.
- Las citas por ocurrir con un profesional anterior no se reasignan solas al cambiar de profesional; quedan para revisión administrativa.
