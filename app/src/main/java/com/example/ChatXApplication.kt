package com.example

import android.app.Application
import com.example.data.local.ChatXDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.AuthAndAdminRepository
import com.example.data.repository.ChatRepository
import com.example.security.E2eeCryptoManager
import com.example.whatsapp.WhatsAppBusinessRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class ChatXApplication : Application() {

    val firestoreDb: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
    }

    val firebaseAuth: FirebaseAuth by lazy {
        Firebase.auth
    }

    val cryptoManager: E2eeCryptoManager by lazy {
        E2eeCryptoManager()
    }

    val localDb: ChatXDatabase by lazy {
        ChatXDatabase.getInstance(this)
    }

    val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(this, cryptoManager)
    }

    val authAndAdminRepository: AuthAndAdminRepository by lazy {
        AuthAndAdminRepository(firestoreDb, firebaseAuth, preferencesManager, cryptoManager)
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(firestoreDb, firebaseAuth, preferencesManager, cryptoManager)
    }

    val whatsAppBusinessRepository: WhatsAppBusinessRepository by lazy {
        WhatsAppBusinessRepository(localDb.whatsAppBusinessDao(), preferencesManager)
    }
}
