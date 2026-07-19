package com.crux.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration scaffold, present from day one (testing.md). With only schema v1 there is
 * no migration to run yet; this proves v1 is exported and openable. When the schema
 * bumps, validate here with:
 *   helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
 * A destructive fallback is never acceptable in its place.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CruxDatabase::class.java,
    )

    @Test
    fun schemaV1_createsAndOpens() {
        helper.createDatabase(TEST_DB, 1).close()
    }

    @Test
    fun migrate1To2_addsRemindOffsetColumn() {
        // seed a v1 task, then run the migration and confirm the row survives with the new null column.
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO tasks (id, title, priority, hasTime, status, createdAt, source, parsedBy) " +
                    "VALUES (1, 'keep me', 3, 0, 'OPEN', 0, 'TYPED', 'MANUAL')",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, CruxDatabase.MIGRATION_1_2).apply {
            query("SELECT remindOffsetMinutes FROM tasks WHERE id = 1").use { c ->
                assert(c.moveToFirst())
                assert(c.isNull(0))
            }
            close()
        }
    }

    @Test
    fun migrate2To3_addsProjectDescriptionColumn() {
        // seed a v2 project, then migrate and confirm the row survives with the new '' description.
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO projects (id, name, rank, archived, createdAt) " +
                    "VALUES (1, 'growbydata', 1, 0, 0)",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 3, true, CruxDatabase.MIGRATION_2_3).apply {
            query("SELECT description FROM projects WHERE id = 1").use { c ->
                assert(c.moveToFirst())
                assert(c.getString(0) == "")
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "crux-migration-test"
    }
}
