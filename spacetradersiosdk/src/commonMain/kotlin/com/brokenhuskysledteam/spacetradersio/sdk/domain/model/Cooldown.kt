package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlin.time.Instant

/**
 * Represents the reactor cooldown period after a ship has used a module that drains its
 * reactor (e.g. scanning, extracting resources, or jumping).
 *
 * **Pattern:** Immutable domain model with an optional timestamp. In any project where an
 * operation is rate-limited by a server-side timer, model the cooldown as a plain `data class`
 * holding both the total/remaining durations and the absolute expiry time. The absolute
 * [expiration] lets a client compute "time until ready" without clocks drifting, while
 * [remainingSeconds] is convenient for an immediate display without doing timestamp arithmetic.
 *
 * **In this project:** Returned alongside extraction, scan, and jump responses and stored in
 * [Ship.cooldown]. The `RefreshScheduler` in the SDK watches [expiration] and triggers a
 * ship refresh automatically once the cooldown has elapsed, so the UI always reflects the
 * correct "ready" state without manual polling.
 *
 * @property shipSymbol The unique identifier of the ship this cooldown belongs to
 *   (e.g. `"MYAGENT-1"`). Matches [Ship.symbol] and is used as the lookup key when
 *   updating the ship in the local store.
 * @property totalSeconds The full duration of the cooldown in seconds as originally issued
 *   by the server. Useful for rendering a progress bar that shows elapsed vs. remaining time.
 * @property remainingSeconds The number of seconds still remaining at the moment this
 *   object was last fetched. Because domain objects are immutable, this value does not
 *   count down — it is a snapshot. Compute live remaining time from [expiration] instead.
 * @property expiration The absolute point in time when the cooldown ends. `null` for ships
 *   that have no active cooldown (the API omits the field in that case). Uses
 *   [kotlinx.datetime.Instant] rather than a platform date type so this model is safe to
 *   share across Android and iOS in `commonMain`.
 */
data class Cooldown(
    val shipSymbol: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    // Nullable: the API omits expiration entirely when there is no active cooldown.
    val expiration: Instant?
)
