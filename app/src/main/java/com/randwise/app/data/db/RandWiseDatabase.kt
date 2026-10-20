package com.randwise.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.randwise.app.data.model.Account
import com.randwise.app.data.model.Budget
import com.randwise.app.data.model.Category
import com.randwise.app.data.model.CategoryRule
import com.randwise.app.data.model.Debt
import com.randwise.app.data.model.Goal
import com.randwise.app.data.model.RecurringBill
import com.randwise.app.data.model.Transaction
import com.randwise.app.data.model.UserProfile
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        UserProfile::class, Account::class, Category::class, CategoryRule::class, Transaction::class,
        Budget::class, RecurringBill::class, Debt::class, Goal::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RandWiseDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun billDao(): BillDao
    abstract fun planDao(): PlanDao

    companion object {
        const val NAME = "randwise.db"

        /** Opens the SQLCipher-encrypted database (FDS §7 encryption-at-rest). */
        fun open(context: Context, passphrase: ByteArray): RandWiseDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, RandWiseDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
