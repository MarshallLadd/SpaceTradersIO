package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract

/**
 * In-memory reactive cache for [Contract] entities, keyed by contract ID.
 *
 * **Pattern:** Typed subclass of [EntityStateStoreImpl] — the entire class body is empty
 * because all logic lives in the base class. Only the concrete type parameters
 * (`String` key, [Contract] value) are provided here. In a new project, repeat this
 * one-liner for each entity type you need a state store for; no additional code is required.
 *
 * **In this project:** Created as a session-scoped resource inside [com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SpaceTradersSessionImpl].
 * Populated by the contracts repository when the API returns contract data; observed by
 * ViewModels via [EntityStateStore.entities] or [EntityStateStore.observe].
 */
class ContractStateStore : EntityStateStoreImpl<String, Contract>()
