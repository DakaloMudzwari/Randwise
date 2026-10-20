package com.randwise.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.randwise.app.data.model.Account
import com.randwise.app.data.model.Budget
import com.randwise.app.data.model.Category
import com.randwise.app.data.model.CategoryRule
import com.randwise.app.data.model.ConsentRecord
import com.randwise.app.data.model.Debt
import com.randwise.app.data.model.Goal
import com.randwise.app.data.model.RecurringBill
import com.randwise.app.data.model.Transaction
import com.randwise.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfile?

    @Upsert
    suspend fun upsert(profile: UserProfile)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY archived, name")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts")
    suspend fun getAll(): List<Account>

    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY bucket, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<Category>

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Query("SELECT * FROM category_rules")
    fun observeRules(): Flow<List<CategoryRule>>

    @Query("SELECT * FROM category_rules")
    suspend fun getRules(): List<CategoryRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: CategoryRule)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE date >= :fromDay ORDER BY date DESC, id DESC")
    fun observeSince(fromDay: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY date, id")
    suspend fun getAll(): List<Transaction>

    @Query("SELECT fingerprint FROM transactions WHERE fingerprint IN (:fingerprints)")
    suspend fun existingFingerprints(fingerprints: List<String>): List<String>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND date BETWEEN :fromDay AND :toDay")
    suspend fun inRange(accountId: Long, fromDay: Long, toDay: Long): List<Transaction>

    /** IGNORE on the unique fingerprint index guarantees zero duplicate rows on re-import. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<Transaction>): List<Long>

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE transferGroupId = :group")
    suspend fun deleteTransferGroup(group: String)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE periodStart = :start")
    suspend fun forPeriod(start: Long): List<Budget>

    @Query("SELECT * FROM budgets WHERE periodStart = (SELECT MAX(periodStart) FROM budgets WHERE periodStart < :start)")
    suspend fun latestBefore(start: Long): List<Budget>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(budgets: List<Budget>)

    @Upsert
    suspend fun upsert(budget: Budget)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId AND periodStart = :start")
    suspend fun delete(categoryId: Long, start: Long)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM recurring_bills ORDER BY dueDay")
    fun observeAll(): Flow<List<RecurringBill>>

    @Query("SELECT * FROM recurring_bills WHERE isConfirmed = 1")
    suspend fun getConfirmed(): List<RecurringBill>

    @Upsert
    suspend fun upsert(bill: RecurringBill)

    @Delete
    suspend fun delete(bill: RecurringBill)
}

@Dao
interface ConsentDao {
    @Query("SELECT * FROM consent_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ConsentRecord>>

    @Insert
    suspend fun insert(record: ConsentRecord)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM debts ORDER BY balanceMinor")
    fun observeDebts(): Flow<List<Debt>>

    @Upsert
    suspend fun upsertDebt(debt: Debt)

    @Delete
    suspend fun deleteDebt(debt: Debt)

    @Query("SELECT * FROM goals ORDER BY isEmergencyFund DESC, name")
    fun observeGoals(): Flow<List<Goal>>

    @Upsert
    suspend fun upsertGoal(goal: Goal)

    @Delete
    suspend fun deleteGoal(goal: Goal)
}
