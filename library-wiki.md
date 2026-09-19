Welcome to the Gadgets-And-Gizmos-Library wiki!
# Gadgets & Gizmos Library API

`gadgetsngizmos` is the reusable API shipped with Gadgets & Gizmos. It gives other mods typed access to controller input, precise kinetics, Sable bodies, physics helpers, ACC graph extensions, Named Event transports, SCM controls, display frames, shipping reservations, tablet apps and shared client rendering.

This page specifies the supported `1.2.x` library surface. Import only the documented library packages. If an integration needs a missing capability, add a small reusable contract to the library instead of reaching across a project boundary.

## Contents

- [Supported versions](#supported-versions)
- [Adding the library](#adding-the-library)
- [API rules](#api-rules)
- [Foundation and lifecycle](#foundation-and-lifecycle)
- [Block entity probes and lookup](#block-entity-probes-and-lookup)
  - [Port presentation groups](#port-presentation-groups)
- [Controllers and orientation](#controllers-and-orientation)
- [Discovery, SubLevels and menus](#discovery-sublevels-and-menus)
- [Kinetics and bearing heads](#kinetics-and-bearing-heads)
- [Virtual kinetics and alternators](#virtual-kinetics-and-alternators)
- [Sable physics](#sable-physics)
- [Client rendering](#client-rendering)
- [ACC graph extensions](#acc-graph-extensions)
- [Named Event transports](#named-event-transports)
- [Display integration](#display-integration)
- [SCM integration](#scm-integration)
- [Shipping](#shipping)
- [Tablet apps](#tablet-apps)
- [Complete public surface](#complete-public-surface)
- [Lifecycle checklist](#lifecycle-checklist)
- [Compatibility and failure behaviour](#compatibility-and-failure-behaviour)

## Supported versions

| Component | Supported version |
| --- | --- |
| Library mod ID | `gadgetsngizmos` |
| Library API range | `1.2.x` |
| Current artifact label | `V1.2.x` |
| Minecraft | `1.21.1` |
| Java | `21` |
| NeoForge | `21.1.225` or newer |
| Create | `6.0.10` or newer |
| Sable | `2.0.3` or newer compatible version |
| Simulated | `1.2.1` or newer compatible version |
| Aeroworks | Optional, `1.2.11` or newer when installed |
| AeroClaims | Optional, `0.9.0` or newer when installed |
| Synaxis | Optional, `1.5.0` with LDLib2 `2.2.17` when using its G&G bridge |

The loader version and artifact label can differ while a beta is being built. Depend on the loader-facing `1.2.x` API range unless a release says otherwise.

## Adding the library

Use the standalone library jar while developing another mod. Do not compile against an unrelated project artifact just to reach implementation classes.

```groovy
dependencies {
    compileOnly files("libs/gadgets-and-gizmos-lib-<version>.jar")
    runtimeOnly files("libs/gadgets-and-gizmos-lib-<version>.jar")
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

Replace the old standalone library jar with `gadgetsngizmos`; never keep both copies installed. Update loader dependencies to `gadgetsngizmos` and use the renamed public entry types shown below. Deprecated Java aliases remain so already-compiled beta integrations can still resolve their old class and method owners where the loader dependency allows them to start. The library also removes its legacy Sable residency ticket while retaining the renamed ticket.

## API rules

### Package boundary

- Import supported types below `com.rieno.gadgetsandgizmos.lib`.
- Never import `com.rieno.gadgetsandgizmos.lib.mixin`.
- Bootstrap and event subscriber classes are lifecycle wiring. NeoForge calls them; another mod should not.
- Treat anything outside the documented `lib` packages as an implementation detail of its owning project.

### Stable IDs and saved data

Use your own namespace for every registered graph node, tablet app, display source, SCM mode, probe factory and guard exception.

```java
ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
        "your_mod", "steerable_nozzle");
```

Treat IDs, serialized enum values, NBT keys and saved target references as persistent data. Do not build an ID from a translated label or a Java class name.

### Ownership and copying

- Documented snapshots and normal registry views are immutable unless their contract says otherwise. Do not assume an early-return empty value is immutable without checking its method contract.
- `GraphValue`, display envelopes and tablet helper values copy mutable payloads where documented. A `TabletStorage` provider owns the copying and atomicity guarantees of its implementation.
- Keep the UUID or registration ID needed to release anything you claim.
- Close leases and native handles. Unregister dynamic integrations when their owning mod or runtime feature goes away.
- Do not retain a live `BlockEntity`, `Level` or `ServerSubLevel` as saved identity. Store the supplied stable target value instead.

### Registration behaviour

| Registry | Normal registration |
| --- | --- |
| Graph definitions | Keeps an equal existing definition and rejects a non-equal definition under the same ID |
| Graph runtimes | Keeps the exact same executor object and rejects a different executor under the same node type |
| Block entity data adapters | Keeps one registration per adapter object, evaluates higher priorities first and has no unregister operation |
| ACC display sources | Rejects a block that already has a source; `registerIfAbsent` reports the conflict |
| ACC display connections | Keeps separate source and target predicate maps; strict registration rejects a duplicate ID within the selected role |
| Tablet apps | Rejects a duplicate; replacement must use `registerOrReplace` |
| Tablet client renderers | Rejects a duplicate; `registerIfAbsent` reports the conflict |
| Physics goggles overlays | Rejects a duplicate; replacement must use `registerOrReplace` |
| SCM control modes | Replaces the entry under the same ID |
| SCM probe factories | Replaces the entry under the same ID |

Never replace an entry outside your namespace. Prefer the strict registration method when the registry supplies one.

### Logical side and thread

- Physics, topology, control authority, shipping and tablet storage are server-side systems.
- Client render packages must only be loaded from client setup or another client-only class.
- Registry mutations belong in mod setup unless the registry explicitly supports a dynamic integration. Graph and block entity adapter registries are process-global and have no unregister operation.
- Run level and block entity mutations on the owning game thread.
- Per-owner schedulers, tablet client sessions and mini renderers are not synchronized. Use them only from their owning runtime or client thread.
- A common/server class must not eagerly reference a client-only implementation type.

### Reflection

Required APIs are direct typed calls. Do not wrap Create, Sable, Simulated, NeoForge or this library in reflection just to avoid a dependency or import.

Reflection is only appropriate for a genuinely optional dependency, a supported upstream version split without one stable API, or unavoidable private upstream access. Keep new reflection inside one compatibility adapter, cache its lookups, expose a typed result and report a real failure. The bearing section documents one current compatibility path whose return value is weaker than proof of a successful write.

## Foundation and lifecycle

Package: `com.rieno.gadgetsandgizmos.lib`

| Type | Use |
| --- | --- |
| `GadgetsNGizmosLibrary` | Supplies `MOD_ID` (`gadgetsngizmos`) and `LEGACY_MOD_ID` (`createthrusterslib`) |
| `GadgetsNGizmosLibraryNeoForge` | NeoForge server/common entry point; lifecycle-owned |
| `PhysicsStaffPowerEvents` | NeoForge event bridge for Physics Staff operation cleanup; lifecycle-owned |

Package: `com.rieno.gadgetsandgizmos.lib.client`

| Type | Use |
| --- | --- |
| `GadgetsNGizmosLibraryClientBootstrap` | Installs the library client hooks and shaders; lifecycle-owned |
| `GadgetsNGizmosLibraryClientNeoForge` | NeoForge client entry point; lifecycle-owned |

`GadgetsNGizmosLibraryConfigs` owns `server` settings in `gadgetsngizmos-server.toml`. Held angles, precise angle propagation and virtual kinetic propagation default to true; kinetic guard logging defaults to false. Read the settings when a compatible feature needs to respect them. Do not call its registration method or write the library config from another mod.

Three startup system properties can disable related mixin groups before config is available: `gadgetsngizmos.disableHeldAngleMixins`, `gadgetsngizmos.disablePreciseAngleMixins` and `gadgetsngizmos.disableVirtualKineticMixins`, with legacy `ct.*` equivalents. The precise opt-out covers the five shared Create/Simulated precise mixins, but not the separately selected Aeroworks compatibility mixin. Aeroworks compatibility is selected only when Aeroworks is installed. The required mixin config uses a default injector requirement of one, so an unexpected ordinary target change can fail startup rather than silently disabling required behavior.

The old `CreateThrustersLibrary`, NeoForge class owners, client bootstrap, `CTLibraryConfigs` and `CTKineticGuard` names remain as deprecated compatibility aliases. `CreateThrustersLibrary` aliases both IDs, the old client bootstrap delegates, and the deprecated NeoForge constructors are inert compatibility owners rather than annotated entry points. New integrations must use the Gadgets & Gizmos names and depend on `gadgetsngizmos`.

The common entry registers the server config and bootstraps current and legacy residency ticket types. Automatic events tick `PhysicsStaffPowerTracker` after server ticks and begin its shutdown when the server stops. The client entry registers the shader listener, and the package-private topology bridge observes plot block/chunk and root-level unload events. A consumer does not add the mixin config or call either bootstrap itself. SCM authority cleanup and dock-scheduler shutdown remain consumer lifecycle responsibilities.

### Physics Staff compatibility

Package: `com.rieno.gadgetsandgizmos.lib.compat`

`PhysicsStaffPowerHooks` recognizes exactly `createthrusters:physics_staff` and uses 1,024 Backtank units per tank. A Physics Staff in the main hand wins selection; if it is unpowered, selection stops rather than falling through to the offhand. Creative players authorize without pressure and report unlimited pressure.

`authorizeAction(...)` can send a missing-Backtank or out-of-pressure action-bar failure. `consumeAir(entity, amount)` succeeds for non-positive amounts and creative players. When requested pressure exceeds the available amount it can consume all remaining pressure and still return false because the full request was not satisfied. Persistent item custom data uses `PhysicsStaffId`, `LockedCount`, `PressureCurrent` and `PressureMax`.

`PhysicsStaffPowerTracker` is overworld `SavedData` named `createthrusters_physics_staff_power`. It saves staff owner and dimension-scoped lock UUIDs, and serializes fractional pending drain when another state change has marked the data dirty; drain-only changes do not themselves guarantee a save. Active drag is transient. Offline owners retain locks but lose drag, while a missing staff or failed/depleted pressure releases live state. `beginShutdown()` stops drag and detaches live lock handles while retaining saved lock ownership for reload.

`PhysicsStaffPowerHooks.getTotalAir*()` reads the first Backtank returned by Create despite the method name. Drag drain per second is `0.25 + 0.12 * max(1, sqrt(max(mass, 0)))`; lock drain is `0.10 + 0.05 *` the same mass factor. These formulae do not repair NaN mass.

`PhysicsStaffInteractionGuard` reference-counts protected initialization SubLevels. Release every retained set symmetrically. Initialization protection applies before powered-staff checks; a powered staff is also denied on the body carrying its player and, when AeroClaims is present, where the active claim denies access. Supply a non-null target UUID for powered checks when AeroClaims is loaded. Failure messages share a one-second per-player cooldown.

### Bundled supporter head stacks

Package: `com.rieno.gadgetsandgizmos.lib.item`

`BundledSupporterHeadStack` stores a packaged texture resource on an ordinary `minecraft:player_head`. `create(texture, displayName)` requires both values, creates the stack and copies its item-name component. `setTexture(...)` requires a non-null player-head stack and texture, and writes the texture under the `gadgetsngizmos:bundled_player_head` custom-data compound using the `Texture` key. Another item is rejected with `IllegalArgumentException`.

`texture(...)` returns the parsed resource ID or `null` for a null stack, another item, missing data or a malformed ID. This helper does not create a game profile or download a skin; the renderer consuming the stack owns the packaged texture lookup.

## Block entity probes and lookup

Package: `com.rieno.gadgetsandgizmos.lib.probe`

### Typed data adapters

Use `BlockEntityDataAdapter<T>` when the target block entity cannot implement the library provider interface directly. An adapter declares its `targetType()`, optional instance test, typed `BlockEntityDataPort` list and read/write operations.

`BlockEntityDataPort` contains a stable ID, graph value type, `READ`, `WRITE` or `READ_WRITE` access and optional editor values. Blank IDs are rejected, a blank type becomes `any`, a null access becomes `READ` and option lists are copied. Use `readable(...)`, `writable(...)` or `readWrite(...)` for ports without options.

Register adapters once during common setup:

```java
BlockEntityDataAdapterRegistry.register(100,
        new BlockEntityDataAdapter<MyBlockEntity>() {
            @Override
            public Class<MyBlockEntity> targetType() {
                return MyBlockEntity.class;
            }

            @Override
            public List<BlockEntityDataPort> ports(MyBlockEntity target) {
                return List.of(BlockEntityDataPort.readWrite(
                        "target", "number"));
            }

            @Override
            public GraphValue read(MyBlockEntity target, String port) {
                return "target".equals(port)
                        ? GraphValue.number(target.target()) : null;
            }

            @Override
            public boolean write(MyBlockEntity target, String port,
                                 GraphValue value) {
                if (!"target".equals(port)) return false;
                target.setTarget(value.asNumber());
                return true;
            }
        });
```

Higher priorities are tested first and only the first matching adapter is used. A block entity's own `BlockEntityDataProvider` declaration takes precedence; adapter ports fill only missing IDs and missing writable options. For a non-null target, published metadata maps are immutable. The null-target early return is an empty local map and should not be retained or mutated as shared state. Registering `null` or the same adapter object twice is ignored. There is no adapter unregister or snapshot API, and adapter/provider exceptions are not isolated by the registry.

### Port presentation groups

`BlockEntityDataPortGroups.group(ports)` derives immutable map-shaped presentation groups from a readable or writable `{ portId -> graphType }` map. It never changes the supplied map or its leaf IDs: use the original leaf ports for graph execution and saved-data compatibility, and use the returned groups only when a UI or graph editor wants to present related fields together.

The helper considers these groups in order: `position` (`x`, `y`, `z`); `inventory` (`items`, `item_count`, `item_capacity`, `item_fill`); `fluids` (`fluids`, `fluid_amount`, `fluid_capacity`, `fluid_fill`); `energy_status` (`energy`, `energy_capacity`, `energy_fill`); `movement` (the directional axes, `magnitude` and `active`); ports whose IDs contain a rotation term such as `angle`, `yaw` or `roll`; then remaining IDs sharing the prefix before their first underscore. A group is returned only when it has at least two unclaimed leaves. Each leaf occurs in at most one group, and entries retain their original IDs and graph types.

Null, blank and incomplete port entries are ignored. The returned outer map and every nested group map are immutable. Generated group IDs avoid collisions with real leaf IDs and with earlier generated groups by using a `_data` suffix when required.

### `BlockEntityDataProvider`

Implement this on a block entity that exposes detailed named data without making the caller import its implementation class.

- `graphReadableData()` publishes readable field IDs and value types.
- `graphWritableData()` publishes writable field IDs and value types.
- `graphWritableOptions()` optionally supplies allowed values or editor hints.
- `readGraphValue(...)` resolves one field.
- `writeGraphValue(...)` validates and applies one mutation.

Field IDs are part of your integration contract. Keep them stable after release.

Provider maps and results are host-owned. Return stable, non-null metadata and perform mutation validation inside `writeGraphValue(...)`.

### `ConnectedBlockEntityProvider`

Implement `connectedBlockEntities()` when a mechanism can expose its live related block entities. Return only loaded, current connections. The caller must handle an empty collection when a carriage, rope endpoint, bearing head or another member is unloaded.

This is the normal probe for questions such as "which carriages belong to this shaft" or "which body is connected to this mechanism". Do not expose the mechanism's private block entity type through the library contract.

### `BlockEntityLookupApi`

Use this instead of duplicating root/SubLevel traversal.

| Method | Behaviour |
| --- | --- |
| `findIncludingSubLevels` | Finds a block entity at a world-facing position in the root level or a loaded SubLevel |
| `resolveIncludingSubLevels` | Resolves the internal position and owning SubLevel ID |
| `find` | Uses an optional saved SubLevel ID and can fall back to the root scope |
| `findExact` | Resolves only the requested scope |
| `findLoadedExact` | Resolves only an already-loaded target and never requests a load |
| `findInSubLevel` | Looks up one typed block entity inside a known SubLevel |

`BlockEntityLookupApi.ResolvedBlockPosition` keeps the resolved `BlockPos` and owning SubLevel UUID together. Keep both parts when a later packet or menu action must target the same body.

`find(...)` can request a short-lived Sable load and may fall back to the root scope when a saved SubLevel cannot resolve and the position is not a plot coordinate. `findExact(...)` may request a load but never falls back. `findLoadedExact(...)` neither requests a load nor falls back. `resolveIncludingSubLevels(...)` returns `(BlockPos.ZERO, null)` for null inputs and otherwise falls back to the supplied position in root scope when traversal finds nothing.

## Controllers and orientation

Package: `com.rieno.gadgetsandgizmos.lib.control`

### Analogue input

| Type | Contract |
| --- | --- |
| `AnalogueChannel` | One configurable value with mode, limits, rate, smoothing, debounce, repeat, step and NBT state |
| `AnalogueAxis` | Combines negative and positive channels into a signed axis |
| `AnalogueChannelMode` | Channel behaviour enum constants `MOMENTARY`, `RAMP`, `STEP`, `LATCH` and `DIRECT` |
| `AnalogueControlChannel` | Canonical named controller channels used by Gadgets & Gizmos integrations |
| `AnalogueSignalPacket` | Serializable channel update value |
| `AnalogueTransmissionTarget` | Receiver contract for transmitted analogue updates |
| `ControllerBindingOwner` | Stable `user` or `graph` owner of a saved custom binding |
| `FrequencyBinding` | Persistent Create Redstone Link frequency pair |
| `CustomKeyEntry` | Persistent user-defined controller key entry |

Use the channel and axis types for deadzones, rise/fall rates, smoothing and input state. Do not reproduce those calculations in a screen, peripheral or block entity.

`AnalogueChannelMode` contains `MOMENTARY`, `RAMP`, `STEP`, `LATCH` and `DIRECT`. A new channel defaults to `RAMP`, rise/fall rates of `0.08`, a `0.1` step, no deadzone, smoothing or debounce, reset-to-zero enabled, repeat disabled and a four-tick repeat interval. Signed channels default to `[-1, 1]`; unsigned channels default to `[0, 1]`. Setter validation is property-specific and does not provide blanket NaN repair.

`press(...)` and `release(...)` reject duplicate edges and respect debounce. `STEP` advances and can repeat while held, `LATCH` toggles, `DIRECT` changes immediately and `MOMENTARY`/`RAMP` use the rate and smoothing path. `reset()` clears live state but retains debounce history. `AnalogueAxis` combines positive minus negative unsigned channel values, then applies its own deadzone and smoothing.

`AnalogueControlChannel` defines pitch up/down (`pitch`, positive/negative, `RAMP`), roll left/right (`roll`, negative/positive, `RAMP`), yaw left/right (`yaw`, negative/positive, `RAMP`), throttle up/down (`throttle`, positive/negative, `STEP`), stabilize (`stabilize`, positive, `LATCH`), strafe left/right (`strafe`, negative/positive, `RAMP`) and lift up/down (`lift`, positive/negative, `RAMP`). These retain their existing `createthrusters.*` translation keys. `byId(...)` trims/lowercases and returns `null` for an unknown ID.

`AnalogueSignalPacket` clamps ordinary finite values to `[-1, 1]`, normalizes null string fields to empty strings, leaves the supplied game tick unchanged and exposes `STREAM_CODEC`; NaN is not repaired by its clamp. `AnalogueTransmissionTarget.reset(...)` sends zero on `channelId()`.

`CustomKeyEntry` is intentionally mutable. A blank constructor ID becomes a random UUID. Defaults include key codes `-1`, label `Custom`, `RAMP`, rise/fall `0.08`, step up/down `0.1`, smoothing `0.2`, binding preset `none` and owner `USER`. It writes a lower-case mode. `fromTag(...)` returns null without a non-blank ID and normalizes invalid modes and owners; `ControllerBindingOwner.fromId(...)` maps null/blank/unknown values to `USER`, while loaded numeric fields remain unvalidated.

### Controller persistence

`AnalogueChannel` writes its mode using uppercase `name()`. `readFromTag(...)` ignores stored `Id` and `Signed` because constructor identity wins, does not restore edge/repeat timestamps, and falls back to `RAMP` for an invalid or case-mismatched mode. `AnalogueAxis.readFromTag(...)` likewise ignores stored `Id`. `ControllerMechanicBinding` also persists an uppercase mode and returns null from `fromTag(...)` for a null/empty tag or unknown mechanic; an invalid mode becomes `RAMP`.

Loaded channel `Min`, `Max` and `StepAmount` values are assigned directly instead of passing through their public setters, so malformed saved numbers are not fully range-normalized.

The channel schema includes `Id`, `Signed`, `Mode`, `Min`, `Max`, `RiseRate`, `FallRate`, `StepAmount`, `Deadzone`, `Smoothing`, `DirectValue`, `TargetValue`, `ResponseValue`, `FilteredValue`, `Value`, `DebounceTicks`, `RepeatIntervalTicks`, `ResetToZero`, `RepeatWhileHeld`, `Pressed` and `Latched`. The axis schema uses `Id`, `Deadzone`, `Smoothing`, `FilteredSignedValue` and `SignedValue`.

`AnalogueSignalPacket` uses `ChannelId`, `Value`, `GameTick`, `SourceId` and `SourceType`. `FrequencyBinding` uses `ChannelId` plus optional `First` and `Second` item compounds. A direct target uses optional `TargetId`, `TargetTypeId`, `GroupId`, `Label`, `CompatModeId`, `SubLevelId` and `BlockPos`.

`ControllerMechanicBinding` uses `ChannelId`, `Mechanic`, `PositiveAxis`, `TranslationKey`, `Mode`, `KeyCode`, optional `LocalOutputSide`, `FrequencyBinding` and optional `DirectTarget`. `CustomKeyEntry` uses `Id`, `KeyCode`, `StepDownKeyCode`, `Label`, `Alias`, lower-case `Mode`, `RiseRate`, `FallRate`, `StepAmount`, `StepDownAmount`, `Deadzone`, `Smoothing`, `LocalSide`, optional `First`, `Second`, `InputFirst`, `InputSecond`, `DirectTarget` and `InputTarget`, plus `BindingPreset` and `Owner`.

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

`ControllerDirectTargetReference` stores a stable root/SubLevel target without retaining a live block entity. Strings are trimmed, positions are copied and the reference is considered bound when its target ID is non-empty; `fromTag(...)` returns `null` without that ID. `withCompatMode(...)` returns the same value when unchanged.

`ControllerMechanic` contains `PITCH`, `ROLL`, `YAW`, `THROTTLE`, `STRAFE`, `LIFT` and `CUSTOM`, with lower-case IDs. `ControllerMechanicBinding` describes one mechanic's channel, translation, mode, key, optional local side, frequency and direct target. It copies its `FrequencyBinding` at construction, and `frequencyBinding()` returns a fresh copy on every call. `FrequencyBinding` copies both item stacks, forces count one and is bound when either half is present.

`FaceBoundSignalRoute` binds a proxy position and face to only the block behind that face. Its position is immutable-copied, both constructor values are required, `attachedPos()` returns `proxyPos.relative(face.getOpposite())` and `matches(null)` is false.

### Directional input

`DirectionalAnalogSource` supplies local input. `DirectionalAnalogMath.fromLocal(...)` uses circular/Euclidean magnitude; `fromSquareLocal(...)` uses the greatest axis magnitude. Both clamp local axes, remove and rescale the deadzone, and return `DirectionalAnalogSnapshot.ZERO` inside it. Positive local Z is forward, negative Z is backward, positive X is left and negative X is right.

`DirectionalAnalogSnapshot` carries the resolved values and rounds each unsigned component to redstone `0..15`. `DirectionalAnalogComponent` selects `forward`, `backward`, `left` or `right`; `sample(null)` uses the zero snapshot and `fromId(...)` uses the supplied fallback, or `FORWARD` when that fallback is null. Use these types together so controller screens, blocks and peripherals agree on diagonal input.

### Orientation

| Type | Contract |
| --- | --- |
| `OrientationPayload` | Timestamped orientation value with radian angles, derived degree accessors, direction, live state and game tick |
| `OrientationTarget` | Consumer of an orientation target |
| `LinkedOrientationSource` | Producer that can be linked to a target |
| `OrientationMath` | Shared orientation conversion and normalization helpers |
| `CardinalTiltController` | Resolves a facing direction and cardinal pulls into a clean tilt direction |

Angles in the control maths package are radians unless a method explicitly says degrees. `OrientationPayload` does not define a codec or NBT format. `OrientationTarget.canAcceptOrientationPayload(...)` defaults to true, while `LinkedOrientationSource.getLinkedAnglesRadians()` is implementer-owned and is not defensively copied by the interface.

`OrientationMath.directionFromAngles(x, z)` normalizes `(tan(z), 1, tan(x))`. `applyDeadzone(...)` uses a strict `abs(value) < deadzone` comparison. `clamp(...)` does not swap reversed bounds, and the orientation helpers do not promise finite output for non-finite input.

`CardinalTiltController.fromDirection(...)` normalizes a meaningful direction into positive north (`-z`), south (`+z`), east (`+x`) and west (`-x`) pulls and otherwise returns `CardinalPulls.ZERO`. `positiveComponent(...)` returns zero for null/near-zero input or a vertical direction. `normalizeMagnitude(...)` returns zero for a maximum at or below `1e-6` and otherwise clamps the absolute ratio to `[0, 1]`.

`resolve(...)` combines a facing normal times neutral bias with raw east-west and south-north pulls, returning the raw facing normal inside the deadzone and a normalized direction outside it. `CardinalPulls` supplies `ZERO`, component addition and horizontal lookup. The builder ignores non-positive single-direction and vertical amounts, but `add(CardinalPulls)` accumulates raw values, including negatives. Null facing/pull inputs are not accepted.

### Hardware input

Package: `com.rieno.gadgetsandgizmos.lib.control.hardware`

`HardwareControllerState` is an immutable device snapshot with copied arrays and safe axis/button access. Missing, out-of-range or non-finite axes read as zero; missing buttons read as false. `HardwareControllerBindings` owns the case-sensitive `hardware:*` binding IDs, standard device layout, conventional channels and deadzone application. It exposes an immutable option list, accepts raw axis IDs `0..15` and raw buttons `0..31`, and defaults to a `0.12` deadzone. Use these IDs when a controller UI needs to save the same hardware binding format.

`options()` contains 69 entries: six standard axes, 15 standard buttons, 16 raw axes and 32 raw buttons. `values(null)` returns an empty immutable map. Standard axes 0–3 and raw axes are deadzone-rescaled, standard triggers 4–5 are remapped to `[0, 1]`, and buttons become zero/one. `conventionalChannels(...)` always returns the 13 canonical channels, preferring a standard alias before raw fallback and sanitizing retrieved values.

### Control maths

Package: `com.rieno.gadgetsandgizmos.lib.control.math`

| Type | Contract |
| --- | --- |
| `PidControllerMath` | Stateless integral-step and anti-windup calculation |
| `LqrControllerMath` | Linear quadratic regulator calculations |
| `AdrcControllerMath` | ADRC calculations and state |
| `AdrcControllerNthOrderMath` | Nth-order ADRC calculations and state |
| `Vector3` | Small immutable three-component maths value |
| `Quaternion` | Immutable quaternion maths value |
| `RotationMath` | Quaternion, Z-X-Z Euler and X-Y-Z Tait-Bryan conversions |

Validation and non-finite handling are method-specific. For example, quaternion normalization maps a non-finite or near-zero length to `Quaternion.IDENTITY`, while vector values, orientation conversion, LQR inputs and arbitrary servo configuration values are not comprehensively sanitized. Supply finite values unless the individual method documents a fallback.

Keep one caller-maintained PID integral or ADRC state per controlled system; sharing it between unrelated targets shares accumulated error or disturbance estimates. LQR vector control requires equal state/gain lengths. `AdrcControllerNthOrderMath.State.z()` returns a copy, but direct record construction does not copy the caller's array; prefer its factories when creating state.

`PidControllerMath.nextIntegral(...)` uses at least a ±1 safety range, applies requested anti-windup bounds only when enabled, swaps reversed finite bounds and maps a NaN sum to zero before the final clamp; a NaN safety limit can still poison the returned value. `LqrControllerMath.control(...)` computes feed-forward plus gain times target error and clamps against finite-fallback bounds; vector length mismatch throws `IllegalArgumentException`.

`AdrcControllerMath.step(...)` initializes a missing state from the measured value, clamps/falls back time step and bandwidths and protects a near-zero plant gain. The nth-order variant clamps order to at least one during `step(...)` and resets a missing/wrong-sized state. Neither family comprehensively repairs non-finite target, actual or arbitrary caller-created state values.

## Discovery, SubLevels and menus

### Controller discovery

Package: `com.rieno.gadgetsandgizmos.lib.discovery`

`ControllerDiscoveryService.scanBlockEntities(...)` builds stable `ControllerDiscoveryNode` values for loaded targets. Use `classify(...)` or `classifyKind(...)` when a custom UI needs the same classification without running a complete scan. Classification is a fixed library mapping, not an extension registry: unmatched blocks resolve to `UNKNOWN`, and `classify(...)` returns `null` for them.

`ControllerDiscoveryKind` contains 24 stable IDs: `thruster`, `fan`, `bearing`, `vector_bearing`, `redstone_link`, `gyroscope_link`, `joystick`, `double_button`, `analog_lever`, `throttle`, `analog_transmission`, `gimbal_sensor`, `magnet`, `claw`, `steering_wheel`, `navigation_table`, `wheel_mount`, `kinetic`, `machine`, `display`, `display_adapter`, `linker_face_input`, `linker_face_output` and `unknown`. `byId(...)` trims and lowercases its input and returns `null` for an unknown ID. Translation keys are `createthrusters.analogue_controller.discovery.<id>`, with `.summary` appended for the summary key; hard-coded classification emits only the subset it recognizes.

Implement `INamedBlockEntity` when discovery should show a useful player-facing name. The name is a display value, not a persistent identity.

`ControllerDiscoveryNode` stores the stable node ID, kind, group, block ID, label, optional SubLevel UUID and immutable block position. Node IDs are `<blockId>@<BlockPos.asLong>#world` or `#<SubLevel UUID>`; blank groups become `world` or `sublevel:<UUID>`, and scans keep the first node per ID. NBT keys are `NodeId`, `Kind`, `GroupId`, `BlockId`, `Label`, optional `SubLevelId` and `BlockPos`; `fromTag(...)` returns `null` without `NodeId`. `asDirectTargetReference()` copies the stable target fields. Labels are presentation only.

`SubLevelBlockEntityCollector` is the compatibility collector for loaded Sable bodies and short-lived lazy-load operations. For SubLevel/plot targets, its ensure methods can process Sable holding changes and briefly ticket a matching root chunk, but still return unavailable when the body/chunk remains unusable. With a null SubLevel ID at a non-plot root position, `ensureTargetLoaded(...)` returns true without testing root chunk load; use `isTargetLoaded(...)` when that distinction matters. `getBlockEntities(...)` merges actors and loaded-chunk block entities by position.

### Root and SubLevel ownership

Package: `com.rieno.gadgetsandgizmos.lib.physics`

`SableLevelApi` is the typed ownership boundary:

- `serverLevel(...)` resolves the root `ServerLevel` from a root or Sable level.
- `containing(...)` finds the loaded body containing a block, entity or precise position.
- `containingId(...)` returns its stable UUID.
- `tracking(...)` returns the body currently carrying an entity.
- `subLevel(...)` resolves one usable body. `subLevels(...)` copies the upstream collection and does not filter removed entries.

The methods call the required Sable API directly. `serverLevel(...)` falls back to the server overworld when the source dimension cannot be resolved. An empty result means the target is absent or unavailable, not that reflection silently failed.

### Connected body dependencies

`SubLevelConnectionApi.resolve(...)` resolves one live SubLevel by UUID. `merge(...)` combines dependency sets and deduplicates by object identity rather than UUID/equality. `connectedTo(...)` reads bodies published by connected block entities. These direct Sable calls are not exception-isolated.

Use it when implementing `BlockEntitySubLevelActor.sable$getConnectionDependencies`:

```java
@Override
public Iterable<SubLevel> sable$getConnectionDependencies() {
    return SubLevelConnectionApi.connectedTo(connectedBlockEntities());
}
```

Only publish real current links. Returning every nearby SubLevel makes unrelated assemblies one topology.

### Assembly and residency

`SubLevelAssemblyApi` assembles an explicit set of blocks, assembles one block and disassembles a live body through typed Sable/Simulated calls. `AssemblyResult` reports the created body and an immutable moved-block offset. Null or empty selections return no body. `prepareCreateContraptions(...)` is the supported route to the internal assembly invoker; it no-ops for missing/empty input. Valid calls invoke upstream APIs directly and let runtime failures propagate; `assembleBlock(...)` can also throw Create's `AssemblyException`.

`SableSubLevelResidency` is in `lib.discovery`. `lease(...)` rejects a blank owner, and `retain(...)` tracks one live `ServerSubLevel` only after its native ticket is added. `synchronize(...)` attempts to match a collection; a failed native add leaves that requested body unretained. `detach()` permanently closes the Java lease while deliberately leaving native tickets installed; `close()` attempts current and legacy ticket removal. Native ticket runtime/linkage failures are swallowed, and close/detach clear local tracking regardless. `bootstrap()` is lifecycle wiring.

### SubLevel-aware menus

Package: `com.rieno.gadgetsandgizmos.lib.menuconfig`

| Type | Contract |
| --- | --- |
| `MenuConfigTarget` | Block position plus optional SubLevel UUID |
| `MenuOpenHeader` | Encodes and decodes the target in menu opening data |
| `MenuBackedBlockEntityTarget<B>` | Menu contract exposing its typed target |
| `MenuBackedBlockEntityResolver` | Resolves the target in a server payload handler |
| `ISimulatedMenuOpen` | Marks a menu using the extended simulated target |

`MenuConfigTarget.STREAM_CODEC` and `MenuOpenHeader` use the same wire order: `BlockPos`, a presence boolean and an optional SubLevel UUID. `ISimulatedMenuOpen` only declares `readExtraOpenData(...)`; the menu implementation still reads the shared header.

`MenuBackedBlockEntityResolver.resolveOpenMenu(...)` accepts only the currently open menu of the requested class whose position and optional SubLevel UUID exactly match the payload, then returns that menu's typed block entity. `resolve(...)` tries that authenticated menu first and permits a direct root-level block entity fallback only when the target has no SubLevel UUID. Neither method performs reach, permission or action validation; the server payload handler still owns those checks.

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

`KineticAngleHelper.publishRotationAngle(...)` returns false when the guard rejects the target, the block is not rotational, held-angle access is unavailable or the normalized value did not change. Held/published lookup returns `NaN` without an active held angle. Absolute lookup returns the held motion angle when present and otherwise resolves Create's live game-time, speed, local checkerboard/cog phase and block-entity phase. `getKineticRotationAngleDegrees(...)` exposes that non-held calculation directly and falls back to zero without a level or rotational block. Degree normalization uses `(-180, 180]`, so `-180` becomes `180`; held changes below `0.05` degrees are unchanged.

Both graph helpers return `ApplyResult` values containing the claimed target count and non-negative stress sum. `HeldAngleKineticGraph` mutates the caller's claimed-target set, traverses all compatible connected neighbours and stops at every precise-output boundary. A null/already-claimed seed releases that graph's previous ownership; a stale target already claimed by another output is not cleared. `PreciseKineticOutputGraph` owns its target set, starts with and permits a boundary source, follows only downstream neighbours whose `source` points to the current block and stops at downstream boundaries.

The built-in exact-angle publishers cover Create hand cranks, valve handles and sequenced gearshifts plus Simulated steering wheels. The optional Aeroworks mixin publishes its servo output directionally. Simulated Torsion Springs already own an exact accumulated output angle through their native API, so consumers should read that existing output rather than adding another publisher. Downstream blocks should read `getHeldRotationAngleDegrees(...)` first or call `getAbsoluteRotationAngleDegrees(...)` for the held-or-live result; they must not treat `getRotationAngleOffset(...)` as an accumulated physical angle.

Both clear stale targets after a normal apply, but precise apply returns `EMPTY` without clearing when its source has no level, and `clear(null, ...)` also retains its internal ownership. Held clear requires a non-null level when it owns targets. Ownership retains the exact kinetic object as well as its position so Simulated extra-kinetic shafts are cleared instead of accidentally clearing only their parent block entity. Keep each graph instance tied to one level/output owner. Directional access has one graph per kinetic owner, so applying a later face replaces or clears stale ownership from the previous face rather than maintaining independent per-face graphs.

Mixin-supplied precise apply/clear facades are server-only void operations and no-op without a level or on the client. Directional apply with a null output face is also a no-op that retains previous ownership. A non-null face whose adjacent target is not kinetic performs an empty graph apply and clears previous directional ownership.

`KineticGraphHelper` caches private Create reflection. Missing methods or invocation failure return empty neighbours or a null modifier, which is intentionally indistinguishable from no traversable connection.

`GadgetsNGizmosKineticGuard` owns configuration-aware package guards used by the library mixins. A disabled feature setting is absolute; an exception does not re-enable it. Exceptions only bypass the built-in exclusion for classes below `dev.simulated_team.simulated.content.blocks.swivel_bearing`; other classes already pass when enabled. A blank key throws `IllegalArgumentException`, a null predicate throws `NullPointerException`, keys are stripped/case-sensitive and an equal key replaces. `RuntimeException`/`LinkageError` from a predicate is optionally logged and treated as no match.

Held-angle persistence writes `CTHeldAngleMask` and `CTAbsoluteRotationAngleMask` plus per-axis `CTHeldAngleX/Y/Z` and `CTAbsoluteRotationAngleX/Y/Z` floats. Reads prefer the absolute keys and fall back to legacy held keys. Disabling the guard clears or ignores loaded held state.

`SingleFaceRotationConfiguration` supplies one configured face and predicate. It exposes a shaft only when the predicate passes and the queried face is exactly that face; its rotation axis is always the configured face's axis. Supply usable non-null constructor values.

### Servo motion

`ServoMotionController` is the reusable bounded servo planner. It starts at angle and speed zero. Construct it with `ServoMotionController.ServoMotionConfig`, call `update(target)` once per active tick and read the current angle and generated speed. It replans after a meaningful target change, clamps proportional RPM to the configured maximum, advances toward the target and snaps/stops inside tolerance.

Use `snapTo(...)` for an immediate stopped position, `stop()` to clear planned motion and `applySyncedState(angle, speed)` when restoring client sync or persisted state. `ServoMotionConfig` performs no validation; supply finite, sensible non-negative limits.

### Bearing head access

`BearingHead.PRIMARY` uses ID `primary` and colour `0x00B7C8`; `SECONDARY` uses `secondary` and `0xF28C28`. The legacy names `cyan`, `left` and `top` resolve to primary; `orange`, `right` and `bottom` resolve to secondary. Unknown IDs return the caller's fallback.

Implement `BearingHeadAccess` when a bearing exposes one or more controllable heads. The contract covers:

- current, target and interpolated angle
- angle range and range updates
- direct target updates
- mounted block and mounted SubLevel identity
- assembly and disassembly state of the mounted head

Use `BearingAngleDriver` to scan from `origin.relative(direction)` for at most `maxSteps` and drive the first compatible downstream Create, Simulated swivel or generic bearing. It stops at a missing/non-kinetic block and passes through other kinetic block entities. The Mechanical path assembles a stopped bearing, writes both block-entity and moved-contraption angles, then synchronizes; the generic `IBearing` path writes and synchronizes. `BlockEntitySynchronizer` keeps that sync host-owned.

For the reflected Simulated swivel path, a detected compatible block currently returns true even when cached private fields are unavailable or an `IllegalAccessException` prevents the write. Treat that boolean as "a compatible swivel was selected", not proof that its angle changed; this is a compatibility limitation of the current API.

## Virtual kinetics and alternators

### Virtual kinetics

Package: `com.rieno.gadgetsandgizmos.lib.virtualkinetics`

| Type | Contract |
| --- | --- |
| `VirtualKineticProvider` | Real owner that exposes virtual kinetic members |
| `VirtualKineticBlockEntity` | One virtual member participating in the Create graph |
| `VirtualKineticHostBlock` | Host-state access for a virtual member |
| `VirtualKineticPos` | Owner position carrying a slot for virtual-member resolution |

`VirtualKineticProvider.ct$getVirtualKinetics()` must return a stable, non-null ordered list with no null entries. Slot order and `ct$getVirtualKineticSaveName(slot)` are persistent data; the default save key is `CTVirtualKinetic<slot>`, while a real kinetic source's selected slot is `CTVirtualKineticSourceSlot`. `ct$getVirtualKinetic(slot)` returns `null` outside the list and the count is the list size.

The library mixins propagate level, block state, invalidation, removal, state-switch detachment and client/server persistence to every member, which is why null list entries are unsafe. Neighbour exposure additionally requires a loaded position, enabled guard, `VirtualKineticHostBlock`, provider and non-null slot lookup; `ct$canExposeVirtualKinetics(...)` defaults true. `VirtualKineticBlockEntity.ct$getVirtualKineticRotationConfiguration()` supplies shaft/axis behavior instead of the host block configuration.

`VirtualKineticPos` inherits `BlockPos.equals(...)` and `hashCode()`, so its slot does not distinguish two instances used as ordinary map or set keys; carry it to the library resolution path and keep slot indices stable. `VirtualKineticBlockEntity` exposes parent/slot metadata, but graph/source resolution uses the provider list and `VirtualKineticPos` slot rather than validating those getters.

### Alternators

Package: `com.rieno.gadgetsandgizmos.lib.power.alternator`

Implement `AlternatorTuning` with minimum RPM, rated RPM, maximum FE per tick and maximum stress impact. `AlternatorKinetics.effectiveRpm(...)` uses absolute speed, coerces the minimum to at least one RPM, returns zero below it and caps generation RPM at a rated value no lower than the minimum. `generatedFePerTick(...)` floors proportional output after coercing maximum FE to at least one.

`stressBaseRatePerRpm(...)` divides a stress impact of at least one by a rated RPM of at least one. `stressAtSpeed(...)` multiplies that rate by absolute speed and is deliberately not capped above rated RPM. Null tuning fails, and non-finite inputs are not comprehensively repaired.

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

`SableSubLevelTelemetryApi.sample(...)` reads one already-loaded body without forcing a load. Its `Snapshot` separates `loaded` from `physicsAvailable` and exposes finite position, linear/angular velocity, speed and mass. `connectedSubLevelIds(...)` returns the root plus loaded, same-level bodies in Sable's connected chain. A body can be logically loaded with `physicsAvailable=false`; position and mass can remain useful while velocities become zero.

`SableTransformApi` converts points and directions between root and SubLevel space, projects through one or every nested body, measures transformed distances and finds loaded bodies intersecting a world box. Direction conversion normalizes a meaningful vector but passes null and near-zero vectors through. `projectOut(...)` stops after convergence or eight nested boundaries. A distance with either endpoint missing is `Double.MAX_VALUE`; direct upstream runtime failures are not generally caught.

`SableAssemblyBoundsApi.envelope(...)` combines finite, ordered bounds from non-null, non-removed bodies around a world-space reference point. `Envelope` exposes a conservative horizontal radius, height and lower-hull offset and clamps radius/height to at least `0.01`. Without usable bounds it returns `Envelope.DEFAULT`, equal to `(1, 1, 0.5)`.

- Use `projectOutOne(...)` for one immediate body boundary.
- Use `projectOut(...)` for the complete nested chain.
- Use `kick(...)` only when intentionally moving an entity into the supplied SubLevel.
- Treat an empty lookup as unavailable and retry from a later lifecycle event when appropriate.

### Topology

Implement `SableAssemblyConnectionProvider` on a `BlockEntitySubLevelActor` when the actor publishes an explicit assembly link. Each `SableAssemblyConnection` is `STRUCTURAL` or `CARRIAGE_COUPLER`.

`SableAssemblyConnection` permits a null target and normalizes a null kind to `STRUCTURAL`; use its structural and carriage-coupler factories for normal edges. `SableAssemblyTopologyApi.discover(...)` considers usable loaded bodies belonging to the same root `ServerLevel`, keeps only the root-reachable graph and treats dependencies as undirected. Optional `ActorFilter` and `ActorClassifier` callbacks select and classify actors. Explicit provider connections are read only from actors accepted by the filter and carry their own kind; for a duplicate undirected edge, `CARRIAGE_COUPLER` wins over `STRUCTURAL`.

`Topology` contains bodies ordered by breadth-first depth then UUID, edges, graph depth, coupler depth, structural carriage partitions and a stable fingerprint. Removing coupler edges creates the carriage partitions; the partition containing the requested root is primary. Callback failures skip the affected actor or dependency. An outer runtime/linkage failure produces `available=false`, empty immutable collections and fingerprint zero.

Use `SableAssemblyTopologyCache` for continuous control. It invalidates when the root object/UUID/level or shared revision changes, a body is removed, or its staggered 200–239 tick safety refresh arrives. `generation()` increments on every recomputation; `revision()` is the shared revision used by the current discovery. `invalidate()` clears only that cache. Call `SableAssemblyTopologyInvalidation.invalidate(...)` when a connection changes outside a known library event.

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

The library owns a package-private NeoForge invalidation bridge for normal body and plot changes. It is lifecycle wiring, not public API. Custom actor-link changes still call `SableAssemblyTopologyInvalidation.invalidate(...)` explicitly.

### Dynamics and impulses

`SableAssemblyDynamicsApi.sample(root)` follows Sable's connected chain but assigns depth zero only to the root and `-1` metadata to other rows. `sample(topology)` preserves the topology selection/depth metadata. A `Snapshot` contains root-local aggregate mass, centre of mass, inertia, inverse inertia and ordered `BodyDynamics` entries. Top-level `physicsAvailable` describes the root rigid body; inspect each body when every status matters. `aggregate(...)` deduplicates selected IDs. An empty selection, missing requested ID or mass-unavailable body returns a loaded zero aggregate instead of partial mass. `Tensor` supplies `ZERO`, matrix conversion, vector transformation and maximum-diagonal helpers.

`SablePointImpulseApi.apply(...)` and `applyDirectional(...)` validate the body, rigid handle, group, mass/tensors and finite point/impulse, write one point impulse and wake through a zero impulse only after success. A poisoned queued accumulator is cleared before it reaches Sable. Directional magnitude is multiplied by the supplied time step and may be negative. Validation failures return false; upstream runtime exceptions are not caught.

`SableMagneticCaptureApi.pullTogether(...)` attempts bounded opposing pulls between two loaded bodies. Supply local anchors, capture radius, maximum closing acceleration and physics time step. Invalid, coincident and out-of-range bodies are ignored. The two impulse writes are not atomic: the method returns true when either side succeeds. Runtime/linkage failures inside pull calculation return false, but validation performed first, including body/anchor access, can still propagate.

### Constraints and yaw joints

`SableYawJointApi.create(...)` returns a `Joint` object even on failure; test `isValid()`. A valid joint requires distinct usable bodies on the same server level, rigid handles, finite anchors and non-zero axes. It locks translation, pitch and roll while allowing yaw around the supplied local axes. `Joint` exposes contacts, servo setup, true zero-force disable, wake, one-shot removal and `close()`. The six-argument factory starts contacts disabled; the seven-argument overload honors its `contactsEnabled` argument.

`progressiveResponse(...)` returns a smooth dead-zone spring, damping and force response. It stays exactly zero through the free angle, rises smoothly and saturates at the supplied maximum. It is a soft force response, not a native hard angular limit.

`SableConstraintApi` is the one supported compatibility facade for Sable constraint package differences. It supplies fixed, free and generic configurations plus add, frame, wake and remove operations. Fixed/free builders replace a null orientation with identity; the generic builder does not. `addConstraint(...)` returns null for the same body and `setFrame(...)` accepts only frame 1 or 2. `wakeUp(pipeline, null)` is a no-op only when the pipeline is non-null; a null pipeline throws `IllegalArgumentException`. Compatibility failures intentionally propagate checked reflection/class-loading exceptions. Only `remove(...)` is best-effort.

### Collision and particle occlusion

`SubLevelParticleOcclusion` provides ray, leading-face probe, synchronous swept-bounds and incremental swept-bounds queries over loaded plots. For ordinary finite inputs, invalid, unavailable and unobstructed distance paths return non-negative `maxDistance`, so unavailable is not distinguishable from clear from that value alone. Supply finite direction, bounds and distance values; NaN is not repaired. A hit subtracts a `1/16` block safety margin. Some later pose, bounds, block or collision access can still propagate runtime/linkage failures.

`ProbeCache` retains loaded status, chunk references and collision shapes across calls. Call `clear()` after relevant chunk, block-state or shape changes or later reads can be stale. Advance a `SweptBoundsScan` until it reports complete, then read `result()`; it processes at least one block per call even with a zero block budget, and a non-positive time budget disables time bounding. `includeTaggedTransparentBlocks=false` skips Create `FAN_TRANSPARENT` blocks, while true tests their actual collision shape.

## Client rendering

Package: `com.rieno.gadgetsandgizmos.lib.client.render`

All types in this section are client-only.

### Area highlights and SubLevel poses

`AreaHighlightRenderTypes.clawMarker()` supplies the singleton translucent highlight render type after the library client bootstrap has registered the `gadgetsngizmos:area_highlight` shader. Consumers do not call the shader event handler or client bootstrap themselves.

`SubLevelClientRenderApi.withPoses(...)` installs interpolated Sable poses for one lookup or raycast and always restores the previous provider in `finally`. Cast, null and action failures propagate. `renderPosition(...)` returns the interpolated render position of one `ClientSubLevel` and does not accept null.

### Physics goggles overlays

Register an extra HUD layer through `PhysicsGogglesOverlayRegistry`:

```java
PhysicsGogglesOverlayRegistry.register(
        ResourceLocation.fromNamespaceAndPath("your_mod", "engine_load"),
        ctx -> drawEngineLoad(ctx.graphics(), ctx.target(), ctx.partialTick()));
```

`Context` supplies the GUI graphics, player, root level, optional looked-at block entity, optional SubLevel ID and partial tick; graphics, player and level are required. `register(...)` rejects ID conflicts. Use `registerOrReplace(...)` only for an integration you own, and `unregister(...)` when a dynamic layer unloads. Rendering snapshots the synchronized registry and invokes layers in unspecified snapshot order on the caller thread. A `RuntimeException` or `LinkageError` from one renderer is logged and does not stop later layers.

### Mini contraption diagram

`SimulatedDiagramMiniRenderer` embeds the Simulated diagram in another screen. Supply a live SubLevel UUID and `DiagramDataSource`, whose schema is `mass()` plus a force list. Every `DiagramForceData` supplies a group resource ID, local point XYZ and local force XYZ. Null forces and null/unknown groups are skipped. `render(...)`, `mouseClicked(...)`, `mouseReleased(...)`, `mouseDragged(...)` and `mouseScrolled(...)` return false when the body/data is unavailable or setup is in retry backoff. `tickHosted(...)` advances its hosted screen at most once per client game tick.

The renderer owns input transformation, hosted ticking and cleanup. A changed target recreates its hosted screen. Runtime/linkage failures log the operation, release the screen and retry after 250, 500, 1,000, 2,000 and then 4,000 milliseconds; success resets the backoff. Call `close()` when the owning screen closes to release framebuffers and current retry state.

`DiagramScreenAccess` is the mixin-backed bridge for upstream private screen details. It exposes viewport update, yaw/pitch-step rotation, SubLevel content rendering, framebuffer release, paper visibility, last/current paper and tab offsets, and render time. The library installs it automatically. Consumers may use the typed bridge where required but must not implement it or call the mixin class.

### Tablet client

Package: `com.rieno.gadgetsandgizmos.lib.client.tablet`

`TabletAppClientRenderer` requires `createScreenState()` and `render(...)`; its default `mouseClicked`, `mouseScrolled`, `keyPressed` and `chatTyped` hooks return false, it has no release/drag hooks, and `ownsAppSurface()` returns false. A custom renderer must create a non-null `TabletAppClientState`. `TabletAppClientSession` lazily owns one state per app ID for one screen and is not synchronized. Call `clear()` when the screen closes; state has no disposal callback.

`TabletAppClientContext` copies app NBT at construction, but `data()` returns that stored mutable copy. Treat it as a construction-time snapshot, not immutable data. A null surface is rebuilt from positive legacy bounds; null action sender and refresh callbacks become no-ops. `TabletAppClientSurface` rejects non-positive dimensions.

`TabletLayout` requires positive logical dimensions but does not validate available dimensions. `fit(...)` uniformly centers a logical layout, `stretch(...)` uses raw available dimensions for scale while clamping returned bounds to at least one, `project(...)` rounds logical rectangles and `Rect.contains(...)` uses inclusive left/top and exclusive right/bottom edges.

Register renderers through `TabletAppClientRegistry` during client setup. Registration is synchronized and conflict-strict, with no replace or snapshot method. `renderer(appId)` returns `null` when none is registered; the consuming host decides any fallback presentation. `ownsAppSurface()` tells that host whether the renderer owns the complete app area beneath host chrome.

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

`GraphNodeDefinition` describes the editor category, typed ports and persistent-state requirement. A null/blank ID is rejected, a null/blank category becomes `core`, and port maps are immutable copies. The class does not enforce resource-location syntax or namespacing; callers must supply a stable namespaced string.

Both process-global registries are synchronized and have no unregister or replacement API. Definition registration keeps an equal existing value and rejects a non-equal duplicate. Runtime registration accepts a duplicate only when it is the exact same executor object. `GraphNodeExecutor` itself does not normalize null outputs, copy inputs or isolate exceptions; the owning host runtime defines that execution boundary.

### Values and services

`GraphValue` supports numbers, booleans, strings, lists and maps. Its compact constructor changes a null/blank type to `any` but retains direct scalar/object values, including null or a non-finite number. The `number(...)`, `string(...)`, `list(...)` and `map(...)` factories sanitize their documented non-finite/null inputs. Nested lists, maps and sets are copied recursively, while arrays, recursive collections and null collection members are rejected. Arbitrary non-collection objects are retained.

`asNumber()` uses `Number.doubleValue()` or parses a string and returns zero on failure. `asBoolean()` accepts a Boolean or uses `Boolean.parseBoolean(...)`. `asString()` returns empty for null and otherwise `String.valueOf(...)`.

`GraphExecutionContext` supplies the current tick, key/value state and typed optional host services. The interface does not enforce state namespacing, persistence, copying or thread safety; those are host responsibilities. Define a non-null `GraphServiceKey<T>` when a reusable node needs a host service. `GraphHostServices.BLOCK_ENTITY`, ID `gadgetsngizmos:block_entity`, exposes a common `BlockEntity` service.

The deprecated raw `services()` map is an empty compatibility method. Do not build a new integration on string keys or `Object` casts.

### Compilation and scheduling

| Type | Contract |
| --- | --- |
| `GraphModel` | Consumer/host-owned minimum node and edge document model |
| `GraphCompiler` | Indexes a graph model and reports structural diagnostics |
| `CompiledGraph` | Immutable nodes and generic incoming/outgoing edge indexes |
| `GraphNodeRegistry` | Conflict-safe definition registry with immutable snapshots |
| `GraphRuntimeRegistry` | Conflict-safe executor registry with immutable snapshots |
| `GraphEventScheduler` | Bounded immediate and delayed event queues with cancellation and cleanup |

`GraphCompiler.compile(null)` returns an empty graph. Null, blank-ID and duplicate-ID nodes are diagnosed and ignored, keeping the first duplicate. Edges with missing endpoints are diagnosed and ignored. The compiler does not validate node types, edge IDs or port names, and null node/edge collections propagate a failure. `CompiledGraph` freezes its maps/lists; unknown lookups return `null` or an empty list.

`GraphEventScheduler` requires positive immediate and scheduled capacities. `enqueue(...)` and `schedule(...)` return false for null/full input. `enqueueAndSchedule(...)` commits both events only when both are non-null and both queues have room. `release(tick)` moves due events while immediate capacity remains; equal-tick order is unspecified, and `scheduledSnapshot()` is immutable but not guaranteed sorted. The scheduler is not synchronized, so use it from one owning runtime thread and call `clear()` when that runtime is removed.

`poll()` removes the next immediate event or returns null. `hasImmediate()`, `hasWork()` and the queue-size methods expose pending state, with delayed events included in work. `removeImmediateIf(...)` and `removeScheduledIf(...)` cancel matching events. Immediate and scheduled snapshots are shallow immutable lists; `clear()` empties both queues.

Package: `com.rieno.gadgetsandgizmos.lib.graph.render`

`GraphViewport` contains client-free pan, zoom and exact screen/graph inverse conversion, with zoom clamped to at least `0.0001` for ordinary finite values. `GraphWireGeometry` produces three orthogonal Manhattan segments through the midpoint X, clamps interpolation to `[0, 1]` and exposes squared hit distance. These helpers do not own a document format or rendering state.

`GraphModel` is deliberately minimal: a node supplies `id` and `type`; an edge supplies `id`, source node/port and target node/port. `GraphCompiler` indexes generic edges without assigning data or execution meaning.

## Named Event transports

Package: `com.rieno.gadgetsandgizmos.lib.namedevents`

`NamedEventBus` is the in-process transport boundary shared by G&G ACC graphs and optional integrations. A transport subscribes once under a stable, namespaced ID, receives every compatible event synchronously, and may publish new events with an authoritative server, dimension, position and endpoint identity.

```java
NamedEventBus.Subscription subscription = NamedEventBus.subscribe(
        "yourmod:machine_events", event -> {
            // Schedule work yourself if this transport owns another thread.
            handleEvent(event.name(), event.data());
        });

NamedEventSource source = new NamedEventSource(server, level.dimension(),
        Vec3.atCenterOf(pos), "yourmod/" + pos.asLong());
NamedEventBus.publish(NamedEvent.of(source, "flight:set_target",
        GraphValue.map(Map.of("altitude", 150, "heading", 270)), 64));
```

`NamedEvent` validates a nonblank topic of at most 128 characters, copies its `GraphValue` payload and clamps a negative maximum distance to zero. A zero distance is unrestricted; a positive distance is evaluated by each receiving transport against the supplied source position and dimension. `NamedEvent.excluding(transportId)` prevents a physical bridge from accepting the event it just imported.

The bus deduplicates event UUIDs in a bounded recent-event window and catches/logs a failing subscriber so one optional integration cannot interrupt the others. It does not schedule work, discover receivers, authenticate topics, enforce distance, or retain a server-specific lifecycle on behalf of a transport. Subscribers must provide those policies and close dynamic `Subscription`s when their runtime is removed.

G&G's ACC, Computed, CC:Tweaked rednet and optional Synaxis bridges use this API. The API itself has no dependency on any of those mods, so another integration can exchange the same immutable names and payloads without linking against their implementation classes.

## Display integration

Package: `com.rieno.gadgetsandgizmos.lib.display`

### Frame envelope

`DisplayFrameEnvelope.create(...)` copies a render payload, keeps presentation metadata separate and records dimensions clamped to at least one. The public keys are `AccDisplayPresentation`, `PixelWidth`, `PixelHeight` and `TargetDisplayMode`. When both payload and presentation are empty it returns a wholly empty tag without dimensions.

```java
CompoundTag frame = DisplayFrameEnvelope.create(
        payload, presentation, 256, 128);

if (DisplayFrameEnvelope.hasRenderablePayload(
        DisplayFrameEnvelope.payload(frame))) {
    forwardFrame(frame);
}
```

`payload(...)` removes only the nested presentation tag, so the pixel dimension keys remain in the returned copy. `presentation(...)` returns a copy, and `requestedMode(...)` returns an empty string for null. `hasRenderablePayload(...)` accepts a non-blank `Format`, a `Lines` list, a `Terminal` compound or a `Widgets` list. Preserve the public envelope keys when relaying a frame.

### Display sources and surfaces

`AccDisplaySourceRegistry` is keyed by `Block`, not registration ID. It registers one block type with an `AccDisplaySource`; the source must implement `frame(blockEntity, width, height)` and can override normalized `interact(...)` and named `input(...)` handlers, whose defaults return false. The interface does not clamp coordinates or isolate provider exceptions.

Use `AccDisplayConnectionRegistry` when a block entity can act as an ACC display source or target but cannot be described by one fixed block type. It has synchronized, separate source and target predicate maps. Runtime exceptions from a predicate are treated as no match. `unregister(id)` short-circuits after removing a source entry, so use distinct IDs for the two roles when both must be independently removed. Frame providers remain registered through `AccDisplaySourceRegistry`.

The connection registration methods are `registerSource[IfAbsent](...)` and `registerTarget[IfAbsent](...)`; queries are `isSource(...)`, `isTarget(...)` and `isConnection(...)`. Source-registry queries are `isSource(...)`, `isSourceBlockId(...)`, `source(...)` and `id(...)`.

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

Normal source registration rejects a second entry for the same block even when the registration ID differs. `registerIfAbsent(...)` reports a block conflict before validating new arguments. Unregister sources by `Block`; source and ID lookup use the target block entity's current block state.

`DisplaySurfaceProjection.normalizedPoint(...)` converts a local tile hit into clamped normalized joined-display coordinates and accounts for the texture-pixel border. Any null dependency returns `(0, 0)`. `normalizedVisiblePoint(...)` additionally accepts visible pixels per row and a non-negative source-top crop; null input returns `(0, 0, false)`. Its `VisiblePoint.inside()` is calculated before output clamping, so reject false before hit-testing content fitted against an edge.

`DisplayWidgetProjection.fit(...)` clamps a widget rectangle into a positive-normalized surface. For fitted bounds, `maximumScale(...)` returns `[0.01, 1]` to keep rotated bounds on the surface; arbitrary out-of-surface bounds plus the minimum floor do not gain that guarantee. `unproject(...)` inverse-rotates and inverse-scales around the widget centre using absolute scale clamped to at least `0.01`. Its local point uses inclusive edges and supplies a clamped horizontal fraction.

`ShipInformationDisplayModes` contains `passenger_information/running_text`, the default `passenger_information/detailed_with_schedule`, `train_destination/simple`, `train_destination/extended`, `train_destination/detailed`, `platform/running_text`, `platform/table`, `platform/focus`, `departure_board/table`, `static_text/simple_text` and `static_text/rich_text`. Use `ids`, `contains`, `normalize`, `label` and `isStaticText` rather than copying strings. `requiresActiveService(...)` is false only for static text. `isAvailable(...)` accepts static text unconditionally and otherwise requires both an active schedule and a present pilot. Null and unknown modes normalize to the default.

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

`ScmControlModeRegistry` lazily installs `createthrusters:airship`, `plane` and `car`. `register(...)` installs or replaces any ID, including a built-in ID, so namespace discipline is a caller responsibility. `unregister(...)` refuses those built-in IDs. Null, missing and invalid resolve IDs fall back to airship. Mode exceptions are not isolated by the registry.

`ScmBuiltinControlModes` exposes the built-in IDs. Built-in IDs serialize as bare paths while external IDs remain namespaced. `ScmFlightBehavior` owns `direct_vector` and `prefer_ship_direction`; blank and unknown values become `DIRECT_VECTOR`. `ControlInput` finite-sanitizes and unit-normalizes forward/up/right/path directions, using `+Z`, `+Y`, `forward × up`, target-minus-position and then forward fallbacks as needed, and clamps non-negative limits. `ControlOutput` sanitizes force/torque, clamps upright stabilization to `[0, 1]` and drive direction to `[-1, 1]`.

Airship mode performs 3D velocity/error control with gravity compensation and `0.75` upright stabilization. Plane mode predicts climb/yaw/bank with forward drive. Car mode uses horizontal throttle/steering, can reverse for heading or clearance and reports drive direction `-1` or `1`.

### Targets and probes

`ScmTarget` is a stable SubLevel-aware selected block and optional signal face. It normalizes a null position to zero and a null block ID to `minecraft:air`; a signal face is cleared without a signal position. `stableId()` encodes root/SubLevel identity, block position/ID and optional signal endpoint for saved map references.

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

The `Context` supplies the selected target, an unnormalized target-sublevel-local suggested direction and a copied linked-target list. Registering the same factory ID replaces its previous entry. Factories run by descending priority, may receive a null block entity and contribute zero or more probes to an immutable combined list. Factory exceptions are not caught.

The four-argument `create(...)` overload rejects a null target through `List.of(...)`. The five-argument overload instead returns empty for a null level/target or removed block entity, defaults a null linked-target list to the selected target, and rejects null entries while copying a supplied list. `ScmControlProbe.Reading` repairs only non-finite speed/effect values; range, neutral and probe vectors are not registry-validated. Exact restoration and grouped-control behavior are integration obligations.

### Map composition and control ownership

`ScmMapCompositionApi` composes only fragments connected to the supplied ID set. A fragment defaults a null fragment ID to its owner and automatically adds its owner to sorted coverage. A valid primary is considered first, then attachments are ordered by owner UUID and fragment UUID. Duplicate owners or fragment IDs are rejected and first accepted coverage wins overlaps. The immutable `Composition` exposes unclaimed IDs, rejected IDs and owner/value lookup. Its deterministic fingerprint covers connected IDs, accepted fragment/owner IDs and effective ownership, but not fragment values; a value-only change leaves it unchanged.

`ScmControlAuthorityApi.claim(...)` is a tick-batched election, not an immediate contest. A call records that controller as a candidate for the supplied tick and returns the already-elected/live owner. The first authority advance on a later tick—through claim, owner, owns or prune—elects among the previous tick's candidates: higher priority wins, with the lexicographically smaller controller UUID string breaking a tie. A backwards tick is denied.

Claim every active tick and gate output writes on `ClaimResult.granted()`. `ownerChanged()` is set only on the granted owner's claim result and compares only previous/current controller UUID, so re-electing the same UUID with changed priority or expiry remains false. The default lease is two ticks and expires at `currentTick + leaseTicks + 1`. A backwards claim is not granted but may still report the current live owner; a backwards `owner(...)` query is empty.

`owner(...)`, `owns(...)` and `prune(...)` can advance an election as well as read/remove state. `release(...)` removes one controller from one authority key without immediately electing a replacement. `releaseController(server, controllerId)` clears that controller across every assembly and returns the number of changed authority keys. `clearServer(server)` removes all authority state for that server. `prune(...)` returns the number of keys it removed after advancing them.

`ScmControlProbeRegistry` and `ScmControlModeRegistry` are separate. A mode decides navigation demand; a probe describes one reversible physical control.

## Shipping

Package: `com.rieno.gadgetsandgizmos.lib.shipping`

### Logistics runs

`ShipLogisticsRun` is the immutable persistent model for named `ITEM`, `FLUID`, `ENERGY` and `FUEL` runs. `ResourceType` exposes ID/label/ARGB colour as item/Item/`0xFF55D6FF`, fluid/Fluid/`0xFF4A8DFF`, energy/FE/`0xFFFFD54F` and fuel/Fuel/`0xFFFF8A3D`. A null run ID becomes a random UUID, a blank name becomes `<Resource label> Run` and names are capped at 64 characters. Each `Endpoint` stores a SubLevel UUID and internal block position; null values normalize to the all-zero root UUID and zero position. Endpoints are deduplicated, sorted by UUID string then packed position and copied.

Use `withName(...)` and `withEndpoints(...)` to create changed copies. Run NBT uses `Id`, `Name`, `Resource` and `Endpoints`; endpoint entries use `SubLevel` and `Pos`. `fromTag(...)` requires only `Id`, defaults an unknown saved resource to `ITEM` and skips malformed endpoints. `isFuelRun()` distinguishes the combined refuelling contract from normal single-resource routes.

### Dock scheduling

`ShipDockScheduler` reserves interchangeable named resources without importing a concrete dock implementation. `DockSlot.resource(...)` can represent a connector, berth or another deterministic resource. `RequestKey` gives one vessel independent channels; the UUID overload remains the primary `dock` channel.

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

`RequestPriority` normalizes distance/ETA and records committed state. `VesselEnvelope` clamps radius and height to at least `0.01` and bottom offset to non-negative, with enclosed-volume and footprint fit helpers. `Lease.granted()` means its dock ID is non-null; otherwise it carries a non-negative queue position and size-aware alternating `HoldingPlacement`.

On a later `request(...)` or `heartbeat(...)` cleanup pass, requests expire after more than 100 ticks without activity; that pass also removes state whose clock moved backwards. Without a later request/heartbeat, stale assignments remain in memory. A heartbeat updates only last-seen time and priority; it does not replace candidates, occupancy, owned dock or vessel envelope. The scheduler is process memory, not saved data. Committed/owned and arrival-ready requests lead ordering, followed by a score from ETA, distance and bounded wait fairness, then stable tie-breakers.

Slot conflicts use connector/resource keys when both slots define them: equal keys conflict even across different dock UUIDs. Otherwise, an equal dock UUID conflicts. Candidate and occupied collections are copied/deduplicated with `List.copyOf`/`Set.copyOf`, so a null entry throws. `DockSlot` requires a dock UUID and normalizes a blank connector to null; `RequestKey` requires an owner UUID and normalizes a blank channel to `dock`. Arrival-ready means committed, within 16 blocks, or ETA from zero through 40 ticks. Successful assignment rotates later connector candidate order per dock as process-local fairness state.

An `ownedDock` receives ownership preference only when that exact `DockSlot` is also present in the request's candidate list. Only exact slot equality bypasses external occupancy; the same dock UUID with another connector is not ownership of that candidate.

Call `release(RequestKey)` for one channel and `release(UUID)` only when every channel owned by that vessel should be removed. Call `removeDock(...)` when a dock disappears. `shutdown(server)` clears state and rejects later work; `finishShutdown(server)` removes the weak server instance after stop.

## Tablet apps

### Server/common app definition

Package: `com.rieno.gadgetsandgizmos.lib.tablet`

`TabletAppDefinition` describes a required stable app ID, copied title/description, accent colour, immutable tabs, icon, immutable declared shared keys and whether the app is built in. A missing title becomes the literal ID path, a missing description becomes empty and a missing icon becomes `<namespace>:textures/gui/apps/<path>.png`. Compatibility constructors create a non-built-in app.

`TabletTabDefinition` trims its ID, copies its title/actions/keyboard-actions and reports whether one action requires keyboard input. `TabletAction` trims tab/action IDs and freezes its argument map; its app ID is not validated by the value type.

`TabletAction` carries the selected app, tab, action and string arguments. `TabletActionContext` stores the supplied server player, tablet stack and optional SubLevel-aware target, including placed-source data; both positions are immutable-copied and `placed()` aliases the flag. The value type does not authenticate or null-check other references, so the handler/dispatcher owns validation. `TabletActionHandler.Result.success(...)` and `failure(...)` replace a null message with `Component.empty()`; direct record construction does not.

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

`TabletAppRegistry` publishes immutable lock-free read state while synchronizing mutations. `register(...)` rejects a conflict, `registerIfAbsent(...)` reports it, `registerOrReplace(...)` returns the previous definition and `unregister(...)` removes an owned app. Each successful publish increments the revision. `apps()` returns the sorted immutable list, `revision()` returns its current revision, `definition(id)` and `handler(id)` are nullable lookups, and `snapshot()` captures the list and revision together.

`TabletAppPrice` is a standalone item/count value; item ID is required, `FREE` is zero `minecraft:emerald`, and a negative count throws `IllegalStateException`. Price is not part of `TabletAppDefinition` and the library does not automate a purchase flow. `TabletAppPurchaseScope` contains `PLAYER` and `DEVICE`.

`TabletAppEntitlementStore` defines `owns(...)`, `grant(...)` and `revoke(...)` for a scope/player/tablet/app tuple. `TabletAppEntitlementApi.store()` returns the installed provider and `available()` reports whether one exists. Its unavailable provider safely returns false. `install(...)` replaces the provider and throws `IllegalStateException` for null; `uninstall(expected)` removes only the same object. `TabletAppAccess.canUse(...)` allows a built-in app, rejects a null app and otherwise asks the store; it does not also check installed-app state.

### Storage and notifications

`TabletStorage` exposes `tablet(...)`, `app(...)`, `installedApps(...)`, `updateTablet(...)`, `updateApp(...)` and `setInstalled(...)`, plus shared-data helpers. The interface documents atomic-style updates and ownership but cannot enforce atomicity or defensive copying in an implementation. `TabletStorageApi.storage()` returns the provider and `available()` reports whether one is installed; its unavailable provider returns empty copies, false results and no-op updates. `install(...)` replaces the provider and throws `IllegalArgumentException` for null, while `uninstall(expected)` removes only the same object.

Storage is keyed by the logical tablet UUID supplied by the caller. Any devices using that UUID address the same provider record.

Declare cross-app records in `TabletAppDefinition.sharedDataKeys`. `shared(...)` returns empty unless tablet/owner/key are valid and declared. `updateShared(...)` passes a copy to the mutation, keeps the previous value when the mutation returns null, and stores/returns copies under the source app's `Shared` compound.

`TabletNotification` is the immutable persisted notification value with NBT keys `Id`, `Title`, `Message` and `Accent`; malformed or blank-ID tags return null. `TabletNotifications` stores a compound list under the app-data key `Notifications`, keeps at most 32 valid entries, replaces an equal ID at the newest/end position and drops the oldest/front entry when full. `dismiss(...)` removes every matching ID and removes the list key when empty; `clear(...)` removes it unconditionally. `count(...)` is available to any consumer UI.

`TabletInteractionMode` owns `STANDARD`, `READER` and `PUSH`. IDs are lower-case enum names; null, blank and unknown strings become `STANDARD`, with whitespace and case tolerated.

### Client renderer

Register a `TabletAppClientRenderer` with `TabletAppClientRegistry` during client setup. `TabletAppClientContext` supplies the app/tab, a construction-time NBT copy, graphics/font, legacy bounds, validated surface, mouse position, action sender and refresh callback. Its `data()` accessor returns the stored mutable copy. Do not execute server mutations directly from the renderer; send a declared action and validate it again in the server handler.

## Complete public surface

The following tables are the complete supported top-level surface for `1.2.x`. Nested records and interfaces are listed with their owning type. Lifecycle-only types are marked and mixin implementation classes are excluded.

### Foundation, compatibility and configuration

| Package | Types |
| --- | --- |
| `lib` | `GadgetsNGizmosLibrary`; `GadgetsNGizmosLibraryNeoForge` *(lifecycle)*; `CreateThrustersLibrary`; `CreateThrustersLibraryNeoForge` *(deprecated aliases)*; `PhysicsStaffPowerEvents` *(lifecycle)* |
| `lib.client` | `GadgetsNGizmosLibraryClientBootstrap` *(lifecycle)*; `GadgetsNGizmosLibraryClientNeoForge` *(lifecycle)*; `CreateThrustersLibraryClientBootstrap`; `CreateThrustersLibraryClientNeoForge` *(deprecated aliases)* |
| `lib.config` | `GadgetsNGizmosLibraryConfigs` (`Server`); `CTLibraryConfigs` (`Server`) *(deprecated alias)* |
| `lib.compat` | `PhysicsStaffPowerHooks` (`StaffActionFailure`); `PhysicsStaffPowerTracker`; `PhysicsStaffInteractionGuard` |
| `lib.item` | `BundledSupporterHeadStack` |

`PhysicsStaffPowerHooks` validates Backtank-powered staff actions, consumes pressure, supplies tooltip values and calculates mass-based drag/lock drain. `PhysicsStaffPowerTracker` owns active operation state. `PhysicsStaffInteractionGuard` protects SCM initialization targets and applies optional AeroClaims permission checks. These are Physics Staff integration contracts, not a general claims or energy API.

### Probe, control and discovery

| Package | Types |
| --- | --- |
| `lib.probe` | `BlockEntityDataAdapter`; `BlockEntityDataAdapterRegistry`; `BlockEntityDataPort` (`Access`); `BlockEntityDataPortGroups`; `BlockEntityDataProvider`; `BlockEntityLookupApi` (`ResolvedBlockPosition`); `ConnectedBlockEntityProvider` |
| `lib.control` | `AnalogueAxis`; `AnalogueChannel`; `AnalogueChannelMode`; `AnalogueControlChannel`; `AnalogueSignalPacket`; `AnalogueTransmissionTarget`; `ControllerBindingOwner`; `ControllerDirectTargetReference`; `ControllerMechanic`; `ControllerMechanicBinding`; `CustomKeyEntry`; `DirectionalAnalogComponent`; `DirectionalAnalogMath`; `DirectionalAnalogSnapshot`; `DirectionalAnalogSource`; `FaceBoundSignalRoute`; `FrequencyBinding`; `IDirectControlReceiver`; `LinkedOrientationSource`; `OrientationMath`; `OrientationPayload`; `OrientationTarget` |
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
| `SableAssemblyConnection` | `Kind`; stable structural or carriage-coupler edge |
| `SableAssemblyConnectionProvider` | Actor-supplied explicit connection contract |
| `SableAssemblyDynamicsApi` | `Snapshot`, `BodyDynamics`, `Tensor`; samples complete or selected topology dynamics |
| `SableAssemblyTopologyApi` | `ActorFilter`, `ActorClassifier`, `Body`, `Edge`, `CarriagePartition`, `Topology`; deterministic body graph |
| `SableAssemblyTopologyCache` | Revision-aware reusable topology cache |
| `SableAssemblyTopologyInvalidation` | Explicit topology revision invalidation |
| `SableConstraintApi` | Supported Sable constraint compatibility facade |
| `SableLevelApi` | Typed root/SubLevel ownership and lookup |
| `SableMagneticCaptureApi` | Bounded non-atomic opposing magnetic impulse attempts |
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
| `lib.namedevents` | `NamedEvent`; `NamedEventBus` (`Subscription`); `NamedEventSource`; `NamedEventTransport` |
| `lib.display` | `AccDisplayConnectionRegistry`; `AccDisplaySource`; `AccDisplaySourceRegistry`; `DisplayFrameEnvelope`; `DisplaySurfaceProjection` (`Point`, `VisiblePoint`); `DisplayWidgetProjection` (`Bounds`, `Point`); `ShipInformationDisplayModes` |

### SCM and shipping

| Package | Types |
| --- | --- |
| `lib.scm` | `ScmBuiltinControlModes`; `ScmControlAuthorityApi` (`Owner`, `ClaimResult`); `ScmControlMode` (`ControlInput`, `ControlOutput`); `ScmControlModeRegistry`; `ScmControlProbe` (`Reading`); `ScmControlProbeRegistry` (`Context`, `Factory`); `ScmFlightBehavior`; `ScmMapCompositionApi` (`Fragment`, `SelectedFragment`, `Composition`); `ScmTarget` |
| `lib.shipping` | `ShipDockScheduler` (`DockSlot`, `RequestKey`, `VesselEnvelope`, `HoldingPlacement`, `RequestPriority`, `Lease`); `ShipLogisticsRun` (`ResourceType`, `Endpoint`) |

### Tablet and client rendering

| Package | Types |
| --- | --- |
| `lib.tablet` | `TabletAction`; `TabletActionContext`; `TabletActionHandler` (`Result`); `TabletAppAccess`; `TabletAppDefinition`; `TabletAppEntitlementApi`; `TabletAppEntitlementStore`; `TabletAppPrice`; `TabletAppPurchaseScope`; `TabletAppRegistry` (`Snapshot`); `TabletInteractionMode`; `TabletNotification`; `TabletNotifications`; `TabletStorage`; `TabletStorageApi`; `TabletTabDefinition` |
| `lib.client.tablet` | `TabletAppClientContext` (`ActionSender`); `TabletAppClientRegistry`; `TabletAppClientRenderer`; `TabletAppClientSession`; `TabletAppClientState`; `TabletAppClientSurface`; `TabletLayout` (`Bounds`, `Rect`) |
| `lib.client.render` | `AreaHighlightRenderTypes`; `DiagramDataSource`; `DiagramForceData`; `DiagramScreenAccess` *(library bridge)*; `PhysicsGogglesOverlayRegistry` (`Context`, `Renderer`); `SimulatedDiagramMiniRenderer`; `SubLevelClientRenderApi` |

## Lifecycle checklist

### Common setup

- Register graph definitions and executors under your namespace.
- Register block entity adapters once; their process-global registry has no unregister operation.
- Register SCM modes and probe factories.
- Register display sources/connections and tablet app definitions.
- Install tablet storage and entitlement providers only from the server persistence owner.
- Register kinetic guard exceptions only when the compatible block needs one.
- Keep any registration handle or ID needed for later removal.

### Client setup

- Register tablet renderers and physics-goggles overlays.
- Create mini diagram renderers from the owning screen, not common setup.
- Create one `TabletAppClientSession` per owning screen.
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
- Clear tablet client sessions and close mini diagram renderers with their owning screen.
- Identity-uninstall owned storage/entitlement providers.
- Unregister dynamic overlays, display sources/connections, apps, client renderers, modes, probes and guard exceptions.
- Shut down server-owned schedulers at the correct server lifecycle event.

## Compatibility and failure behaviour

### Unloaded SubLevels

A missing, removed or unloaded SubLevel is normal. Use the empty, unavailable, sentinel or `loaded=false` result documented by that method. Retry from a real tick, load or connection event when the feature can continue later. Only lookup methods documented as load-aware may request a short-lived ticket.

### Invalid data

Maths and physics APIs sanitize non-finite numeric input only where documented. Some compatibility and native APIs deliberately propagate runtime, linkage, class-loading or reflection failures; others return false or a sentinel. Graph, tablet, display and client overlay registries reject their documented registration conflicts. SCM mode and probe registration replaces the entry under the same ID, so never register over another mod's namespace. Do not replace malformed-data outcomes with guessed defaults that could target another body or control.

### Optional mods

Keep optional-mod integration in one adapter. Check the mod is loaded before calling it and do not expose its classes through your public contract. The library selects its Aeroworks mixin only when Aeroworks is present and performs AeroClaims authorization only when AeroClaims is loaded; absence means no claim test, while an incompatible/throwing installed AeroClaims API is not exception-isolated.

### Version changes

Patch and beta releases may add methods and types without breaking the `1.2.x` contract. A removed method, changed serialized ID or changed ownership rule requires a documented compatibility decision. Compile against the same library version you test at runtime.

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
