package org.randomcat.agorabot.permissions

private const val BOT_PERMISSION_SCOPE = "bot"

data class BotScopeActionPermission(
    private val commandName: String,
    private val actionName: String,
) : BotPermission {
    override val path = PermissionScopedPath(
        scope = BOT_PERMISSION_SCOPE,
        basePath = PermissionPath(listOf(commandName, actionName))
    )

    private val commandPath = PermissionPath(listOf(commandName))

    override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
        return when (userContext) {
            is UserPermissionContext.Unauthenticated -> false

            is UserPermissionContext.Authenticated -> {
                val user = userContext.user

                return botContext.isBotAdmin(userId = user.id) ||
                        botContext.checkGlobalPath(userId = user.id, path.basePath)
                            .mapDeferred { botContext.checkGlobalPath(userId = user.id, commandPath) }
                            .isAllowed()
            }
        }
    }
}

data class BotScopeCommandPermission(private val commandName: String) : BotPermission {
    override val path = PermissionScopedPath(
        scope = BOT_PERMISSION_SCOPE,
        basePath = PermissionPath(listOf(commandName))
    )

    fun action(actionName: String) = BotScopeActionPermission(
        commandName = commandName,
        actionName = actionName,
    )

    override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
        return when (userContext) {
            is UserPermissionContext.Unauthenticated -> false

            is UserPermissionContext.Authenticated -> {
                val user = userContext.user

                return botContext.isBotAdmin(userId = user.id) ||
                        botContext.checkGlobalPath(userId = user.id, path.basePath).isAllowed()
            }
        }
    }
}

object BotScope {
    fun command(commandName: String) = BotScopeCommandPermission(commandName)

    fun admin() = object : BotPermission {
        override val path = PermissionScopedPath(
            scope = BOT_PERMISSION_SCOPE,
            basePath = PermissionPath(listOf("admin"))
        )

        override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
            return when (userContext) {
                is UserPermissionContext.Unauthenticated -> false
                is UserPermissionContext.Authenticated -> botContext.isBotAdmin(userId = userContext.user.id)
            }
        }
    }
}
