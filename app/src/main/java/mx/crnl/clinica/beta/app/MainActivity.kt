package mx.crnl.clinica.beta.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import mx.crnl.clinica.beta.core.ui.theme.ClinicalTheme

class MainActivity : ComponentActivity() {
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
