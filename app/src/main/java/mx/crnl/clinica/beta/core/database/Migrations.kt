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

/**
 * v2 → v3: agrega las concesiones de acceso interárea (`access_grants`) y las solicitudes de cambio de profesional
 * (`professional_override_requests`). Solo crea tablas nuevas: no toca ni reescribe ninguna fila existente.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `access_grants` (" +
                "`accessGrantId` TEXT NOT NULL, " +
                "`accessRequestId` TEXT NOT NULL, " +
                "`patientId` TEXT NOT NULL, " +
                "`granteeUserId` TEXT NOT NULL, " +
                "`ownerAreaCode` TEXT NOT NULL, " +
                "`scopeCode` TEXT NOT NULL, " +
                "`validFrom` INTEGER NOT NULL, " +
                "`expiresAt` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`grantedBy` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`revokedBy` TEXT, " +
                "`revokedAt` INTEGER, " +
                "PRIMARY KEY(`accessGrantId`), " +
                "FOREIGN KEY(`accessRequestId`) REFERENCES `interarea_access_requests`(`accessRequestId`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                "FOREIGN KEY(`patientId`) REFERENCES `patients`(`patientId`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                "FOREIGN KEY(`granteeUserId`) REFERENCES `demo_users`(`userId`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                "FOREIGN KEY(`grantedBy`) REFERENCES `demo_users`(`userId`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                "FOREIGN KEY(`revokedBy`) REFERENCES `demo_users`(`userId`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_access_grants_accessRequestId` ON `access_grants` (`accessRequestId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_access_grants_patientId` ON `access_grants` (`patientId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_access_grants_granteeUserId` ON `access_grants` (`granteeUserId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_access_grants_status` ON `access_grants` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_access_grants_expiresAt` ON `access_grants` (`expiresAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_access_grants_grantedBy` ON `access_grants` (`grantedBy`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_access_grants_revokedBy` ON `access_grants` (`revokedBy`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `professional_override_requests` (" +
                "`overrideRequestId` TEXT NOT NULL, " +
                "`patientId` TEXT NOT NULL, " +
                "`areaCode` TEXT NOT NULL, " +
                "`currentAssignmentId` TEXT NOT NULL, " +
                "`currentProfessionalId` TEXT NOT NULL, " +
                "`requestedProfessionalId` TEXT NOT NULL, " +
                "`requestedBy` TEXT NOT NULL, " +
                "`reason` TEXT NOT NULL, " +
                "`requestedAt` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`reviewedBy` TEXT, " +
                "`reviewedAt` INTEGER, " +
                "`resolutionReason` TEXT, " +
                "PRIMARY KEY(`overrideRequestId`), " +
                "FOREIGN KEY(`patientId`) REFERENCES `patients`(`patientId`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                "FOREIGN KEY(`currentAssignmentId`) REFERENCES `professional_assignments`(`assignmentId`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                "FOREIGN KEY(`currentProfessionalId`) REFERENCES `demo_users`(`userId`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                "FOREIGN KEY(`requestedProfessionalId`) REFERENCES `demo_users`(`userId`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                "FOREIGN KEY(`requestedBy`) REFERENCES `demo_users`(`userId`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                "FOREIGN KEY(`reviewedBy`) REFERENCES `demo_users`(`userId`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_professional_override_requests_patientId_areaCode_status` " +
                "ON `professional_override_requests` (`patientId`, `areaCode`, `status`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_professional_override_requests_currentAssignmentId` ON `professional_override_requests` (`currentAssignmentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_professional_override_requests_currentProfessionalId` ON `professional_override_requests` (`currentProfessionalId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_professional_override_requests_requestedProfessionalId` ON `professional_override_requests` (`requestedProfessionalId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_professional_override_requests_requestedBy` ON `professional_override_requests` (`requestedBy`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_professional_override_requests_reviewedBy` ON `professional_override_requests` (`reviewedBy`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_professional_override_requests_status` ON `professional_override_requests` (`status`)")
    }
}
