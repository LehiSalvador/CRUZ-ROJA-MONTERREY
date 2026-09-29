package mx.crnl.clinica.beta.app

import android.app.Application

class ClinicalApplication : Application() {
    val container: AppContainer by lazy { DefaultAppContainer(this) }
}
