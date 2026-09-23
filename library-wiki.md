# Gadgets & Gizmos experimental library API

The library owns reusable mechanisms under `com.rieno.gadgetsandgizmos.lib`.
The addon owns blocks, controller persistence, graph actions, networking, optional-mod adapters,
and player-facing configuration. Library APIs must not import addon implementation classes.
Java 21, Minecraft 1.21.1 and NeoForge are required; physics integration uses Sable.

## SCM, autopilot and shipping boundaries

| Library API | Contract and addon consumer |
| --- | --- |
| `ScmOrientation` | Validated signed local forward/up frame; consumed by the SCM profile, runtime and GUI. |
| `ScmControlAxes` | Physical torque/force to authored yaw and longitudinal group demand; consumed by SCM routing, profile/linker faces and wheel steering. |
| `ScmSpeedControl` | Converts actual/permitted speed into mutually exclusive, direction-independent Acceleration, Deceleration and Brake channel demands. Acceleration remains non-zero at cruise to hold the selected speed. |
| `ScmTarget` | Stable body/block identity with optional signal position, face and channel; `blockStableId()` groups every face of one physical block while `stableId()` identifies an exact binding. |
| `IDirectControlReceiver` | Named direct-control receiver; `applyExclusiveDirectControllerSignal(...)` clears an opposing channel before selecting one side, with atomic overrides supported by two-direction devices. |
| `ScmControlMode`, `ScmControlModeRegistry`, `ScmBuiltinControlModes` | Register and resolve vehicle control policies; convert world-space navigation input into control demand. Existing compatibility constructors remain supported. |
| `ScmControlProbe`, `ScmControlProbeRegistry` | Extensible live control probes; addon adapters supply block-specific behavior. |
| `ScmControlAuthorityApi` | Shared control ownership used when multiple SCMs participate in an assembly. |
| `ScmMapCompositionApi` | Compose connected control-map inputs without depending on addon map implementations. |
| `ScmFlightBehavior` | Shared flight-control behavior and calculations. |
| `GroundPathPlanner` | Capability-sized bicycle-model route steering, corner speed planning, direction-aligned rejoin, and committed forward/reverse multi-point recovery with host-supplied pose validation. |
| `LocalDetourPlanner` | Bounded immediate detours with a host-supplied segment collision validator. |
| `SablePathfinder` | Bounded generic world route search, directed route-graph stitching, route-terminal handoff geometry, immediate clear-direct completion, incremental queued full-route planning with shared tick work budgets, swept-hull waypoint advancement, live retained-route cursor selection and rejoining across loaded root-world and Sable-sublevel space. |
| `ScheduleRouteLoop` | Deterministic shortest ordered stop selection: one candidate per schedule layer, with an optional final-to-first cyclic edge cost. |
| `ScmVehicleClassifier` | Host-independent vehicle suggestions and registered mode selection validation; explicit selections override detection. |
| `ReactiveCollisionAvoidance` | Per-tick clearance-based travel-corridor escape decisions which temporarily override steering without owning or replacing the retained route; the addon supplies live world observations. |
| `WaypointProgressTracker` | Capture and stall tracking for an active point-to-point manoeuvre. |
| `OrientedHull` | Signed hull face/edge/corner targeting, independent of blocks, graph nodes and world access. |
| `SableAssemblyBoundsApi` | Conservative connected-body envelope and rotation-safe spatial radius for consumers that need clearance from a Sable hull. |
| `SableTransformApi` | Shared body/world and body/body coordinate transforms. |
| `SubLevelParticleOcclusion` | Root/Sable block-shape ray and swept-hull collision queries, with moving-body envelopes used only as a broad phase before exact shape refinement. |
| `SubLevelPreviewRenderer` | Client-only live craft rendering, picking, highlights, directional arrow markers, snapshot fallback and optional full-bright presentation. |
| `ScratchBlockDefinition`, `ScratchBlockRegistry`, `ScratchSurfaceAccess` | Reusable Scratch block contracts and registration. |
| `ScratchBlockSurface` | Client-only Scratch rendering/layout consumed by the schedule workspace. |
| `ShipDockScheduler`, `ShipLogisticsRun` | Shared dock/logistics state mechanisms; dock requests are ordered by committed physical ownership, arrival readiness, live distance and FIFO sequence. Named `DockSlot.resource(...)` values isolate independent resources at one dock, such as connector provisioning, connectorless arrival and landing zones. Addon code supplies gameplay integration and must only mark ownership committed for the requested destination. |

The addon retains `ShipControlModuleRuntime` as the server-side coordinator: world collision queries,
actuator lifecycle, initialization, graph commands, control-map persistence and pilot/schedule integration.
`ScmConfigurationProfile` retains gameplay group routing and save data. `ShippingScheduleGraph`,
`ShippingScheduleRuntime` and the CC bridge translate Create schedule content into addon behavior.
`ScmLiveSubLevelPreviewRenderer` is an addon adapter for filters and control-face selection; the actual
renderer and arrow geometry remain in the library. New reusable calculations belong here, not in those adapters.

## Explicit craft orientation

`new ScmOrientation(forward, up)` accepts two Minecraft `Direction` values on different axes.
There are 24 valid signed frames. Null, parallel and opposite-axis pairs are rejected.
`forwardVector()` and `upVector()` return signed unit vectors. `rightVector()` is `forward × up`.
The vectors are in the owning body's local block coordinates, never world compass headings or velocity-derived directions.

```java
ScmOrientation frame = new ScmOrientation(Direction.NORTH, Direction.UP);
Vec3 forward = frame.forwardVector(); // (0, 0, -1)
Vec3 right = frame.rightVector();     // (1, 0, 0)
```

`fromMount(forward, up)` preserves the sign of forward and supplies a perpendicular up if needed.
Missing forward defaults to NORTH; missing up defaults to UP. If they share an axis, up becomes
NORTH for vertical forward, or UP otherwise. This is a generic mounting helper, not an ACC dependency.

`toTag()` stores direction names as `Forward` and `Up`. `fromTag(tag)` returns an `Optional`:
missing, unknown or parallel directions produce an empty result. Callers choose the fallback policy.

In the addon, profile version 6 stores the optional `Orientation` compound. Its absence means
follow the ACC's `horizontal_facing` and mounting `facing` (UP when placed on the SCM).
Older profiles remain automatic. GUI selections store an explicit frame; resetting removes it.
The server rejects malformed GUI orientation requests and supplies authoritative defaults to the viewer.
Changing orientation invalidates directional navigation/control history. Orientation-only saves do not
recalibrate the craft. Propulsion routing and intentional reverse recovery remain separate from frame selection.

## Coordinate transforms and targeting

### Directional control outputs

`ScmControlMode.ControlOutput.torque` is physical world-space torque demand, not a signed wheel input.
With `right = forward × up`, positive physical torque about up turns left.
`ScmControlAxes.yawRightDemand(torque, up, reverseSteering)` converts this to a positive-right authored
control; set `reverseSteering` only for a vehicle steering while travelling backward.
`yawTorque(up, rightDemand)` performs the inverse conversion for a manual right/left yaw action.
Generic signed yaw remains a physical axis command; explicit Yaw Right/Left match their names.
`wheelSteeringDemand(...)` then converts that authored yaw to Offroad's known-working signed WheelMount
input contract; negative selects its left signal, positive selects its right signal, and reverse travel
still performs exactly one steering inversion.

Car mode returns physical yaw in forward and reverse travel. Hosts must perform reverse steering once
at the actuator boundary, not both in the mode and again in the wheel handler. The addon uses the same
conversion for group selection, profile faces, linker faces and generic wheel inputs.

`ScmControlAxes.longitudinalDrive(force, forward, travelDirection)` returns signed drive in [-1, 1].
For a nonzero travel direction, opposing force is braking and produces no powered gear-group demand;
it must not be replaced by the magnitude of an acceleration request. Zero travel direction supports
bidirectional force actuators, including reverse thrust for braking. Separate Acceleration, Deceleration
and Brake groups retain their scalar speed-plan controls. Both yaw and longitudinal conversion safely
neutralize invalid vectors.

`ScmControlMode.ControlOutput.driveDirection` selects forward or backward travel. `driveStrength` is a
direction-independent speed setpoint, not a directional force request; built-in modes keep it non-zero while
holding cruise speed. `ScmSpeedControl.plan(...)` should be used when a host needs distinct actuator channels:
Acceleration gains or maintains a non-zero permitted speed, Deceleration reduces to another non-zero speed,
and Brake is reserved for a zero-speed stop. Its outputs are mutually exclusive. `collisionLookahead(...)`,
`safeSpeed(...)` and `stoppingSpeed(...)` expose the same response-aware braking envelope for inexpensive
live probes. For a positive radius, `captureApproachSpeed(...)` keeps a caller-selected minimum approach
speed outside a target capture radius, then returns zero inside it, preventing low-speed drivetrains from
asymptotically stopping short. A zero radius retains normal exact-target braking. Collision and traffic caps
remain caller-owned and may still override that approach speed.

Car terminal approach uses the actual planar distance, not the length of a normalized direction
(which was always one for nonzero offsets). Far targets therefore no longer trigger a one-block braking speed.

`SableTransformApi.transformDirectionBetween(targetPose, sourcePose, direction)` rotates a vector
from source-local through world into target-local space. It preserves magnitude and sign and does not
apply translation. `transformPositionBetween(targetPose, sourcePose, position)` also applies translation.
Supply non-null poses/vectors; neither method mutates the supplied poses.

The SCM transforms the selected ACC-local frame into the allocation root, then into world space for
navigation. The preview rotates its markers from the same ACC body's local space into the viewer root.
Thus craft rotation and connected-body transforms do not redefine which end is forward.

`OrientedHull.targetPoint(points, right, up, forward, rightSelector, upSelector, forwardSelector, fallback)`
projects points onto a perpendicular craft frame. Selectors choose minimum (negative), middle (zero),
or maximum (positive). Positive forward selects the craft's front, even when that is world/local -Z.
Empty or invalid geometry/bases return the finite fallback (or zero for an invalid fallback).
Non-finite sample points are ignored. Center of mass remains a host-supplied physical value, not a hull midpoint.

## Point-to-point navigation and vehicle selection

The library navigation surface supplies reusable decisions and geometry only. The addon owns command
lifecycle and SCM/schedule integration. `SablePathfinder` owns the reusable Sable-aware world route
search; it has no entity dependency, transport or renderer ownership.

`SablePathfinder` accepts root-world `Location.world(position)` endpoints or body-local
`Location.subLevel(id, position)` endpoints. Local endpoints resolve through the body's current Sable
pose for every call to `plan`, so a caller can replan as an SCM-controlled body moves. The returned
`Waypoint` values are root-world positions: an entity, robot, ship or other consumer converts them into
its own control frame. `Safety(horizontalRadius, height, bottomOffset)` defines the complete clearance
envelope, preventing routes through narrow gaps rather than treating the routed object as a point.

`SableAssemblyBoundsApi.envelope(subLevels, reference)` reports horizontal, vertical and bottom extents
plus `spatialRadius()`: the greatest reference-to-hull-corner distance. Use `spatialRadius()` for a
rotation-safe flight envelope, since a craft that pitches or rolls can move a formerly horizontal hull
corner vertically. The three-argument `Envelope` constructor remains available for consumers with a
fixed-orientation envelope. `Envelope.targetOverlapTolerance(padding)` returns a center-to-target
distance which covers the complete hull radius plus non-negative padding; it is an arrival test helper,
not collision geometry.

`Movement` enables `GROUND`, `WATER` and/or `FLIGHT` grid moves. The caller's `Validator` remains
authoritative for support, fluid and application-specific rules; this keeps the API usable by non-mob
actors. Combine that validator with `sableCollisionValidator(CollisionOptions, GroundContactPolicy,
CollisionPrecision)` to test the complete envelope against root-world and transformed Sable geometry.
`CollisionPrecision.SWEPT` is the authoritative full-envelope option for accepting or revalidating route
legs; it cannot accept a gap that only a face-probe pattern misses. `PROBED` remains the default of the
existing overloads for bounded high-frequency host reaction checks. Supply the routed craft's body IDs in
`excludedSubLevelIds` so it is not treated as its own obstacle.

`SubLevelParticleOcclusion.findEnvelopeBlockingDistance(...)` returns the first broad-phase translated-AABB
overlap distance for caller-supplied moving and obstacle envelopes. An envelope can contain empty space and
is not authoritative collision geometry. High-frequency probed queries use it only to find candidate
non-excluded Sable bodies, then refine those candidates against their loaded block collision shapes with a
complete swept-bounds check. A supplied `ProbeCache` also merges the root container's current body snapshot
with Sable's spatial-index result once per cache lifetime, so a newly loaded or moving vehicle cannot be
temporarily absent from reactive collision checks. Root-world geometry retains the bounded leading-face
probe path.

`findSubLevelEnvelopeBlockingDistance(...)` exposes the live non-excluded Sable-body broad phase directly
for an immediate moving-body safety override. It is intentionally separate from route acceptance: hosts may
use the conservative envelope distance to evade a vehicle which entered the active corridor, while retained
routes continue to use collision-shape-refined validation and remain unchanged.

`SubLevelBlockEntityCollector.findContainingServerLevel(...)` resolves a SubLevel's containing server
dimension from the live Sable container or its persisted tracking-point index. It does not request a
SubLevel or chunk load, so persistent registries can normalize saved locations during world startup.

The built-in Sable validator uses only loaded root chunks and loaded Sable plot chunks. It never creates
chunk tickets: an absent, loading or body-replaced chunk yields `UNAVAILABLE`, while a collision yields
`BLOCKED`. `Result.reachedDestination()` is true only for `COMPLETE`; a non-complete result may contain
a safe partial route and must be replanned against the next live chunk/sublevel state. This makes
dynamically loaded SCM chunks available as soon as Sable reports them while never routing through missing
geometry. Existing `SableSubLevelResidency` leases remain the caller's lifecycle tool for retaining active
bodies.

`queue(request)` validates a policy-compliant clear direct segment once and completes it immediately;
otherwise it creates a `QueuedPlan` with stable resolved endpoints. Call `advance(expansionBudget)` on the
owning game thread to spread one full bounded A* search across ticks. `result()` always exposes the latest
safe partial route; `finished()` distinguishes that intermediate route from the retained terminal result,
and `progress()` reports bounded planner work. The request's `maximumExpansions` remains the total search
limit rather than a per-tick limit. Queue ownership, cancellation, persistence and real-time invalidations
remain with the caller. A live controller may follow that accepted partial prefix while planning continues;
the unvalidated connection from the prefix to the requested destination is diagnostic intent and is never
movement authority.

`WorkBudget(maximumWorkPerTick)` is a thread-safe caller-owned allowance for sharing a route-planning budget.
Call `claim(tick, requestedWork)` before advancing one queue, or `claim(owner, tick, requestedWork)` to
round-robin retained queues that were deferred in an earlier tick. Both return no more than the remaining
work for that supplied tick and reset on the next tick value. Call `release(owner)` when that queue is
discarded. The budget owns no queues or world state, so integrations retain their own planner lifecycle.

`advanceWaypointOverlap(waypoints, nextWaypointIndex, previousPosition, currentPosition, safety,
capturePadding)` advances sequential checkpoint state when the safety envelope swept between two samples
already overlaps a waypoint. The additive `WaypointOverlap` overload accepts a consumer-provided exact
hull-sweep test for non-box collision bounds. Both return `WaypointAdvance(nextWaypointIndex,
skippedWaypoints)` and clear only checkpoints physically crossed in order. This lets ships, vehicles and
other consumers rejoin a live route without circling points now behind their hull.

`advanceRouteOverlap(waypoints, nextWaypointIndex, routeOrigin, previousPosition, currentPosition,
safety, capturePadding)` performs the equivalent cursor update for route legs. It selects the endpoint of
the first remaining leg crossed by the swept safety envelope, then consumes any sequential checkpoint also
crossed by that envelope. The additive `RouteLegOverlap` overload lets a consumer supply its exact
multi-piece hull/segment test. A controller can therefore project forward along the intersected leg instead
of turning back toward an unreachable waypoint; route validation and steering remain caller-owned. Cursor
advance is limited to a contiguous overlap beginning on the active leg, so a later self-crossing leg cannot
be mistaken for route already travelled.

`routeProjection(waypoints, nextWaypointIndex, routeOrigin, currentPosition)` identifies the nearest
continuous point on a remaining retained leg. Equal-distance crossings prefer the earliest remaining leg,
so geometric ties cannot randomly skip route progress. Its additive `minimumFirstLegProgress` overload
excludes active-leg positions behind progress already committed by the caller. `RouteProjection` reports
the selected position, leg endpoint index, clamped leg progress and squared distance.
`routeLegProjection(...)` exposes the raw projection operation for one leg, while
the compatibility `routeCursor(...)` view retains only its endpoint index and distance. These APIs are
geometry-only and do not mutate or validate the route; consumers use the projected position to join a
cached route without first returning to an already-passed waypoint. Ground-mode waypoints use planar
projection and distance, so a vehicle's center-of-mass height cannot make it select a different visible
leg; the additive `routeLegProjection(..., RouteMode)` overload provides the same choice for one leg.
`routeLegTrackingTarget(...)` advances a caller-selected lookahead from that projection, allowing normal
cross-track correction and accepted partial-route following without manufacturing or retaining a separate
rejoin route. Its additive `minimumLegProgress` overload clamps the tracking cursor to progress already
committed by the caller, so an off-course excursion cannot select a target behind the vehicle.

`orientRoute(waypoints, routeOrigin, currentPosition, destination, endpointTolerance)` treats retained
geometry as a reusable safe path rather than a directed ownership instruction. When either endpoint
matches the caller-owned destination it returns an `OrientedRoute` toward that endpoint, reversing segment
order and movement modes when necessary. Closed routes choose the direction with the shorter projected
remaining travel. The caller still owns destination selection, live collision checks and route retention.

`routeGraphPath(routeLegs, currentPosition, destination, endpointTolerance)` selects the nearest physical
point on a connected safe-route graph, then returns the shortest bidirectional sequence of retained legs
from that point to the caller-owned destination. It preserves each leg's validated movement mode and
returns an `OrientedRoute` beginning at the projected join point. This prevents a consumer from choosing
an unrelated loop branch or generating a new destination route when the calculated graph already contains
the required path. Continuations use deterministic shortest-path traversal over retained graph endpoints,
so dense cyclic graphs cannot lose a valid destination by exhausting a recursive branch-search cap. The
five-argument overload separates the exact graph-connection tolerance from the
host-owned live-destination capture tolerance, preventing small live target movement from disconnecting an
otherwise valid calculated graph.

`ScheduleRouteLoop.select(layers, cyclic)` resolves exactly one caller-owned `Candidate<T>` from every
non-empty ordered stop layer. It minimizes the complete stop-to-stop chain and, for cyclic schedules, the
closing edge back to the first stop. Input order is the deterministic tie-breaker. The result is geometry
selection only: the host retains schedule execution, persistence, collision validation and destination
ownership.

`GroundPathPlanner.forwardRouteSteering(position, controlTarget, legStart, corner, nextWaypoint,
capabilities)` anticipates a retained bend using the vehicle's minimum bicycle-model turning radius. It
returns steering direction only: the caller retains the certified current leg as its control target and
continues validating the actual live travel corridor.

`GroundPathPlanner.forwardRouteControl(position, controlTarget, routeOrigin, routeWaypoints,
nextWaypointIndex, capabilities, requestedSpeed, maximumLateralAcceleration, brakingAcceleration,
responseSeconds, minimumCornerSpeed)` scans through collinear checkpoints to the first real bend. Its
`ForwardRouteControl` reports the bend, tangent distance, anticipatory steering direction, physical corner
speed and the upstream braking-envelope speed. This lets a steering vehicle settle before the tangent and
turn smoothly instead of discovering the corner at its final waypoint.

`GroundPathPlanner.planForwardRecovery(request)` uses the same host-validated bicycle model as reverse
recovery but permits only forward curves. It is intended for a live collision override which has selected
a clear course-correction target; the host validates every pose using its exact current hull and world state.

`RouteTrafficPriority` compares bounded retained-route intents through
`resolve(subject, traffic, settings)` and
returns deterministic right-of-way without modifying any route. `Participant` supplies a stable vehicle
ID, position, velocity, future polyline, movement mode, horizontal/vertical hull clearance and expected speed. The
coordinator distinguishes crossing, head-on and same-direction encounters, ignores vehicles beyond the
coordination horizon or with safely separated arrival times, makes only the later or closing vehicle yield,
and returns the distance available for caller-owned braking. Stable vehicle IDs break exact ties, while
physical collision probing and local pass-around steering remain host responsibilities. Hosts must aggregate
participants by their containing root world/dimension rather than comparing individual SubLevel `Level`
objects, since every moving vehicle can have a distinct level view.
`requiresRetreat(decision, liveTravelClearance, requiredClearance)` identifies a yielding crossing or
head-on vehicle whose live corridor has become unsafe; the host validates and executes any reverse manoeuvre.

`AutopilotDebugSnapshot` is a detached, renderer-independent diagnostic contract for live vehicle
brains. A host publishes a stable vehicle ID, display name, root-world nameplate anchor, primary behavior,
game tick and ordered `Section` / `Entry` values. Entries carry a semantic `Tone` without prescribing
transport or gameplay behavior, so integrations can expose real planner, route, traffic, collision and
control decisions without coupling the library to an addon runtime. `graphValue()` exposes that complete
snapshot as a portable nested `GraphValue` map, including anchor data and ordered section/entry maps. The client-only
`AutopilotDebugNameplateRenderer.render(...)` draws complete snapshots as full-bright camera-facing boxes;
the caller owns permission checks, synchronization, snapshot frequency and visibility.

`stitchRouteGraph(routeLegs, terminalLegIndex, endpointTolerance)` resolves independently retained
`RouteLeg` values into the connected route ending at the requested terminal leg. It first finds a closed
directed cycle and backtracks past stale or dead-end branches; when no cycle exists, it returns the longest
connected predecessor chain. `StitchedRoute` reports the flattened waypoints, graph-leg count and whether
the result is closed, allowing consumers to prefer an authoritative schedule graph over an unrelated
one-off cached approach. The library does not assign schedule identities or own cache persistence.

`routeTerminal(anchor, outward, standOffDistance, verticalOffset, captureRadius)` creates a reusable
`RouteTerminal` near a coarse destination anchor. Consumers can terminate a persistent route near a
dock, station or service point without embedding a dynamically assigned final connector in that route,
then hand control to their live point-to-point approach. `RouteTerminal.reached(position)` applies the
normalized capture radius; connector provisioning, reservation and final docking remain host-owned.

`findRouteRejoin(rootLevel, waypoints, nextWaypointIndex, currentPosition, safety, validator)` evaluates
the remaining retained route against live traversal policy. It selects the first onward-clear leg directly
reachable from the current position, or an earlier onward-clear leg that requires a local plan. The API does
not mutate the route: callers plan only to that selected waypoint, splice the resulting detour ahead of the
unchanged suffix, and retain responsibility for real-time invalidation and immediate collision response.

`findRouteLegRejoin(...)` and `scanRouteLegRejoin(...)` provide the projected equivalent for vehicle
controllers. They begin with a continuous projection on the current ordered leg, validate travel to that
projected point, and preserve that leg's onward segment before considering a later leg. The additive
`minimumFirstLegProgress` scan overload prevents a rejoin behind progress already committed on the active
leg, so a geometrically closer crossing or return leg cannot reverse destination progress. If a new obstacle splits
a long leg after its nearest projection, the scan samples and refines the first onward-clear interior point
past the obstruction as the local-detour target; the authored endpoint is only a bounded fallback when no
interior point can preserve the clear suffix. The scan form bounds leg checks and returns a continuation
index for later ticks.

`scanRouteRejoin(rootLevel, waypoints, nextWaypointIndex, currentPosition, safety, validator,
maximumWaypointChecks)` provides the same retained-suffix policy under a fixed validation budget.
`RouteRejoinScan` returns a safe rejoin when found, or the next suffix cursor when the pass is incomplete;
callers resume on a later tick rather than repeatedly scanning a long cached route. A found blocked direct
leg is a safe local-detour target, while a directly reachable leg can be spliced immediately. The API does
not retain caller route state or replace close-range collision response.

Completed routes use bounded farthest-safe shortcuts only when the full swept segment is revalidated clear. This
removes unnecessary grid-staircase corners (including safe diagonals) without spending one validation per grid cell,
while retaining every collision-required bend; partial in-progress results retain their original grid route for search
diagnostics.

`SablePathfinder.DebugRoute` is an immutable, detached root-world route snapshot for diagnostics.
It contains an integration-defined ID, origin, destination, waypoints and planner `Outcome`; the planner
does not retain, synchronize or render it. Client integrations may pass snapshots to the client-only
`SablePathfinderDebugRenderer.render(...)`, which draws outcome-coloured route segments, waypoint boxes,
and distinct origin/destination markers. `targetLegValidated` records whether the destination leg was independently
validated; validated final segments are solid, while an unvalidated connection from the accepted route suffix to
the requested destination is a yellow dashed intent guide. `BLOCKED`, `UNAVAILABLE` and `LIMIT_REACHED` snapshots
still distinguish accepted solid geometry from that non-authoritative destination guide. `DebugRouteStyle.LIVE` uses
the normal outcome colours; `DebugRouteStyle.CACHED` renders an accepted retained route in orange so integrations
can distinguish stored plans from current steering. Command permissions, snapshot publication and transport remain
the consuming mod's responsibility.

`QueuedPlan.debugRoute(id)` adds a bounded rolling `checkedSegments` sample while a search remains active. Each segment records
the actual `CLEAR`, `BLOCKED` or `UNAVAILABLE` result considered by the live search, while `waypoints` remains the
best accepted safe partial path. This is diagnostic-only: it does not change planning priority, route ownership,
collision policy or control. The client renderer draws checked clear segments green, blocked segments red and
unavailable segments amber. The sample is discarded at completion so a cached route renders only its accepted legs.

`RoutePreferences` optionally gives a route an initial forward direction, low distance weight, lateral
and reverse penalties, a weak A* heuristic and a required forward-commit distance. With a preferred
forward direction, the planner keeps one occupancy branch per grid cell and forward-commit state: it prefers continuing forward,
charges a turn or reversal, and only accepts a terminal segment which meets the requested heading
alignment. The initial commitment prevents an immediate reverse shortcut, while later heading changes
still allow a safe turn-around. `RoutePreferences.DIRECT` preserves the original shortest-clear-route
behavior, and the existing ten-argument `Request` constructor still uses it.

`ReactiveCollisionAvoidance.resolve(Request)` selects an immediate escape from host-probed candidate
directions when the current travel corridor is unsafe. Equal-clearance candidates prefer the least
disruptive route-aligned escape, while a materially clearer opposing direction remains eligible.
`steeringDirection` blends a forward ground escape into the direct bearing so a steering vehicle is not
sent toward a sharp lateral control point. The host is responsible for refreshing candidate clearances
from live world data on every autonomous movement tick and for retaining the planned route while a reactive
escape temporarily owns steering.

`LocalDetourPlanner.plan(Request)` performs one bounded local detour search using the caller's
`SegmentValidator`. `MovementModel` limits candidates for steering, planar or spatial craft. This is
an immediate obstacle-avoidance helper, not a retained world route: the addon continues to own the
real destination and rechecks each active segment.

`GroundPathPlanner.planReverseRecovery(PoseRequest)` supplies a bounded reverse-only bicycle-model
manoeuvre for a blocked steering vehicle. `planForwardRouteRejoin(RouteRejoinRequest)` uses the live
position and facing plus a projected retained-route position and travel tangent to produce a forward-only
curve which merges at the physical turning radius instead of cutting diagonally across and oscillating.
`planRecoveryManeuver(PoseRequest)` performs the same bounded pose search with forward and reverse
available and records the selected gear on every waypoint and curve. `planRouteRejoin(RouteRejoinRequest)`
first tries the shortest forward merge, then permits a committed multi-point gear-changing manoeuvre when
forward-only geometry is blocked or cannot acquire the requested route tangent. Exhausting the bounded
search after accepting a collision-tested move returns the best safe partial manoeuvre, including directly
from the origin expansion. Hosts can execute that prefix and immediately continue planning from the
resulting pose instead of parking between searches.
Wheelbase and maximum steering angle determine the minimum turning radius; `PoseValidator` remains
authoritative for the live hull/world collision result.
`shouldRefreshReverseRoute` tells a host when sufficient reverse travel and forward clearance permit
returning to the direct destination. Reverse is recovery behavior, not an alternate shortest route.

`WaypointProgressTracker.observe` provides point capture, pass-through and stall observations for the
currently active local manoeuvre. `reset` starts a new observation and `clear` discards it. It does not
own a route or choose a destination.

`ShipDockScheduler.resolveHoldingPosition(...)` applies a scheduler `HoldingPlacement` and moves the
result outside caller-supplied retained-route corridors. This lets waiting traffic park clear of ingress
geometry without changing FIFO order, destination leases or the caller-owned final docking route.

`ScmVehicleClassifier.suggest(wheels, liftingSurfaces)` suggests plane when lifting surfaces are
present, car for wheels without wings, otherwise airship. The addon supplies a cached loaded-craft
sample (up to 2,048 blocks); this is a heuristic, not a physical proof. The SCM graph exposes Auto
and registered vehicle modes. Addon profile version 6 stores `VehicleType`; missing values use Auto.
The main ACC Save/Save-and-Apply/close-save transaction includes dirty SCM configuration, with graph
revision validation before changing SCM state. Deprecated Initialize nodes no longer select the mode.

## Preview arrows

Pass `SubLevelPreviewRenderer.Marker(bodyId, localPosition, localDirection, color)` to `render`.
Markers draw three-block directional arrows using the body's current preview transform. Keep positions
and directions in that body's local coordinates; do not pre-rotate them into world space.
Call `invalidate()` after changing caller-owned overlays. Markers currently require live body poses;
snapshot fallback blocks alone do not provide enough orientation data to draw a reliable body-local arrow.
Rendering and input APIs are client-only. Close the renderer when its screen closes.
Call `setFullBright(true)` for an inspection view that uses full block light instead of the source world's
day/night lighting; it does not alter world rendering or gameplay light.
Live previews render the static block body normally, then render Create's exact Flywheel-owned kinetic
partials into the off-screen buffer. Model selection comes from the registered Create renderer where
possible, with renderer-equivalent handling for gearboxes, split shafts, cogwheels, encased cogs, fans,
crafters and mixers whose compatibility renderer exits while Flywheel is active. This keeps shafts and cogs
animated without rotating a duplicate copy of the block housing. The registered renderer still contributes
non-instanced details, and `BlockEntityPreviewDecorator` remains additive optional-mod detail for parts such
as addon tyres.

The SCM GUI shows blue forward and green up arrows at the ACC, matching the underlines on its
Forward/Up selection buttons. Changes preview immediately and become authoritative on the main ACC Save.

## Maintenance and verification

Update this page and `wiki-changes.md` in the same change as a public API addition or behavioral change.
Update the addon's `CCT.md` whenever SCM/schedule or CC contracts change. Keep examples and migration notes
consistent with source; generated CC documentation does not replace the human-facing wiki.

`gradlew clean build` runs library-boundary and mixin checks plus `verifyScmApis`.
The SCM regression suite covers all 24 frames, NBT rejection/round trips, signed hull targets,
rotated body transforms and translation/magnitude preservation. The addon runs `verifyScmProfiles`
and its existing `verifyComputerCraftDocs` check. Physics and GUI behavior still require in-game testing.

`verifyLibraryWiki` rejects undocumented additions to the SCM/navigation/Scratch/shipping API inventory.
The addon's `verifyCctWiki` rejects missing schedule-method coverage in `CCT.md`. These checks detect
missing entries, not prose accuracy; behavior changes still require a documentation review.

## Linked kinetic connections

`LinkedKineticBlockEntity` (`com.rieno.gadgetsandgizmos.lib.kinetics`) extends Create's `KineticBlockEntity` for a reciprocal point-to-point connection. Both endpoints share Create's normal kinetic network: the connection conveys signed RPM at a 1:1 ratio and contributes zero generated capacity and zero stress of its own. Real generators and consumers are counted once, including parallel links, stacked pairs and loops. Overload stops the shared network through Create's normal stress handling; local shafts and cogwheel ratios still apply.

Subclass it and implement `resolveKineticLink()` to return the saved partner while it is loaded, or `null` otherwise. The partner must resolve back to this endpoint, occupy a loaded block position, and share the same `Level`. Positions are the block entities' storage/plot `BlockPos` values, including Sable SubLevel plot positions, rather than transformed world/render coordinates. This API does not connect different dimensions or structurally attach physics bodies.

After changing the saved partner, call `refreshKineticLink()` on the owning server thread. The base tick also checks endpoint availability. It detaches the old connection before reconnecting, schedules ordinary Create propagation, and drops unavailable partners without retaining generated RPM. Consumers own pairing persistence, SubLevel identity/lookup, loading dependencies, interaction rules and spline rendering.

```java
@Override
protected LinkedKineticBlockEntity resolveKineticLink(){
    return findLoadedPartner();
}

public void changePartner(BlockPos pos){
    savedPartner = pos.immutable();
    refreshKineticLink();
}
```

Migration: consumers replacing a generating proxy should call `rebuildKineticNetworkOnLoad()` when reading their old server-side NBT format. During initialization this discards that network's old aggregate cache, clears proxy contributions, and recalculates loaded real members. Unloaded source/load totals are deliberately discarded because an inflated or non-finite aggregate cannot identify them; they return as their chunks load. Pair positions and IDs remain the consumer's responsibility.

The addon Physics Gantry Belt Wheel uses this API in stable and experimental. Its `LinkedPos` and `LinkedSubLevelId` saves remain valid; legacy `GeneratedLinkSpeed` marks the one-time rebuild and is no longer written. Rendering chooses one endpoint independently of power direction. Replacing a pairing clears the previous reciprocal pair.

Verification: `gradlew clean build` runs the linked-kinetics JUnit regressions as part of `test`. These exercise Create's network accounting and rotation propagation with a mocked level. They do not replace in-game checks of SubLevel movement, chunk unload/reload, spline visuals, source removal, parallel/serial stacks and overload recovery.
