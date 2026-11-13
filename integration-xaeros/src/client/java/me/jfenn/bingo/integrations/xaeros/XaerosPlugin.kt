package me.jfenn.bingo.integrations.xaeros

import me.jfenn.bingo.plugin.IBingoInternalPlugin
import me.jfenn.bingo.platform.IModEnvironment
import org.koin.core.Koin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal class XaerosPlugin : IBingoInternalPlugin {
    override val clientModule = module {
        singleOf(::XaerosController)
    }

    override fun initialize(koin: Koin) {
        if (koin.get<IModEnvironment>().envType == IModEnvironment.EnvType.CLIENT) {
            koin.get<XaerosController>()
        }
    }
}
