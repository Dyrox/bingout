package me.jfenn.bingo.common.draft

import me.jfenn.bingo.common.commands.hasState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext

class DraftCommand(
    commandManager: ICommandManager,
    private val text: TextProvider,
) {
    private fun IExecutionContext.handle(result: DraftController.Result) {
        when (result) {
            DraftController.Result.Ok -> Unit
            DraftController.Result.NotDrafting -> error(text.string(StringKey.DraftNotDrafting))
            DraftController.Result.NotYourTurn -> error(text.string(StringKey.DraftNotYourTurn))
            DraftController.Result.NoRerolls -> error(text.string(StringKey.DraftNoReroll))
        }
    }

    init {
        commandManager.register("bingo") {
            literal("draft") {
                requires { hasState(DraftController.DRAFTING) }

                // Vanilla command transport also works when custom C2S channels are unavailable.
                literal("choose") {
                    string("turn") { turnArg ->
                        integer("option", min = -1, max = 1) { optionArg ->
                            executes {
                                handle(scope.get<DraftController>().choose(
                                    playerOrThrow, getArgument(turnArg), getArgument(optionArg)
                                ))
                            }
                        }
                    }
                }

                literal("pick") {
                    integer("option", min = 1, max = DraftController.OFFER_SIZE) { optionArg ->
                        executes {
                            val controller = scope.get<DraftController>()
                            handle(controller.pick(playerOrThrow, getArgument(optionArg) - 1))
                        }
                    }
                }

                literal("reroll") {
                    executes {
                        val controller = scope.get<DraftController>()
                        handle(controller.reroll(playerOrThrow))
                    }
                }
            }
        }
    }
}
