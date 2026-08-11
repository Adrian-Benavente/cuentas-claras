package com.cuentasclaras.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        GroupEntity::class,
        MemberEntity::class,
        ExpenseEntity::class,
        ExpenseSplitEntity::class,
        ExpenseCategoryEntity::class,
        SettlementPaymentEntity::class,
        PeriodClosureEntity::class,
        CacheFlagEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class CuentasClarasDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}
