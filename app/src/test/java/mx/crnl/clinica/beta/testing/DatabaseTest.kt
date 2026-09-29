package mx.crnl.clinica.beta.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import org.junit.After
import org.junit.Before

/** Base para pruebas con una base Room en memoria real (Robolectric aporta el SQLite de Android). */
abstract class DatabaseTest {
    protected lateinit var context: Context
    protected lateinit var db: ClinicalDatabase

    @Before
    fun openDatabase() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ClinicalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    protected fun count(table: String): Int =
        db.query("SELECT COUNT(*) FROM $table", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    protected fun foreignKeyViolations(): Int =
        db.query("PRAGMA foreign_key_check", null).use { it.count }
}
