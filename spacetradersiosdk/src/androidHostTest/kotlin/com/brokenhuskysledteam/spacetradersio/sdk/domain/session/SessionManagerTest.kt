package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.testing.FakeTokenRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerTest {

    @Test
    fun requireSessionThrowsWhenNoSession() {
        val manager = SessionManager(FakeTokenRepository(storedToken = null))
        assertFailsWith<IllegalStateException> {
            manager.requireSession()
        }
    }

    @Test
    fun loginCreatesSessionAndSavesToken() {
        val tokenRepo = FakeTokenRepository(storedToken = null)
        val manager = SessionManager(tokenRepo)
        manager.login("my-token")
        assertNotNull(manager.requireSession())
        assertEquals("my-token", tokenRepo.storedToken)
    }

    @Test
    fun logoutDestroysSessionAndClearsToken() {
        val tokenRepo = FakeTokenRepository(storedToken = "my-token")
        val manager = SessionManager(tokenRepo)
        manager.login("my-token")
        manager.logout()
        assertNull(tokenRepo.storedToken)
        assertFailsWith<IllegalStateException> { manager.requireSession() }
    }

    @Test
    fun restoreIfAuthenticatedCreatesSessionWhenTokenExists() {
        val manager = SessionManager(FakeTokenRepository(storedToken = "existing-token"))
        manager.restoreIfAuthenticated()
        assertNotNull(manager.requireSession())
    }

    @Test
    fun restoreIfAuthenticatedDoesNothingWhenNoToken() {
        val manager = SessionManager(FakeTokenRepository(storedToken = null))
        manager.restoreIfAuthenticated()
        assertFailsWith<IllegalStateException> { manager.requireSession() }
    }

    @Test
    fun restoreIfAuthenticatedDoesNotRecreateExistingSession() {
        val tokenRepo = FakeTokenRepository(storedToken = "token")
        val manager = SessionManager(tokenRepo)
        manager.restoreIfAuthenticated()
        val session1 = manager.requireSession()
        manager.restoreIfAuthenticated()
        val session2 = manager.requireSession()
        assertTrue(session1 === session2)
    }

    @Test
    fun loginAfterLogoutCreatesNewSession() {
        val tokenRepo = FakeTokenRepository(storedToken = null)
        val manager = SessionManager(tokenRepo)
        manager.login("token-1")
        val session1 = manager.requireSession()
        manager.logout()
        manager.login("token-2")
        val session2 = manager.requireSession()
        assertTrue(session1 !== session2)
    }

    @Test
    fun sessionContainsAllStores() {
        val manager = SessionManager(FakeTokenRepository(storedToken = null))
        manager.login("token")
        val session = manager.requireSession()
        assertNotNull(session.fleetStateStore)
        assertNotNull(session.agentStateStore)
        assertNotNull(session.contractStateStore)
        assertNotNull(session.refreshScheduler)
    }
}
