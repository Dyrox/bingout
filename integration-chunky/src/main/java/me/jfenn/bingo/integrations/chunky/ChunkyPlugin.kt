package me.jfenn.bingo.integrations.chunky

import me.jfenn.bingo.platform.scope.BingoScope
import me.jfenn.bingo.plugin.IBingoInternalPlugin
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.scopedOf
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module

internal class ChunkyPlugin : IBingoInternalPlugin {
    override val module: Module = module {
        scope<BingoScope> {
            factoryOf(ChunkyFactory::create) bind IChunkyApi::class
            scopedOf(::ChunkyController)
        }
    }

    override fun onScopeStarted(scope: Scope) {
        scope.get<ChunkyController>()
    }
}