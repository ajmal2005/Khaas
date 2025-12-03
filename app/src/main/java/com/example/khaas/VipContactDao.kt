package com.example.khaas

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VipContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: VipContact)

    @androidx.room.Update
    suspend fun updateContact(contact: VipContact)

    @Delete
    suspend fun deleteContact(contact: VipContact)

    @Query("SELECT * FROM vip_contacts")
    suspend fun getAllContacts(): List<VipContact>

    @Query("SELECT EXISTS(SELECT 1 FROM vip_contacts WHERE REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(phoneNumber, ' ', ''), '-', ''), '(', ''), ')', ''), '+', '') = :normalizedNumber)")
    suspend fun isContactVip(normalizedNumber: String): Boolean

    @Query("SELECT * FROM vip_contacts WHERE REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(phoneNumber, ' ', ''), '-', ''), '(', ''), ')', ''), '+', '') = :normalizedNumber LIMIT 1")
    suspend fun getContactByPhoneNumber(normalizedNumber: String): VipContact?
}
