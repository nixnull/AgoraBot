package org.randomcat.agorabot.commands

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import org.randomcat.agorabot.commands.impl.BaseCommand
import org.randomcat.agorabot.commands.impl.BaseCommandImplReceiver
import org.randomcat.agorabot.commands.impl.BaseCommandStrategy
import org.randomcat.agorabot.commands.impl.noArgs
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val END_DUCK_SESSION_CMD = "I fixed it"

private val RESPONSES = listOf(
    "Tell me more.",
    "Okay.",
    "How so?",
    "Really?",
    "Is that correct?",
    "Okay...",
    "I guess.",
    "Alright.",
)

class DuckCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    private data class ListeningSpec(
        private val channelId: String,
        private val userId: String,
    )


    object JdaEventListenerMarker

    private val listening = AtomicReference<PersistentSet<ListeningSpec>>(persistentSetOf())
    private val jdaMap = WeakHashMap<JDA, JdaEventListenerMarker>()
    private val jdaLock = ReentrantReadWriteLock()

    private fun registerJdaListener(jda: JDA) {
        jdaLock.read {
            if (jdaMap.containsKey(jda)) return
        }

        jdaLock.write {
            if (jdaMap.containsKey(jda)) return

            jda.addEventListener(object {
                @SubscribeEvent
                fun onMessage(event: GuildMessageReceivedEvent) {
                    if (!event.message.isFromGuild) return

                    val currentListening = listening.get()

                    val listeningSpec = ListeningSpec(
                        channelId = event.channel.id,
                        userId = event.author.id,
                    )

                    if (currentListening.contains(listeningSpec)) {
                        if (event.message.contentStripped.equals(END_DUCK_SESSION_CMD, ignoreCase = true)) {
                            event.channel.sendMessage("Good for you! I'll be quiet now.").queue()

                            listening.updateAndGet {
                                it.remove(listeningSpec)
                            }
                        } else {
                            event.channel.sendMessage(RESPONSES.random()).queue()
                        }
                    }
                }
            })

            jdaMap.put(jda, JdaEventListenerMarker)
        }
    }

    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("start") {
                noArgs {
                    registerJdaListener(currentJda())

                    val listeningSpec = ListeningSpec(
                        channelId = currentChannel().id,
                        userId = currentMessageEvent().author.id,
                    )

                    listening.updateAndGet {
                        it.add(listeningSpec)
                    }

                    respond("Alright, I'm listening. Type \"I fixed it\" to stop.")
                }
            }
        }
    }
}
