package me.jfenn.bingo.integrations.xaeros

import me.jfenn.bingo.plugin.IBingoInternalPlugin
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

internal class XaerosPlugin : IBingoInternalPlugin {
    override val clientModule = module {
        singleOf(::XaerosController) withOptions { createdAtStart() }
    }
}