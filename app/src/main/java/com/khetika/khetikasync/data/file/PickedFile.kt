package com.khetika.khetikasync.data.file

/** A file selected from the system picker, ready to be uploaded. */
data class PickedFile(
    val uri: String,
    val displayName: String,
)
