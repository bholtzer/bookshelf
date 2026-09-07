package com.bihstudio.madafim.domain.model

data class User(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val editorShareCode: String,
)
