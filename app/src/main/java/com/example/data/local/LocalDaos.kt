package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WhatsAppNotificationDao {
    @Query(
        """
        SELECT * FROM whatsapp_notifications
        WHERE timestampMs = (
            SELECT MAX(n2.timestampMs)
            FROM whatsapp_notifications n2
            WHERE n2.conversationKey = whatsapp_notifications.conversationKey
        )
        ORDER BY timestampMs DESC
        """
    )
    fun observeLatestThreads(): Flow<List<WhatsAppNotificationEntity>>

    @Query("SELECT * FROM whatsapp_notifications WHERE conversationKey = :conversationKey ORDER BY timestampMs ASC")
    fun observeThreadMessages(conversationKey: String): Flow<List<WhatsAppNotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotification(entity: WhatsAppNotificationEntity)

    @Query("UPDATE whatsapp_notifications SET active = 0, updatedAtMs = :nowMs WHERE notificationKey = :notificationKey")
    suspend fun markNotificationRemoved(notificationKey: String, nowMs: Long = System.currentTimeMillis())

    @Query("DELETE FROM whatsapp_notifications")
    suspend fun clearAll()
}

@Dao
interface WhatsAppBusinessDao {
    @Query("SELECT * FROM wa_business_threads ORDER BY lastMessageAtMs DESC")
    fun observeThreads(): Flow<List<WhatsAppBusinessThreadEntity>>

    @Query("SELECT * FROM wa_business_threads WHERE phoneNumber = :phoneNumber LIMIT 1")
    fun observeThread(phoneNumber: String): Flow<WhatsAppBusinessThreadEntity?>

    @Query("SELECT * FROM wa_business_messages WHERE phoneNumber = :phoneNumber ORDER BY timestampMs ASC")
    fun observeMessages(phoneNumber: String): Flow<List<WhatsAppBusinessMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThread(thread: WhatsAppBusinessThreadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: WhatsAppBusinessMessageEntity)

    @Query("DELETE FROM wa_business_messages")
    suspend fun clearMessages()

    @Query("DELETE FROM wa_business_threads")
    suspend fun clearThreads()
}
