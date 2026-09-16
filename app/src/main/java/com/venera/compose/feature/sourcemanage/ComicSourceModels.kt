package com.venera.compose.feature.sourcemanage

data class RepoSourceItem(
    val name: String,
    val fileName: String,
    val key: String,
    val version: String,
    val description: String? = null
)
