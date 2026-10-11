package me.jfenn.bingo.common.scope

import me.jfenn.bingo.plugin.IBingoInternalPlugin
import org.koin.core.Koin
import java.util.*

object BingoPluginHolder {
    private val plugins = ServiceLoader.load(IBingoInternalPlugin::class.java).toList()

    val clientModules = plugins.mapNotNull { it.clientModule }
    val modules = plugins.mapNotNull { it.module }

    fun init(koin: Koin) {
        plugins.forEach { it.initialize(koin) }
    }

    fun close() {
        plugins.forEach { it.close() }
    }
}