package me.jfenn.bingo.platform

import java.nio.file.Path

interface IModEnvironment {
    val configDir: Path
    val gameDir: Path
    val envType: EnvType

    fun isModLoaded(modId: String): Boolean

    /** Whether this is a development launch (e.g. `gradle runClient`), which enables the dev test tools */
    val isDevelopment: Boolean get() = false

    enum class EnvType {
        CLIENT,
        SERVER,
    }
}