package mx.crnl.clinica.beta.core.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Abre un enlace en otra aplicación instalada (p. ej. WhatsApp) mediante un intent. La aplicación no hace ninguna
 * petición de red ni envía nada: solo cede la pantalla, y la persona decide en la otra aplicación.
 */
fun interface ExternalLinkLauncher {
    /** Verdadero si una aplicación aceptó el enlace; falso si no hay ninguna capaz de abrirlo. Nunca lanza. */
    fun open(uri: String): Boolean
}

// `String.toUri` es de core-ktx, que la aplicación no declara: no se añade una dependencia por una llamada.
@Suppress("UseKtx")
class AndroidExternalLinkLauncher(private val context: Context) : ExternalLinkLauncher {
    override fun open(uri: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        // Desde una actividad no hace falta una tarea nueva; solo con otro tipo de contexto sí.
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Las pruebas sustituyen este valor; por omisión se usa el lanzador real de Android. */
val LocalExternalLinkLauncher = compositionLocalOf<ExternalLinkLauncher?> { null }

@Composable
fun rememberExternalLinkLauncher(): ExternalLinkLauncher {
    val provided = LocalExternalLinkLauncher.current
    val context = LocalContext.current
    return provided ?: remember(context) { AndroidExternalLinkLauncher(context) }
}
