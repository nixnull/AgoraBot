package org.randomcat.agorabot.commands

import org.randomcat.agorabot.util.repeat

private val UNLIKELY_RESPONSES = listOf(
    "SHENANIGANS",
    "I GUESS",
    "PROBABLY",
    "WHO KNOWS",
    "OH GOD, DEFINITELY NOT",
    "DON'T ASK ME",
    "THE ONLY REAL ARGUMENT IN FAVOR OF THIS IS WISHFUL THINKING",
    "A TYPICAL EXAMPLE OF \"I SAY I DO, THEREFORE I DO\" WHICH HAS PLAGUED AGORA FOR A LONG TIME",
    "IF YOU SAY SO",
    "UNFORTUNATELY",
    "IT'S IN THE BEST INTEREST OF THE GAME FOR THIS TO BE TRUE",
)

private val LIKELY_RESPONSES = listOf(
    "TRUE",
    "FALSE",
    "DISMISS",
    "IRRELEVANT",
)

private val RESPONSES = LIKELY_RESPONSES.repeat(10) + UNLIKELY_RESPONSES

class CrystalBallCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        matchFirst {
            noArgs { _ ->
                respond("Judged ${RESPONSES.random()}.")
            }

            args(StringArg("statement")) { (statement) ->
                respond("\"$statement\" judged ${RESPONSES.random()}.")
            }
        }
    }
}
