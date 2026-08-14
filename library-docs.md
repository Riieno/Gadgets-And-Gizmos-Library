# Gadgets & Gizmos Library API

`gadgetsngizmos` is the reusable API shipped with Gadgets & Gizmos. It gives other mods typed access to controller input, precise kinetics, Sable bodies, physics helpers, ACC graph extensions, SCM controls, display frames, shipping reservations, tablet apps and shared client rendering.

This page specifies the supported `1.2.x` library surface. Import the library packages, not the `createthrusters` addon implementation. If an integration needs a missing capability, add a small reusable contract to the library instead of reaching into an addon class.

## Contents

- [Supported versions](#supported-versions)
- [Adding the library](#adding-the-library)
- [API rules](#api-rules)
- [Foundation and lifecycle](#foundation-and-lifecycle)
- [Block entity probes and lookup](#block-entity-probes-and-lookup)
- [Controllers and orientation](#controllers-and-orientation)
- [Discovery, SubLevels and menus](#discovery-sublevels-and-menus)
- [Kinetics and bearing heads](#kinetics-and-bearing-heads)
- [Virtual kinetics and alternators](#virtual-kinetics-and-alternators)
- [Sable physics](#sable-physics)
- [Client rendering](#client-rendering)
- [ACC graph extensions](#acc-graph-extensions)
- [Display integration](#display-integration)
- [SCM integration](#scm-integration)
- [Shipping](#shipping)
- [Tablet apps](#tablet-apps)
- [Complete public surface](#complete-public-surface)
- [Lifecycle checklist](#lifecycle-checklist)
- [Compatibility and failure behaviour](#compatibility-and-failure-behaviour)
- [Jar layout](#jar-layout)

## Supported versions

| Component | Supported version |
| --- | --- |
| Library mod ID | `gadgetsngizmos` |
| Library API range | `1.2.x` |
| Minecraft | `1.21.1` |
| Java | `21` |
| NeoForge | `21.1.225` or newer |
| Create | `6.0.10` or newer |
| Sable | `2.0.3` or newer compatible version |
| Simulated | `1.2.1` or newer compatible version |
| Aeroworks | Optional, `1.2.11` or newer when installed |
| AeroClaims | Optional, `0.9.0` or newer when installed |

The loader version and artifact label can differ while a beta is being built. Depend on the loader-facing `1.2.x` API range unless a release says otherwise.

## Adding the library

Use the standalone library jar while developing another mod. Do not compile against the addon jar just to reach library classes.

```groovy
dependencies {
    compileOnly files("libs/gadgetsngizmos-V1.2.0-Beta-13.jar")
    runtimeOnly files("libs/gadgetsngizmos-V1.2.0-Beta-13.jar")
}
```

Use the dependency form required by your NeoForge development plugin if it remaps local mod jars. The important part is that the standalone library is available on both the compile and development runtime classpaths.

Declare the loader dependency in your `neoforge.mods.toml`:

```toml
[[dependencies.your_mod_id]]
modId="gadgetsngizmos"
type="required"
versionRange="[1.2.0,1.3.0)"
ordering="AFTER"
side="BOTH"
```

The library itself requires Create, Sable and Simulated. Your mod should still declare any of those dependencies it calls directly.

### Updating from `createthrusterslib`

Replace the old standalone library jar with `gadgetsngizmos`; never keep both copies installed. Update loader dependencies to `gadgetsngizmos` and use the renamed public entry types shown below. Deprecated Java aliases remain so already-compiled beta integrations can still resolve their old class and method owners where the loader dependency allows them to start.

The addon itself still uses `createthrusters` for blocks, items, entities, recipes, translations, packets and saved content. Those IDs have not moved, so existing worlds keep their registered content and saved references. The library also removes the old Sable residency ticket as it retains the renamed ticket.

## API rules

### Package boundary

- Import supported types below `com.rieno.gadgetsandgizmos.lib`.
- Never import `com.rieno.gadgetsandgizmos.lib.mixin`.
- Never import `com.rieno.gadgetsandgizmos.content`, `compat`, `mixin` or `neoforge` classes from the addon source set.
- Bootstrap and event subscriber classes are lifecycle wiring. NeoForge calls them; another mod should not.
- Built-in ACC documents, screens, packets, SQLite stores and content block entities are addon implementation, not library API.

### Stable IDs and saved data

Use your own namespace for every registered graph node, tablet app, display source, SCM mode, probe factory and guard exception.

```java
ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
        "your_mod", "steerable_nozzle");
```

Treat IDs, serialized enum values, NBT keys and saved target references as persistent data. Do not build an ID from a translated label or a Java class name.

### Ownership and copying

- Public snapshots and registry views are immutable unless their type says otherwise.
- `GraphValue`, display envelopes, tablet storage values and other NBT boundaries copy mutable payloads.
- Keep the UUID or registration ID needed to release anything you claim.
- Close leases and native handles. Unregister dynamic integrations when their owning mod or runtime feature goes away.
- Do not retain a live `BlockEntity`, `Level` or `ServerSubLevel` as saved identity. Store the supplied stable target value instead.

### Registration behaviour

| Registry | Normal registration |
| --- | --- |
| Graph definitions and runtimes | Keeps the existing identical entry and rejects a conflicting implementation |
| ACC display sources | Rejects a block that already has a source; `registerIfAbsent` reports the conflict |
| Tablet apps | Rejects a duplicate; replacement must use `registerOrReplace` |
| Tablet client renderers | Rejects a duplicate; `registerIfAbsent` reports the conflict |
| Physics goggles overlays | Rejects a duplicate; replacement must use `registerOrReplace` |
| SCM control modes | Replaces the entry under the same ID |
| SCM probe factories | Replaces the entry under the same ID |

Never replace an entry outside your namespace. Prefer the strict registration method when the registry supplies one.

### Logical side and thread

- Physics, topology, control authority, shipping and tablet storage are server-side systems.
- Client render packages must only be loaded from client setup or another client-only class.
- Registry mutations belong in mod setup unless the registry explicitly supports a dynamic integration.
- Run level and block entity mutations on the owning game thread.
- A common/server class must not eagerly reference a client-only implementation type.

### Reflection

Required APIs are direct typed calls. Do not wrap Create, Sable, Simulated, NeoForge or this library in reflection just to avoid a dependency or import.

Reflection is only appropriate for a genuinely optional dependency, a supported upstream version split without one stable API, or unavoidable private upstream access. Keep that reflection inside one compatibility adapter, cache its lookups, expose a typed result and report a real failure. Never silently report success after a required call failed.

## Foundation and lifecycle

Package: `com.rieno.gadgetsandgizmos.lib`

| Type | Use |
| --- | --- |
| `GadgetsNGizmosLibrary` | Supplies the stable `MOD_ID` and shared library identity |
| `GadgetsNGizmosLibraryNeoForge` | NeoForge server/common entry point; lifecycle-owned |
| `PhysicsStaffPowerEvents` | NeoForge event bridge for Physics Staff operation cleanup; lifecycle-owned |

Package: `com.rieno.gadgetsandgizmos.lib.client`

| Type | Use |
| --- | --- |
| `GadgetsNGizmosLibraryClientBootstrap` | Installs the library client hooks and shaders; lifecycle-owned |
| `GadgetsNGizmosLibraryClientNeoForge` | NeoForge client entry point; lifecycle-owned |

`GadgetsNGizmosLibraryConfigs` owns the server settings for held angles, precise angle propagation, virtual kinetic propagation and kinetic guard logging. Read the settings when a compatible feature needs to respect them. Do not call its registration method or write the library config from another mod.

The old `CreateThrustersLibrary`, NeoForge entry point, client bootstrap, `CTLibraryConfigs` and `CTKineticGuard` names remain as deprecated compatibility aliases. New integrations must use the Gadgets & Gizmos names and depend on `gadgetsngizmos`.

The library automatically installs its own mixins and client setup. A consumer does not add the library mixin config to its own manifest.

## Block entity probes and lookup

Package: `com.rieno.gadgetsandgizmos.lib.probe`

### `BlockEntityDataProvider`

Implement this on a block entity that exposes detailed named data without making the caller import its implementation class.

- `graphReadableData()` publishes readable field IDs and value types.
- `graphWritableData()` publishes writable field IDs and value types.
- `graphWritableOptions()` optionally supplies allowed values or editor hints.
- `readGraphValue(...)` resolves one field.
- `writeGraphValue(...)` validates and applies one mutation.

Field IDs are part of your integration contract. Keep them stable after release.

### `ConnectedBlockEntityProvider`

Implement `connectedBlockEntities()` when a mechanism can expose its live related block entities. Return only loaded, current connections. The caller must handle an empty collection when a carriage, rope endpoint, bearing head or another member is unloaded.

This is the normal probe for questions such as "which carriages belong to this shaft" or "which body is connected to this mechanism". Do not expose the mechanism's private block entity type through the library contract.

### `BlockEntityLookupApi`

Use this instead of duplicating root/SubLevel traversal.

| Method | Behaviour |
| --- | --- |
| `findIncludingSubLevels` | Finds a block entity at a world-facing position in the root level or a loaded SubLevel |
| `resolveIncludingSubLevels` | Returns the block entity together with its resolved internal position and SubLevel ID |
| `find` | Uses an optional saved SubLevel ID and can fall back to the root scope |
| `findExact` | Resolves only the requested scope |
| `findLoadedExact` | Resolves only an already-loaded target and never requests a load |
| `findInSubLevel` | Looks up one typed block entity inside a known SubLevel |

`BlockEntityLookupApi.ResolvedBlockPosition` keeps the resolved `BlockPos` and owning SubLevel UUID together. Keep both parts when a later packet or menu action must target the same body.

## Controllers and orientation

Package: `com.rieno.gadgetsandgizmos.lib.control`

### Analogue input

| Type | Contract |
| --- | --- |
| `AnalogueChannel` | One configurable value with mode, limits, rate, smoothing, debounce, repeat, step and NBT state |
| `AnalogueAxis` | Combines negative and positive channels into a signed axis |
| `AnalogueChannelMode` | Stable channel behaviour IDs such as momentary, ramp, step, latch and direct |
| `AnalogueControlChannel` | Canonical named controller channels used by Gadgets & Gizmos integrations |
| `AnalogueSignalPacket` | Serializable channel update value |
| `AnalogueTransmissionTarget` | Receiver contract for transmitted analogue updates |
| `FrequencyBinding` | Persistent Create Redstone Link frequency pair |
| `CustomKeyEntry` | Persistent user-defined controller key entry |

Use the channel and axis types for deadzones, rise/fall rates, smoothing and input state. Do not reproduce those calculations in a screen, peripheral or block entity.

### Direct control

Implement `IDirectControlReceiver` when a block entity accepts a named direct value:

```java
public final class MyNozzleBlockEntity extends BlockEntity
        implements IDirectControlReceiver {
    @Override
    public void applyDirectControllerSignal(String channelId, float val) {
        if (!"throttle".equals(channelId)) return;
        setThrottle(Mth.clamp(val, 0.0F, 1.0F));
        setChanged();
    }
}
```

`ControllerDirectTargetReference` stores a stable root/SubLevel target without retaining a live block entity. `ControllerMechanic` and `ControllerMechanicBinding` describe a supported controller mechanic and its configured binding.

### Directional input

`DirectionalAnalogSource` supplies local input. `DirectionalAnalogMath` applies deadzone and square/circular conversion. `DirectionalAnalogSnapshot` carries the resolved forward, back, left and right values. Use these together so controller screens, blocks and peripherals agree on diagonal input.

### Orientation

| Type | Contract |
| --- | --- |
| `OrientationPayload` | Serializable orientation value |
| `OrientationTarget` | Consumer of an orientation target |
| `LinkedOrientationSource` | Producer that can be linked to a target |
| `OrientationMath` | Shared orientation conversion and normalization helpers |
| `CardinalTiltController` | Resolves a facing direction and cardinal pulls into a clean tilt direction |

Angles in the control maths package are radians unless a method explicitly says degrees.

### Hardware input

Package: `com.rieno.gadgetsandgizmos.lib.control.hardware`

`HardwareControllerState` is an immutable device snapshot with safe axis/button access. `HardwareControllerBindings` owns the `hardware:*` binding IDs, standard device layout, conventional channels and deadzone application. Use these IDs when a controller UI needs to save the same hardware binding format.

### Control maths

Package: `com.rieno.gadgetsandgizmos.lib.control.math`

| Type | Contract |
| --- | --- |
| `PidControllerMath` | Finite PID calculations and controller state |
| `LqrControllerMath` | Linear quadratic regulator calculations |
| `AdrcControllerMath` | ADRC calculations and state |
| `AdrcControllerNthOrderMath` | Nth-order ADRC calculations and state |
| `Vector3` | Small immutable three-component maths value |
| `Quaternion` | Immutable quaternion maths value |
| `RotationMath` | Quaternion, Z-X-Z Euler and X-Y-Z Tait-Bryan conversions |

All helpers sanitize non-finite values at their API boundary. Keep one controller state per controlled system; sharing state between unrelated targets also shares their accumulated error.

## Discovery, SubLevels and menus

### Controller discovery

Package: `com.rieno.gadgetsandgizmos.lib.discovery`

`ControllerDiscoveryService.scanBlockEntities(...)` builds stable `ControllerDiscoveryNode` values for loaded targets. Use `classify(...)` or `classifyKind(...)` when a custom UI needs the same classification without running a complete scan. `ControllerDiscoveryKind` contains the stable kind ID and translation key.

Implement `INamedBlockEntity` when discovery should show a useful player-facing name. The name is a display value, not a persistent identity.

`SubLevelBlockEntityCollector` is the compatibility collector for loaded Sable bodies and short-lived lazy-load operations. It can test or request target loading, identify plot positions, enumerate live bodies, read one loaded block entity and collect loaded world block entities around a position. Prefer `BlockEntityLookupApi` and `SableLevelApi` for ordinary typed lookup. Use the collector only when its load-aware enumeration behaviour is actually required; its `Object`-typed methods are retained for compatibility.

### Root and SubLevel ownership

Package: `com.rieno.gadgetsandgizmos.lib.physics`

`SableLevelApi` is the typed ownership boundary:

- `serverLevel(...)` resolves the root `ServerLevel` from a root or Sable level.
- `containing(...)` finds the loaded body containing a block, entity or precise position.
- `containingId(...)` returns its stable UUID.
- `tracking(...)` returns the body currently carrying an entity.
- `subLevel(...)` and `subLevels(...)` resolve already-live bodies.

The methods call the required Sable API directly. An empty result means the target is absent or unavailable, not that reflection silently failed.

### Connected body dependencies

`SubLevelConnectionApi.resolve(...)` resolves one live SubLevel by UUID. `merge(...)` combines dependency sets without duplicate bodies. `connectedTo(...)` reads bodies published by connected block entities.

Use it when implementing `BlockEntitySubLevelActor.sable$getConnectionDependencies`:

```java
@Override
public Iterable<SubLevel> sable$getConnectionDependencies() {
    return SubLevelConnectionApi.connectedTo(connectedBlockEntities());
}
```

Only publish real current links. Returning every nearby SubLevel makes unrelated assemblies one topology.

### Assembly and residency

`SubLevelAssemblyApi` assembles an explicit set of blocks, assembles one block and disassembles a live body through typed Sable/Simulated calls. `AssemblyResult` reports the created body and moved-block offset. Null or empty selections return no body; `assembleBlock(...)` can throw Create's `AssemblyException` when the selected block cannot assemble.

`SableSubLevelResidency` is in `lib.discovery`. `lease(...)` creates an owner-scoped lease, `retain(...)` adds one live server body and `synchronize(...)` makes the retained set match a collection. Close the `Lease` when ownership ends. Use `detach()` during normal world shutdown or a block entity chunk transition when the saved ticket should survive that Java object. `bootstrap()` is lifecycle wiring and is called by the library.

### SubLevel-aware menus

Package: `com.rieno.gadgetsandgizmos.lib.menuconfig`

| Type | Contract |
| --- | --- |
| `MenuConfigTarget` | Block position plus optional SubLevel UUID |
| `MenuOpenHeader` | Encodes and decodes the target in menu opening data |
| `MenuBackedBlockEntityTarget<B>` | Menu contract exposing its typed target |
| `MenuBackedBlockEntityResolver` | Resolves the target in a server payload handler |
| `ISimulatedMenuOpen` | Marks a menu using the extended simulated target |

Use the header and resolver together. Sending only a `BlockPos` works in the root world but targets the wrong coordinates when the same block is mounted on a body.

## Kinetics and bearing heads

Package: `com.rieno.gadgetsandgizmos.lib.kinetics`

### Held and precise angles

| Type | Contract |
| --- | --- |
| `HeldKineticAngleAccess` | Stores the held kinetic angle exposed by the library mixin |
| `PreciseKineticOutputAccess` | Applies or clears one exact output angle |
| `DirectionalPreciseKineticOutputAccess` | Applies an exact angle for a selected face |
| `PreciseKineticOutputBoundary` | Marker for a graph boundary that owns precise output behaviour |
| `KineticAngleHelper` | Normalizes, compares and resolves kinetic angles |
| `KineticGraphHelper` | Shared Create kinetic graph traversal |
| `HeldAngleKineticGraph` | Applies held angles through a compatible graph |
| `PreciseKineticOutputGraph` | Applies precise output angles through a compatible graph |

The graph helpers return `ApplyResult` values so a caller can distinguish a handled update from an unavailable or incompatible target. Use the supplied synchronizer callback when the changed block entity needs an explicit sync.

`GadgetsNGizmosKineticGuard` owns configuration-aware package guards used by the library mixins. Register a keyed exception only for a compatible block that must cross one guard. A throwing predicate is logged and treated as no match. Unregister a dynamic exception when its integration unloads.

`SingleFaceRotationConfiguration` supplies the standard one-face `IRotate` implementation.

### Servo motion

`ServoMotionController` is the reusable bounded servo planner. Construct it with `ServoMotionController.ServoMotionConfig`, call `update(...)` while active and read the current angle and generated speed.

Use `applySyncedState(angle, speed)` when restoring client sync or persisted state. It updates the current state without scheduling new motion.

### Bearing head access

`BearingHead` contains the stable `PRIMARY` and `SECONDARY` serialized IDs and colours. The legacy names `cyan`, `left` and `top` resolve to `PRIMARY`; `orange`, `right` and `bottom` resolve to `SECONDARY`.

Implement `BearingHeadAccess` when a bearing exposes one or more controllable heads. The contract covers:

- current, target and interpolated angle
- angle range and range updates
- direct target updates
- mounted block and mounted SubLevel identity
- assembly and disassembly state of the mounted head

Use `BearingAngleDriver` to drive the first compatible downstream Create or Simulated bearing. Its `BlockEntitySynchronizer` lets the host perform the correct sync without the helper importing addon networking.

## Virtual kinetics and alternators

### Virtual kinetics

Package: `com.rieno.gadgetsandgizmos.lib.virtualkinetics`

| Type | Contract |
| --- | --- |
| `VirtualKineticProvider` | Real owner that exposes virtual kinetic members |
| `VirtualKineticBlockEntity` | One virtual member participating in the Create graph |
| `VirtualKineticHostBlock` | Host-state access for a virtual member |
| `VirtualKineticPos` | Stable owner position and slot identity |

The library mixins handle graph lookup, source resolution, persistence and propagation after these contracts are implemented. Keep a virtual slot stable for the lifetime of its saved block entity.

### Alternators

Package: `com.rieno.gadgetsandgizmos.lib.power.alternator`

Implement `AlternatorTuning` with minimum RPM, rated RPM, maximum FE per tick and maximum stress impact. `AlternatorKinetics` supplies matching effective RPM, output and stress calculations.

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

## Sable physics

Package: `com.rieno.gadgetsandgizmos.lib.physics`

### Telemetry and transforms

`SableSubLevelTelemetryApi.sample(...)` reads one already-loaded body without forcing a load. Its `Snapshot` separates `loaded` from `physicsAvailable` and exposes finite position, linear/angular velocity, speed and mass.

`SableTransformApi` converts points and directions between root and SubLevel space, projects through one or every nested body, measures transformed distances and finds loaded bodies intersecting a world box.

`SableAssemblyBoundsApi.envelope(...)` combines loaded SubLevel world bounds around a world-space reference point. `Envelope` exposes a conservative horizontal radius, height and lower-hull offset. Missing bodies return `Envelope.DEFAULT`.

- Use `projectOutOne(...)` for one immediate body boundary.
- Use `projectOut(...)` for the complete nested chain.
- Use `kick(...)` only when intentionally moving an entity into the supplied SubLevel.
- Treat an empty lookup as unavailable and retry from a later lifecycle event when appropriate.

### Topology

Implement `SableAssemblyConnectionProvider` on a `BlockEntitySubLevelActor` when the actor publishes an explicit assembly link. Each `SableAssemblyConnection` is `STRUCTURAL` or `CARRIAGE_COUPLER`.

`SableAssemblyTopologyApi.discover(...)` creates a deterministic connected topology around a loaded root body. Actor dependencies are treated as undirected. Optional `ActorFilter` and `ActorClassifier` callbacks select and classify actors.

`Topology` contains ordered bodies and edges, graph depth, coupler depth, structural carriage partitions and a stable fingerprint. Removing coupler edges creates the carriage partitions; the partition containing the requested root is primary.

Use `SableAssemblyTopologyCache` for continuous control. It keeps the topology until its revision changes and performs a staggered safety refresh. Call `SableAssemblyTopologyInvalidation.invalidate(...)` when a connection changes outside a known library event.

```java
private final SableAssemblyTopologyCache topologyCache =
        new SableAssemblyTopologyCache();

void tickControl(ServerSubLevel root) {
    SableAssemblyTopologyApi.Topology topology = topologyCache.get(root);
    SableAssemblyDynamicsApi.Snapshot dynamics =
            SableAssemblyDynamicsApi.sample(topology);
    if (!topology.available()
            || !dynamics.loaded()
            || !dynamics.physicsAvailable()) return;

    // Reuse the same topology and dynamics snapshot for this control tick
}
```

`SableAssemblyTopologyEvents` is the library event bridge and is not called by consumers.

### Dynamics and impulses

`SableAssemblyDynamicsApi.sample(topology)` samples exactly the topology already selected. Its `Snapshot` contains root-local aggregate mass, centre of mass, inertia, inverse inertia and ordered `BodyDynamics` entries. Use `aggregate(...)` for one selected subset. A missing or mass-unavailable selected body returns a loaded zero aggregate instead of a misleading partial mass.

`SablePointImpulseApi.apply(...)` and `applyDirectional(...)` validate finite values, write one point impulse and wake the body only after a successful write. A non-finite queued accumulator is cleared before it reaches Sable.

`SableMagneticCaptureApi.pullTogether(...)` applies bounded equal-and-opposite pulls between two loaded bodies. Supply local anchors, capture radius, maximum closing acceleration and physics time step. Invalid, coincident and out-of-range bodies are ignored.

### Constraints and yaw joints

`SableYawJointApi.create(...)` creates a rotary joint that locks translation, pitch and roll while allowing yaw around the supplied local axes. `Joint` exposes validity, contacts, servo setup, true zero-force disable, wake, removal and `close()`. Contacts start disabled.

`progressiveResponse(...)` returns a smooth dead-zone spring, damping and force response. It stays exactly zero through the free angle, rises smoothly and saturates at the supplied maximum. It is a soft force response, not a native hard angular limit.

`SableConstraintApi` is the one supported compatibility facade for Sable constraint package differences. It supplies fixed, free and generic configurations plus add, frame, wake and remove operations. Do not reproduce its compatibility reflection in feature code. Use stable typed Sable calls directly for APIs that do not vary.

### Collision and particle occlusion

`SubLevelParticleOcclusion` provides shared collision, clearance and particle-occlusion queries over loaded plots. `ProbeCache` reuses repeated query state and `SweptBoundsScan` describes a swept collision result. Empty results mean the body could not be queried.

## Client rendering

Package: `com.rieno.gadgetsandgizmos.lib.client.render`

All types in this section are client-only.

### Area highlights and SubLevel poses

`AreaHighlightRenderTypes` supplies the registered translucent highlight render type. The library client bootstrap owns shader registration.

`SubLevelClientRenderApi.withPoses(...)` installs interpolated Sable poses for one lookup or raycast and always restores the previous provider. `renderPosition(...)` returns the interpolated render position of one `ClientSubLevel`.

### Physics goggles overlays

Register an extra HUD layer through `PhysicsGogglesOverlayRegistry`:

```java
PhysicsGogglesOverlayRegistry.register(
        ResourceLocation.fromNamespaceAndPath("your_mod", "engine_load"),
        ctx -> drawEngineLoad(ctx.graphics(), ctx.target(), ctx.partialTick()));
```

`Context` supplies the GUI graphics, player, root level, looked-at block entity, active SubLevel ID and partial tick. `register(...)` rejects ID conflicts. Use `registerOrReplace(...)` only for an integration you own, and `unregister(...)` when a dynamic layer unloads. One failing renderer is logged with its exception and does not stop later layers.

### Mini contraption diagram

`SimulatedDiagramMiniRenderer` embeds the Simulated diagram in another screen. Supply a `DiagramDataSource`; each force entry implements `DiagramForceData`.

The renderer owns its input forwarding, hosted tick and cleanup. Failed setup is logged with the operation, attempt and retry delay, then retried with bounded backoff. Stable Simulated APIs are called directly and are assumed to be present.

`DiagramScreenAccess` is the mixin-backed bridge for upstream private screen details. The library installs it automatically. Consumers may use the typed bridge where required but must not implement it or call the mixin class.

### Tablet client

Package: `com.rieno.gadgetsandgizmos.lib.client.tablet`

`TabletAppClientRenderer` draws one registered tablet app. `TabletAppClientContext` supplies its definition, snapshot, safe canvas, input state and `ActionSender`. Register it through `TabletAppClientRegistry` during client setup. Apps without a custom renderer use the standard layout.

## ACC graph extensions

Package: `com.rieno.gadgetsandgizmos.lib.graph`

### Registering a node

Register one `GraphNodeDefinition` and a matching `GraphNodeExecutor` under the same stable ID:

```java
ResourceLocation nodeId = ResourceLocation.fromNamespaceAndPath(
        "your_mod", "scale");

GraphApi.nodes().register(new GraphNodeDefinition(
        nodeId.toString(),
        "math",
        Map.of("value", "number", "factor", "number"),
        Map.of("value", "number"),
        false));

GraphApi.runtimes().register(nodeId.toString(), (ctx, inputs) -> Map.of(
        "value", GraphValue.number(
                inputs.get("value").asNumber()
                        * inputs.get("factor").asNumber())));
```

`GraphNodeDefinition` describes the editor category, typed ports and persistent-state requirement. `GraphNodeExecutor` receives immutable inputs and `GraphExecutionContext`.

### Values and services

`GraphValue` supports numbers, booleans, strings, lists and maps. Factory methods normalize values, defensively copy nested collections and reject recursive collections and mutable arrays. Use `asNumber`, `asBoolean` and `asString` for the normal conversions.

`GraphExecutionContext` supplies the current tick, namespaced persistent state and typed optional host services. Define a `GraphServiceKey<T>` when a reusable node needs a host service. `GraphHostServices.BLOCK_ENTITY` exposes the common host block entity without leaking the addon controller type.

The deprecated raw `services()` map is an empty compatibility method. Do not build a new integration on string keys or `Object` casts.

### Compilation and scheduling

| Type | Contract |
| --- | --- |
| `GraphModel` | Addon-owned node and edge document model |
| `GraphCompiler` | Validates and compiles a graph model |
| `CompiledGraph` | Immutable indexed nodes, ports and data/execution edges |
| `GraphNodeRegistry` | Conflict-safe definition registry with immutable snapshots |
| `GraphRuntimeRegistry` | Conflict-safe executor registry with immutable snapshots |
| `GraphEventScheduler` | Bounded immediate and delayed event queues with cancellation and cleanup |

`GraphEventScheduler` releases delayed events deterministically by tick. Clear a scheduler when the owning runtime is removed.

Package: `com.rieno.gadgetsandgizmos.lib.graph.render`

`GraphViewport` contains client-free pan, zoom and coordinate conversion. `GraphWireGeometry` produces points and segments for graph wires. The ACC editor and its document format remain addon implementation.

## Display integration

Package: `com.rieno.gadgetsandgizmos.lib.display`

### Frame envelope

`DisplayFrameEnvelope.create(...)` copies a render payload, keeps presentation metadata separate and records its pixel size. Use `payload(...)`, `presentation(...)` and `requestedMode(...)` to read it. `hasRenderablePayload(...)` validates the supported text, terminal and widget shapes.

```java
CompoundTag frame = DisplayFrameEnvelope.create(
        payload, presentation, 256, 128);

if (DisplayFrameEnvelope.hasRenderablePayload(
        DisplayFrameEnvelope.payload(frame))) {
    forwardFrame(frame);
}
```

Preserve the public envelope keys when relaying a frame. Do not merge presentation fields into the source payload.

### Display sources and surfaces

`AccDisplaySourceRegistry` registers one block type with an `AccDisplaySource`. The source returns the normal render payload for a requested size and can handle normalized click/input events when interactive.

```java
AccDisplaySourceRegistry.register(
        ResourceLocation.fromNamespaceAndPath(
                "your_mod", "weather_station"),
        YOUR_WEATHER_STATION.get(),
        (source, width, height) -> {
            CompoundTag frame = new CompoundTag();
            frame.putString("Format", "text");
            frame.putString("Source", "Weather Station");
            return frame;
        });
```

Normal `register(...)` rejects a conflict. `registerIfAbsent(...)` is for optional cooperative integration. Call `unregister(...)` when a dynamic source disappears.

`DisplaySurfaceProjection.normalizedPoint(...)` converts a local tile hit into normalized joined-display coordinates and accounts for the texture-pixel border. `Point` contains the normalized X/Y result.

`ShipInformationDisplayModes` is the canonical mode list. Use `ids`, `contains`, `normalize`, `label` and `isStaticText` rather than copying mode strings.

## SCM integration

Package: `com.rieno.gadgetsandgizmos.lib.scm`

### Vehicle control modes

Implement `ScmControlMode` to convert one navigation sample into world-space force and torque. `ControlInput` supplies pose, velocity, route direction, accumulated error, clearances and navigation limits. `ControlOutput` contains force, torque, gravity compensation, upright stabilization and drive direction.

```java
ScmControlModeRegistry.register(new ScmControlMode() {
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(
                "your_mod", "submarine");
    }

    public String displayName() {
        return "Submarine";
    }

    public ControlOutput navigate(ControlInput input) {
        return new ControlOutput(
                force, torque, false, 0.0D, 1.0D);
    }
});
```

`ScmControlModeRegistry.register(...)` installs or replaces the mode under its ID. Release a dynamic non-built-in mode with `unregister(...)`. `resolve(...)`, `modes()` and `serializedIds()` provide stable read access. Only replace an ID owned by your mod.

`ScmBuiltinControlModes` exposes the built-in `AIRSHIP_ID`, `PLANE_ID` and `CAR_ID`. `ScmFlightBehavior` owns the saved `direct_vector` and `prefer_ship_direction` values. Custom modes always use their own namespace.

### Targets and probes

`ScmTarget` is a stable SubLevel-aware selected block and face. `stableId()` is suitable for saved map references.

Implement `ScmControlProbe` for a reversible control the SCM can calibrate. A probe supplies:

- stable adapter ID and display name
- optional shared control group ID
- minimum and maximum control
- neutral control, which defaults to zero
- apply and exact restore operations
- live `Reading`
- target-sublevel-local effect direction and position
- current availability

Register a factory through `ScmControlProbeRegistry`:

```java
ScmControlProbeRegistry.register(
        ResourceLocation.fromNamespaceAndPath(
                "your_mod", "steerable_nozzle"),
        100,
        (blockEntity, ctx) -> blockEntity instanceof MyNozzleBlockEntity nozzle
                ? List.of(new MyNozzleProbe(nozzle, ctx.target()))
                : List.of());
```

The `Context` supplies the selected target, target-sublevel-local suggested direction and full linked target list. Registering the same factory ID replaces its previous entry, so only replace IDs owned by your mod. A factory returns zero or more probes. `neutralControl()` must disengage the control without detaching it or changing ownership. The probe must restore the exact original state after every sample, including failed or cancelled calibration.

### Map composition and control ownership

`ScmMapCompositionApi` composes a primary fragment and connected fragments deterministically. `Fragment` carries an ID, owner, coverage set and value. `SelectedFragment` records the chosen order. `Composition` exposes accepted fragments, rejected duplicate IDs, values and ownership queries.

`ScmControlAuthorityApi.claim(...)` elects one controller for a server/assembly key across tick boundaries. Claim while active, respect `ownerChanged` before writing outputs, heartbeat by reclaiming with the current tick and release when control ends. `Owner` records controller ID, priority and expiry. `ClaimResult` records whether the caller won and whether ownership changed.

`ScmControlProbeRegistry` and `ScmControlModeRegistry` are separate. A mode decides navigation demand; a probe describes one reversible physical control.

## Shipping

Package: `com.rieno.gadgetsandgizmos.lib.shipping`

### Logistics runs

`ShipLogisticsRun` is the immutable persistent model for named `ITEM`, `FLUID`, `ENERGY` and `FUEL` runs. Each `Endpoint` stores a SubLevel UUID and internal block position. Runs and endpoints support NBT conversion.

Use `withName(...)` and `withEndpoints(...)` to create changed copies. `isFuelRun()` distinguishes the combined refuelling contract from normal single-resource routes.

### Dock scheduling

`ShipDockScheduler` reserves interchangeable named resources without importing the addon Ship Dock block. `DockSlot.resource(...)` can represent a connector, berth or another deterministic resource. `RequestKey` gives one vessel independent channels; the UUID overload remains the primary `dock` channel.

```java
ShipDockScheduler scheduler = ShipDockScheduler.get(server);
ShipDockScheduler.Lease lease = scheduler.request(
        new ShipDockScheduler.RequestKey(shipId, "holding"),
        candidates,
        occupiedSlots,
        currentSlot,
        new ShipDockScheduler.RequestPriority(
                distance, etaTicks, committed),
        new ShipDockScheduler.VesselEnvelope(
                radius, height, bottomOffset),
        level.getGameTime());

scheduler.heartbeat(
        new ShipDockScheduler.RequestKey(shipId, "holding"),
        priority, level.getGameTime());
scheduler.release(new ShipDockScheduler.RequestKey(shipId, "holding"));
```

`RequestPriority` contains distance, ETA and committed state. `VesselEnvelope` carries conservative radius, height and lower-hull offset, with enclosed-volume and footprint fit helpers. `Lease` returns the granted dock/resource or the queue position plus a size-aware `HoldingPlacement` for safe fallback lanes.

Call `release(RequestKey)` for one channel and `release(UUID)` only when every channel owned by that vessel should be removed. Call `removeDock(...)` when a dock disappears. Call `shutdown(server)` during server stopping and `finishShutdown(server)` after stop so late requests are rejected and the server reference is released.

## Tablet apps

### Server/common app definition

Package: `com.rieno.gadgetsandgizmos.lib.tablet`

`TabletAppDefinition` describes a stable app ID, title, description, accent colour, icon, immutable tabs and declared shared keys. `TabletTabDefinition` describes a tab, action IDs and which actions require keyboard input.

`TabletAction` carries the selected app, tab, action and string arguments. `TabletActionContext` supplies the authenticated player, tablet stack and optional SubLevel-aware target, including placed-tablet source data.

Register the definition and server handler during common setup:

```java
TabletAppRegistry.register(definition, (ctx, action) -> {
    if (!"refresh".equals(action.actionId())) {
        return TabletActionHandler.Result.failure(
                Component.literal("Unknown action"));
    }

    // Validate the player and target before changing app data
    return TabletActionHandler.Result.success(
            Component.literal("Updated"));
});
```

`register(...)` rejects a conflict. Use `registerIfAbsent(...)` for cooperative optional support and `registerOrReplace(...)` only for an app you own. `TabletAppRegistry.snapshot()` returns a revisioned immutable app list for long-lived screens.

### Storage and notifications

`TabletStorage` is the server persistence contract. `TabletStorageApi.storage()` returns the installed provider; `available()` tells a standalone consumer whether one is installed. `install(...)` and `uninstall(...)` belong to the storage owner, not normal app code.

Storage is keyed by logical tablet UUID. Physical tablets sharing that UUID intentionally share installed apps, app data, bindings, routes and SCM workspace data.

Declare cross-app records in `TabletAppDefinition.sharedDataKeys`. `shared(...)` and `updateShared(...)` reject undeclared keys and keep ownership under the source app.

`TabletNotification` is the immutable persisted notification value. `TabletNotifications` supplies an app-owned inbox. Post with a stable notification ID to create or replace one entry, then dismiss or clear it when resolved. The tablet status bar and app badges use the shared count.

`TabletInteractionMode` owns the persisted `STANDARD`, `READER` and `PUSH` values.

### Client renderer

Register a `TabletAppClientRenderer` with `TabletAppClientRegistry` during client setup. Its `TabletAppClientContext` supplies the app snapshot, safe canvas and action sender. Do not execute server mutations directly from the renderer; send a declared action and validate it again in the server handler.

## Complete public surface

The following tables are the complete supported top-level surface for `1.2.x`. Nested records and interfaces are listed with their owning type. Lifecycle-only types are marked and mixin implementation classes are excluded.

### Foundation, compatibility and configuration

| Package | Types |
| --- | --- |
| `lib` | `GadgetsNGizmosLibrary`; `GadgetsNGizmosLibraryNeoForge` *(lifecycle)*; `CreateThrustersLibrary`; `CreateThrustersLibraryNeoForge` *(deprecated aliases)*; `PhysicsStaffPowerEvents` *(lifecycle)* |
| `lib.client` | `GadgetsNGizmosLibraryClientBootstrap`; `GadgetsNGizmosLibraryClientNeoForge` *(lifecycle)*; `CreateThrustersLibraryClientBootstrap`; `CreateThrustersLibraryClientNeoForge` *(deprecated aliases)* |
| `lib.config` | `GadgetsNGizmosLibraryConfigs` (`Server`); `CTLibraryConfigs` *(deprecated alias)* |
| `lib.compat` | `PhysicsStaffPowerHooks` (`StaffActionFailure`); `PhysicsStaffPowerTracker`; `PhysicsStaffInteractionGuard` |

`PhysicsStaffPowerHooks` validates Backtank-powered staff actions, consumes pressure, supplies tooltip values and calculates mass-based drag/lock drain. `PhysicsStaffPowerTracker` owns active operation state. `PhysicsStaffInteractionGuard` protects SCM initialization targets and applies optional AeroClaims permission checks. These are Physics Staff integration contracts, not a general claims or energy API.

### Probe, control and discovery

| Package | Types |
| --- | --- |
| `lib.probe` | `BlockEntityDataProvider`; `BlockEntityLookupApi` (`ResolvedBlockPosition`); `ConnectedBlockEntityProvider` |
| `lib.control` | `AnalogueAxis`; `AnalogueChannel`; `AnalogueChannelMode`; `AnalogueControlChannel`; `AnalogueSignalPacket`; `AnalogueTransmissionTarget`; `ControllerDirectTargetReference`; `ControllerMechanic`; `ControllerMechanicBinding`; `CustomKeyEntry`; `DirectionalAnalogMath`; `DirectionalAnalogSnapshot`; `DirectionalAnalogSource`; `FrequencyBinding`; `IDirectControlReceiver`; `LinkedOrientationSource`; `OrientationMath`; `OrientationPayload`; `OrientationTarget` |
| `lib.control.hardware` | `HardwareControllerBindings` (`BindingOption`); `HardwareControllerState` |
| `lib.control.math` | `AdrcControllerMath` (`State`, `Result`); `AdrcControllerNthOrderMath` (`State`, `Result`); `LqrControllerMath`; `PidControllerMath`; `Quaternion`; `RotationMath`; `Vector3` |
| `lib.gimbal` | `CardinalTiltController` (`Builder`, `CardinalPulls`) |
| `lib.discovery` | `ControllerDiscoveryKind`; `ControllerDiscoveryNode`; `ControllerDiscoveryService`; `INamedBlockEntity`; `SableSubLevelResidency` (`Lease`); `SubLevelBlockEntityCollector` |

### Kinetics, power and menus

| Package | Types |
| --- | --- |
| `lib.kinetics` | `BearingAngleDriver` (`BlockEntitySynchronizer`); `BearingHead`; `BearingHeadAccess`; `GadgetsNGizmosKineticGuard`; `CTKineticGuard` *(deprecated alias)*; `DirectionalPreciseKineticOutputAccess`; `HeldAngleKineticGraph` (`ApplyResult`, `KineticTargetSynchronizer`); `HeldKineticAngleAccess`; `KineticAngleHelper`; `KineticGraphHelper`; `PreciseKineticOutputAccess`; `PreciseKineticOutputBoundary`; `PreciseKineticOutputGraph` (`ApplyResult`); `ServoMotionController` (`ServoMotionConfig`); `SingleFaceRotationConfiguration` |
| `lib.virtualkinetics` | `VirtualKineticBlockEntity`; `VirtualKineticHostBlock`; `VirtualKineticPos`; `VirtualKineticProvider` |
| `lib.power.alternator` | `AlternatorKinetics`; `AlternatorTuning` |
| `lib.menuconfig` | `ISimulatedMenuOpen`; `MenuBackedBlockEntityResolver`; `MenuBackedBlockEntityTarget`; `MenuConfigTarget`; `MenuOpenHeader` |

### Physics

| Type | Nested/public values and use |
| --- | --- |
| `SableAssemblyConnection` | Stable structural or carriage-coupler edge |
| `SableAssemblyConnectionProvider` | Actor-supplied explicit connection contract |
| `SableAssemblyDynamicsApi` | `Snapshot`, `BodyDynamics`, `Tensor`; samples complete or selected topology dynamics |
| `SableAssemblyTopologyApi` | `ActorFilter`, `ActorClassifier`, `Body`, `Edge`, `CarriagePartition`, `Topology`; deterministic body graph |
| `SableAssemblyTopologyCache` | Revision-aware reusable topology cache |
| `SableAssemblyTopologyEvents` | NeoForge invalidation bridge *(lifecycle)* |
| `SableAssemblyTopologyInvalidation` | Explicit topology revision invalidation |
| `SableConstraintApi` | Supported Sable constraint compatibility facade |
| `SableLevelApi` | Typed root/SubLevel ownership and lookup |
| `SableMagneticCaptureApi` | Bounded equal-and-opposite magnetic pull |
| `SablePointImpulseApi` | Validated point and directional impulses |
| `SableSubLevelTelemetryApi` | `Snapshot`; finite loaded-body telemetry |
| `SableAssemblyBoundsApi` | `Envelope`; conservative loaded-assembly radius, height and lower-hull offset |
| `SableTransformApi` | Root/SubLevel point, direction, distance and bounds transforms |
| `SableYawJointApi` | `Joint`, `ProgressiveYawResponse`; constrained yaw joint ownership |
| `SubLevelAssemblyApi` | `AssemblyResult`; typed assembly and disassembly |
| `SubLevelConnectionApi` | Actor dependency resolution and merging |
| `SubLevelParticleOcclusion` | `ProbeCache`, `SweptBoundsScan`; plot collision and occlusion queries |

### Graph and display

| Package | Types |
| --- | --- |
| `lib.graph` | `CompiledGraph` (`Port`); `GraphApi`; `GraphCompiler`; `GraphEventScheduler` (`Scheduled`); `GraphExecutionContext`; `GraphHostServices`; `GraphModel` (`Node`, `Edge`); `GraphNodeDefinition`; `GraphNodeExecutor`; `GraphNodeRegistry`; `GraphRuntimeRegistry`; `GraphServiceKey`; `GraphValue` |
| `lib.graph.render` | `GraphViewport`; `GraphWireGeometry` (`Point`, `Segment`) |
| `lib.display` | `AccDisplaySource`; `AccDisplaySourceRegistry`; `DisplayFrameEnvelope`; `DisplaySurfaceProjection` (`Point`); `ShipInformationDisplayModes` |

### SCM and shipping

| Package | Types |
| --- | --- |
| `lib.scm` | `ScmBuiltinControlModes`; `ScmControlAuthorityApi` (`Owner`, `ClaimResult`); `ScmControlMode` (`ControlInput`, `ControlOutput`); `ScmControlModeRegistry`; `ScmControlProbe` (`Reading`); `ScmControlProbeRegistry` (`Context`, `Factory`); `ScmFlightBehavior`; `ScmMapCompositionApi` (`Fragment`, `SelectedFragment`, `Composition`); `ScmTarget` |
| `lib.shipping` | `ShipDockScheduler` (`DockSlot`, `RequestKey`, `VesselEnvelope`, `HoldingPlacement`, `RequestPriority`, `Lease`); `ShipLogisticsRun` (`ResourceType`, `Endpoint`) |

### Tablet and client rendering

| Package | Types |
| --- | --- |
| `lib.tablet` | `TabletAction`; `TabletActionContext`; `TabletActionHandler` (`Result`); `TabletAppDefinition`; `TabletAppRegistry` (`Snapshot`); `TabletInteractionMode`; `TabletNotification`; `TabletNotifications`; `TabletStorage`; `TabletStorageApi`; `TabletTabDefinition` |
| `lib.client.tablet` | `TabletAppClientContext` (`ActionSender`); `TabletAppClientRegistry`; `TabletAppClientRenderer` |
| `lib.client.render` | `AreaHighlightRenderTypes`; `DiagramDataSource`; `DiagramForceData`; `DiagramScreenAccess` *(library bridge)*; `PhysicsGogglesOverlayRegistry` (`Context`, `Renderer`); `SimulatedDiagramMiniRenderer`; `SubLevelClientRenderApi` |

## Lifecycle checklist

### Common setup

- Register graph definitions and executors under your namespace.
- Register SCM modes and probe factories.
- Register display sources and tablet app definitions.
- Register kinetic guard exceptions only when the compatible block needs one.
- Keep any registration handle or ID needed for later removal.

### Client setup

- Register tablet renderers and physics-goggles overlays.
- Create mini diagram renderers from the owning screen, not common setup.
- Keep all client render imports behind a client-only class boundary.

### Runtime

- Resolve saved targets through the root/SubLevel APIs.
- Treat unloaded bodies and missing block entities as normal unavailable state.
- Reuse topology caches and per-tick snapshots.
- Claim SCM control before writing outputs.
- Heartbeat dock reservations and release them on every exit path.
- Validate players, targets and declared actions again on the server.

### Cleanup

- Close `SableSubLevelResidency.Lease` and `SableYawJointApi.Joint` owners.
- Release SCM authority and dock scheduler requests.
- Clear graph event schedulers owned by removed runtimes.
- Unregister dynamic overlays, display sources, apps, modes, probes and guard exceptions.
- Shut down server-owned schedulers at the correct server lifecycle event.

## Compatibility and failure behaviour

### Unloaded SubLevels

A missing, removed or unloaded SubLevel is normal. Use the empty, unavailable or `loaded=false` result supplied by the API. Retry from a real tick, load or connection event when the feature can continue later. Do not force-load a ship just to complete an optional UI or control query.

### Invalid data

Maths and physics APIs sanitize non-finite numeric input where documented. Graph, tablet, display and client overlay registries reject normal registration conflicts. SCM mode and probe registration replaces the entry under the same ID, so never register over another mod's namespace. Parsers may return an unavailable value for malformed saved data. Do not replace those outcomes with guessed defaults that could target another body or control.

### Optional mods

Keep optional-mod integration in one adapter. Check the mod is loaded before calling it and do not expose its classes through your public contract. Aeroworks and AeroClaims hooks in this library already follow that boundary.

### Version changes

Patch and beta releases may add methods and types without breaking the `1.2.x` contract. A removed method, changed serialized ID or changed ownership rule requires a documented compatibility decision. Compile against the same library version you test at runtime.

## Jar layout

- `gadgetsngizmos-<version>.jar` is the reusable library mod.
- `createthrusters-<version>.jar` is the Gadgets & Gizmos addon and requires the library jar.
- `Create-Gadgets-and-Gizmos-<version>.jar` is the distribution wrapper containing both jars as separate NeoForge Jar-in-Jar entries.

The addon source set may depend on the library source set. The library source set must never import addon classes. If a reusable integration needs addon knowledge, add a typed contract, callback, registry or value object to the library and make the addon implement it.
