package com.dominions.modmerger

import com.dominions.modmerger.core.ModMerger
import com.dominions.modmerger.domain.ModGroupRegistry
import com.dominions.modmerger.gamedata.Dom6CsvGameDataProvider
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.infrastructure.GamePathsManager

data class ApplicationComponents(
    val modMerger: ModMerger,
    val fileSystem: FileSystem,
    val gamePathsManager: GamePathsManager,
    val groupRegistry: ModGroupRegistry,
    val gameDataProvider: Dom6CsvGameDataProvider
)