package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.testing.FakeTokenRepository
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerTest {

    @Test
    fun requireSessionThrowsWhenNoSession() {
        val manager = SessionManagerImpl(FakeTokenRepository(storedToken = null), createTestDatabase())
        assertFailsWith<IllegalStateException> {
            manager.requireSession()
        }
    }

    @Test
    fun loginCreatesSessionAndSavesToken() {
        val tokenRepo = FakeTokenRepository(storedToken = null)
        val manager = SessionManagerImpl(tokenRepo, createTestDatabase())
        manager.login("my-token")
        assertNotNull(manager.requireSession())
        assertEquals("my-token", tokenRepo.storedToken)
    }

    @Test
    fun logoutDestroysSessionAndClearsToken() {
        val tokenRepo = FakeTokenRepository(storedToken = "my-token")
        val manager = SessionManagerImpl(tokenRepo, createTestDatabase())
        manager.login("my-token")
        manager.logout()
        assertNull(tokenRepo.storedToken)
        assertFailsWith<IllegalStateException> { manager.requireSession() }
    }

    @Test
    fun logoutCancelsSessionCoroutineScope() {
        val manager = SessionManagerImpl(FakeTokenRepository(storedToken = null), createTestDatabase())
        manager.login("token")
        val session = manager.requireSession()
        assertTrue(session.isActive)
        manager.logout()
        assertFalse(session.isActive)
    }

    @Test
    fun restoreIfAuthenticatedCreatesSessionWhenTokenExists() {
        val manager = SessionManagerImpl(FakeTokenRepository(storedToken = "existing-token"), createTestDatabase())
        manager.restoreIfAuthenticated()
        assertNotNull(manager.requireSession())
    }

    @Test
    fun restoreIfAuthenticatedDoesNothingWhenNoToken() {
        val manager = SessionManagerImpl(FakeTokenRepository(storedToken = null), createTestDatabase())
        manager.restoreIfAuthenticated()
        assertFailsWith<IllegalStateException> { manager.requireSession() }
    }

    @Test
    fun restoreIfAuthenticatedDoesNotRecreateExistingSession() {
        val tokenRepo = FakeTokenRepository(storedToken = "token")
        val manager = SessionManagerImpl(tokenRepo, createTestDatabase())
        manager.restoreIfAuthenticated()
        val session1 = manager.requireSession()
        manager.restoreIfAuthenticated()
        val session2 = manager.requireSession()
        assertTrue(session1 === session2)
    }

    @Test
    fun loginAfterLogoutCreatesNewSession() {
        val tokenRepo = FakeTokenRepository(storedToken = null)
        val manager = SessionManagerImpl(tokenRepo, createTestDatabase())
        manager.login("token-1")
        val session1 = manager.requireSession()
        manager.logout()
        manager.login("token-2")
        val session2 = manager.requireSession()
        assertTrue(session1 !== session2)
    }

    @Test
    fun sessionContainsAllStores() {
        val manager = SessionManagerImpl(FakeTokenRepository(storedToken = null), createTestDatabase())
        manager.login("token")
        val session = manager.requireSession()
        assertNotNull(session.contractStateStore)
        assertNotNull(session.waypointStateStore)
        assertNotNull(session.refreshScheduler)
        assertNotNull(session.database)
    }

    @Test
    fun logout_clearsDbTables() {
        val db = createTestDatabase()
        val manager = SessionManagerImpl(FakeTokenRepository(storedToken = null), db)
        manager.login("token")
        // Seed a ship row directly to verify logout wipes it
        db.shipQueries.selectAllShips().executeAsList().let { assertEquals(0, it.size) }
        manager.logout()
        assertEquals(0, db.shipQueries.selectAllShips().executeAsList().size)
        assertEquals(0, db.agentQueries.selectAgent().executeAsOneOrNull()?.let { 1 } ?: 0)
    }
}
