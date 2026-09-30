# Acceso a la Beta local

Cuentas para probar la Beta. Son **credenciales ficticias de la Beta local**: no son credenciales institucionales, no protegen datos reales y no dan acceso a ningún sistema. Existen solo dentro de la base local de la aplicación y sus datos son ficticios.

La autenticación de esta versión es local y sirve para demostrar sesiones y estados de cuenta; no equivale a la seguridad de producción.

## Cuentas de prueba

| Correo | Contraseña | Perfil | Estado | Al iniciar sesión |
| --- | --- | --- | --- | --- |
| `mariana.elizondo@example.org` | `Beta-Local-2026` | Profesional · Psicología | Activa | Entra |
| `rodrigo.villarreal@example.org` | `Beta-Local-2026` | Profesional · Psicología | Activa | Entra |
| `paola.garza@example.org` | `Beta-Local-2026` | Profesional · Nutrición | Activa | Entra |
| `eduardo.trevino@example.org` | `Beta-Local-2026` | Profesional · Medicina General | Activa | Entra |
| `claudia.benavides@example.org` | `Beta-Local-2026` | Coordinadora · Psicología | Activa | Entra |
| `hector.montemayor@example.org` | `Beta-Local-2026` | Administrador clínico | Activa | Entra |
| `valeria.ramos@example.org` | `Beta-Local-2026` | Profesional · Nutrición | Pendiente de aprobación | Rechazada: solicitud pendiente |
| `gerardo.leal@example.org` | `Beta-Local-2026` | Profesional · Medicina General | Suspendida | Rechazada: cuenta suspendida |
| `ivan.rangel@example.org` | `Beta-Local-2026` | Profesional · Psicología | Rechazada | Rechazada: solicitud rechazada |
| `sofia.cantu@example.org` | `Beta-Local-2026` | Profesional · Psicología | Inactiva | Rechazada: cuenta inactiva |

Los correos no distinguen mayúsculas; la contraseña sí.

## Solicitar una cuenta

**Solicitar cuenta** en la pantalla de acceso crea una cuenta con estado *Pendiente de aprobación* y su propia contraseña. Esa cuenta no puede iniciar sesión hasta que se apruebe: la coordinación de su área (solo profesionales de esa área) o la administración clínica la aprueba en **Solicitudes → Cuentas**. Si se rechaza, la cuenta queda *Rechazada* y no entra. Aprobar no cambia ni muestra la contraseña.

Para probarlo: solicita una cuenta de *Profesional · Psicología*, entra como `claudia.benavides@example.org` (o `hector.montemayor@example.org`) y apruébala; `valeria.ramos@example.org` (Nutrición) solo la puede revisar la administración clínica.

## Volver al estado inicial

La administración clínica puede restablecer los datos ficticios en **Perfil → Herramientas de Beta → Restablecer datos de Beta**: elimina los cambios locales (pacientes, citas, solicitudes, cuentas nuevas y bitácora) y restaura las cuentas y los datos de esta tabla. Pide dos confirmaciones y cierra la sesión.

## Datos de contacto y cédulas

Los correos usan dominios reservados para ejemplos, los teléfonos están en un rango no asignable (`+52 81 0000 XXXX`) y las cédulas profesionales en un rango que no se expide (`0000XXXX`).
