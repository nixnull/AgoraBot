package org.randomcat.agorabot.commands.base

import net.dv8tion.jda.api.entities.Message
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.commands.base.args as doArgs
import org.randomcat.agorabot.commands.base.noArgs as doNoArgs

data class ContextReceiverArg<Context, Recevier, Arg>(
    override val context: Context,
    val receiver: Recevier,
    val arg: Arg,
) : WithContext<Context>

data class ContextAndReceiver<Context, Receiver>(
    val context: Context,
    val receiver: Receiver,
)

fun <C, R, A> ContextAndArg<ContextAndReceiver<C, R>, A>.flatten(): ContextReceiverArg<C, R, A> {
    return ContextReceiverArg(
        context = context.context,
        receiver = context.receiver,
        arg = arg,
    )
}

interface BaseCommandArgumentStrategy {
    fun sendArgumentErrorResponse(
        source: CommandEventSource,
        invocation: CommandInvocation,
        errorMessage: String,
        usage: String,
    )
}

interface BaseCommandOutputStrategy {
    fun sendResponse(source: CommandEventSource, invocation: CommandInvocation, message: String)
    fun sendResponseMessage(source: CommandEventSource, invocation: CommandInvocation, message: Message)

    fun sendResponseAsFile(
        source: CommandEventSource,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    )

    fun sendResponseTextAndFile(
        source: CommandEventSource,
        invocation: CommandInvocation,
        textResponse: String,
        fileName: String,
        fileContent: String,
    )
}

interface BaseCommandDependencyStrategy {
    fun tryFindDependency(tag: Any): Any?
}

interface BaseCommandExecutionStrategy {
    fun executeCommandBlock(block: suspend () -> Unit)
}

interface BaseCommandStrategy :
    BaseCommandArgumentStrategy,
    BaseCommandOutputStrategy,
    BaseCommandDependencyStrategy,
    BaseCommandExecutionStrategy

interface BaseCommandContext : CommandDependencyProvider {
    val source: CommandEventSource
    val invocation: CommandInvocation
}


interface BaseCommandExecutionReceiver {
    fun respond(message: String)
    fun respond(message: Message)
    fun respondWithFile(fileName: String, fileContent: String)
    fun respondWithTextAndFile(text: String, fileName: String, fileContent: String)
}

@CommandDslMarker
interface BaseCommandExecutionReceiverRequiring<out Requirement> : BaseCommandExecutionReceiver {
    fun requirement(): Requirement
}

private val nullPendingInvocation = object : PendingInvocation<Nothing> {
    override fun execute(block: suspend (Nothing) -> Unit) {
        // Do nothing.
    }
}

abstract class BaseCommand(private val strategy: BaseCommandStrategy) : Command {
    companion object {
        fun <Ctx, R> ArgumentDescriptionReceiver<ContextAndReceiver<Ctx, R>>.noArgs(
        ) = doNoArgs().prependAlwaysTransform { it.flatten() }

        fun <A, AE, Ctx, R> ArgumentDescriptionReceiver<ContextAndReceiver<Ctx, R>>.args(
            a: CommandArgumentParser<A, AE>,
        ) = doArgs(a).prependAlwaysTransform { it.flatten() }

        fun <A, AE, B, BE, Ctx, R> ArgumentDescriptionReceiver<ContextAndReceiver<Ctx, R>>.args(
            a: CommandArgumentParser<A, AE>,
            b: CommandArgumentParser<B, BE>,
        ) = doArgs(a, b).prependAlwaysTransform { it.flatten() }

        fun <A, AE, B, BE, C, CE, Ctx, R> ArgumentDescriptionReceiver<ContextAndReceiver<Ctx, R>>.args(
            a: CommandArgumentParser<A, AE>,
            b: CommandArgumentParser<B, BE>,
            c: CommandArgumentParser<C, CE>,
        ) = doArgs(a, b, c).prependAlwaysTransform { it.flatten() }

        fun <A, AE, B, BE, C, CE, D, DE, Ctx, R> ArgumentDescriptionReceiver<ContextAndReceiver<Ctx, R>>.args(
            a: CommandArgumentParser<A, AE>,
            b: CommandArgumentParser<B, BE>,
            c: CommandArgumentParser<C, CE>,
            d: CommandArgumentParser<D, DE>,
        ) = doArgs(a, b, c, d).prependAlwaysTransform { it.flatten() }

        fun <Ctx, R> ArgumentDescriptionReceiver<ContextAndReceiver<Ctx, R>>.noArgs(
            block: R.(CommandArgs0) -> Unit,
        ) = noArgs().execute { block(it.receiver, it.arg) }

        fun <A, AE, Ctx, R> ArgumentDescriptionReceiver<ContextAndReceiver<Ctx, R>>.args(
            a: CommandArgumentParser<A, AE>,
            block: R.(CommandArgs1<A>) -> Unit,
        ) = args(a).execute { block(it.receiver, it.arg) }

        fun <A, AE, B, BE, Ctx, R> ArgumentDescriptionReceiver<ContextAndReceiver<Ctx, R>>.args(
            a: CommandArgumentParser<A, AE>,
            b: CommandArgumentParser<B, BE>,
            block: R.(CommandArgs2<A, B>) -> Unit,
        ) = args(a, b).execute { block(it.receiver, it.arg) }

        fun <A, AE, B, BE, C, CE, Ctx, R> ArgumentDescriptionReceiver<ContextAndReceiver<Ctx, R>>.args(
            a: CommandArgumentParser<A, AE>,
            b: CommandArgumentParser<B, BE>,
            c: CommandArgumentParser<C, CE>,
            block: R.(CommandArgs3<A, B, C>) -> Unit,
        ) = args(a, b, c).execute { block(it.receiver, it.arg) }

        fun <A, AE, B, BE, C, CE, D, DE, Ctx, R> ArgumentDescriptionReceiver<ContextAndReceiver<Ctx, R>>.args(
            a: CommandArgumentParser<A, AE>,
            b: CommandArgumentParser<B, BE>,
            c: CommandArgumentParser<C, CE>,
            d: CommandArgumentParser<D, DE>,
            block: R.(CommandArgs4<A, B, C, D>) -> Unit,
        ) = args(a, b, c, d).execute { block(it.receiver, it.arg) }
    }

    @CommandDslMarker
    private class ExecutionReceiverImpl(
        private val strategy: BaseCommandStrategy,
        private val source: CommandEventSource,
        private val invocation: CommandInvocation,
    ) : BaseCommandExecutionReceiver {
        override fun respond(message: String) {
            strategy.sendResponse(source, invocation, message)
        }

        override fun respond(message: Message) {
            strategy.sendResponseMessage(source, invocation, message)
        }

        override fun respondWithFile(fileName: String, fileContent: String) {
            strategy.sendResponseAsFile(source, invocation, fileName, fileContent)
        }

        override fun respondWithTextAndFile(text: String, fileName: String, fileContent: String) {
            strategy.sendResponseTextAndFile(source, invocation, text, fileName, fileContent)
        }
    }

    override fun invoke(source: CommandEventSource, invocation: CommandInvocation) {
        val theSource = source
        val theInvocation = invocation

        TopLevelExecutingArgumentDescriptionReceiver(
            UnparsedCommandArgs(invocation.args),
            onError = { msg ->
                strategy.sendArgumentErrorResponse(
                    source = source,
                    invocation = invocation,
                    errorMessage = msg,
                    usage = usage()
                )
            },
            object :
                ExecutingExecutionReceiverProperties<ContextAndReceiver<BaseCommandContext, BaseCommandExecutionReceiver>> {
                override fun <ParseResult, Arg> receiverOnSuccess(
                    results: List<ParseResult>,
                    mapParsed: (List<ParseResult>) -> Arg,
                ): PendingInvocation<ContextAndArg<ContextAndReceiver<BaseCommandContext, BaseCommandExecutionReceiver>, Arg>> {
                    return object :
                        PendingInvocation<ContextAndArg<ContextAndReceiver<BaseCommandContext, BaseCommandExecutionReceiver>, Arg>> {
                        override fun execute(block: suspend (ContextAndArg<ContextAndReceiver<BaseCommandContext, BaseCommandExecutionReceiver>, Arg>) -> Unit) {
                            strategy.executeCommandBlock {
                                block(
                                    ContextAndArg(
                                        context = ContextAndReceiver(
                                            context = object : BaseCommandContext {
                                                override val source: CommandEventSource
                                                    get() = theSource

                                                override val invocation: CommandInvocation
                                                    get() = theInvocation

                                                override fun tryFindDependency(tag: Any): Any? {
                                                    return strategy.tryFindDependency(tag)
                                                }
                                            },
                                            receiver = ExecutionReceiverImpl(
                                                strategy = strategy,
                                                source = source,
                                                invocation = invocation,
                                            )
                                        ),
                                        arg = mapParsed(results),
                                    ),
                                )
                            }
                        }
                    }
                }

                override fun receiverOnError(): PendingInvocation<Nothing> {
                    return nullPendingInvocation
                }
            },
        ).impl()
    }

    protected abstract fun TopLevelArgumentDescriptionReceiver<ContextAndReceiver<BaseCommandContext, BaseCommandExecutionReceiver>>.impl()

    fun usage(): String {
        return UsageTopLevelArgumentDescriptionReceiver(nullPendingInvocation).apply { impl() }.usage()
    }
}

typealias BaseCommandImplReceiver =
        TopLevelArgumentDescriptionReceiver<ContextAndReceiver<BaseCommandContext, BaseCommandExecutionReceiver>>