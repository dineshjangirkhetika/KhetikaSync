package com.khetika.khetikasync.data.auth

/** Identity surface used everywhere in the app — independent of Firebase. */
data class AppUser(
    val uid: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
)
