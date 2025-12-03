package com.example.khaas

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [VipContact::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vipContactDao(): VipContactDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "khaas_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
