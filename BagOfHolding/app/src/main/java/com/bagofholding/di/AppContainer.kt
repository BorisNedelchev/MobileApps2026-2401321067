package com.bagofholding.di

import android.content.Context
import com.bagofholding.data.db.DatabaseHelper
import com.bagofholding.data.db.SqliteCatalogDao
import com.bagofholding.data.db.SqliteCharacterDao
import com.bagofholding.data.db.SqliteInventoryDao
import com.bagofholding.data.repository.CatalogRepository
import com.bagofholding.data.repository.CharacterRepository
import com.bagofholding.data.repository.DataChangeNotifier
import com.bagofholding.data.repository.InventoryRepository
import com.bagofholding.network.LanShareClient
import com.bagofholding.network.NsdServiceDiscovery
import com.bagofholding.network.ServiceDiscovery
import com.bagofholding.ui.common.ThemePreferences

/**
 * Manual dependency injection container (see "Manual dependency injection" in the Android docs).
 * One instance lives in [com.bagofholding.BagOfHoldingApp]; ViewModels receive their
 * dependencies through constructors, so tests can pass fakes instead.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val databaseHelper by lazy { DatabaseHelper(appContext) }
    private val changeNotifier = DataChangeNotifier()
    private val inventoryDao by lazy { SqliteInventoryDao(databaseHelper) }

    val characterRepository by lazy {
        CharacterRepository(SqliteCharacterDao(databaseHelper), inventoryDao, changeNotifier)
    }
    val inventoryRepository by lazy { InventoryRepository(inventoryDao, changeNotifier) }
    val catalogRepository by lazy { CatalogRepository(SqliteCatalogDao(databaseHelper), changeNotifier) }

    val shareClient by lazy { LanShareClient() }
    val themePreferences by lazy { ThemePreferences(appContext) }

    /** A new instance per share screen, because it holds NSD registrations. */
    fun createServiceDiscovery(): ServiceDiscovery = NsdServiceDiscovery(appContext)
}
