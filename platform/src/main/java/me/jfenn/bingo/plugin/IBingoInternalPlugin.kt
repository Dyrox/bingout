package me.jfenn.bingo.plugin

import org.koin.core.Koin
import org.koin.core.module.Module

interface IBingoInternalPlugin {
    val clientModule: Module? get() = null
    val module: Module? get() = null
    fun initialize(koin: Koin) {}
    fun close() {}
}
