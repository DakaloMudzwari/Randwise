package com.randwise.app

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.randwise.app.data.db.RandWiseDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every schema change must migrate real user data without loss. This creates a v1 database with
 * data, runs the migration and lets Room validate the result against the exported v2 schema.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RandWiseDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_keepsDataAndAppliesDefaults() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO user_profile (id, payday, adjustPaydayForWeekend, currency, monthlyIncomeMinor, safetyBufferMinor, plannedSavingsMinor, emergencyTargetMonths) " +
                    "VALUES (1, 25, 1, 'ZAR', 2850000, 100000, 300000, 3)",
            )
            execSQL("INSERT INTO recurring_bills (id, name, merchantPattern, expectedAmountMinor, dueDay, isConfirmed, categoryId) VALUES (1, 'Rent', 'LANDLORD', 850000, 1, 1, NULL)")
            execSQL("INSERT INTO goals (id, name, targetMinor, savedMinor, isEmergencyFund, targetDate) VALUES (1, 'Emergency fund', 6000000, 1250000, 1, NULL)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true)

        db.query("SELECT monthlyIncomeMinor, payFrequency, payAnchorDate FROM user_profile WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2_850_000L, c.getLong(0))
            assertEquals("MONTHLY", c.getString(1))
            assertTrue(c.isNull(2))
        }
        db.query("SELECT expectedAmountMinor, frequency, anchorDate FROM recurring_bills WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(850_000L, c.getLong(0))
            assertEquals("MONTHLY", c.getString(1))
            assertTrue(c.isNull(2))
        }
        db.query("SELECT savedMinor, linkedAccountId FROM goals WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1_250_000L, c.getLong(0))
            assertTrue(c.isNull(1))
        }
        db.query("SELECT COUNT(*) FROM consent_records").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
