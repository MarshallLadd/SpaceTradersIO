package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Generic in-memory reactive store for domain entities, indexed by a key.
 *
 * **Pattern:** Typed key-value state store (interface). Define your store contract here
 * so consumers (repositories, ViewModels) depend only on the interface and can be tested
 * with a hand-written fake. In a new project, implement this interface (or extend
 * [EntityStateStoreImpl]) for each entity type you want to cache in memory between
 * API calls.
 *
 * **In this project:** Concrete subclasses ([ContractStateStore], [WaypointStateStore])
 * are session-scoped — created on login and discarded on logout — so the store is always
 * consistent with the currently authenticated agent.
 *
 * The store models its state as a single `Map<K, T>` snapshot. Every write replaces the
 * entire map atomically (via [kotlinx.coroutines.flow.MutableStateFlow.update]), which
 * means observers never see a partially-updated collection.
 *
 * @param K The key type used to index entities (e.g. `String` for an ID or symbol).
 * @param T The entity type stored (e.g. [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract]).
 *
 * @property entities Hot [StateFlow] emitting the full entity map whenever any entry
 *   changes. Collect this in a ViewModel when you need to observe all entities at once
 *   or combine multiple stores.
 */
interface EntityStateStore<K, T> {
    val entities: StateFlow<Map<K, T>>

    /**
     * Returns a [Flow] that emits the entity for [key] whenever it changes, or `null`
     * if no entity with that key is present.
     *
     * [distinctUntilChanged] is applied so the flow only emits when the value actually
     * changes, avoiding redundant recompositions in Compose UI or ViewModel processing.
     *
     * @param key The key to observe.
     * @return A cold flow derived from [entities].
     */
    fun observe(key: K): Flow<T?>

    /**
     * Inserts or replaces the entity for [key].
     *
     * @param key   The identifying key.
     * @param entity The entity value to store.
     */
    fun put(key: K, entity: T)

    /**
     * Inserts or replaces multiple entities in a single atomic update.
     *
     * Prefer this over calling [put] in a loop when loading a batch response from the
     * API — a single map update emits exactly one value to [entities] observers instead
     * of one emission per entry.
     *
     * @param entities Map of keys to entity values to merge into the store.
     */
    fun putAll(entities: Map<K, T>)

    /**
     * Applies [transform] to the entity at [key] and stores the result.
     *
     * A no-op if no entity exists for [key], making this safe to call speculatively
     * after a partial API response without risking a crash or stale insert.
     *
     * @param key       The key of the entity to update.
     * @param transform A pure function that receives the current value and returns the
     *   updated value. Must not have side effects, as [MutableStateFlow.update] may
     *   retry it under contention.
     */
    fun update(key: K, transform: (T) -> T)

    /**
     * Removes the entity with [key] from the store.
     *
     * @param key The key to remove. Safe to call if the key does not exist.
     */
    fun remove(key: K)

    /**
     * Removes all entities from the store, emitting an empty map to observers.
     *
     * Called by [com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SpaceTradersSessionImpl.destroy]
     * during logout to prevent stale data from leaking into a subsequent session.
     */
    fun clear()
}

/**
 * Default implementation of [EntityStateStore] backed by a [MutableStateFlow].
 *
 * **Pattern:** Open-class base implementation. Declare the logic once here; concrete
 * subclasses provide only the type parameters (see [ContractStateStore],
 * [WaypointStateStore]). Using `open class` instead of `abstract class` means
 * subclasses need zero boilerplate — the subclass body can be a single `()` invocation.
 *
 * **Why `StateFlow<Map<K, T>>`?** A single flow wrapping the entire map provides atomic
 * snapshot semantics: every call to [put], [putAll], [remove], or [clear] replaces the
 * whole map in one [MutableStateFlow.update] CAS operation. Observers always see a
 * consistent snapshot and never observe a map mid-update.
 *
 * **In this project:** Injected indirectly — consumers receive [ContractStateStore] or
 * [WaypointStateStore] and interact through the [EntityStateStore] interface. The `open`
 * modifier is also what allows test code to subclass this directly if needed.
 */
open class EntityStateStoreImpl<K, T> : EntityStateStore<K, T> {

    private val _entities = MutableStateFlow<Map<K, T>>(emptyMap())

    /** Public read-only view of the backing [MutableStateFlow]. */
    override val entities: StateFlow<Map<K, T>> = _entities.asStateFlow()

    override fun observe(key: K): Flow<T?> =
        // map + distinctUntilChanged: derive a per-key flow from the shared map flow
        // without creating a separate MutableStateFlow per entity.
        _entities.map { it[key] }.distinctUntilChanged()

    override fun put(key: K, entity: T) {
        // `+` on a Map returns a new map with the entry added/replaced — immutable update.
        _entities.update { it + (key to entity) }
    }

    override fun putAll(entities: Map<K, T>) {
        // Merges by key: existing keys not in [entities] are preserved.
        _entities.update { it + entities }
    }

    override fun update(key: K, transform: (T) -> T) {
        _entities.update { map ->
            // Return the map unchanged if the key is absent — avoids inserting a
            // default/null value for an entity the store has never seen.
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
