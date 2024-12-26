package com.dominions.modmerger.domain

data class ModGroup(
    val id: String,
    val name: String,
    val description: String,
    val autoGrouping: (ModFile) -> Boolean
)