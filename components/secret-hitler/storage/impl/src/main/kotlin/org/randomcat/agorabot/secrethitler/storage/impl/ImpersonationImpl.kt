package org.randomcat.agorabot.secrethitler.storage.impl

import kotlinx.collections.immutable.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.persist.AtomicCachedStorage
import org.randomcat.agorabot.config.persist.ConfigPersistService
import org.randomcat.agorabot.config.persist.StorageStrategy
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerMutableImpersonationMap
import java.nio.file.Path

class SecretHitlerJsonImpersonationMap(
    storagePath: Path,
    persistService: ConfigPersistService,
) : SecretHitlerMutableImpersonationMap {
    private data class ValueType(
        val namesByUserId: PersistentMap<String, String>,
        val dmRecipientsByName: PersistentMap<String, PersistentSet<String>>,
    ) {
        constructor() : this(
            namesByUserId = persistentMapOf(),
            dmRecipientsByName = persistentMapOf(),
        )

        fun toStorage(): StorageType {
            return StorageType(
                namesByUserId = namesByUserId,
                dmRecipientsByName = dmRecipientsByName,
            )
        }
    }

    @Serializable
    private data class StorageType(
        val namesByUserId: Map<String, String>,
        val dmRecipientsByName: Map<String, Set<String>>,
    ) {
        fun toValue(): ValueType {
            return ValueType(
                namesByUserId = namesByUserId.toPersistentMap(),
                dmRecipientsByName = dmRecipientsByName.mapValues { (_, v) -> v.toPersistentSet() }.toPersistentMap(),
            )
        }
    }

    private object Strategy : StorageStrategy<ValueType> {
        override fun defaultValue(): ValueType {
            return ValueType()
        }

        override fun encodeToString(value: ValueType): String {
            return Json.encodeToString<StorageType>(value.toStorage())
        }

        override fun decodeFromString(text: String): ValueType {
            return Json.decodeFromString<StorageType>(text).toValue()
        }
    }

    private val impl = AtomicCachedStorage<ValueType>(storagePath, Strategy, persistService)

    override fun currentNameForId(userId: String): String? {
        return impl.getValue().namesByUserId[userId]
    }

    override fun dmUserIdsForName(name: String): Set<String>? {
        return impl.getValue().dmRecipientsByName[name]?.takeIf { it.isNotEmpty() }
    }

    override fun setNameForId(userId: String, newName: String) {
        impl.updateValue { old ->
            old.copy(namesByUserId = old.namesByUserId.put(userId, newName))
        }
    }

    override fun clearNameForId(userId: String) {
        impl.updateValue { old ->
            old.copy(namesByUserId = old.namesByUserId.remove(userId))
        }
    }

    override fun addDmUserIdForName(name: String, userId: String) {
        impl.updateValue { old ->
            old.copy(
                dmRecipientsByName = old.dmRecipientsByName.put(
                    name,
                    (old.dmRecipientsByName[userId] ?: persistentSetOf()).add(userId),
                ),
            )
        }
    }

    override fun clearDmUsersForName(name: String) {
        impl.updateValue { old ->
            old.copy(dmRecipientsByName = old.dmRecipientsByName.put(name, persistentSetOf()))
        }
    }
}
