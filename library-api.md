# Gadgets & Gizmos Library API

`gadgetsngizmos` is the reusable API shipped with Gadgets & Gizmos. Use it when your addon needs to join the controller, ACC graph, Sable, SCM, display, shipping or tablet systems properly. Do not compile against the `createthrusters` addon jar or import its implementation classes. The addon keeps that namespace so existing blocks, items and worlds remain compatible, but it is not the library identity.

```toml
[[dependencies.your_mod_id]]
modId="gadgetsngizmos"
type="required"
versionRange="[1.2.0,1.3.0)"
ordering="AFTER"
side="BOTH"
```

The library targets Minecraft 1.21.1, NeoForge 21.1.225+, Create 6.0.10+, Sable 2.0.3+ and Simulated 1.2.1+. Aeroworks and AeroClaims support are optional.

When updating from a beta that shipped `createthrusterslib`, replace that standalone jar with `gadgetsngizmos`; do not keep both jars installed. The addon still registers blocks, items, entities, recipes, translations and saved content under `createthrusters`, so existing worlds keep the same content IDs. Deprecated Java aliases and legacy Sable residency-ticket cleanup cover the old beta API and saved ticket names during the transition.

## The rules for using the library

- Only import `com.rieno.gadgetsandgizmos.lib.*`. Library mixins, bootstrap code, client implementation code and all `createthrusters` addon classes are internal.
- A missing, removed or unloaded Sable sub-level is normal. Use the API’s unavailable results and retry from a real lifecycle event; do not force-load a ship just to complete a control action.
- Use stable IDs for registrations and saved data. Resource locations, controller target references, graph node IDs and tablet app IDs are all intended to survive world reloads.
- Register normal integrations at startup. If a runtime integration can disappear, unregister it and release any ticket, lease, ownership claim or cached native handle you created.
- Keep your own mechanics and persistence in your addon. The library supplies contracts and safe shared behaviour, not storage for private addon state.

## Foundation, configuration and compatibility

`GadgetsNGizmosLibrary.MOD_ID` is the stable library mod ID: `gadgetsngizmos`. Use that constant when an integration needs the library's own namespace. `GadgetsNGizmosLibraryNeoForge`, `GadgetsNGizmosLibraryClientNeoForge`, `GadgetsNGizmosLibraryClientBootstrap` and `PhysicsStaffPowerEvents` are lifecycle wiring; do not instantiate or invoke them from an addon. Deprecated `CreateThrustersLibrary`, `CreateThrustersLibraryNeoForge`, `CreateThrustersLibraryClientNeoForge` and `CreateThrustersLibraryClientBootstrap` aliases remain for source and binary compatibility with the beta API.

`com.rieno.gadgetsandgizmos.lib.config.GadgetsNGizmosLibraryConfigs` owns the server settings for global held-angle support, precise-angle propagation, virtual-kinetic propagation and kinetic-guard logging. Read those accessors if your integration needs to respect the server setting. Never call `register` or write this config from another addon; it is registered by the library mod entry point. `CTLibraryConfigs` is a deprecated compatibility alias.

`com.rieno.gadgetsandgizmos.lib.compat` is the deliberate Physics Staff compatibility surface. `PhysicsStaffPowerHooks` identifies the powered staff, validates a Backtank-powered action, consumes pressure, exposes tooltip values and provides the mass-based drag/lock drain calculations. `PhysicsStaffPowerTracker` stores active operation state. `PhysicsStaffInteractionGuard` checks protected SCM initialization targets and optional AeroClaims permission. Use these only when you are intentionally joining the Physics Staff mechanic; they are not a general-purpose claims or energy API.

`com.rieno.gadgetsandgizmos.lib.client.render.AreaHighlightRenderTypes` is the supported translucent area-highlight render type. It is client-only. Keep it behind a client boundary and let the library's automatic client bootstrap register the shader; do not call `GadgetsNGizmosLibraryClientBootstrap` yourself.

Package: `com.rieno.gadgetsandgizmos.lib.probe`

Implement `BlockEntityDataProvider` when a block entity exposes named readable or writable values. Port maps publish the stable field ID and value type, `readGraphValue` returns the typed value and `writeGraphValue` performs a validated mutation. Implement `ConnectedBlockEntityProvider` when another integration needs to inspect the live block entities joined to a mechanism without importing its implementation class.

`BlockEntityLookupApi` finds block entities through root levels and loaded Sable SubLevels without importing addon compatibility code. Use `findIncludingSubLevels` for world-facing interaction, `find` for an optional saved SubLevel ID, `findExact` when scope fallback is not allowed, and `findLoadedExact` when the lookup must never load a target. `ResolvedBlockPosition` keeps the internal block position and owning SubLevel ID together.

## Controls, input and orientation

Package: `com.rieno.gadgetsandgizmos.lib.control`

This is the public controller surface. `AnalogueChannel`, `AnalogueAxis`, `AnalogueChannelMode`, `AnalogueControlChannel`, `ControllerMechanic` and `ControllerMechanicBinding` cover signed/unsigned input, press/release/tap/step behaviour, rise/fall rates, smoothing, deadzones, debounce, repeat, redstone conversion and NBT persistence.

Implement `IDirectControlReceiver` when a block entity can accept a direct named controller value. `AnalogueSignalPacket` is the serializable update value and `AnalogueTransmissionTarget` is the transmission-side receiver contract. `FrequencyBinding` stores a Create Redstone Link pair. `ControllerDirectTargetReference` stores a stable world or sub-level target without retaining a live block entity.

```java
public final class MyNozzleBlockEntity extends BlockEntity
        implements IDirectControlReceiver {
    @Override
    public void applyDirectControllerSignal(String channelId, float value) {
        if ("throttle".equals(channelId)) {
            setThrottle(Mth.clamp(value, 0.0F, 1.0F));
            setChanged();
        }
    }
}
```

`DirectionalAnalogMath`, `DirectionalAnalogSnapshot` and `DirectionalAnalogSource` convert local square/circular input into forward, back, left and right values. Use them rather than duplicating deadzone and diagonal handling.

`OrientationPayload`, `OrientationTarget`, `LinkedOrientationSource` and `OrientationMath` are the orientation producer/consumer contracts. `CustomKeyEntry` is the saved user-defined controller-key model. `com.rieno.gadgetsandgizmos.lib.control.hardware` adds `HardwareControllerState` and `HardwareControllerBindings` for the standard `hardware:*` IDs and normalisation used by the addon.

`com.rieno.gadgetsandgizmos.lib.gimbal.CardinalTiltController` resolves a facing direction plus cardinal pulls into a clean tilt direction. `com.rieno.gadgetsandgizmos.lib.control.math` contains `PidControllerMath`, `LqrControllerMath`, `AdrcControllerMath`, `AdrcControllerNthOrderMath`, `Vector3` and `Quaternion`. `RotationMath` converts normalised quaternions, Z-X-Z Euler and X-Y-Z Tait-Bryan angles; all angles are radians.

## Discovery, sub-level access and menus

Package: `com.rieno.gadgetsandgizmos.lib.discovery`

`ControllerDiscoveryService.scanBlockEntities` classifies targets into stable `ControllerDiscoveryNode` values. Use `classify` or `classifyKind` when a custom UI needs the same classification. `ControllerDiscoveryKind` supplies stable kind IDs and translation keys. Implement `INamedBlockEntity` if the target needs a useful user-facing name.

`SubLevelBlockEntityCollector` is the supported way to resolve Sable targets, read a loaded block entity, enumerate loaded bodies and collect loaded block entities. It owns the short-lived lazy-load ticket behaviour and safely returns when a plot was removed or unloaded during lookup.

`SableLevelApi` is the typed root-level and ownership surface. Use `serverLevel` for code that must work from either a root level or a Sable level, `containing` and `containingId` for blocks, entities and precise positions, `tracking` for the body carrying an entity, and `subLevel` or `subLevels` for already-live bodies. These calls assume the required Sable API is present and never silently disable themselves through reflection.

`SubLevelConnectionApi` resolves and combines actor dependencies without duplicate IDs. `connectedTo` collects the SubLevels published by connected block entities and is useful when implementing `BlockEntitySubLevelActor.sable$getConnectionDependencies` on a multi-block mechanism.

`SubLevelAssemblyApi` assembles an explicit block collection or one single block and disassembles a live body through the required Sable and Simulated APIs. It derives and validates bounds for callers, so feature code does not need reflective assembly helpers.

`SableSubLevelResidency.lease` creates an owner-scoped persistent Sable 2.0.3 ticket. Call `close` when the owner is finished. Call `detach` for normal world shutdown or a block-entity chunk transition where the saved ticket should survive. `bootstrap` registers the ticket type before Sable restores saved leases.

Package: `com.rieno.gadgetsandgizmos.lib.menuconfig`

`MenuConfigTarget` and `MenuOpenHeader` carry a block position plus optional sub-level UUID through menu data. Implement `MenuBackedBlockEntityTarget<B>` on a menu, then use `MenuBackedBlockEntityResolver.resolve` in the server payload handler. `ISimulatedMenuOpen` marks a menu that reads the extended simulated target. This is the difference between a menu working on a parked build and only working in the root world.

## Kinetics, virtual kinetics and alternators

Package: `com.rieno.gadgetsandgizmos.lib.kinetics`

`HeldKineticAngleAccess`, `PreciseKineticOutputAccess`, `DirectionalPreciseKineticOutputAccess` and `PreciseKineticOutputBoundary` are the public precise-angle hooks. `KineticAngleHelper`, `KineticGraphHelper`, `HeldAngleKineticGraph` and `PreciseKineticOutputGraph` perform graph-safe propagation. `SingleFaceRotationConfiguration` supplies a one-face `IRotate` implementation.

`ServoMotionController` and its nested `ServoMotionController.ServoMotionConfig` are the reusable bounded servo planner. Use `applySyncedState` to restore an angle and speed received through client sync or persisted state without scheduling fresh motion. `SingleFaceRotationConfiguration` is the supplied one-face `IRotate` implementation. `BearingAngleDriver.driveFirstDownstreamBearing` drives the first compatible Create or Simulated bearing and synchronizes it through `BearingAngleDriver.BlockEntitySynchronizer`.

`BearingHead` supplies stable `PRIMARY` and `SECONDARY` serialized IDs and colours. Implement `BearingHeadAccess` when a multi-head bearing exposes its current, target and interpolated angles, range controls, mounted block/sub-level targets and assembly mutation. This keeps controllers, peripherals and renderers independent from one bearing implementation.

```java
AlternatorTuning tuning = new AlternatorTuning() {
    public double minRpm() { return 32.0D; }
    public double ratedRpm() { return 256.0D; }
    public int maxFePerTick() { return 4096; }
    public double maxStressImpact() { return 16.0D; }
};

int fePerTick = AlternatorKinetics.generatedFePerTick(speed, tuning);
float stress = AlternatorKinetics.stressAtSpeed(speed, tuning);
```

`GadgetsNGizmosKineticGuard` contains the configuration-aware checks used by the library kinetic mixins. Register a keyed predicate with `registerGuardException` only when a compatible block needs to bypass a package guard, and call `unregisterGuardException` when the integration unloads. A predicate that throws is isolated and treated as no match. `CTKineticGuard` is a deprecated compatibility alias.

Package: `com.rieno.gadgetsandgizmos.lib.virtualkinetics`

Implement `VirtualKineticProvider` on the real owner, `VirtualKineticBlockEntity` on each virtual member and `VirtualKineticHostBlock` on the host state. `VirtualKineticPos` identifies a slot. The library mixins handle Create graph lookup, persistence, source resolution and propagation once those contracts are implemented.

Package: `com.rieno.gadgetsandgizmos.lib.power.alternator`

Implement `AlternatorTuning` with minimum/rated RPM, maximum FE/t and maximum stress. `AlternatorKinetics` gives the matching effective RPM, generated FE/t and stress calculations, so an alternator implementation uses the same maths everywhere.

## Sable telemetry, topology, dynamics and joints

Package: `com.rieno.gadgetsandgizmos.lib.physics`

`SableSubLevelTelemetryApi.sample` reads one already-loaded body without forcing a load. Its `Snapshot` exposes finite position, linear/angular velocity, speed and mass, plus separate loaded and physics-available flags.

`SableAssemblyTopologyApi.discover` creates a deterministic connected view around a loaded root body. Actor dependencies are treated as undirected so a connection is still found when only one endpoint publishes it. Pass an `ActorFilter` and `ActorClassifier` when only selected actors should count. Implement `SableAssemblyConnectionProvider` on a `BlockEntitySubLevelActor` when your actor publishes an explicit link. Each `SableAssemblyConnection` is `STRUCTURAL` or `CARRIAGE_COUPLER`; a coupler classification wins when both describe the same undirected edge.

`Topology` exposes ordered bodies and edges, graph/coupler depth, structural carriage partitions and a stable fingerprint. Removing coupler edges creates the partitions; the partition containing the requested root is primary.

```java
private final SableAssemblyTopologyCache topologyCache =
        new SableAssemblyTopologyCache();

void tickControl(ServerSubLevel root) {
    SableAssemblyTopologyApi.Topology topology = topologyCache.get(root);
    SableAssemblyDynamicsApi.Snapshot dynamics =
            SableAssemblyDynamicsApi.sample(topology);
    if (!topology.available() || !dynamics.loaded() || !dynamics.physicsAvailable()) return;
    // Reuse this topology for the rest of the active control tick.
}

void onMyActorConnectionChanged(ServerLevel level) {
    SableAssemblyTopologyInvalidation.invalidate(level);
}
```

`SableAssemblyTopologyCache` keeps a filtered topology until the level topology revision changes, then performs a root-staggered 200–239 tick safety refresh in case a third-party event was missed. It is the normal choice for continuous control. Do not call `discover` every tick when a cache can be shared.

`SableAssemblyDynamicsApi.sample(topology)` samples exactly the topology you already have. Its `Snapshot` exposes root-local aggregate mass, centre of mass, inertia, inverse inertia and ordered per-body `BodyDynamics`. `aggregate(snapshot, ids)` and `snapshot.aggregate(ids)` recompute exactly one selected body/carriage subset in the same coordinate frame. Missing or mass-unavailable selected bodies return a loaded zero aggregate rather than a misleading partial value.

`SableYawJointApi.create` makes a rotary joint that locks translation, pitch and roll while allowing yaw around the supplied local axes. `Joint` safely exposes validity, contacts, servo setup, true zero-force disable, wake, removal and close. Contacts begin disabled. `progressiveResponse` returns a smooth dead-zone spring/damping/force response: exactly zero through the free angle, rising smoothly to the maximum and saturated beyond it. It is a soft force response, not a native hard angular limit.

`SableConstraintApi` centralizes the remaining current/legacy Sable constraint package bridge. Use `fixedConfiguration`, `freeConfiguration` or `genericConfiguration` to create compatible configurations, then `addConstraint`, `setFrame`, `wakeUp` and `remove` to manage the handle. Use this bridge only where a supported Sable range genuinely changes class or method shape; stable Sable calls should use their typed API directly.

`SablePointImpulseApi.apply` and `applyDirectional` validate finite inputs, submit a safe point impulse and wake the body only after a successful write. The methods clear a non-finite queued accumulator rather than letting it reach Sable.

`SableMagneticCaptureApi.pullTogether` applies a bounded, equal-and-opposite point pull to two loaded bodies. Give it local anchors, a capture radius, a maximum closing acceleration and the physics time step; it ignores invalid, coincident and out-of-range bodies.

`SubLevelParticleOcclusion` contains the shared SubLevel collision, clearance and particle-occlusion queries. It works against loaded Sable plots and returns explicit empty results when a body cannot be queried; addons should use it instead of copying plot/chunk traversal into their feature code.

`SableTransformApi` converts positions and directions between root and SubLevel space, projects through one or every nested SubLevel, measures transformed distance and finds loaded bodies intersecting a world box. Use `projectOutOne` for one immediate body boundary and `projectOut` for the complete nested chain. Its methods use the required Sable API directly, so an unavailable result means the target is absent rather than a reflective lookup failed.

`SableAssemblyBoundsApi.envelope(...)` combines loaded SubLevel world bounds around a world-space reference point. Its immutable `Envelope` exposes a conservative horizontal radius, total height and lower-hull offset for berth fit, queue spacing and similar placement policy. Missing bodies return `Envelope.DEFAULT`.

`com.rieno.gadgetsandgizmos.lib.compat` contains the shared Physics Staff boundary: `PhysicsStaffPowerHooks`, `PhysicsStaffPowerTracker` and `PhysicsStaffInteractionGuard` cover backtank use, active operation cleanup and protected assembly checks.

## Advanced Contraption Controller graphs

Package: `com.rieno.gadgetsandgizmos.lib.graph`

Register a `GraphNodeDefinition` through `GraphApi.nodes()` and the matching `GraphNodeExecutor` through `GraphApi.runtimes()`. The definition tells the editor the stable type, category, typed inputs/outputs and whether state is persistent. The executor receives immutable `GraphValue` inputs plus `GraphExecutionContext` for namespaced state, the current tick and typed host services. Define a `GraphServiceKey<T>` when a reusable node and its host share an optional service contract; a missing service returns an empty optional. `GraphHostServices.BLOCK_ENTITY` exposes the common block-entity host without leaking the addon controller type. The raw `services()` map remains as an empty deprecated compatibility method.

```java
String id = "your_mod:scale";
GraphApi.nodes().register(new GraphNodeDefinition(
        id, "math",
        Map.of("value", "number", "factor", "number"),
        Map.of("value", "number"), false));

GraphApi.runtimes().register(id, (context, inputs) -> Map.of(
        "value", GraphValue.number(inputs.get("value").asNumber()
                * inputs.get("factor").asNumber())));
```

`GraphValue` defensively copies nested collection payloads and rejects recursive collections and mutable arrays. `GraphNodeRegistry` and `GraphRuntimeRegistry` reject conflicting registrations and return immutable snapshots. `GraphModel.Node`, `GraphModel.Edge` and `GraphCompiler.compile` are available for an addon-owned graph document. `CompiledGraph` gives indexed nodes, incoming data edges, outgoing execution edges and immutable topology. `GraphEventScheduler` has bounded immediate/delayed queues, deterministic tick release, cancellation, snapshots and cleanup. `GraphWireGeometry` and `GraphViewport` are client-free wire and viewport helpers.

Built-in graph behaviour, the ACC document format and the editor are addon implementation. The contracts above are the extension point.

## Display frames and SCM integration

Package: `com.rieno.gadgetsandgizmos.lib.display`

`DisplayFrameEnvelope.create` copies a payload, keeps presentation metadata separate and records pixel dimensions. Use `payload`, `presentation` and `requestedMode` to read it back. `hasRenderablePayload` checks text, terminal and widget frame shapes before a provider registers the frame.

```java
CompoundTag frame = DisplayFrameEnvelope.create(
        payload, presentation, 256, 128);
if (DisplayFrameEnvelope.hasRenderablePayload(DisplayFrameEnvelope.payload(frame))) {
    forwardFrame(frame);
}
```

The public key constants let a compatible relay preserve the whole envelope. `ShipInformationDisplayModes` is the canonical mode registry; use `ids`, `normalize`, `label` and `isStaticText` rather than duplicating mode strings. `DisplaySurfaceProjection.normalizedPoint` converts a local tile hit into normalized joined-display coordinates, including the texture-pixel border adjustment.

`AccDisplaySourceRegistry` registers one block type as an external ACC source. Supply an `AccDisplaySource` that returns the normal renderable payload for the requested pixel size. The source can also handle normalized click and input events when its frame is interactive. Registered sources appear beside Universal Display Adapters in the ACC external-source picker and also work when placed directly beside an ACC Display.

```java
AccDisplaySourceRegistry.register(
        ResourceLocation.fromNamespaceAndPath("your_mod", "weather_station"),
        YOUR_WEATHER_STATION.get(), (source, width, height) -> {
            CompoundTag frame = new CompoundTag();
            frame.putString("Format", "text");
            frame.putString("Source", "Weather Station");
            // Add Lines, Terminal or Widgets as appropriate for the source.
            return frame;
        });
```

Package: `com.rieno.gadgetsandgizmos.lib.scm`

`ScmFlightBehavior` holds the saved `direct_vector` and `prefer_ship_direction` IDs. `ScmTarget` is a stable selected ship-local block/face target.

`ScmControlMode` is the vehicle-navigation boundary used after initialization. Its `ControlInput` supplies world-space pose, velocity, route direction, accumulated position error, clearances and navigation limits; return world-space force and torque in `ControlOutput`. Register a mode with `ScmControlModeRegistry.register`. Registered modes appear in the Initialize Control Module selector and persist on the controller. `ScmBuiltinControlModes` exposes the built-in `AIRSHIP_ID`, `PLANE_ID` and `CAR_ID` values; custom modes should use their own namespace so later vehicle types can be added without changing the SCM runtime.

```java
ScmControlModeRegistry.register(new ScmControlMode() {
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath("your_mod", "submarine");
    }

    public String displayName() {
        return "Submarine";
    }

    public ControlOutput navigate(ControlInput input) {
        return new ControlOutput(force, torque, false, 0.0D, 1.0D);
    }
});
```

Implement `ScmControlProbe` for a reversible control the SCM can calibrate. A probe declares adapter ID, display name, optional shared `controlGroupId`, range, live `Reading`, target-sublevel-local effect direction/position, neutral control, apply operation and restore operation. `neutralControl()` defaults to zero and must return the value that disengages the physical control without changing its ownership or attachment. Override `isAvailable` when the probe depends on a live attachment or another state that can disappear while the map is running. Car initialization first maps propulsion, kinetic controls, bearings and aerodynamic surfaces. It then follows each connected kinetic chain from its source through the active controls and alternate branches, retains a measured driveline response from that pass, and maps each wheel control with the driveline held active. Every retained configuration uses nine samples interpolated into the control map. This lets chained controls such as a directional gearshift and analogue transmission be discovered without either block knowing about the other, without expanding every wheel or control choice into a Cartesian test matrix. Register factories through `ScmControlProbeRegistry.register`.

```java
ScmControlProbeRegistry.register(
        ResourceLocation.fromNamespaceAndPath("your_mod", "steerable_nozzle"),
        100,
        (blockEntity, context) -> blockEntity instanceof MyNozzleBlockEntity nozzle
                ? List.of(new MyNozzleProbe(nozzle, context.target()))
                : List.of());
```

The factory receives the selected target, suggested direction and full linked target list, so cooperating controls can be calibrated together. The probe must restore the exact original state after sampling.

`ScmMapCompositionApi` owns deterministic primary-first fragment composition across connected sub-levels. Fragments carry a stable ID, owner and coverage set; duplicate owner/fragment IDs are reported through rejected IDs instead of being silently guessed. `ScmControlAuthorityApi.claim` elects one controller per server/assembly key across the tick boundary. Claim while active, release when finished and respect `ownerChanged` before writing outputs. SCM linker targets are intentionally separate from ordinary controller discovery.

## Shipping dock scheduling

Package: `com.rieno.gadgetsandgizmos.lib.shipping`

`ShipLogisticsRun` is the persistent model for named `ITEM`, `FLUID`, `ENERGY` and `FUEL` resource runs. Runs have stable UUIDs, immutable endpoints and NBT support. Fuel runs can expose fluid, FE and burnable item capability while normal item runs stay independent. Endpoints are sub-level-aware and can intentionally be shared by several runs.

`ShipDockScheduler` reserves interchangeable resources without coupling a caller to the addon Ship Dock block. `DockSlot.resource(...)` identifies a named connector, berth or other resource while the existing constructor remains source compatible. Use `RequestKey` channels when one vessel needs independent reservations, such as a primary dock and a queue berth. The UUID overload uses the primary `dock` channel.

Call `request` with the request key, ordered candidate slots, physical occupancy, an optional already-owned slot, `RequestPriority`, `VesselEnvelope` and game time. It returns a `Lease` with the resource key, queue position and a size-aware `HoldingPlacement`. `VesselEnvelope` supplies horizontal radius, height and lower-hull offset; `fits(...)` validates enclosed volumes and `fitsFootprint(...)` validates landing surfaces. `RequestPriority` contains distance, ETA and committed state; arrival-ready ships can claim an idle slot while early far-away requests cannot freeze a resource.

```java
ShipDockScheduler scheduler = ShipDockScheduler.get(server);
ShipDockScheduler.Lease lease = scheduler.request(
        new ShipDockScheduler.RequestKey(shipId, "park"),
        candidates, occupiedSlots, currentSlot,
        new ShipDockScheduler.RequestPriority(distance, etaTicks, committed),
        new ShipDockScheduler.VesselEnvelope(radius, height, bottomOffset),
        level.getGameTime());

scheduler.heartbeat(
        new ShipDockScheduler.RequestKey(shipId, "park"),
        priority, level.getGameTime());
scheduler.release(new ShipDockScheduler.RequestKey(shipId, "park"));
```

`release(RequestKey)` removes one channel. `release(UUID)` removes every channel owned by that vessel and is intended for full cancellation or teardown. Call `removeDock` when a logical dock disappears. Integrations must call `shutdown` at server stopping and `finishShutdown` after stop so late requests are rejected and server references are released.

## Tablet apps and client rendering

Package: `com.rieno.gadgetsandgizmos.lib.tablet`

`TabletAppDefinition` describes a stable app ID, title, description, accent, icon, immutable tabs and declared shared keys. `TabletTabDefinition` describes a tab plus action IDs and optional keyboard actions. `TabletAction` carries one selected app/tab/action plus string arguments. `TabletActionContext` supplies the authenticated player, tablet stack and optional sub-level-aware target, including placed-tablet source data.

Register a definition and matching server handler on both logical sides through `TabletAppRegistry.register`. Use `registerIfAbsent`, `registerOrReplace` and `unregister` when an integration is dynamic. `snapshot` provides a revisioned immutable list for long-lived screens. Apps without a client renderer receive the standard tab and action layout. A custom client can register `TabletAppClientRenderer` through `com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRegistry`; its context supplies the app snapshot, safe canvas and action sender.

```java
TabletAppRegistry.register(definition, (context, action) -> {
    if (!"refresh".equals(action.actionId())) {
        return TabletActionHandler.Result.failure(Component.literal("Unknown action"));
    }
    // Validate the player/context, then mutate only this app's records.
    return TabletActionHandler.Result.success(Component.literal("Updated"));
});
```

`TabletInteractionMode` is the persisted `STANDARD`, `READER` and `PUSH` interaction contract. `TabletStorage` and `TabletStorageApi.storage()` are the server-side persistence boundary. Storage is keyed by logical tablet UUID, so physical tablets sharing the UUID intentionally share installed apps, app data, bindings, routes and SCM workspace data. Declare cross-app records with `TabletAppDefinition.sharedDataKeys`; `shared` and `updateShared` reject undeclared keys and preserve ownership under the source app.

`TabletAppClientContext` supplies a custom renderer with its app snapshot, safe canvas and action sender. `TabletNotification` is the immutable persisted notification value. `TabletNotifications` gives every app a small persisted notification inbox. Use `post` with a stable notification ID to create or replace an entry, then use `dismiss` or `clear` when it is resolved. The tablet status bar and app icon badges read the shared count, so integrations do not need their own badge or cross-app storage.

Built-in RDP, ship, dock, ACC, redstone, settings and other tablet screens, the SQLite provider and packets are addon implementation. Addons should register their own app instead of reusing those internals.

Package: `com.rieno.gadgetsandgizmos.lib.client.render`

`AreaHighlightRenderTypes` is the supported shader-backed translucent area-highlight render type. Keep it on the client side; it does not make server-side rendering safe.

`SimulatedDiagramMiniRenderer` embeds the Simulated contraption diagram in another client screen. Pass a `DiagramDataSource` whose forces implement `DiagramForceData`; the renderer accepts input and retries failed setup with bounded backoff. Every failed setup attempt is logged with the operation, attempt and retry delay. Stable Simulated APIs are called directly. `DiagramScreenAccess` is the mixin-backed bridge for upstream private screen details and is implemented automatically by the library.

`SubLevelClientRenderApi.withPoses` runs a client lookup or raycast with interpolated Sable poses installed and always restores the previous provider. `renderPosition` returns the interpolated position used to draw one `ClientSubLevel`.

`PhysicsGogglesOverlayRegistry` lets an addon register an extra physics-goggles HUD layer without importing the addon client. Its `Context` supplies the graphics, player, root level, looked-at block entity, active SubLevel ID and partial tick. Normal `register` rejects ID conflicts; use `registerOrReplace` only for an integration you own and `unregister` when it unloads. One failing renderer is logged with its full exception and does not stop the remaining layers.

## Jar layout and dependency direction

- `gadgetsngizmos-<version>.jar` is the reusable library mod.
- `createthrusters-<version>.jar` is the Gadgets & Gizmos addon and requires the library jar.
- `Create-Gadgets-and-Gizmos-<version>.jar` is the distribution wrapper containing both jars as separate NeoForge Jar-in-Jar entries.

The addon source set may depend on the library source set. The library source set must never import addon classes. If a new API needs addon knowledge, the fix is a small library contract, callback or registry — not a library-to-addon import.
