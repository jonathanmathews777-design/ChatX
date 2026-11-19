package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WhatsAppNotificationEntity::class,
        WhatsAppBusinessThreadEntity::class,
        WhatsAppBusinessMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ChatXDatabase : RoomDatabase() {
    abstract fun whatsAppNotificationDao(): WhatsAppNotificationDao
    abstract fun whatsAppBusinessDao(): WhatsAppBusinessDao

    companion object {
        @Volatile
        private var INSTANCE: ChatXDatabase? = null

        fun getInstance(context: Context): ChatXDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatXDatabase::class.java,
                    "chatx_local_integrations.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
