package com.zaus.nullwave.core.database.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.zaus.nullwave.data.database.NullWaveDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Driver and database, contributed to the application graph.
 *
 * **In `:core:data`, not in `:app`'s `AppGraph`.** `AppGraph.kt` carried a commented-out sketch of this
 * for a while, and following it would have made `:app` name the data layer вЂ” the opposite of how every
 * other module registers itself. A `@ContributesTo` binding container self-registers, so `:app` depends
 * on this module and never mentions what is inside it.
 *
 * Both bindings are `@SingleIn(AppScope::class)`: one driver and one database per process. A second
 * `AndroidSqliteDriver` over the same file is a second connection pool, and SQLite will happily hand you
 * `SQLITE_BUSY` for your trouble.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object DatabaseBindings {

    /**
     * `PRAGMA foreign_keys = ON` is the load-bearing line here.
     *
     * SQLite disables foreign keys **per connection, by default**, so without this the
     * `ON DELETE CASCADE` clauses in `Playlist.sq` are decoration: deleting a playlist would leave its
     * entries behind, pointing at nothing. It goes in `onOpen` rather than `onCreate` because it has to
     * be set on every connection, not once per database.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideSqlDriver(context: Context): SqlDriver = AndroidSqliteDriver(
        schema = NullWaveDatabase.Schema,
        context = context,
        name = DatabaseName,
        callback = object : AndroidSqliteDriver.Callback(NullWaveDatabase.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(driver: SqlDriver): NullWaveDatabase = NullWaveDatabase(driver)

    private const val DatabaseName = "nullwave.db"
}
