package mx.crnl.clinica.beta.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: separa las credenciales locales de los usuarios (tabla 1:1) y añade la cédula profesional.
 * Conserva todos los datos existentes; las credenciales de las cuentas ya cargadas las aporta la
 * versión 2 del conjunto de datos iniciales.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `demo_users` ADD COLUMN `professionalLicense` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `demo_credentials` (" +
                "`userId` TEXT NOT NULL, " +
                "`algorithm` TEXT NOT NULL, " +
                "`iterations` INTEGER NOT NULL, " +
                "`salt` TEXT NOT NULL, " +
                "`passwordHash` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`userId`), " +
                "FOREIGN KEY(`userId`) REFERENCES `demo_users`(`userId`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
        )
    }
}
