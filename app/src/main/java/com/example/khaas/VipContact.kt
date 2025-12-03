package com.example.khaas

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vip_contacts")
data class VipContact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phoneNumber: String,
    val customRingtoneUri: String? = null
)
