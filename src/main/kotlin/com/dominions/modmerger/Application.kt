package com.dominions.modmerger

import com.dominions.modmerger.core.ModMergerService
import com.dominions.modmerger.infrastructure.FileSystem
import mu.KLogger
import mu.KotlinLogging

abstract class Application(
    protected val modMergerService: ModMergerService,
    protected val fileSystem: FileSystem,
) {
    protected val logger: KLogger = KotlinLogging.logger { }
    abstract fun run()
}