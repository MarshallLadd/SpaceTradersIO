package com.brokenhuskysledteam.spacetradersio.ui.systemmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard

/**
 * Stateful entry point for the System Map screen.
 *
 * **Pattern:** Stateful/stateless screen split. The stateful composable owns ViewModel
 * creation and state collection. It immediately delegates all rendering to the stateless
 * [SystemMapScreenContent], which receives only plain data and a lambda. To apply in a new
 * project: keep every `hiltViewModel()` and `collectAsStateWithLifecycle()` call in a
 * single outer composable and pass the results down — this makes the inner composable
 * fully previewable and testable without a Hilt graph.
 *
 * **In this project:** Wraps [SystemMapScreenContent] and provides the [SystemMapViewModel]
 * obtained via Hilt navigation.
 *
 * @param onNavigateBack Callback invoked when the user requests back navigation. Currently
 *   wired through the nav host but not yet surfaced as a UI affordance on this screen.
 * @param viewModel Hilt-injected ViewModel; defaults to the standard `hiltViewModel()` so
 *   tests can supply a fake without changing the call site.
 */
@Composable
fun SystemMapScreen(
    onNavigateBack: () -> Unit,
    viewModel: SystemMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SystemMapScreenContent(uiState = uiState, onEvent = viewModel::onEvent)
}

/**
 * Stateless System Map screen content.
 *
 * **Pattern:** Stateless content composable. Renders three possible states:
 * 1. **Loading** — full-screen spinner while the first waypoint page loads.
 * 2. **Fatal error** — full-screen error card with a Retry button (shown only when the
 *    waypoint list is also empty; a non-empty list with an error shows an inline error card
 *    above the list instead).
 * 3. **Content** — a [LazyColumn] containing the sort/filter controls and the waypoint tree.
 *
 * A [ScanlineOverlay] is rendered on top of all states to maintain the retro terminal
 * aesthetic. It is placed inside the root [Box] so it composites over the content rather
 * than displacing it.
 *
 * @param uiState Current screen state produced by [SystemMapViewModel].
 * @param onEvent Callback for all user interactions; routes to [SystemMapViewModel.onEvent].
 */
@Composable
fun SystemMapScreenContent(
    uiState: SystemMapUiState,
    onEvent: (SystemMapEvent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // Show a full-screen error only when there are no cached waypoints to display.
            // If waypoints exist, the inline error card inside the LazyColumn is shown instead.
            uiState.error != null && uiState.waypoints.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    TerminalCard(title = "Error") {
                        Text(text = uiState.error, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        TerminalButton(
                            text = "Retry",
                            onClick = { onEvent(SystemMapEvent.RetryClicked) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            else -> {
                // Local (composable-owned) view state: whether the spatial map canvas is shown,
                // and which waypoint the user tapped on the canvas. Kept here rather than in the
                // ViewModel because it is pure presentation state with no domain meaning.
                var showMap by remember { mutableStateOf(true) }
                var selectedMapWaypoint by remember { mutableStateOf<WaypointSummary?>(null) }

                // Hierarchical LazyColumn: parent waypoints and their orbitals are rendered as
                // flat list items. Nested LazyColumns are not allowed in Compose (they conflict
                // with the parent's scroll measurement), so orbital items are emitted as
                // sibling `items()` blocks via recursive calls inside WaypointRow, using
                // `padding(start = indentLevel * 24.dp)` to express hierarchy visually.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                        Text(
                            text = "SYSTEM: ${uiState.systemSymbol}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // Show ship context line when this screen was opened from a ship detail.
                        uiState.selectedShip?.let { ship ->
                            Text(
                                text = "// ${ship.symbol} @ ${ship.waypointSymbol} | FUEL: ${ship.fuelCurrent}/${ship.fuelCapacity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        // LIST / MAP view toggle.
                        Row {
                            TerminalButton(
                                text = if (showMap) "● MAP" else "○ MAP",
                                onClick = { showMap = true },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TerminalButton(
                                text = if (!showMap) "● LIST" else "○ LIST",
                                onClick = { showMap = false },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (!showMap) {
                            SortBar(uiState = uiState, onEvent = onEvent)
                            Spacer(modifier = Modifier.height(8.dp))
                            FilterChipRow(uiState = uiState, onEvent = onEvent)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Spatial map: a Canvas plot of all waypoints by coordinate. Tapping a dot
                    // selects it and reveals a Navigate affordance below.
                    if (showMap) {
                        item {
                            SystemMapCanvas(
                                waypoints = uiState.waypoints.flattenSummaries(),
                                shipLocation = uiState.selectedShip?.let { it.x to it.y },
                                selected = selectedMapWaypoint,
                                onSelect = { selectedMapWaypoint = it }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            selectedMapWaypoint?.let { wp ->
                                SelectedWaypointCard(
                                    waypoint = wp,
                                    showNavigate = uiState.selectedShip != null
                                        && wp.symbol != uiState.selectedShip.waypointSymbol
                                        && uiState.selectedShip.navStatus != ShipNavStatus.IN_TRANSIT,
                                    onNavigate = { onEvent(SystemMapEvent.NavigateToWaypoint(wp.symbol)) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }

                    // Inline error shown when a refresh fails but cached waypoints are present.
                    if (uiState.error != null) {
                        item {
                            TerminalCard(title = "Error", borderColor = MaterialTheme.colorScheme.error) {
                                Text(text = uiState.error, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Action result card shown after a navigation command completes.
                    if (uiState.actionResult != null) {
                        item {
                            ActionResultCard(result = uiState.actionResult, onDismiss = { onEvent(SystemMapEvent.ActionResultDismissed) })
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Render each root waypoint node. WaypointRow recursively appends its
                    // orbital children as additional composables within the same LazyColumn
                    // frame via the forEach loop at the bottom of WaypointRow.
                    // In LIST mode, render the hierarchical waypoint rows. (MAP mode shows the
                    // canvas plot instead, added above.)
                    if (!showMap) {
                        items(uiState.waypoints, key = { it.waypoint.symbol }) { node ->
                            WaypointRow(
                                node = node,
                                isCurrentLocation = node.waypoint.symbol == uiState.focusWaypointSymbol,
                                showNavigateButton = uiState.selectedShip != null
                                    && node.waypoint.symbol != uiState.selectedShip.waypointSymbol
                                    && uiState.selectedShip.navStatus != ShipNavStatus.IN_TRANSIT,
                                isActionInProgress = uiState.isActionInProgress,
                                indentLevel = 0,
                                onNavigate = { onEvent(SystemMapEvent.NavigateToWaypoint(it)) }
                            )
                        }
                    }
                }
            }
        }
        // Retro scanline effect rendered last so it composites on top of all content.
        ScanlineOverlay()
    }
}

/**
 * Sort mode and distance-origin selector row.
 *
 * **Pattern:** Active-state button toggle. The active selection is indicated by wrapping
 * the label in brackets (e.g., `[NAME]` vs `NAME`) rather than a separate selected-state
 * color, matching the terminal aesthetic. To apply in a new project: pass the current
 * selection into the composable and derive the button label from an equality check.
 *
 * When [SortMode.DISTANCE] is active, a second row of buttons appears to choose whether
 * distances are measured from the system center or the selected ship's location.
 *
 * @param uiState Current screen state; provides [SystemMapUiState.sortMode] and
 *   [SystemMapUiState.distanceOrigin].
 * @param onEvent Callback used to emit [SystemMapEvent.SortModeSelected] and
 *   [SystemMapEvent.DistanceOriginSelected].
 */
@Composable
private fun SortBar(uiState: SystemMapUiState, onEvent: (SystemMapEvent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TerminalButton(
            text = if (uiState.sortMode == SortMode.NAME) "[NAME]" else "NAME",
            onClick = { onEvent(SystemMapEvent.SortModeSelected(SortMode.NAME)) },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        TerminalButton(
            text = if (uiState.sortMode == SortMode.DISTANCE) "[DISTANCE]" else "DISTANCE",
            onClick = { onEvent(SystemMapEvent.SortModeSelected(SortMode.DISTANCE)) },
            modifier = Modifier.weight(1f)
        )
    }
    // The distance-origin sub-row is conditional on distance sort being active to avoid
    // showing controls that have no effect when the list is sorted by name.
    if (uiState.sortMode == SortMode.DISTANCE) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TerminalButton(
                text = if (uiState.distanceOrigin == DistanceOrigin.SYSTEM_CENTER) "[CENTER]" else "CENTER",
                onClick = { onEvent(SystemMapEvent.DistanceOriginSelected(DistanceOrigin.SYSTEM_CENTER)) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TerminalButton(
                text = if (uiState.distanceOrigin == DistanceOrigin.SHIP_LOCATION) "[SHIP]" else "SHIP",
                onClick = { onEvent(SystemMapEvent.DistanceOriginSelected(DistanceOrigin.SHIP_LOCATION)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Horizontally scrollable row of filter toggle chips for waypoint type and trait filtering.
 *
 * **Pattern:** Set-membership chip row. Each chip represents one filter value. Active state
 * is determined by checking whether the value is a member of the corresponding `Set` in
 * [SystemMapUiState] — no separate boolean flag per chip is needed. Toggling a chip emits
 * a [SystemMapEvent.TypeFilterToggled] or [SystemMapEvent.TraitFilterToggled] event;
 * the ViewModel adds or removes the value from the set. To apply in a new project: drive
 * chip active/inactive state purely from `value in set` and let the ViewModel own the set.
 *
 * The row uses `horizontalScroll` rather than a `LazyRow` because the number of chips is
 * small and fixed; `LazyRow` inside a `LazyColumn` item would require a fixed height.
 *
 * Active chips are displayed with brackets around their label (e.g., `[PLANET]`) to match
 * the terminal aesthetic used throughout the app.
 *
 * @param uiState Provides [SystemMapUiState.activeTypeFilters] and
 *   [SystemMapUiState.activeTraitFilters] for membership checks.
 * @param onEvent Callback used to emit filter toggle events.
 */
@Composable
private fun FilterChipRow(uiState: SystemMapUiState, onEvent: (SystemMapEvent) -> Unit) {
    // Curated subset of types/traits surfaced as quick filters. Full enum lists would
    // overflow the row and cover less common cases the user rarely needs.
    val typeFilters = listOf(WaypointType.PLANET, WaypointType.GAS_GIANT, WaypointType.ASTEROID_FIELD, WaypointType.JUMP_GATE)
    val traitFilters = listOf(WaypointTraitSymbol.MARKETPLACE, WaypointTraitSymbol.SHIPYARD, WaypointTraitSymbol.UNCHARTED)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        typeFilters.forEach { type ->
            val active = type in uiState.activeTypeFilters
            TerminalButton(
                text = if (active) "[${type.name}]" else type.name,
                onClick = { onEvent(SystemMapEvent.TypeFilterToggled(type)) }
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        traitFilters.forEach { trait ->
            val active = trait in uiState.activeTraitFilters
            TerminalButton(
                text = if (active) "[${trait.name}]" else trait.name,
                onClick = { onEvent(SystemMapEvent.TraitFilterToggled(trait)) }
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

/**
 * Renders one waypoint node as a [TerminalCard] and recursively renders its orbitals.
 *
 * **Pattern:** Hierarchical flat-list rendering. Because Compose does not allow nested
 * `LazyColumn` / `LazyRow` layouts (the inner lazy layout cannot participate in the outer
 * layout's size measurement), the orbital children are emitted as plain composables in a
 * `forEach` loop immediately after the parent card. Each level adds
 * `indentLevel * 24.dp` of start padding to express parent/child relationships visually
 * without a nested scroll container.
 *
 * **Navigate button display logic:** The "Navigate" button is shown on a waypoint card only
 * when all three conditions are true:
 * 1. A ship is selected (`selectedShip != null` in the parent call site).
 * 2. The ship is NOT currently [ShipNavStatus.IN_TRANSIT] — a ship in transit cannot be
 *    commanded.
 * 3. The waypoint is not the ship's current location — navigating to the current waypoint
 *    is a no-op and would produce an API error.
 *
 * The "YOU ARE HERE" indicator uses [MaterialTheme.colorScheme.tertiary] (amber in the
 * terminal theme) to call attention to the ship's current position among many cards.
 *
 * @param node The waypoint node to render, including any orbital children.
 * @param isCurrentLocation `true` if this waypoint is the ship's current location; triggers
 *   the amber "YOU ARE HERE" label.
 * @param showNavigateButton Whether to show the Navigate button on this card. Evaluated once
 *   for the parent and passed unchanged to orbitals so they respect the same rule.
 * @param isActionInProgress `true` while a navigation command is in-flight; disables the
 *   button and shows an ellipsis label to prevent double-taps.
 * @param indentLevel Nesting depth; root waypoints use 0, orbitals use 1.
 * @param onNavigate Called with the waypoint symbol when the Navigate button is tapped.
 */
@Composable
private fun WaypointRow(
    node: WaypointNode,
    isCurrentLocation: Boolean,
    showNavigateButton: Boolean,
    isActionInProgress: Boolean,
    indentLevel: Int,
    onNavigate: (String) -> Unit
) {
    val wp = node.waypoint
    TerminalCard(
        title = wp.symbol,
        modifier = Modifier.padding(start = (indentLevel * 24).dp, bottom = 8.dp)
    ) {
        Text(
            text = "TYPE: ${wp.type.name} | DIST: ${"%.1f".format(node.distance)} AU",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "COORDS: (${wp.x}, ${wp.y})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        // Show notable trait badges. Only the three most actionable traits are surfaced;
        // the full trait list is available on a future waypoint-detail screen.
        if (wp.hasMarketplace || wp.hasShipyard || wp.isUncharted) {
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                if (wp.hasMarketplace) {
                    Text(text = "[MARKET] ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (wp.hasShipyard) {
                    Text(text = "[SHIPYARD] ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (wp.isUncharted) {
                    Text(text = "[UNCHARTED] ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // Amber "YOU ARE HERE" label using tertiary color to stand out against the
        // default green primary text. The tertiary color maps to amber in the terminal theme.
        if (isCurrentLocation) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "> YOU ARE HERE",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        if (showNavigateButton) {
            Spacer(modifier = Modifier.height(8.dp))
            TerminalButton(
                text = if (isActionInProgress) "..." else "Navigate",
                onClick = { onNavigate(wp.symbol) },
                // Disable during an in-flight request to prevent issuing duplicate commands.
                enabled = !isActionInProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Recursively render orbitals as siblings in the parent LazyColumn, incrementing indent.
    // This produces a flat Compose node tree while preserving visual hierarchy.
    node.orbitals.forEach { orbital ->
        WaypointRow(
            node = orbital,
            isCurrentLocation = orbital.waypoint.symbol == node.waypoint.symbol,
            showNavigateButton = showNavigateButton,
            isActionInProgress = isActionInProgress,
            indentLevel = indentLevel + 1,
            onNavigate = onNavigate
        )
    }
}

/**
 * Amber feedback card displayed after a ship navigation command completes.
 *
 * **Pattern:** Action result banner. Rather than relying on a `Snackbar` (which auto-dismisses
 * and can be missed), command feedback is displayed as a persistent card that the user must
 * explicitly dismiss. This is consistent with the terminal aesthetic and ensures the fuel
 * consumption numbers are readable at the user's pace.
 *
 * Uses [MaterialTheme.colorScheme.tertiary] (amber in the terminal theme) for all text and
 * the card border to visually distinguish it from regular waypoint cards.
 *
 * @param result The action result to display; currently only [SystemMapActionResult.NavigationStarted].
 * @param onDismiss Called when the user taps the Dismiss button, which emits
 *   [SystemMapEvent.ActionResultDismissed] to clear [SystemMapUiState.actionResult].
 */
@Composable
private fun ActionResultCard(result: SystemMapActionResult, onDismiss: () -> Unit) {
    val amberColor = MaterialTheme.colorScheme.tertiary
    TerminalCard(title = "Command Output", borderColor = amberColor) {
        when (result) {
            is SystemMapActionResult.NavigationStarted -> {
                Text(text = "> NAVIGATION INITIATED", style = MaterialTheme.typography.bodyMedium, color = amberColor)
                Text(text = "  DESTINATION: ${result.destinationSymbol}", style = MaterialTheme.typography.bodyMedium, color = amberColor)
                Text(text = "  FUEL CONSUMED: ${result.fuelConsumed}", style = MaterialTheme.typography.bodyMedium, color = amberColor)
                Text(text = "  FUEL REMAINING: ${result.fuelRemaining}", style = MaterialTheme.typography.bodyMedium, color = amberColor)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TerminalButton(
            text = "Dismiss",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Flattens the waypoint tree (parents + orbitals) into a single list of summaries for plotting. */
private fun List<WaypointNode>.flattenSummaries(): List<WaypointSummary> =
    flatMap { listOf(it.waypoint) + it.orbitals.flattenSummaries() }

/**
 * A 2-D spatial plot of the system's waypoints on a [Canvas], scaled to fit their coordinate
 * bounds. Each waypoint is a dot coloured by its salient trait (marketplace/shipyard/uncharted);
 * the selected ship's location is drawn as a ring ("you are here"). Tapping near a dot selects
 * that waypoint via [onSelect]. This is the spatial upgrade to the waypoint list — coordinates
 * that were previously text are now plotted.
 */
@Composable
private fun SystemMapCanvas(
    waypoints: List<WaypointSummary>,
    shipLocation: Pair<Int, Int>?,
    selected: WaypointSummary?,
    onSelect: (WaypointSummary) -> Unit
) {
    if (waypoints.isEmpty()) {
        TerminalCard(title = "MAP") {
            Text("NO WAYPOINTS TO PLOT.", color = MaterialTheme.colorScheme.tertiary)
        }
        return
    }

    val marketColor = MaterialTheme.colorScheme.primary
    val shipyardColor = MaterialTheme.colorScheme.tertiary
    val dimColor = MaterialTheme.colorScheme.outline
    val defaultColor = MaterialTheme.colorScheme.onSurfaceVariant
    val ringColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    val xs = waypoints.map { it.x } + listOfNotNull(shipLocation?.first)
    val ys = waypoints.map { it.y } + listOfNotNull(shipLocation?.second)
    val minX = xs.min().toFloat(); val spanX = (xs.max() - xs.min()).coerceAtLeast(1).toFloat()
    val minY = ys.min().toFloat(); val spanY = (ys.max() - ys.min()).coerceAtLeast(1).toFloat()
    val margin = 0.12f

    // Maps data coordinates to canvas pixels (y inverted so higher y is up). Shared by the draw
    // pass and the tap hit-test so both agree on where each dot is.
    fun project(x: Float, y: Float, w: Float, h: Float): Offset {
        val nx = (x - minX) / spanX
        val ny = (y - minY) / spanY
        return Offset((margin + nx * (1 - 2 * margin)) * w, (margin + (1 - ny) * (1 - 2 * margin)) * h)
    }
    fun colorFor(wp: WaypointSummary): Color = when {
        wp.isUncharted -> dimColor
        wp.hasShipyard -> shipyardColor
        wp.hasMarketplace -> marketColor
        else -> defaultColor
    }

    TerminalCard(title = "MAP") {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .pointerInput(waypoints) {
                    detectTapGestures { tap ->
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        val hit = waypoints.minByOrNull { (project(it.x.toFloat(), it.y.toFloat(), w, h) - tap).getDistanceSquared() }
                        if (hit != null && (project(hit.x.toFloat(), hit.y.toFloat(), w, h) - tap).getDistance() <= 44f) {
                            onSelect(hit)
                        }
                    }
                }
        ) {
            // Faint center crosshair at system origin (0,0), if within bounds.
            val origin = project(0f, 0f, size.width, size.height)
            drawLine(gridColor, Offset(0f, origin.y), Offset(size.width, origin.y))
            drawLine(gridColor, Offset(origin.x, 0f), Offset(origin.x, size.height))

            waypoints.forEach { wp ->
                val o = project(wp.x.toFloat(), wp.y.toFloat(), size.width, size.height)
                drawCircle(colorFor(wp), radius = if (wp == selected) 10f else 5f, center = o)
                if (wp == selected) drawCircle(ringColor, radius = 14f, center = o, style = Stroke(width = 2f))
            }
            shipLocation?.let { (sx, sy) ->
                val o = project(sx.toFloat(), sy.toFloat(), size.width, size.height)
                drawCircle(shipyardColor, radius = 9f, center = o, style = Stroke(width = 3f))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "◍ MARKET  ◍ SHIPYARD  ○ YOUR SHIP  •  TAP A DOT",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/** Info + Navigate affordance for the waypoint the user tapped on the [SystemMapCanvas]. */
@Composable
private fun SelectedWaypointCard(
    waypoint: WaypointSummary,
    showNavigate: Boolean,
    onNavigate: () -> Unit
) {
    TerminalCard(title = waypoint.symbol) {
        Text(waypoint.type.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        val traits = waypoint.traits.joinToString { it.name }
        if (traits.isNotBlank()) {
            Text(traits, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        if (showNavigate) {
            Spacer(modifier = Modifier.height(8.dp))
            TerminalButton(text = "NAVIGATE", onClick = onNavigate, modifier = Modifier.fillMaxWidth())
        }
    }
}
