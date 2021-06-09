package org.randomcat.agorabot.digest

import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.requests.RestAction

private fun digestMessageWithNickname(message: Message, nickname: String?): DigestMessage {
    return DigestMessage(
        senderUsername = message.author.name,
        senderNickname = nickname,
        id = message.id,
        channelName = message.channel.name,
        content = message.contentDisplay,
        messageDate = message.timeCreated.toInstant(),
        attachmentUrls = message.attachments.map { it.url }.toImmutableList()
    )
}

private fun digestMessageWithForcedMember(message: Message, realMember: Member): DigestMessage =
    digestMessageWithNickname(
        message = message,
        nickname = realMember.nickname,
    )

fun Message.retrieveDigestMessage(): RestAction<DigestMessage> {
    // Use retrieveMessage to force update of nickname
    return guild.retrieveMember(author).map { retrievedMember ->
        digestMessageWithForcedMember(message = this, realMember = retrievedMember)
    }
}

fun RestAction<List<Message>>.mapToDigestMessages() = flatMap { it.retrieveDigestMessages() }

fun List<Message>.retrieveDigestMessages(): RestAction<List<DigestMessage>> {
    val messages = this // for clarity

    val nicknamesMapAction = RestAction.allOf(
        messages
            .filter { it.isFromGuild }
            .map { it.author to it.guild }
            .distinctBy { it.first.id to it.second.id }
            .map { (author, guild) -> guild.retrieveMember(author).map { (author.id to guild.id) to it.nickname } }
    ).map { it.toMap() }

    return nicknamesMapAction.map { nicknamesMap ->
        messages.map { digestMessageWithNickname(message = it, nickname = nicknamesMap[it.author.id to it.guild.id]) }
    }
}
