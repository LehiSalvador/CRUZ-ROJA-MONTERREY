package mx.crnl.clinica.beta.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import mx.crnl.clinica.beta.core.ui.theme.ClinicalTheme
import mx.crnl.clinica.beta.core.util.ClinicTime

class MainActivity : ComponentActivity() {
    // No se publica como app bundle (fuera de alcance), por eso el aviso de cambios de idioma no aplica.
    @SuppressLint("AppBundleLocaleChanges")
    // Toda la aplicación está en español de México; los componentes de Material (selectores de fecha y hora) siguen el mismo idioma
    // aunque el dispositivo esté configurado en otro.
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply { setLocale(ClinicTime.locale) }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ClinicalApplication).container
        setContent {
            ClinicalTheme {
                ClinicalApp(container)
            }
        }
    }
}
