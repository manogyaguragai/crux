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

    private companion object {
        const val TEST_DB = "crux-migration-test"
    }
}
