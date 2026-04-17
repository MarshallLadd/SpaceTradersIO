package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint

/**
 * In-memory reactive cache for [Waypoint] entities, keyed by waypoint symbol.
 *
 * **Pattern:** Typed subclass of [EntityStateStoreImpl] — see [ContractStateStore] for a
 * full explanation of the pattern. The waypoint symbol (e.g. `"X1-DF55-20250Z"`) is used
 * as the key because it is globally unique and matches how the SpaceTraders API references
 * waypoints in ship navigation and market responses.
 *
 * **In this project:** Created as a session-scoped resource inside [com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SpaceTradersSessionImpl].
 * Populated by `SystemRepositoryImpl` when a system's waypoints are fetched from the API.
 * Consumers (e.g. ship detail screens) can observe individual waypoints reactively via
 * [EntityStateStore.observe] rather than re-fetching from the network on each navigation.
 */
class WaypointStateStore : EntityStateStoreImpl<String, Waypoint>()
