package io.github.openwarpkit.warpscout.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.openwarpkit.warpscout.data.HistoryDao
import io.github.openwarpkit.warpscout.data.WarpScoutDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): WarpScoutDatabase =
        Room.databaseBuilder(context, WarpScoutDatabase::class.java, "warpscout.db").build()

    @Provides
    fun historyDao(database: WarpScoutDatabase): HistoryDao = database.historyDao()
}
