package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

interface EntityStateStore<K, T> {
    val entities: StateFlow<Map<K, T>>
    fun observe(key: K): Flow<T?>
    fun put(key: K, entity: T)
    fun putAll(entities: Map<K, T>)
    fun update(key: K, transform: (T) -> T)
    fun remove(key: K)
    fun clear()
}

class EntityStateStoreImpl<K, T> : EntityStateStore<K, T> {

    private val _entities = MutableStateFlow<Map<K, T>>(emptyMap())
    override val entities: StateFlow<Map<K, T>> = _entities.asStateFlow()

    override fun observe(key: K): Flow<T?> =
        _entities.map { it[key] }.distinctUntilChanged()

    override fun put(key: K, entity: T) {
        _entities.update { it + (key to entity) }
    }

    override fun putAll(entities: Map<K, T>) {
        _entities.update { it + entities }
    }

    override fun update(key: K, transform: (T) -> T) {
        _entities.update { map ->
            val existing = map[key] ?: return@update map
            map + (key to transform(existing))
        }
    }

    override fun remove(key: K) {
        _entities.update { it - key }
    }

    override fun clear() {
        _entities.update { emptyMap() }
    }
}
