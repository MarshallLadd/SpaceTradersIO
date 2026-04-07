package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

open class EntityStateStore<K, T> {

    private val _entities = MutableStateFlow<Map<K, T>>(emptyMap())
    val entities: StateFlow<Map<K, T>> = _entities.asStateFlow()

    fun observe(key: K): Flow<T?> =
        _entities.map { it[key] }.distinctUntilChanged()

    fun observeAll(): StateFlow<Map<K, T>> = entities

    fun put(key: K, entity: T) {
        _entities.update { it + (key to entity) }
    }

    fun putAll(entities: Map<K, T>) {
        _entities.update { it + entities }
    }

    fun update(key: K, transform: (T) -> T) {
        _entities.update { map ->
            val existing = map[key] ?: return@update map
            map + (key to transform(existing))
        }
    }

    fun remove(key: K) {
        _entities.update { it - key }
    }

    fun clear() {
        _entities.update { emptyMap() }
    }
}
