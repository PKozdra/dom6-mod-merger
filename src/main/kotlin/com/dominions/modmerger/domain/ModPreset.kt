package com.dominions.modmerger.domain

data class ModPreset(
    val name: String,
    val modPaths: Set<String>,
    val createdAt: Long = System.currentTimeMillis()
)