package me.jfenn.bingo.plugin

import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.scope.Scope

interface IBingoInternalPlugin {
    val clientModule: Module? get() = null
    val module: Module? get() = null
    fun onScopeStarted(scope: Scope) {}
    fun initialize(koin: Koin) {}
    fun close() {}
}
