package mx.crnl.clinica.beta.core.demo

import android.content.res.AssetManager

fun interface SeedFileReader {
    fun read(fileName: String): String
}

class AssetSeedFileReader(private val assets: AssetManager) : SeedFileReader {
    override fun read(fileName: String): String =
        assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
