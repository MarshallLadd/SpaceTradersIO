package com.brokenhuskysledteam.spacetradersio.sdk.e2e

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.RegisterResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.testing.FakeTokenRepository
import org.junit.Assume.assumeTrue

/**
 * Shared support for **live end-to-end tests** that exercise the SDK against the real
 * SpaceTraders API.
 *
 * **How gating works:** these tests only run when the build is invoked with `-Pe2e` (see
 * `build.gradle.kts`), which forwards `spacetraders.e2e.enabled=true` and the account token
 * as JVM system properties. Every `*LiveTest` calls [assumeE2eEnabled] first, so a normal
 * `testAndroidHostTest` run (or CI) skips them entirely and never touches the network.
 *
 * **How auth works:** [registerE2eAgent] builds a *real* [SpaceTradersClient] (no mock engine —
 * `httpClientFactory = null` selects the OkHttp engine on the JVM classpath), registers a fresh
 * agent with a randomized callsign using the account token, and stores the returned agent token
 * in an in-memory [FakeTokenRepository] so all subsequent authenticated calls carry it. Using a
 * throwaway agent per run keeps tests reproducible and resilient to SpaceTraders' periodic
 * season resets.
 */
object E2eEnv {
    /** The account token forwarded by Gradle, or `null` when E2E is disabled / unconfigured. */
    val accountToken: String? = System.getProperty("spacetraders.e2e.accountToken")?.takeIf { it.isNotBlank() }

    /** True only when the build opted in via `-Pe2e` **and** an account token is available. */
    val enabled: Boolean = System.getProperty("spacetraders.e2e.enabled") == "true" && accountToken != null

    private const val CALLSIGN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    /** A random valid callsign (`CLD` + 8 alphanumerics = 11 chars, within the 3–14 limit). */
    fun randomCallSign(): String =
        "CLD" + (1..8).map { CALLSIGN_ALPHABET.random() }.joinToString("")
}

/**
 * Skips the calling test unless live E2E is enabled and configured. Call this as the first
 * line of every `*LiveTest` test method.
 */
fun assumeE2eEnabled() {
    assumeTrue(
        "Live E2E disabled — run with -Pe2e and a valid spacetraders.accountToken in secrets.properties",
        E2eEnv.enabled
    )
}

/**
 * A ready-to-use live session: a real authenticated [SpaceTradersClient], the freshly
 * registered agent's callsign, the raw registration bundle, and lazily-constructed endpoint
 * clients. Later phases extend this with their own API clients (market, etc.).
 */
class E2eSession(
    val client: SpaceTradersClient,
    val agentSymbol: String,
    val registration: RegisterResponseDto
) {
    val fleetApi = FleetApiImpl(client)
}

/**
 * Registers a fresh throwaway agent against the live API and returns a ready [E2eSession].
 *
 * @param faction Starting faction symbol (default `COSMIC`).
 */
suspend fun registerE2eAgent(faction: String = "COSMIC"): E2eSession {
    val token = requireNotNull(E2eEnv.accountToken) { "E2E account token missing" }
    val tokenRepository = FakeTokenRepository(storedToken = null)
    val client = SpaceTradersClient(tokenRepository = tokenRepository, httpClientFactory = null)
    val symbol = E2eEnv.randomCallSign()
    val registration = AccountsApi(client).register(symbol = symbol, faction = faction, accountToken = token)
    // Store the agent token so client.authenticated carries it on every subsequent call.
    tokenRepository.saveToken(registration.token)
    return E2eSession(client = client, agentSymbol = symbol, registration = registration)
}
