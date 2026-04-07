package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntityStateStoreTest {

    private fun createStore() = EntityStateStoreImpl<String, String>()

    @Test
    fun initialStateIsEmpty() {
        val store = createStore()
        assertTrue(store.entities.value.isEmpty())
    }

    @Test
    fun putAddsEntity() = runTest {
        val store = createStore()
        store.put("key1", "value1")
        assertEquals("value1", store.entities.value["key1"])
    }

    @Test
    fun putReplacesExistingEntity() = runTest {
        val store = createStore()
        store.put("key1", "value1")
        store.put("key1", "value2")
        assertEquals("value2", store.entities.value["key1"])
    }

    @Test
    fun putAllMergesEntities() = runTest {
        val store = createStore()
        store.put("existing", "stays")
        store.putAll(mapOf("a" to "1", "b" to "2"))
        assertEquals(3, store.entities.value.size)
        assertEquals("stays", store.entities.value["existing"])
        assertEquals("1", store.entities.value["a"])
    }

    @Test
    fun updateTransformsEntity() = runTest {
        val store = createStore()
        store.put("key1", "hello")
        store.update("key1") { it.uppercase() }
        assertEquals("HELLO", store.entities.value["key1"])
    }

    @Test
    fun updateIgnoresNonexistentKey() = runTest {
        val store = createStore()
        store.update("missing") { it.uppercase() }
        assertTrue(store.entities.value.isEmpty())
    }

    @Test
    fun removeDeletesEntity() = runTest {
        val store = createStore()
        store.put("key1", "value1")
        store.remove("key1")
        assertNull(store.entities.value["key1"])
    }

    @Test
    fun clearRemovesAllEntities() = runTest {
        val store = createStore()
        store.putAll(mapOf("a" to "1", "b" to "2"))
        store.clear()
        assertTrue(store.entities.value.isEmpty())
    }

    @Test
    fun observeEmitsValueForKey() = runTest {
        val store = createStore()
        store.put("key1", "value1")
        val observed = store.observe("key1").first()
        assertEquals("value1", observed)
    }

    @Test
    fun observeEmitsNullForMissingKey() = runTest {
        val store = createStore()
        val observed = store.observe("missing").first()
        assertNull(observed)
    }

    @Test
    fun observeEmitsNullAfterRemove() = runTest {
        val store = createStore()
        store.put("key1", "value1")
        store.remove("key1")
        val observed = store.observe("key1").first()
        assertNull(observed)
    }
}
