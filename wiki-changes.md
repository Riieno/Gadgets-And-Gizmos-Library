# Optimization API additions

## Sable residency readiness

`SableSubLevelResidency.isFullyLoaded(SubLevel)` verifies that every chunk in a live server SubLevel plot has a loaded chunk holder. `areFullyLoaded(Collection<? extends SubLevel>)` applies the same check to a nonempty assembly. Both are server-thread availability checks only; they do not request chunks or add tickets.

## Projected block lights and feed refresh settings

`lib.physics.ProjectedLightSource(BlockState)` owns a `TransientLightBeam` point source. Call `update(level, pose, length, excludedSource)` on the server game thread to raycast loaded world and Sable terrain, offset the light half a block towards the lens, and maintain an invisible caller-owned light block there. The supplied block should expire itself when `TransientLightBeam.isOwned(level, pos)` becomes false. `close()` removes the source; shared positions remain lit until their last owner releases them. Terrain and fluids are never replaced. If the initial point occupies a partial block or foliage, the adjacent hit face is tried instead. World unload releases ownership and stale persisted light blocks expire through their scheduled ticks. Client calls do not mutate blocks. `sourcePosition(lens, hit)` exposes the bounded offset calculation.

`TransientLightBeam.updatePoint(level, position)` adds single-point placement alongside the existing beam methods. Changes use ordinary server block updates and Minecraft light propagation, so clients, shader packs and secondary views receive the same lighting without a surface overlay pass. Camera flashlights consume this API using the addon's existing invisible plume light block; the surface-overlay API remains available for other consumers.

`ViewSceneRenderer.setMinimumRefreshRate(IntSupplier)` installs a live client refresh setting (1?60 FPS, default 10). Each requested source uses its own `ViewRefreshSchedule`, so four feeds each receive ten updates per second rather than splitting ten updates. Missed deadlines are skipped, not queued; render cost does not lower the requested cadence. Achievable FPS remains limited by the host frame rate and hardware. The addon supplies `[client].cameraMinimumRefreshRate` from `createthrusters-client.toml`.

Each feed owns a vanilla `LightTexture` and refreshes it before capturing. `captureLightmap()` is a client integration hook used to route secondary terrain/entity lightmap lookups; the player's lightmap binding is restored afterwards. Feed sky rendering omits the underground void plane and applies the bounded terrain fog only to terrain, preserving the sky pass.

## Independent display scenes

`ViewSceneRenderer.request` and `draw` retain their existing signatures. Each requested `ViewReference` now owns a client `LevelRenderer`, its terrain visibility state, and separate Sable ship render data. Displays using the same source reuse that source's scene and texture. Sources render only client-loaded data within the capture distance, with an independent refresh schedule for each source. No client renderer is created or loaded on a dedicated server.

`ViewSceneWorldAccess.gadgetsngizmos$swapViewRenderer(renderer)` returns the previous client renderer; restore it in a `finally` block after drawing a secondary scene. `ViewSceneAccess` adds `gadgetsngizmos$prepareView(position)` to position terrain storage around the lens and `gadgetsngizmos$closeView()` to release its allocations. These are client-only integration contracts used by the library's capture facade.

Client world and ship section updates are forwarded to retained scenes. Renderer, camera and framebuffer context are restored before the player's frame. Expired sources, world changes and shader/resource reloads release the scene's terrain, ship and framebuffer allocations. Capture render distance overrides apply only during the secondary pass and do not change saved player options.

`ViewChunkCompat` is a client-only optional Sodium integration. It seeds a renderer's private chunk history after terrain initialization and synchronizes ready chunk additions/removals around the lens before secondary terrain updates. Secondary renderers never consume Sodium's shared client chunk event queue, leaving player updates intact. Each capture refreshes its own draw lists after Veil restores the previous perspective lists. Iris captures select the unshaded adapter before pipeline preparation, avoiding construction of a second shader pack pipeline.

## Graph binding reconciliation

`com.rieno.gadgetsandgizmos.lib.graph.GraphReconciliation.removeNodes(graph, predicate)` removes matching nodes and all incident wires from a mutable `GraphModel`. It returns the removed nodes in graph order so a host can release their runtime bindings. Other nodes and wires retain their order and identifiers. Apply it separately to the root graph and function bodies on the owning game thread. The predicate is evaluated before mutation; repeated removal returns an empty list.

All existing registry and control signatures remain available. Saved identifiers, graph data, control tolerances, iteration limits and packet fields retain their existing format.

## Optional runtime type matching

`com.rieno.gadgetsandgizmos.lib.compat.OptionalTypeMatcher` implements `Predicate<Object>`.

```java
private static final OptionalTypeMatcher JOINT = new OptionalTypeMatcher(
        "optional.mod.JointBlockEntity");

boolean supported = JOINT.test(blockEntity);
```

It matches a named class in the superclass chain or a directly implemented interface on that chain. It does not load the named optional class. Positive and negative results are cached by concrete runtime class using `ClassValue`; block entity instances are not retained. Null targets are unavailable.

## Loaded SCM relation snapshots

`ScmSubLevelRelationRegistry.register(id, priority, filter, provider)` adds an optional `Predicate<BlockEntity>` filter. Providers receive only valid matching entities, and are skipped when that selection is empty. The existing three-argument registration keeps its unfiltered behavior.

`ScmSubLevelRelationRegistry.loadedRelations(SubLevel root)` collects loaded bodies once per dimension, game tick, topology revision and provider revision. The root is included even if it has not yet appeared in the container. Repeated queries and other controllers in the dimension share the resulting immutable relation list. No chunks are requested or loaded.

Call it on the owning game thread. Connectivity is sampled again on the next tick; topology or provider changes invalidate the snapshot immediately. `forgetLoadedRelations(Level level)` also invalidates it for direct changes during a tick, and releases it on level unload. `revision()` exposes registration changes to consumers caching derived membership. Snapshots retain UUIDs, block positions and adapter IDs instead of live entities or levels.

`relations(level, scoped)` continues to resolve the caller's explicit collection immediately without caching its results.

## Tick snapshots

`com.rieno.gadgetsandgizmos.lib.util.TickSnapshotCache<T>` exposes:

```java
T get(Object key, long tick, Supplier<? extends T> loader)
void invalidate()
```

Use it on the owning game thread. Keys use equality and must include every input that can change within the tick. The loader runs when the key or tick changes, or after explicit invalidation. Null results are cached for that key and tick. Invalidating releases the key and value. Consumers should invalidate during lifecycle cleanup.

## Selective NBT copies

`com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagCopies.copyExcept(CompoundTag src, Set<String> omitted)` deep-copies every included field and never visits omitted payloads. Null source yields an empty compound; a null omission set includes all fields. Included nested compounds, lists and arrays remain independent from the source.

## Numerical reuse

`ScmAdaptiveStateModel.sample` and `ScmPrecisionAllocator.allocate` retain the existing APIs. Identical model samples and holding allocations with no previous throttle can reuse bounded pure numerical results. Keys snapshot all input units and include every relevant numerical parameter. Inputs that change are recomputed; allocations with previous throttle still solve normally.

Each thread retains at most eight models and eight holding allocations, with a separate total input budget of 16,384 for each cache. Each model retains at most 128 directional authority results. The caches contain no world state. Returned matrices and throttle arrays remain independent copies.

Column magnitudes, bounds and penalties are prepared outside iteration loops; bounds, ordering, convergence thresholds and iteration limits are preserved.

## Additional performance APIs

`GraphTextInputs.compileJoin(CompoundTag)` returns an immutable `CompiledJoin` with ordered `ports()` and `join(Function<String, String>)`. Compile it alongside the graph and replace it when the input schema changes. It retains the existing input order and null-text handling without rebuilding the schema during evaluation. The original `join` entry point remains available.

`SubLevelBlockIndex<T>` groups a snapshot of references by sublevel UUID and immutable block position. Construct it with the values and their UUID/position accessors. `at(UUID, BlockPos)` returns an immutable candidate list in source order, or an empty list. Null UUIDs identify world blocks; every face and adapter is retained for the caller's exact matching rules. Rebuild after changing references or their addresses. The index retains no level and performs no world reads.

`WaypointSpline.Segment.fractionAtDistance` reuses exact arc measurements at the existing binary-search fractions. Each thread retains at most eight segments (256 KiB of measurements); search steps, sample counts, and numeric results remain unchanged.

## Prepared control routing and signal batches

`control.ActionGroupRouting<T>` prepares immutable group definitions and action bindings for repeated owner-thread control passes. `firstUnitsForAction` retains the first matching group, `unitsForActions` requires every action to be bound, and `unitsForExplicitActions` includes the supplied implicit action for nonempty requests. Duplicate group IDs remain supported. Selection caching is bounded to 64 combinations. Construct a new routing snapshot after editing bindings.

`discovery.SubLevelBlockIndex.Cache<T>` retains a bounded number of physical-address indexes by collection identity. Inputs must remain immutable until `clear()`; this API is confined to its owning thread. Faces, adapters, null world-body IDs, and source order remain available to consumer matching rules.

`control.SourceSignalBatch<K>` collects final per-source integer values and applies them to an owner-controlled `Map<K, Map<String, Integer>>`. Values are clamped to the configured maximum; zero removes a source. Later writes replace earlier writes to the same key/source. `applyTo` mutates the full store before returning effective maximum changes, then clears the queued updates. Consumers publish their query state before notifying returned changes. Hidden sources remain stored and become effective when stronger sources disappear.

`control.ControlDomains.connected` groups controls joined transitively by shared keys, preserving input order and ascending-index breadth-first traversal. Empty domains remain independent. Each domain-key membership list is traversed once.

`discovery.SubLevelActorLookup` exposes an immediate actor lookup by block position. The Sable LevelPlot mixin implements it using Sable's live actor map. `SubLevelBlockEntityCollector` uses it after a chunk lookup misses and treats a missing actor as an authoritative absence and retains the scan fallback for unsupported plots, removed actors, or mismatched actors. No world state or actor snapshots are cached.


## Reusable view and camera APIs

Package: `com.rieno.gadgetsandgizmos.lib.view`.

- `ViewSource` exposes a loaded lens pose, bounded mouse/zoom controls, availability and exclusive control ownership. Addon block entities implement this contract; the library never imports addon content.
- `ViewPose` owns its position, rotation quaternion and FOV (5-120 degrees). `forward()` is lens-local negative Z transformed into world space. Rotation access returns a separate snapshot.
- `ViewRig` supplies Locked, Sentry and Manual aim, Local/Global orientation, clamped pan (-180-180), tilt (-135-135), zoom and joint decomposition. Pan is model Y and tilt is model X. Global compensates the mounting quaternion, including body pitch and roll; the camera child receives the residual stabilization rotation.
- `ViewTransforms` projects block-local positions and mounting rotations through loaded Sable poses. Physical clients register an interpolated pose provider; servers use the logical pose.
- `ViewReference` stores dimension, optional SubLevel UUID and immutable block position. `resolve(Level)` only returns a loaded, available `ViewSource`; it never requests chunks. `toTag()`/`fromTag()` provide the shared packet/display reference envelope.
- `ViewRaycast.trace()` searches loaded root terrain, intersecting loaded SubLevels and pickable entities. Limits are 1,024 blocks and 64 rays per tick. Ray zero is the central ray; additional deterministic samples cover the lens cone. A supplied source reference excludes its own block. Filters accept IDs, #tags, entity UUIDs and SubLevel UUIDs with allowlist/blacklist semantics; ignored geometry is transparent to the query. An empty blacklist accepts all; an empty allowlist accepts none.
- `ViewRaycast.Result.details()` exports an immutable `GraphValue` map: type, distance, world position, local block position, block ID/state, face, entity ID/UUID and SubLevel ID. `details(Level)` additionally includes dimension, precise local hit position and loaded body transform/mass/velocities. Missing or unloaded information remains empty.
- `RemoteViewSessions.open(ServerPlayer, ViewReference)` locks the player body while transferring lens control to one viewer. It returns false for unavailable sources, dead/riding players and already-owned sources. `close()` restores position, rotation and gravity. Exit, source loss, dimension change, disconnect, shutdown and missing client heartbeat release ownership. The caller selects the authorized player/source on the server game thread.

Client-only package: `com.rieno.gadgetsandgizmos.lib.client.view`.

- `RemoteViewClient` handles acknowledged sessions, mouse pan/tilt, scroll zoom and Esc/Control exit while blocking normal movement and interaction input. Camera preferences are restored after exit.
- `ViewSceneRenderer.request()` returns a live framebuffer texture for a loaded reference; `draw()` draws it on a supplied display surface. Displays using the same source share its scene and texture. Captures have at most eight active sources and 320-pixel dimensions, and refresh each due source independently at the configured rate. Idle/world-change captures release GPU resources. Captured scenes skip nested camera displays to avoid recursion.

The addon registers its Camera with `AccDisplaySourceRegistry` under its own namespace and transports `Format="camera"` plus a `ViewSource` reference in existing ACC display frames. All additions preserve existing display and graph APIs.

`ViewRaycast.Filter.fromValue(GraphValue, boolean)` accepts comma/semicolon/whitespace-separated text and graph lists through the library API. Entity hits also identify loaded bodies tracking or carrying world-space entities; their bounds remain in world space while hit details retain body-local coordinates. Neighboring block-shape reads use a loaded-only `BlockGetter`.


## Camera category filters and capture budgets

`ViewRaycast.Category` exposes stable selector IDs in order: `sub_levels`, `passive_mobs`, `hostile_mobs`, `players`, and `blocks`. `Filter.fromValue` accepts any combination as comma-separated IDs, labels, or a typed graph list. Empty blacklists accept all targets; empty allowlists accept none. `blocks` matches root-level block geometry; `sub_levels` matches body block geometry and body-associated entities. Mob and player categories also apply to entities on bodies. Existing IDs, tags, and UUID entries remain supported.

`ViewCaptureBudget(refreshRate, workloadFraction)` is a reusable timing policy. Call `ready(now)` before a capture and `completed(end, duration)` after it, using nanoseconds. Slow work extends the cooldown; `reset()` clears it when the owning world changes.

`ViewSceneRenderer` schedules each visible source independently at a configurable rate, defaulting to ten updates per second per source. The legacy `ViewCaptureBudget` remains available to other API consumers but is no longer used to throttle camera feeds. Targets are bounded to 320 pixels on either axis and only grow while retained. `captureDistance()` exposes the 96-block secondary view distance to rendering integrations. Each source compiles and retains its own terrain and ship meshes without changing the main view's occlusion state. Camera feeds omit clouds and weather. When Iris is present, an optional pipeline adapter keeps shader shadow and postprocessing passes in the main view; camera feeds render unshaded without reloading packs or changing chunk mesh settings.

## Camera tilt and perspective correction

ViewRig tilt now measures degrees from the mounting Up face: zero points Up, -90 points along the mounted north direction, +90 points along the mounted south direction, and the limits remain -135/+135. Locked resolves to zero tilt; Sentry sweeps around -90 for a horizontal patrol. Existing saved tilt numbers and Manual node scalars use this corrected reference without changing their serialized keys.

ViewRig.joints(Angles) exposes separate stand pan and camera hinge rotations for local rigs. ViewRig.orientation and worldOrientation use the same joints as the rendered partials.

ViewSceneRenderer uses Veil's perspective rendering with a separate LevelRenderer per source, including separate Sodium visibility state. Visible camera sections compile in the source's own terrain storage. Displays sharing a source reuse its GPU target, and each source retains its own refresh schedule.

## Rotating model collision shapes and physical target identity

`com.rieno.gadgetsandgizmos.lib.geometry.VoxelShapeTransforms.rotate(VoxelShape, Quaterniondc, Vec3)` rotates every shape box about the supplied block-local pivot. It returns the union of each rotated box's axis-aligned bounds, matching Minecraft's collision representation. Cardinal rotations preserve exact model coordinates; arbitrary angles conservatively enclose the geometry. The caller supplies model measurements and joint transforms, and caches shapes when appropriate. This API is available on both logical sides.

`ControllerDiscoveryNode.hasSameBlockTarget(ControllerDiscoveryNode)` compares a non-null block position and sub-level identity independently of node IDs, labels, block types and input/output roles. It returns false when either result lacks a physical block position. Callers remain responsible for checking selected faces and current availability before reading or writing a target.

`GraphTargetPortLayout.compose(targets, mergeLikePorts, stableTargetIds)` optionally maps current target IDs to their retained port identifiers. Generated ports and section IDs use those retained identifiers, while runtime bindings use the current target IDs. The existing two-argument overload retains its previous behavior. Callers persist this mapping when target reference IDs change but graph wiring should survive.

## Camera atmosphere and LOD compatibility

`ViewSceneRenderer.projectionDistance()` exposes the secondary scene's 1024-block projection plane, which contains vanilla's celestial and sky geometry. `captureDistance()` remains a separate terrain budget capped at 96 blocks and respects a smaller effective client render distance. Camera feeds apply spherical fog before this terrain limit and measure the lower sky horizon from the source lens.

`ViewSceneEnvironment.save()` returns a restoration callback for vanilla fog colours, water-biome transition history, fog uniforms and optional shared LOD camera state. Run the callback in a `finally` block after a secondary render. `ViewLodCompat.save()` provides the optional Distant Horizons state portion independently.

Display feeds render their own bounded vanilla/Sodium terrain rather than sharing a LOD renderer's occlusion or temporal buffers. Optional Voxy hooks keep its LOD allocation and viewports on the main player view, while retaining native chunk draws in feeds. Optional Distant Horizons hooks suppress its main-view terrain, transparency and fade passes during capture and restore its shared camera state afterwards. Neither integration changes the player's saved LOD or fog settings. All hooks are client-only and tolerate either mod being absent.


## Player interaction context and personal graph state

`lib.interaction.InteractionOrigin` captures a server player's UUID, name, dimension, position, rotation and interaction tick without retaining the entity. `BlockInteractionTracker.record(level, pos, player)` accepts authorized host interactions. Vanilla block-use, attack and pressure-plate interactions are recorded automatically, including blocks without block entities and targets on Sable sub-levels. `last` reads retained backend information; `recent` limits signal attribution to a caller-specified tick window. `signal(level, pos, origin)` propagates an attributed graph output or clears the recent attribution for an anonymous output. This metadata remains on the server and adds no visible graph ports. Each loaded level retains at most 4096 recently used block records; replacement and level unload invalidate old records.

`InteractionContext.run(origin, action)` scopes synchronous output writes and restores the previous context in `finally`. `NamedEvent.origin()` carries this optional context through internal event transports; the original constructor and factory remain available. Hosts must never accept a player identity supplied by a client as authoritative.

`GraphPlayerScopePlan.analyze` discovers personal UI/action dependencies, indirect variables, and stateful execution paths using host callbacks. `PlayerScopedMap` separates personal runtime keys by the current player UUID while retaining shared mechanical state. Its snapshot/restore APIs support host persistence, and `prunePlayers` removes obsolete keys after compilation. The map retains up to 128 player scopes per graph. ACC runtime saves personal HUD values and delayed event identities, samples HUD ports separately for each observer, and never substitutes every observer for an anonymous camera action.

## View rate, control, binding and UI APIs

`ViewRayBudget.next(tick, raysPerSecond)` distributes a finite scalar rate across twenty game ticks, including fractional rates and multiple rays per tick. It samples once per tick, caps rates at 1280 per second, and avoids accumulated work after unload or tick gaps. Camera saved `RaysPerTick` values migrate by multiplying by twenty; saved graph defaults and persistent values migrate similarly. Connected wires retain their IDs and connect to `rays_per_second`.

`ControlledViewSource` extends `ViewSource` with `ViewControlState` and `applyViewSettings`. `ViewControlInput` contains bounded relative pan/tilt, optional absolute FOV, mode and flashlight settings; validate it before mutation. `ViewControlSessions.apply` arbitrates short server control leases, validates loaded sources, excludes body-lock sessions, and permits one motion sample per source per tick. The host authorizes its bound source and world access before calling this API. Settings remain on the source after the lease expires; leases expire on disconnect, removal or inactivity. `release` ends ownership without changing settings.

`ViewSourceBindings` persists up to 128 unique, named references while keeping dimension and sub-level identity intact. It supports adding, renaming, removing and NBT round trips. Hosts own pairing, entitlement, distance and access checks. An unloaded source remains bound and appears unavailable until loaded again; the API does not request remote server chunks.

`lib.client.view.ViewControlPanel` renders and handles a bottom-centred analogue stick, zoom slider, mode selectors and flashlight toggle through a host command callback. `Canvas` supports normal GUIs and `ProjectedViewControlCanvas` supports existing world surfaces without allocating a framebuffer. Call `mouseReleased` when a screen, source or pointer capture closes. `ViewFeedGrid` draws and selects up to four visible sources using each source's independent refresh schedule and renderer. Camera controls and rendering APIs stay in client packages; control ownership and source state remain server-side.

`ViewControlPanel.renderOverlay(Canvas, Font, x, y, width, height, state, sender)` floats controls over a feed: an analogue stick and flashlight icon on the left, uppercase rounded mode buttons at the bottom, and a vertical zoom slider on the right. It leaves the scene unobstructed between widgets. `ViewControlLayout.overlay` and `compact` expose the exact geometry used for drawing and pointer handling; existing `render` overloads retain the tablet's compact strip. Overlay zoom moves from wide FOV at the bottom to narrow FOV at the top. Hosts scale drawing and pointer coordinates together.

Both control layouts convert horizontal stick deflection to the rig's pan convention: dragging left turns the view left, and dragging right turns it right.

## Surface floodlights

`ViewRaycast.traceBlocks` performs the existing Sable-aware trace without entity queries. `lib.physics.SurfaceFloodlight.update` samples a loaded client source at most every four ticks, projects a widening footprint onto actual hit faces, and exposes immutable `Patch` samples to client renderers. `profile(distance)` and `Profile.intensity(distanceFromCenter)` produce a small bright close patch and a wider fading distant patch. `close` removes all patches immediately; unload also releases them.

`lib.client.SurfaceFloodlightRenderer` draws the hit material with localized vertex fading in each scene's own depth buffer, including transformed sub-level faces. Its dedicated material shader adds brighter warm light independently of terrain shaders and optional deferred light backends. It uses independent vertex storage and does not change world blocks, terrain light storage, the player's culling state or camera textures. The common floodlight API loads safely on dedicated servers, where it performs no rendering.

`SurfaceFloodlightRenderer.renderScene(level, cameraPosition, viewMatrix, projectionMatrix)` draws patches into the host's current framebuffer with an independent frustum and material shader. It restores view/projection matrices, shader selection, texture bindings, program, blending, depth, polygon offset and culling state. Hosts using surface overlays can call it after a complete perspective render returns; the normal player view draws after its world shader processing and before the hand clears world depth. Each view retains a private GPU depth copy after opaque block entities finish. The material shader compares against that opaque visibility with a small distance and pixel-slope tolerance, so subsequent translucent and particle depth writes cannot erase the beam. Opaque intervening surfaces still block the light. Depth copies share the scene's attachment identity across shader framebuffer changes, refresh every frame and release on world unload. The lighting pass does not run inside shadow passes or use particle targets.

The client bootstrap calls `SurfaceFloodlightRenderer.registerShader(RegisterShadersEvent)` during shader reload. The `AFTER_LEVEL` subscriber retains and immediately consumes the current frame's matrices through `renderMainScene()`, after world compositing and before hand rendering clears depth. Secondary scene hosts should call `renderScene` directly instead. Hosts that dispatch the standard `AFTER_BLOCK_ENTITIES` stage automatically retain opaque depth; other hosts receive a fresh copy of their current target's depth when drawing the light. Targets without a depth texture retain conventional depth testing.

`ViewShaderCompat.terrainShader(ResourceLocation, String)` adapts Sodium's terrain include only during an active secondary capture using Iris's extended terrain format. It centres lightmap samples, applies separately encoded ambient occlusion to colour, and supplies cutout material settings instead of interpreting tangent handedness as a material. It leaves the player's shader sources and shared mesh settings unchanged.

Each captured scene now owns its Sable sub-level dispatcher and compilation queue as well as its world renderer and meshes. `ViewSceneRenderer.captureSubLevelDispatcher()` returns that dispatcher during a capture and `null` outside it; the client integration routes Sable's dispatcher lookup through this hook. Capture overrides apply only on the render thread, so asynchronous player mesh jobs retain the player pipeline and sub-level data. Queued feed uploads cannot run through the player's sub-level dispatcher. Closing or replacing a scene cancels its mesh jobs and releases its dispatcher.

`ViewSceneRenderer.invalidateScenes(LevelRenderer)` retires cached camera scenes when the main renderer rebuilds for shader changes, resource reloads or terrain refreshes. Calls from a captured renderer are ignored. The next requested capture recreates its renderer, dispatcher and ship meshes while retaining its source and texture registration.

`ViewShaderCompat.withMeshFormat(Runnable)` configures an upload using the vertex format stored in its mesh instead of Iris's current automatic format expansion. It restores Iris's layout flag even when the upload throws, and invokes the callback directly when Iris is absent. Camera vertex buffers retain their ownership at allocation so delayed uploads also use this scope after the capture ends. A mesh compiled with 32-byte vertices therefore keeps a 32-byte GPU stride even if the player enables shaders before its upload completes; explicitly extended meshes retain their own format. Player-owned buffer uploads keep their existing handling.

`ViewSource.updateViewEffects(partialTick)` is an additive client callback with an empty default. The capture facade calls it for the selected source before rendering, so effects remain current when a remote block entity is loaded but outside the client's ticking area. Implementations must guard their client work and retain the same effect instance used by normal source ticks.

## Safe native sub-level removal

`lib.physics.SableSubLevelLifecycleApi.remove(ServerLevel, Collection<ServerSubLevel>)` permanently removes loaded bodies on the owning server thread. It validates the entire selection, evacuates entities, unloads every selected plot while all native bodies remain available, then removes the bodies from their container and native storage. Duplicate selections are removed once. Foreign, unavailable bodies and calls during a physics step are rejected before mutation.

`SubLevelArchiveApi` uses this lifecycle for storing, deleting and extraction rollback. Serialization and durable archive creation still happen before unloading. Archive format, ownership and body identities remain unchanged; full body data stays in server archive files, while the tablet receives catalogue metadata and bounded previews.

The overload accepting `partialTick` also matches moving sub-level surfaces to their interpolated render pose, keeping the light overlay on the visible face and in the same depth buffer. Pass the scene's game-time partial tick; the overload without it uses the current pose.

`InteractionContext.runOutputs(origins, action)` retains a batch's per-binding interaction origins. Hosts use `runOutput(bindingId, action)` around each final binding write so mixed-player batches and scalar-derived pulses cannot assign one player's identity to another output. An explicit null origin clears attribution for anonymous writes; nested batches restore the prior context even on failure.

`TabletAppClientContext.forSurface()` retains actions, dialogs and app state while expanding the canvas to the host's complete app surface. CCTV uses this surface so its grid and controls fit inside the standard tablet. `ViewSceneRenderer.drawFitted` draws a feed inside the requested bounds while preserving the current capture's aspect ratio; the existing `draw` behavior remains available.

Floodlight material geometry is cached between beam updates and invalidated when a hit block or baked model changes. Each render checks its own frustum, transforms only visible sub-level patches, and prunes obsolete cached geometry; static beams do not rebuild their surface meshes every frame.

`PlayerScopedMap.sharedSnapshot()` exports shared runtime values independently of the active player context. Hosts must use it for the shared portion of a save, with `playerSnapshot()` stored separately, so saving from inside a player-triggered action cannot promote personal values into global defaults. Camera UI control explicitly retains its selected mode even when it already matches a Manual graph; unchanged graph scalars leave the stick aim intact, while changed scalars retake graph control.

Independent controller keys retain their per-binding player owner while active. Personal HUD sampling uses that owner before the controller block's latest general interaction, so a second player pressing another key cannot take over the first player's held input.

## Deferred worker recipe lookup

The addon owns worker assignment, saved orders, routing links and the displayed `Looking up recipe` status. Shared indexing, asynchronous computation, result caching and tick budgeting belong to the library APIs below.

### Server work scheduling

`com.rieno.gadgetsandgizmos.lib.util.DeferredWorkScheduler.forServer(server)` shares one scheduler across consumers on a server. `submit` evaluates detached data on two background threads. Background tasks use `onOwnerThread` for recipe adapters, registry probes and live world or inventory reads. Never access Minecraft world state directly from the submitted task.

NeoForge post-tick events service at most 64 live queries within a shared 2 ms soft budget. A running callback completes before the budget is checked again, so callbacks must remain small. `checkpoint` yields long graph searches every 256 operations without restarting their recursive state. Admission is bounded to 64 unfinished jobs. Server shutdown cancels jobs and releases background threads.

`DeferredLookup<K,V>` provides bounded completed-result caching and retains unfinished lookups. `get` throws `DeferredWorkScheduler.Pending` until its result is available. `getForRequest` separates a stable request identity from changing snapshot keys, so changing inventory counts cannot continually restart a running search. `forgetRequests` releases cancelled request identities; `clear` cancels the consumer's cached work.

### Recipe discovery and planning

`WorkerRecipeCatalog.prepareIndex(level)` starts or returns the shared asynchronous index for that server recipe manager. `deferredIndex(level)` polls it and throws `Pending` while building. `producingRecipes(level, output)` retrieves only matching recipe holders, including Create chance outputs and fluids. Opaque ingredient probes and axe transformations are processed in batches of 32 registry entries. Recipe reloads and server shutdown invalidate the catalog automatically. Client and server recipe managers have separate indexes; the synchronous `index` API remains available for existing consumers.

`WorkerRecipeCatalog.supportedDeferred(scheduler, level, definition, supported)` checks each ingredient variant through a separate owner-thread query. Call this from scheduled background work. The supplied plan predicate runs on the server thread.

`WorkerRecipeLookup.plan` and `prerequisites` perform graph search on detached snapshots and cache positive and negative results. Plan and repair caches retain up to 64 entries each for 20 ticks after completion; relevant-resource lookups retain up to 128 entries per index. Supply an immutable context that identifies relevant machine, stock and tool state. `RecipeSupport` runs in the background and must schedule every live check through its supplied scheduler; executable-plan, final-route, routing-cost and ingredient-stock callbacks run on the owner thread.

Wrap a context in `WorkerRecipeLookup.RequestContext(requestId, state)` to retain in-flight progress for one saved order even while inventory counts change. Results from that original snapshot are delivered once, then later requests use current snapshot keys. Execution must still validate and reserve live inputs. `cancel(requestId)` releases the request's pending identities; `clear` cancels all of that consumer's searches on link changes or unload.

`WorkerRecipeIndex.recipes(recipeId)` resolves a saved recipe's machine variants directly. Dependency and relevant-resource caches are bounded to 128 outputs, and dependency traversal schedules each resource once.

### Saved lookup orders

`WorkerWorkOrder.recipeLookup(task, operation, sourceId, destinationId, processorId, returnStationId)` creates a normal saved and cancellable lookup order. `operation == PROCESSING` restricts the final operation to processing; other values allow automatic production. `lookingUpRecipe()` identifies the additive modes `lookup_recipe` and `lookup_processing_recipe`. Existing mode ids and saved orders retain their behavior.

Once lookup completes, the addon replaces that order with its complete dependency-first chain. Missing inputs and assembly retries use the same deferred planning APIs and display the lookup status while waiting. Unavailable recipes are reported in the worker's status after evaluation.

The additive `WorkerRecipeChain.orders(..., sourceId, processorId)` overload binds the last recipe visit to a selected processor while preserving a later delivery order.

## On-demand worker recipe lookup

`WorkerRecipeCatalog.prepareLookup(Level)` warms output-to-recipe links and world interaction outputs within the shared server tick budget. It returns a readiness future without resolving unrelated ingredients. Normal server startup uses this method.

`WorkerRecipeCatalog.deferredIndex(Level, WorkerResourceKey)` returns a cached index containing producers reachable from the requested output. It throws `DeferredWorkScheduler.Pending` while metadata or relevant ingredients are being resolved. Consumers poll on later ticks. Recipe adaptations are shared across output requests, and reloads cancel and invalidate both metadata and dependency indexes. Completed output indexes are bounded to 128 entries per recipe manager.

`producingRecipes(Level, WorkerResourceKey)` uses the lightweight output catalog, so legacy execution checks do not wait for full ingredient discovery. Existing full-index methods remain available for consumers that explicitly need every recipe.

`WorkerRecipeCatalog.Adapter.outputs(Level, RecipeHolder<?>)` can declare output keys without evaluating ingredients. Its default implementation derives outputs from the existing `recipes` method, preserving adapter compatibility. Adapters with costly ingredient resolution should override it with a cheap output description.

The additive `supportedDeferred(scheduler, level, recipe, routable, supported)` overload first checks whether a linked processor can run the recipe at all. `routable` describes recipe identity and processor type, independent of the selected ingredient variant; both callbacks run on the owner thread. When no direct processor exists, composite stages are checked without probing every root ingredient variant. Worker crafting that needs no processor remains supported. The existing overload retains its behavior.

## Sublevel attachments and retargetable fixed constraints

`com.rieno.gadgetsandgizmos.lib.physics.SubLevelBlockAttachment` is an opt-in block contract. Implement `isAttachedTo(BlockState state, Direction supportDir)` to identify the adjacent support a block should travel with. `supportDir` points from the attachment toward its support. Keep gameplay-specific face and mode state in the consuming addon.

`SubLevelAttachmentApi.includeAttachments(ServerLevel, Iterable<BlockPos>)` expands a selection with attached neighbors without gathering unrelated supports. The library's `SubLevelAssemblyAttachmentsMixin` applies it to Sable assembly and block movement. Native movement still copies state and complete block entity data.

`SubLevelAttachmentApi.moveBlocks(ServerLevel, AssemblyTransform, Iterable<BlockPos>, Runnable)` guards both source and destination positions for the duration of the transfer. `isMoving(Level, BlockPos)` lets a consuming block suppress temporary support checks. Guards are nested and cleared even after a failed transfer. `trackingBounds(AssemblyTransform, BoundingBox3ic)` consumes the completed movement bounds and includes attachments in the following native tracking-point transfer.

`SableConstraintApi.fixedConfiguration(Vector3dc, Vector3dc, Quaterniondc)` creates the fixed configuration; `addConstraint` adds it through the compatible pipeline. `setFrame(PhysicsConstraintHandle, int, Vector3dc, Quaterniondc)` accepts frame 1 or 2 and now supports Rapier fixed handles, including legacy/current scene field names, through a cached native frame bridge. Retarget frames and wake affected bodies on the owning server physics path rather than adding a spring motor.

`SableConstraintApi.rotaryOrientation(Quaterniondc baseFrame, Quaterniondc mountedFrame, double angleRadians)` computes `baseFrame * rotationY(angleRadians) * conjugate(mountedFrame)`. Addon bearings retain control over their requested angles, anchors and mounted-body lifecycle. Existing configuration methods remain compatible, and no saved identifiers or NBT formats change.

## Sublevel schematics and paced construction

Reusable schematic APIs live in `com.rieno.gadgetsandgizmos.lib.physics.archive`. Gameplay policy, Worker network selection, tablet screens and temporary mannequin presentation remain in the consuming addon.

- `SubLevelSchematic` is an immutable assembly of bodies, local block states and safe block entity configuration. Body orientations and block NBT are copied. `preview(limit)` provides bounded render samples; builds use every block.
- `SubLevelSchematicArchive.capture(player, archiveId, maximumBodies, maximumBlocks)` exports a complete stored assembly without consuming the archive. It checks ownership, dimension and limits, maps native body references into fresh template identities, and applies Create's safe schematic NBT policy. Inventory contents are excluded.
- `SubLevelSchematicFiles.catalogue(server)` lists flat `.nbt` files in the server's `schematics` directory using opaque file IDs. `load(level, id, maximumBodies, maximumBlocks)` accepts vanilla block structures and Sable's `sub_levels` extension, sanitizes configuration, and checks block registrations, dimensions, body IDs and block counts. Entity templates and nested sublevels are rejected. The server overload uses the overworld registry context. `save(server, name, schematic)` restricts names to letters, digits, underscores and hyphens, preserves existing files with numbered suffixes, and writes compressed native-compatible structures. `decode`, `encode` and `sanitize` support explicit import/export integrations.
- `SchematicMaterials.requirements(level, schematic)` uses Create's requirement registry, including double slabs, strict components and durable tools. Unsupported survival costs produce a visible error for the consumer. `shortages(requirements, endpoints)` simulates stock availability; `reserve(player, refundPos, requirements, endpoints)` withdraws the complete bill from real `WorkerEndpoint` inventories. The consumer filters endpoint authorization, network membership and shared inventory identities. Call `Reservation.commit()` after successful construction or `refund()` after removing an incomplete build. Both are idempotent. Refunds prefer the original storage, then the online player's inventory, then item drops at the original destination. These reservations are held in memory; consumers must clean up on disconnect, cancellation and normal server shutdown.
- `SubLevelSchematicBuild(player, schematic, target, rotation)` validates loaded destination bounds, claims, collisions, plot dimensions and a normalized world rotation before allocating fresh body IDs. `tick(maximumBlocks)` builds a bounded batch in bottom-to-top world layers and returns block positions for presentation. Zero-sized batches retain the pose while waiting. `placedBlocks()`, `totalBlocks()`, `complete()` and `bodyIds()` expose progress. `close()` removes an incomplete assembly; a completed assembly remains in the world. Native splitting, invalid-mass removal, block entity ticks, interaction and actor forces are suppressed while construction owns a body. Reference mapping uses Sable's native schematic serialization context so configuration can refer to newly allocated bodies.
- `SubLevelConstructionState.isBuilding(body)`, `pose(body)` and `mass(body, original)` expose lifecycle state for native integration callbacks. The library owns retention and release. Consumers that replace Sable's removal loop must skip invalid-mass removal for bodies where `isBuilding` is true. The pose compensates for changes to the native center of mass while retaining the intended world placement. Empty bodies receive finite temporary mass until their scheduled blocks supply physical mass; no placeholder world blocks are inserted. Completed bodies must have valid native mass before construction releases them.

Mutation must run on the owning server thread. The consumer decides creative bypass, required network membership, pacing, visual entities and UI. Keep the selected template immutable throughout construction, call `close()` before refunding cancellation, and release visual entities in every completion or failure path.

```java
var template = SubLevelSchematicFiles.load(player.serverLevel(), fileId, bodyLimit, blockLimit);
var requirements = SchematicMaterials.requirements(player.serverLevel(), template);
var materials = SchematicMaterials.reserve(player, target, requirements, authorizedEndpoints);
SubLevelSchematicBuild build;
try{
    build = new SubLevelSchematicBuild(player, template, target, rotation);
}catch(RuntimeException err){
    materials.refund();
    throw err;
}
// Retain build and materials in the consumer's server tick session
// Each tick, until completion or failure:
try{
    build.tick(batchSize);
    if(build.complete()){
        materials.commit();
        build.close();
    }
}catch(RuntimeException err){
    build.close();
    materials.refund();
    throw err;
}
```

The consumer retains the build across ticks and closes it after completion or cancellation.

## Persistent picked block targets

`SubLevelBlockTargetApi.resolve(level, target)` binds a loaded block to Sable's saved tracking points and returns its current SubLevel UUID and block position. Keep the target ID stable when storing the returned location. Native assembly and disassembly transfers move the binding, including transfers between nested SubLevels and the world. Unloaded or missing targets return `null` without replacing their saved tracking points.

`ControllerDirectTargetReference.withFace(face)` adds an optional selected face. The additive `Face` NBT key is omitted for legacy references. `withLocation(subLevelId, pos)` preserves the target ID, compatibility mode and original face; `ControllerDiscoveryNode.withLocation(subLevelId, pos)` preserves discovery metadata.

`SubLevelBlockTargetApi.resolveFace(level, target)` returns the selected face in the block's current coordinate frame. Retain the original face in saved references and use the resolved face for current reads and writes. Three native markers inside the block track assembly rotations without changing generated graph port IDs or saved face-map keys. Calls that register or resolve server bindings belong on the owning game thread.

`BlockStateDataAccess.write` checks loaded Sable plot chunks through `SubLevelBlockEntityCollector.isTargetLoaded`, so loaded child SubLevels accept state-property writes without requiring a world chunk at their plot position. Unloaded targets remain unavailable.

## Worker planning from current stock

`WorkerRecipeSource` supplies detached producer definitions for one requested resource. Sources returned by `WorkerRecipeCatalog.deferredSource(Level)` are shared for each recipe-manager generation. Obtain the source on the server thread and call `producing` only from `DeferredWorkScheduler` background work. Producer lists, including empty lists, remain cached until recipe/tag reload or server shutdown. Ordinary producer adaptation is batched within the shared owner-thread budget; opaque ingredient probes remain sliced. `resolvedRecipes(Level, ResourceLocation)` reads previously adapted definitions on the owner thread without starting a dependency search.

`WorkerRecipePlanner.planFromStock` and `prerequisitesFromStock` reserve existing stock first and resolve producers only for missing inputs. They stop at the first complete executable schedule in deterministic stock and routing preference order, retaining sibling-input backtracking, quantity accounting, cycle limits and machine-input credits. These methods avoid eager graph ranking and exhaustive comparison of all successful recipe trees. Existing exhaustive planning APIs remain available.

`WorkerRecipeLookup.planFromStock` and `prerequisitesFromStock` provide deferred, bounded cached versions of these operations. Successful plans and repairs no longer expire after one second; changed resource amounts, recipe-source generation or caller context select a new computation. Consumers must include machine and tool state in their immutable context and clear the lookup after link/configuration changes. Saved queued plans should execute directly; repair or replan only when their live inputs or routes are unavailable.

The existing two-argument `WorkerRecipeLookup.RequestContext` constructor remains available. Its additive three-argument constructor accepts `refresh=true` for an explicit retry after a cached route fails. `DeferredLookup.refreshForRequest` replaces a completed result once and preserves the new attempt across pending polls, including changes to live stock while that attempt runs.


## Schematic file formats and private client catalogues

`SubLevelSchematicFiles` now accepts gzip-compressed or raw structure NBT, Photomancy v1 block blueprints, and Toolgun native v8/v9 `.excraft` plot archives. Readers preserve body orientation, saved height, palette indexes and block entity configuration. Existing size/body/block limits and safe inventory stripping still apply. Entity/contraption payloads are rejected visibly; they are not silently converted to blocks.

Shared files live in the dedicated server's `schematics/` directory. Integrated servers use the world's `schematics/` directory, keeping the host's game-directory client files private during LAN play. Exports use the same shared directory. The catalogue scans nested folders up to five levels and accepts `.nbt`/`.excraft` case-insensitively. Opaque IDs for existing top-level shared filenames remain unchanged.

New reusable APIs:

- `catalogue(Path, String idPrefix)` scans a caller-owned folder without reading block payloads. `read(Path, String, UUID)` resolves the opaque ID again, bounds bytes and rejects escaping/symbolic-link files. `decode(HolderGetter<Block>, byte[], int, int)` applies a 64 MiB file / 256 MiB decoded NBT budget.
- `SubLevelSchematic.Body.importFrame()` retains a `Format`, original UUID, source coordinate origin and blueprint-local ID until `sanitize` translates references. The original five-argument body constructor remains available. Native encoding preserves unsanitized import frames; `sanitize` consumes them and returns native template bodies.
- `SchematicImportAdapters.register(ResourceLocation, Adapter)` allows a consuming mod to translate its owned foreign NBT references before safe serialization. Register during common setup under the owning mod's namespace. Adapters receive the immutable assembly/body/block and an independent mutable configuration tag; they must not mutate the world.
- `SchematicClientFiles.receive(server, authenticatedOwner, fileId, total, offset, chunk)` receives ordered slices up to 32 KiB. Consumers must authenticate feature and source permissions before calling. Files are retained in memory for their owner only, never written into or added to the shared catalogue. There is one selection/transfer per player, a 64 MiB file limit and a 128 MiB aggregate server limit. Incomplete transfers expire after 60 seconds; logout and server stop release retained bytes. `contains`, `load` and `remove` require the owning server thread. Loading uses the same server registry and safe NBT policy as shared files.
- Client-only `ClientSchematicFiles.catalogue()/refresh()` scan `schematics/`, `Sable-Schematics/` and `enxv_aeronautics_structures/` asynchronously. `upload(UUID, Consumer<Chunk>, Consumer<Exception>)` reads one selected file in the background and sends at most four chunks per client tick through the consumer's network bridge. `status()` exposes transfer/read failures and progress. Disconnect clears local transfer state. Do not load this class on a dedicated server.

File privacy concerns the catalogue and retained upload. A completed construction still belongs to the authoritative multiplayer world and uses the existing survival material reservation policy.

Format references: [Photomancy blueprint source](https://github.com/Rew1nd-dev/sable-schematic-api/blob/master/src/main/java/dev/rew1nd/sableschematicapi/blueprint/SableBlueprint.java), [Toolgun native format](https://github.com/userenxv/create-aeronautics-toolgun/blob/main/src/main/java/com/enxv/aeronauticsstructuretool/blueprint/codec/NativeBlueprintFormat.java).

Machine identity checks for stock-first searches use `WorkerRecipeCatalog.routableDeferred`; ingredient variants are validated only after stock selects the actual plan. This avoids tag-wide physical route probes before selecting a stocked ingredient. Valid schedules and repairs retain their cache entries; unavailable results retry after 20 ticks to observe newly usable world routes. The additive `DeferredLookup(int, long, ToLongFunction<V>)` constructor selects retention from a completed result while preserving the existing fixed-retention constructor.

## Rigid fixed attachments

`SableConstraintApi.rigidFixedConstraint(ServerLevel, ServerSubLevel, ServerSubLevel, Vector3dc, Vector3dc, Quaterniondc)` creates a reusable `SableRigidConstraint` implementing Sable's `FixedConstraintHandle`. Either body may be null to attach to the world. Positions use each body's plot coordinates, or world coordinates for a null body. The initial orientation relates body A to body B.

The attachment retains a native Rapier fixed joint for impulses and load reaction. Sable 2.0.3 gives its fixed joints spring softness; the library removes the resulting pose and relative velocity error after every physics substep. Connected parent, child, nested, and sibling bodies are corrected together. Freely moving attachments preserve their combined center of mass and linear and angular momentum. World attachments transfer reaction to the world.

Use `SableConstraintApi.setFrame(handle, 1, position, orientation)` and frame 2 to change the controlled pose. Frames are synchronized after block actors update, before the next native solver step. Requested frame motion determines the relative velocities; no spring motors are used. The library updates native and logical poses before later physics consumers.

Creation returns null for unavailable bodies, duplicate child parents, or cycles. Invalid frame positions or rotations throw `IllegalArgumentException`. Remove the handle when its owner detaches; level unload and server shutdown also release retained handles before their native scenes close. Existing plain fixed configurations and generic constraint APIs retain their behavior.
## Schematic attachment frames

`SubLevelSchematic` additionally accepts an immutable `List<Joint>`; its existing one-argument constructor remains available. A `Joint` contains its two template body IDs, `JointType` (`FIXED`, `FREE`, `BEARING`), compact body-local anchors, relative orientation and bearing axes. Invalid endpoints, poses and axes are rejected before construction.

`SubLevelSchematicFiles` retains joints in its namespaced native NBT extension. Toolgun v8/v9 welds with `constraint_space=saved_plot_local_v1` are supported; earlier weld coordinate encodings produce an explicit compatibility error. Safe block configuration conversion preserves these attachment frames.

`SubLevelSchematicBuild` creates all native constraints after the last block and rolls back a failed installation. `SubLevelSchematicJoints.retained(ServerSubLevel)` exposes saved native plot-local frames. These frames are kept in sublevel user data and recreated when both bodies load. Assembly discovery includes their structural links, and archived schematic export remaps them to independent template identities and compact coordinates.

## Schematic imports with unavailable blocks

`SubLevelSchematicFiles.decode` skips block palette entries that are unavailable in the receiving registry for native NBT, Photomancy v1, and Toolgun v8/v9. Photomancy unavailable palette IDs are skipped too. Supported blocks keep their world placement and safe block data; material requirements count only supported blocks. Bodies with no available blocks and welds attached to those bodies are omitted. Unknown weld endpoints and malformed geometry still fail validation. A file containing no available blocks reports that condition. This applies to shared server files and private client uploads.

## Bounded entity skylight

Client API: `SubLevelEntityLighting.skyLight(int sky, Vector3dc worldProbe, Iterable<? extends SubLevel> bodies)` returns world skylight shaded by intersecting client plots, preserving Sable skylight scaling. Supply an unpacked skylight value from 0 to 15 and world-space probe coordinates. Empty plots and non-finite transforms are ignored; scans stay inside each plot and the level build height, with at most 4096 vertical samples per plot. A client renderer mixin applies the API to Sable entity skylight, including player mannequin proxies and shader shadow passes. Extreme finite coordinates are floored without integer wraparound.


## Schematic placement and particle lighting

`SubLevelEntityLighting.particleLight(int, Vector3dc, Iterable<? extends SubLevel>)` preserves plot block light and samples skylight within valid plot/build bounds using logical poses. Empty construction plots are skipped. Entity skylight retains render-pose sampling. The client particle mixin applies this API before Sable particle lighting.

`SchematicClientFiles` retains the completed private selection while a replacement is incomplete. Both allocations count toward the server byte limit. `cancelUpload(MinecraftServer, UUID)` discards only an incomplete transfer; `remove` still releases all player bytes. Incomplete-transfer expiry preserves the ready selection.

`SubLevelSchematicFiles.sanitize` removes missing inventory items and unavailable item components before invoking block entity loaders, preserving installed blocks and their safe configuration.

Schematic sanitization also preserves block states whose entity factory legitimately returns null, including Create train-door upper halves. Such states keep their blocks without block entity configuration.

`ItemStackNbtSanitizer.withoutUnavailableItems(CompoundTag)` returns a copy that drops nested item stacks whose registry IDs are unavailable and strips unavailable component types from installed items. It preserves surrounding configuration and never changes the supplied NBT. Consumers of compressed NBT must invoke it after decoding; controller schematic payloads now use this API.


## Hosted block entities and native vector allocation

Added reusable APIs under `com.rieno.gadgetsandgizmos.lib`; the library contains no Flight Control implementation imports.

### HostedBlockEntities

`HostedBlockEntities.publish(host, components)` publishes a detached component snapshot. Components must share the host's level, have distinct positions, and occupy neither the host position nor a real block entity position. Publication rejects another live host's identities. It places no blocks and does not override global world lookups.

Integrations explicitly use `resolve(level, pos)` for native linking and `host(component)` for the real owner. Use `positionAvailable(host, pos)` when assigning identities and `components(host)` for an immutable snapshot. Call `remove(host)` on unload or removal. `notifyHost(component, sync)` routes persistence and SmartBlockEntity synchronization onto the owner's game thread. `physicsTick(host, subLevel, handle, dt, active)` dispatches native Sable actor callbacks once through the real host.

`SableLevelApi.containing(BlockEntity)` now resolves a published component through its owner, while preserving ordinary block entity behavior. Dirty and SmartBlockEntity synchronization mixins forward hosted state to its real host.

### VectorThrustReceiver

`VectorThrustReceiver.applyVectorControllerForce(channelId, force)` accepts body-local force including a retained zero command. `releaseVectorControllerForce(channelId)` releases that channel's ownership. Native integrations implement their engine constraints and ownership rules.

### ScmVectorAllocationRegistry

Register an `Allocator` strictly under the integration's namespace using `register(id, allocator)`. `Unit(target, blockEntity, momentArm, fullForce, fullTorque)` preserves actuator identity and physical geometry; the block entity may be absent. All vectors must be finite.

`Request(host, rootSubLevelId, centerOfMass, units, force, torque)` carries a physical wrench in the root body's local frame. Moment arms and requested torque are relative to the supplied center of mass. Host, body ID and center are optional for standalone use. The shorter constructors remain available.

`allocate(request)` or `allocateBatch(requests)` selects the first allocator supporting the complete request. `supportsBatch` defaults to supporting every constituent request. `allocate` returns immutable per-unit forces in exactly the input order and a finite, nonnegative residual. An incorrect result size is rejected. `Allocator.apply(requests, results)` publishes commands only after every result has been validated; its default implementation does nothing. This supports coupled carriages without publishing a partially solved batch.

### GraphNodePresentationRegistry

Strictly register `Presentation(defaults, sections, options)` under a namespaced node type. The two-argument constructor omits enum options. `initialize(type, data)` encodes typed nested defaults, input section labels, ordered ports and option lists into editor NBT. `orderedInputs(type, ports)` keeps each collapsible section contiguous while retaining undeclared ports. All presentation collections are immutable copies.

`GraphValue.of(value)`, `member(key)` and `entries()` support recursive map/list/scalar data shared by integration schemas. `GraphExecutionContext.nodeId()` has an empty default and allows consumers to resolve host-owned state for the executing node.

Validation: clean library build, API boundary and mixin checks, hosted ownership/collision/physics tests, presentation tests, finite vector and atomic allocation batch tests.

`HostedBlockEntities.updateState(level, pos, state)` retains a native visual block state on a published component and synchronizes its real host. It returns false for ordinary world positions, allowing the integration to retain its native world update path. Native integrations can use this to prevent an animation update from placing a detached control block.

`positionForSlot(host, slot, width)` provides stable nearby detached identities, switching below the host near maximum world height. `findAvailableSlot(host, firstSlot, width, reserved)` skips real block entities, occupied identities, reserved positions and invalid height. Keep hosted publication alive until native component cleanup finishes; `notifyHost` consumes late synchronization from a still-published component whose host is already removed.

`GraphHostServices.NODE_OUTPUTS` exposes the reusable `GraphNodeOutputs.snapshot(nodeId)` callback. Hosts may provide this read-only snapshot service during preview evaluation while withholding the mutable `BLOCK_ENTITY` service. Native component IDs, profile references and telemetry can therefore be wired into settings without executing control side effects.


## Cooperative control and editable block bindings

`ScmOrientation.toBody`, `fromBody` and `rotation` map canonical north/up coordinates to any of the 24 signed craft frames. `SableBodyFrameView` presents logical/render orientation and inertia in a configured craft frame while retaining real block positions. `SableImpulseCapture` captures requested world impulses without applying them and converts a substep into physical force/torque.

`BlockEntityBindings.replace/host/remove` associate physical components with a graph host without changing world lookup, ticker registration, or component lifetime. Ownership is exclusive and release does not remove the physical block.

`GraphBlockSettings.reconcile` imports untouched native defaults on first binding, propagates subsequent block GUI edits into graph defaults, and gives changed or wired graph settings priority during concurrent edits. Its immutable result separates graph and block patches.

`ScmWrenchSourceRegistry` strictly registers namespaced sources that supply fresh physical force and torque about a requested root-body center of mass. The SCM remains responsible for control priority, allocation and actuator output; sources publish requests rather than driving actuators independently.

## Optional native control frames and data synchronization

`SableBodyFrameHandle(delegate, frame)` converts controller-frame impulses to physical body axes, preserves point positions and world velocity reads, and rejects direct velocity changes and teleportation. `applyForcesAndReset` consumes and resets a queued force total. Use it with `SableImpulseCapture` when evaluating an optional controller without applying native impulses.

`ScmPrecisionAllocator.normalizedForce(units, force)` and `normalizedTorque(units, torque)` convert physical requests to signed normalized capacity. `ScmControlPriority.remainingForce(request, primaryActions, forward, up)` and `remainingTorque` exclude axes claimed by primary SCM actions, permitting optional control sources to cooperate on the remaining axes.

`GraphBlockPosition.value(pos)` encodes block coordinates or an empty optional selection. `read(value)` returns a block position or null when any coordinate is absent, invalid, non-finite or outside the integer range; it never substitutes the world origin for missing coordinates.

`BlockEntityDataSync.enqueue(component)` coalesces queued synchronization requests, checks world identity on the server thread and sends the component update packet to tracking players, including Sable plot tracking. It does not send a block state update or rebuild chunk meshes. `HostedBlockEntities.notifyHostData(component, sync)` forwards dirty state and optional packet-only synchronization to a detached component's host. Existing `notifyHost` behavior remains available.

### Environmental support in optional wrench sources

`ScmWrenchSourceRegistry.Wrench` adds `compensationForce()`, identifying the environmental support already included in `force()`. The three-argument constructor remains available and defaults that vector to zero. Sampling aggregates compensation only from active sources. `ScmControlPriority.withoutSharedCompensation(request, optionalCompensation, primaryCompensation)` removes optional environmental support along the primary support axis while preserving velocity feedback and perpendicular control.

### Physical tensor and wrench references

`ScmOrientation.fromBodyTensor(Matrix3dc)` expresses a physical body tensor in canonical control axes using the same signed frame as vector conversion. It preserves cross-axis inertia terms and does not mutate its input.

`ScmWrenchSourceRegistry.Wrench.aboutCenter(Vec3 sourceCenter, Vec3 targetCenter)` moves a wrench's torque reference between finite centers in the same coordinate frame. Force and environmental compensation remain unchanged; torque gains `(sourceCenter - targetCenter) cross force`.

### Data updates for physical bindings

Bound physical Create components keep their normal lifetime and tick ownership. Their `sendData()` calls send the component's block-entity packet through `BlockEntityDataSync` instead of invalidating its block or chunk mesh. Detached Create components use `HostedBlockEntities.notifyHostData` to send the real host's packet. Block-state changes still use the owning level's normal state update API.

## Interpolated sublevel overlays

`SubLevelClientRenderApi.localModelView(subLevel, partialTicks, localOrigin, camera, view)` builds a camera-relative model-view matrix from Sable's interpolated client pose. Translation is calculated in doubles before converting to floats, so large plot coordinates retain precision. Body rotation and scale are applied once; local overlay offsets can then be composed onto the returned matrix. The pose overload accepts an explicit `Pose3dc`; a null body or pose supports ordinary world coordinates. This API does not depend on block-entity mesh visibility or frustum culling.

## Signed optional SCM intent

`ScmControlPriority.additionalWrench(request, primaryCompensation)` preserves all signed force and torque components of an active optional request, including axes already used by primary SCM commands. Callers add the returned demand before the shared actuator allocation; opposing requests subtract. Environmental support already supplied by the primary controller is removed from the optional request on that support axis while perpendicular compensation and control feedback remain. Inactive requests return `Wrench.NONE`. Sources remain responsible for publishing only fresh, explicitly requested control intent. Existing axis-priority methods remain available for consumers that require exclusive axes.


## Stable schematic file selections

`SubLevelSchematicFiles.catalogue` returns all supported files within the existing directory depth, including entries beyond the previous 128-file cutoff. IDs remain derived from the namespace prefix and relative filename. `read(Path, String, Entry)` reads a retained catalogue selection directly and checks its ID, extension, directory boundary and file-size limit. The existing UUID overload resolves against the complete catalogue. Client schematic reads use the retained entry instead of rescanning the directory.

Detached Create block entities used by schematic import, archive export and material callbacks are marked virtual before their configuration is loaded. Consumers can use Create's `isVirtual()` flag to avoid synchronizing or mutating live world state while these callbacks read saved data.

## Automatic component binding and detached projections

`BlockEntityBindings.select(host, components, current, reserved, matches)` selects a live component from a caller-supplied sublevel roster. It retains a valid current match, otherwise chooses the nearest compatible block with a stable position tie break. Removed blocks, other levels, reserved components and other controllers' bindings are excluded. Selection does not mutate ownership; publish the resulting bindings with `replace` on the game thread. A missing match returns null.

`GraphNodePresentationRegistry.removeInputs(data, ports)` retires defaults, dynamic inputs, input options, ordering and section metadata while retaining unrelated node settings. Consumers also retire their own persistent values and graph edges.

`SubLevelClientRenderApi.anchoredPose(clientSubLevel, partialTicks, origin, anchor, localRotation)` supplies an interpolated render pose for a detached component. Its synthetic local origin maps to the real anchor in the sublevel, and the local rotation composes with the interpolated body orientation. Use the returned pose with `localModelView` to preserve precision at large plot coordinates. This client-only API neither moves blocks nor changes physics poses.

`SubLevelProjectionContext.render(component, pose, action)` scopes a detached projection pose during a native renderer call on the render thread. `pose(component)` returns the matching pose and `pose()` returns the current pose. Both return null outside a scope. Nested scopes restore previous state even if a renderer throws. This API supplies rendering context only and does not override the real body pose.

`SubLevelWeatherHeight.rainHeight(Level, int x, int offset, int z, Iterable<? extends SubLevel>)` samples loaded solid/fluid shelter with valid plot bounds, finite transforms, world-height clamps and a 4096-sample vertical budget. Empty construction plots keep the vanilla weather height.

`SubLevelSchematicBuild` adds a constructor with `maximumSections` (1-64), `sections(List<Placement>, int)`, `sectionProgress()` and `tickSections(int)`. Sections retain spatial columns and build upwards independently within the same rollback/material transaction. `SectionProgress` exposes the stable section index, counts, this batch's placed points and a nullable next block target. The existing constructor and `tick(int)` retain single-section behavior.


## Vector propulsion feedback and leveling

`VectorThrustReceiver.controllerThrustConeDegrees()` reports the live half-angle of the provider's forward thrust cone. Its default is zero, preserving fixed-direction implementations. Integrations report geometry through this API and retain their own force allocation, fuel and physics logic.

`ScmPrecisionAllocator.Unit` additionally accepts `(force, torque, precision, momentArm, coneDegrees)`. The three-argument constructor remains available. Cone-aware normalization counts steerable force and the torque generated about the supplied moment arm; scalar allocation continues to operate on nominal force columns. `ScmThrustGeometry.maximumProjection()` supplies the signed support function for a bounded cone, and `steeringForces()` supplies its two transverse feedback directions.

The new `normalizedAcceleration(units, acceleration, mass, holdingForce)` and `physicalForce(units, correction, holdingForce)` overloads preserve negative corrections by reducing opposing holding thrust when reverse thrust is unavailable. Add the physical holding force once after converting a correction. The original overloads retain their behavior without holding support.

`ScmAdaptiveStateModel.forceActuators()` builds nominal throttle and transverse steering columns in the caller's coordinate frame using mass and inverse inertia. Steering columns linearize the provider's response; the provider's allocator enforces the combined nonlinear cone and thrust limits. `ScmArticulatedFlightControl` uses these columns for carriage and shared feedback.

`ScmControlAxes.uprightError(up, desiredUp, forward)` returns the shortest leveling rotation in radians, including inversion. `ScmControlPriority.remainingTorque(request, drivenTorque, forward, up)` removes leveling on actively driven yaw, pitch and roll axes while retaining the other axes.

## Shared worker recipe relationships

`WorkerRecipeRelationships` stores a detached recipe graph with shared ingredient alternative groups. It retains output links and reverse ingredient links, rather than enumerating crafting trees. `rank(outputs, stocked, supported, machineStock)` propagates reachable stock through each related ingredient group and returns production costs. Unseeded conversion cycles remain unreachable. Machine support and preloaded input credits are supplied by the consumer; their callbacks must follow the caller's thread ownership rules. Repeated output scopes are cached with limits on both entry count and total related recipes.

`WorkerRecipeSource.relationships(outputs)` is an additive default method. Simple producer callbacks discover each related resource once; sources with an existing graph should override it to return their retained relationships. `WorkerRecipeIndex` now implements `WorkerRecipeSource` and shares one relationship map across consumers. Its existing producer, consumer and dependency APIs remain available.

`WorkerRecipeCatalog.prepareRelationships(level)` warms detached definitions and their relationship map in bounded owner-thread batches. Call it after server startup; the library also warms new generations after datapack reload synchronization. Demand lookup can resolve the requested relationships while full warmup is still running. Ingredient definitions, producer definitions and graph generations are discarded together when recipes or tags reload.

`WorkerRecipePlanner.planFromStock` first allocates directly stocked inputs. Missing inputs trigger relationship ranking, excluding unreachable tag members before quantity-aware planning and shared-stock checks. Completed schedules still retain every production step for execution without a fresh search at each stage. The exhaustive planning overloads keep their existing schedule preference behavior.

`WorkerIngredientAllocation.allocate(ingredients, amounts, available)` returns exact per-slot reservations, or null when shared stock cannot cover all slots. It uses capacities to handle overlapping tags and large quantities without enumerating item distributions. It reads detached values and does not mutate the supplied inventory map.

Validation includes the worker planning and execution suites, 20,000 unseeded tag cycles, quantity-starved conversion and sibling routes, and billion-item overlapping allocations. An opt-in `WorkerRecipeRelationshipsTest` scale case is enabled with test JVM property `gg.worker.scale=true` and a 2 GB heap; it creates 1,000,002 detached recipe definitions and checks that a small requested output consults only its related recipes. This fixture is not a measurement of a live modpack server.

## Explicit attitude intent and controller display ownership

`ScmWrenchSourceRegistry.Wrench` adds an immutable `rotationActions` set. Use `ship_pitch`, `ship_roll`, and `ship_yaw` to declare explicitly commanded attitude axes independently of the resulting torque vector. Navigation yaw about world up must not suppress leveling merely because the ship is tilted. Existing three- and four-argument constructors remain available and declare no attitude override. Registry composition, center conversion, and `ScmControlPriority.additionalWrench` retain this metadata.

`ScmControlAxes.withSupportActions(actions, support, up)` keeps the propulsion action which supplies holding force available during correction routing. This permits descent by reducing lift when engines cannot thrust downward. Vertical Airship navigation no longer authors a horizontal heading when its path has no horizontal direction.

`ControlOwnerReceiver` exposes `setControllerOwner(channelId, displayKey)` and `releaseControllerOwner(channelId)` for provider displays. `ControllerOwnership` is an immutable snapshot with `NONE`, `present`, `claim`, `release`, and CompoundTag `save`/`read`. Claims cannot replace a different channel; releases only clear the matching owner. Providers synchronize these display snapshots and manage their lifetime alongside actual actuator ownership. The display contract does not itself grant physics authority or persist live control across world loads.

## Pose-consistent linear motion feedback

`ScmLinearMotionFeedback.sample(tick, position, velocity, tickSeconds)` reconciles a physics solver velocity with the displacement between consecutive control-loop poses. It extrapolates half the observed velocity change to retain the current acceleration response while removing repeated impulse-phase offsets at rest. The first sample, missed ticks, and detected teleports use the solver velocity; repeated samples in the same tick reuse the result. Call `reset()` when the controlled body or reference point changes. Samples must use the same coordinate frame and reference point. This is controller feedback, not a replacement for the physics solver velocity. The addon enables it for Airships at the live assembly center of mass so docking can settle without interpreting the gravity impulse phase as real movement.

Airship feedback uses the live assembly center of mass and its mass-weighted velocity, so the motion sampling also applies to articulated assemblies using that same reference point.

Docking adapters can also keep one motion sampler per connector body, sample that body origin and solver linear velocity, then add angular point velocity for each connector. This keeps connector feedback consistent without substituting the assembly average for motion of an articulated connector body.

Airship terminal capture now tracks the actual control target on all linear axes instead of retaining a path tangent which ignores residual cross-track position error. Transit steering continues to use the supplied path direction. This also lets docking retain cross-axis alignment while completing its axial approach.

## Secondary scene state isolation

`ViewSceneEnvironment.save()` now retains Minecraft shader selection, texture slots, texture matrices, GPU texture and vertex bindings, framebuffer bindings, viewport and cached draw state, alongside the existing fog and optional LOD state. Its restoration action also restores optional Iris camera uniforms, timing values, immediate-render flags and deferred blend/depth overrides. GPU restoration temporarily bypasses Iris draw locks so it cannot overwrite the deferred state. Run the action in a `finally` block on the render thread.

`ViewSceneRenderer` scopes feed allocation, target resize, rendering and disposal with this restoration. Each feed retains its own world renderer and lightmap. Display and tablet consumers continue using `request`, `draw` or `drawFitted`; they do not manage shader or GPU state.

## Deferred prerequisite search

`WorkerRecipeSearch.plan(...)` searches a detached `WorkerRecipeSource` using an iterative queue. It returns the first complete executable schedule, preserves alternative routes for backtracking, and remembers repeated combinations of stock and pending work. The remembered-state cache holds at most 16,384 entries; eviction does not reject the request. There is no recursive tree depth or total branch-count ceiling in this API.

`WorkerRecipeSearch.prerequisites(...)` repairs the remaining inputs of a saved plan using the same search. Existing `WorkerRecipePlanner.planFromStock(...)` and `prerequisitesFromStock(...)` delegate to these APIs. Existing exhaustive planner APIs remain available. Save formats and recipe identifiers are unchanged.

Stock reservations stay within one coordinating search. Fully stocked overlapping ingredient groups use `WorkerIngredientAllocation`, including groups that become stocked after a prerequisite finishes. Tag supplies may combine several produced members. A supply attempt must increase the quantity available to its ingredient group; recycling that makes no progress cannot restart the same attempt indefinitely.

Callers supply immutable recipe definitions and inventory snapshots. Direct calls execute callbacks on their calling thread. Game integrations should use `WorkerRecipeLookup`, which keeps live machine callbacks on the owning game thread and caches all credited input slots of one definition in one owner query. Completed schedules remain cached and their queued steps execute without planning the entire chain again.

### Bounded parallel indexes

`DeferredWorkScheduler.parallel(List<? extends Supplier<T>>)` joins independent detached tasks in input order. Within a scheduler it submits batches of at most two children and releases the parent's computation permit while waiting. When job capacity is full, it evaluates a child inline. Outside scheduled work it evaluates tasks sequentially. Children must not mutate a shared inventory reservation.

The scheduler permits at most two computing jobs per server, reduced to one on small processors, with at most 64 submitted jobs including children. Virtual threads waiting for owner queries or child results release their computation permits. `checkpoint()` checks cancellation and yields a long computation without waiting for a server tick. Interrupted direct searches also stop at checkpoints.

Owner-thread queries retain the shared limit of 64 queries and a 2 ms admission budget per tick. An individual callback must remain short because an already running callback cannot be preempted. Live Minecraft state remains on its owning thread. Server stop cancels submitted work and releases waiting queries.

Validation includes deep chains, large alternative sets, mixed tag supplies, overlapping reservations after prerequisites, preloaded machines, concurrent parent/child searches, and optional exported pack recipes. Set the test JVM property `gg.worker.pack` to an exported recipe fixture to enable the pack test; `gg.worker.scale=true` enables the million-definition relationship test.

`WorkerRecipeRelationships.rank(..., boolean includePreloadedRoutes)` is an additive overload. Setting the flag includes preloaded alternatives even when some other storage route already reaches the output. The original overload retains its behavior.

The iterative search first restricts prerequisite choices to relationships reachable from the inventory snapshot. If that phase cannot finish, it propagates cached machine credits through the graph once and retries with those routes enabled. Unreachable branches are rejected using the relationship map rather than expanded into combinations of unavailable recipes. A missing ingredient still reports an unavailable-input failure instead of a search-limit failure.

## Isolated framebuffer GUI rendering

`FramebufferGuiRenderer` in `lib.client.render` owns an offscreen GUI texture and private vertex storage. Construct it with a unique resource location in the consuming mod's namespace, then call `render(pixelWidth, pixelHeight, guiWidth, guiHeight, draw)` on the render thread. The draw callback receives a `GuiGraphics` with nested scissor clipping scaled to the framebuffer rather than the player window. Check `isReady()` before drawing `texture()` on a surface. Close the renderer when the surface expires or its world unloads.

Allocation, resize, drawing and cleanup preserve the surrounding framebuffer, viewport, shader state and item lighting. Projected GUIs do not flush pending player geometry or resize the game window. The addon owns screen construction, input routing and refresh scheduling.

`ViewSceneRenderer` now owns separate `RenderBuffers` for each world feed. `captureBuffers()` exposes them only during capture, allowing client integrations to route secondary geometry without consuming player batches. Feed creation and rendering leave Flywheel's player visual manager, culling, render origin and reload events untouched; secondary views use normal block entity fallback rendering. Player rendering continues to use its configured Flywheel backend.

## Worker dependency graph routes

`WorkerRecipeRelationships.routes(outputs, stocked, supported, machineStock, includePreloadedRoutes)` returns `Routes`, containing the existing `Ranks` and an immutable `preferredRecipes` map. Each reachable output maps to one producing definition chosen during forward propagation. Alternatives share their relationship groups; conversion cycles do not require expanding recipe trees. The map describes relaxed reachability, so callers must still reserve exact quantities and validate machine routes.

`WorkerRecipePlanner.planFromStock` and `prerequisitesFromStock` first extract a preferred dependency graph, reserve its stock, and validate the complete candidate. Rejected machine routes trigger another graph selection. Exact alternative search remains available when shared quantities or ingredient variants require it. Successful lookup results and queued prerequisite steps remain reusable without rediscovering the tree at each crafting stage.

`DeferredWorkScheduler.queryTogether(tasks)` queues independent owner-thread queries in batches of at most 64. Results retain input order. All consumers share the existing 64-query and 2 ms admission budget per tick; an individual callback must still be kept short. Nested `onOwnerThread` calls inside an admitted query execute immediately on the same owner thread. Outside scheduler work, `queryTogether` evaluates tasks directly, so standalone planners retain synchronous behavior. Recipe support, physical route validation, and machine input credits are checked together for a complete candidate, and fallback preload probes use the same batching.

Preloaded-input fallback starts with producers of the requested output, then visits unresolved prerequisites. A missing mandatory ingredient with no supported producer prunes its entire branch after checking the parent's preloaded inputs. Shared ingredient groups expand once, avoiding unrelated live machine probes and repeated traversal of large tags.

## Worker recipe routing and ordinary belt inputs

`WorkerRecipeSearch.plan` has an additive overload accepting `ToIntFunction<WorkerRecipePlan> routingPenalty` before `IngredientStock`. The previous overload remains available and supplies zero routing cost. `WorkerRecipePlanner.planFromStock` forwards its existing routing callback to this search.

Detached recipe ranking includes ingredient quantities, recipe yields and required output batches. Live route penalties are read with the same owner-thread validation as machine support and then cached for detached comparisons. These are practical route estimates; they do not enumerate every possible recipe combination to prove a globally optimal plan.

`CreateWorkerMachines` honors `WorkerMachineSite.inputs()` for ordinary processing on belts as well as sequenced assemblies. A marked belt input must be on the connected moving line at or before the processing position and inside the selected machine area. With no marked inputs, workers retain direct insertion at the processing belt segment. Press links continue resolving to the work position two blocks below the press.
## SCM machine discovery and filtered recipe routes

`WorkerMachine.supportedProcessorTypes()` provides an owner-thread snapshot for detached planning. Its compatibility default evaluates `maySupportProcessor` against registered recipe types; integrations can override it with a finite immutable declaration. Create processors now restrict the gate to their actual receiving block and driver. Existing `WorkerMachineRegistry.accessPositions` resolves press/mixer face links to the receiving depot, belt or basin; basin processing checks the burner directly beneath it without requiring an area.

`WorkerMachineRegistry` retains registered adapters and built-in integrations ahead of a new fallback, `WorkerModdedMachines`. The fallback reads public `recipeType`/`getRecipeType` and plural getters or fields, including wrapped providers, with cached accessors and bounded traversal. It uses the block's NeoForge item/fluid capabilities for transfers. Block names and recipe names do not establish processing capability.

`WorkerModdedRecipes` reads registered recipe serializer codecs on the owner thread. Supported item/fluid schemas include sized single inputs and declared input/output lists. Amounts, guaranteed outputs and required catalysts are preserved. Custom processing conditions, unknown required resources, unsupported formats and chance-only outputs are rejected with diagnostics. Exposed EU recipe limits are checked when available. This fallback does not provide universal compatibility with arbitrary internal machine code or chemical inventories; those integrations use `WorkerMachineRegistry.register` and `WorkerRecipeCatalog.register` to describe their actual requirements and transfers.

`readSerialized(id, type, data)` adapts supported documents against loaded registries/tags. `recipes`, `declaredOutputs` and `diagnostics` expose loaded recipe data and rejection reasons. Use these APIs on the owner thread. Declared output names remain indexed even when execution requires a custom adapter so consumers can explain the rejection.

`WorkerRecipeRoutes(source, processorTypes)` removes unavailable physical processors before discovering their ingredients while retaining worker crafting alternatives. It reuses compact filtered relationship maps across equal source/type/output profiles, capped at 32 maps and 250,000 recipes per source. Empty root routes return `missing_machine` before reading prerequisites. Missing prerequisite routes and compatibility failures remain available to addon app/node diagnostics.

Verified API formats: [Modern Industrialization's recipe schema](https://github.com/AztechMC/Modern-Industrialization/blob/2.5.8/src/main/java/aztech/modern_industrialization/machines/recipe/MachineRecipe.java). Runtime checks also use the locally installed machine implementations; no third-party reference sources are modified.

## Air-only compound-hull clearance

`SubLevelParticleOcclusion.findAirOnlyBoundsBlockingDistance(...)` measures a moving compound hull without resolving root-world block collision shapes. Loaded root-world air is the only traversable state; every other loaded root block is an immediate no-entry boundary. Every non-excluded Sable sub-level is checked through its current world envelope, so other vehicles remain no-entry without raycasting their internal blocks.

Pass the caller-owned `ProbeCache` to share loaded chunk and Sable body snapshots across queries in one game tick. `includeRootLevel` preserves callers such as docking that intentionally scan only other bodies. The API never requests chunks; unavailable root chunks are skipped just as the existing loaded-only occlusion queries do.


## Create casing API

`com.rieno.gadgetsandgizmos.lib.create.encasing.CreateCasingApi` adds native encasing variants, direct casing aliases, and saved belt materials. Call registration methods from enqueued common setup after block and item registration. Use the owning mod namespace for belt material IDs. Registration rejects duplicates; aliases are direct and dedicated native variants take precedence.

- `registerVariant(base, variant)` registers a Create `EncasableBlock` / `EncasedBlock` pair.
- `registerAlias(casingItem, equivalentItem)` lets another casing use existing encased variants when no dedicated variant matches. The fallback keeps the existing variant's appearance and behavior.
- `registerBeltCasing(id, casingBlock)` gives that material Andesite belt mechanics. Casing application is free, matching Create.
- `getBeltCasing(id)` returns the registered block, or null for a null or unavailable ID.

`CustomEncasedShaftBlock` and `CustomEncasedCogwheelBlock` retain Create's native orientation, cog shaft connections, wrenching, and schematic requirements while accepting the consuming mod's block entity type supplier. Register those blocks and block entity types under the consuming mod namespace; supply matching valid blocks and normal Create renderers/visualizers.

`CustomBeltCasing` is implemented on Create belt segments by library mixins. `getCasingMaterial()` returns a nullable material ID; `setCasingMaterial(id)` updates and synchronizes it on the owning game thread. Null restores the native appearance. Materials use the additive NBT key `gadgetsngizmos:CasingMaterial`; Create's `Casing` enum remains unchanged. Unavailable or invalid saved materials fall back to native Andesite. Applying a vanilla casing or wrench clears the custom material, including replacements where the native casing enum stays the same.

On the client, `BeltCasingModels.register(id, texture)` associates the same material ID with a block-atlas texture. Register once during client setup; the texture must be referenced by a loaded model or atlas source. The library retextures stationary belt casing and cover geometry and updates particles/model data after sync. Native belt geometry, movement, dye, pulley, tunnel, and cover behavior remain Create-owned. `MATERIAL_PROPERTY` is the model-data property for integrations. Keep this class out of common/server loading.

Validation: `CreateCasingApiTest` covers strict registration, alias delegation, free casing application, belt mechanics, and sneak guards. Addon GameTests cover every shaft/cog axis, connected cog shafts, belt NBT reload, vanilla casing replacement, and wrench removal. Run from the addon with `gradlew runCasingGameTest "-Pcasing_game_tests=true" "-Pneoforge_version=21.1.228"`; the supplied local Sable, Simulated, and Aeronautics jars require NeoForge 21.1.228. The test run uses the required runtime dependencies and an isolated world under `build/casing-gametest`. GameTest classes/resources are excluded from release jars. All three GameTests passed on NeoForge 21.1.228; normal addon and library builds and unit tests passed with the existing 21.1.225 project setting.

## Vector allocation during shared steering

`ScmVectorAllocationRegistry.Request` adds `prioritizeTorque`. Providers can preserve commanded attitude authority when translation saturates the available engines. Existing constructors retain balanced allocation. Hosts should request torque priority while a steering source actively claims rotation axes; ordinary translation keeps its existing allocation policy.

`ScmControlPriority.remainingTorque` also applies to automatic navigation heading, face/docking alignment and attitude-lock feedback. Filter that feedback using active optional rotation actions before adding explicit primary and optional steering demands. Direct steering from both sources remains additive.


## Advanced controller math API

Reusable code lives in `com.rieno.gadgetsandgizmos.lib.control.math` and `lib.graph.math`.

- `Vector3`: additive `scale`, `dot`, `cross`, `normalized`, `isFinite` methods.
- `Matrix3`: immutable row-major 3x3 matrices acting on column vectors; creation, addition, subtraction, composition, scaling, transpose, determinant and inverse. `A.multiply(B)` applies B first. Singular/ill-conditioned inverses throw `IllegalArgumentException`.
- `Quaternion`: raw Hamilton algebra, magnitude, dot, add/subtract/scale, multiply, conjugate, inverse; normalized vector rotation, axis-angle conversion, rotation-matrix conversion and shortest-arc SLERP. Algebra preserves non-unit magnitude. `normalized()` uses scaling to avoid overflow/underflow and returns identity for zero/non-finite inputs.
- `SpatialTransforms`: point world/local conversion with origin and local-to-world orientation; tensor conversion using `R D R^T`. Use quaternion rotation for directions.
- `Kinematics.LinearState` and `predict(state, jerk, seconds)`: exact constant-jerk kinematics; ZERO jerk gives constant acceleration.
- `Kinematics.RigidState` and `predict(state, jerk, angularJerk, drag, angularDrag, seconds, steps)`: RK4 coupling of body thrust, evolving orientation and body-frame damping tensors. World position/velocity; body acceleration/jerk/angular velocity/angular acceleration/angular jerk. Quaternion derivative is `q * (omega,0) / 2`; drag subtracts `R D R^T v` from world acceleration. Damping tensors use rates in inverse seconds, not force coefficients. Steps are adaptively increased for damping/angular motion, with a 4096-step limit. Invalid time/step count or excessive work throws `IllegalArgumentException`.
- `MathGraphValues`: raw or typed uppercase/lowercase XYZ/XYZW map codec; matrices use `m00` through `m22`; named Euler `alpha/beta/gamma` and Tait-Bryan `roll/pitch/yaw` maps retain XYZ aliases. Named fields take precedence. Angle converters preserve map keys and nested maps/lists, converting numeric fields and retaining other values.
- `MathGraphNodes.nodes(namespace)`: immutable definitions, executors, defaults, titles and summaries. Consumers register these under their own namespace using existing strict `GraphApi` registries. No addon imports or game-physics mutations.
- `MathGraphNodes.rotationNode(id)`: supports the six existing rotation converter IDs for additive named outputs and map compatibility. These existing serialized IDs remain unqualified; all new ACC math IDs use `createthrusters:`.
- `GraphExecutionContext.executionTriggered()` is a new default method (false). Hosts return true only for explicit execution pulses; stateful math nodes retain state under `motion` and never advance on passive reads. Predictors have no retained state. Reset restores supplied initial state on exec; the next exec resumes integration. Invalid math returns `valid=false` with empty maps/zero scalars and retains the last valid integrator state.

All angles are radians, time is seconds, and angular velocities are radians/second. Existing ports, serialized keys and XYZ map reads remain supported. New numeric angle outputs are labelled with their angles and axes by each host. Numerical regression tests compare coupled motion against analytic drag, rotating-thrust and constant-jerk solutions; controller tests cover serialization, named angle outputs and execution-versus-preview behavior.


## Pi math node

`MathGraphNodes.nodes(namespace)` now also provides `namespace:pi`: a stateless Math node with no inputs, a `value` number output equal to Java `Math.PI` (3.141592653589793), and the suite's existing `valid` boolean output. The addons register it as `createthrusters:pi`, displayed as Pi. Existing node IDs and behaviour are preserved.

### Craft attitude and world projections

`ScmAttitude.measure(bodyRotation, orientation)` measures pitch, heading and bank from a body quaternion and the configured forward/up frame. Angles are degrees. Positive pitch raises the nose; positive roll lowers the craft's right side. Vertical headings remain finite.

`SubLevelClientRenderApi.withProjection(projection, action)` scopes an already model-view transformed world projection, restores projection/model-view state even if rendering throws, and has a buffer-source overload that flushes its own buffers independently of world rendering.

`WorldProjectionRenderTypes.color()` renders two-sided translucent color quads without depth testing or depth writes, for world-anchored information that must remain visible when its host turns behind it. Keep geometry anchored through Sable's interpolated pose.
SableSubLevelResidency.isFullyLoaded now checks the restored chunks published by Sable's plot load, rather than the unused slots in its reserved plot grid. Sparse plots can resume control after restart; missing plots, empty loads, removed bodies and unavailable chunk data still return false.

WorldProjectionRenderTypes.color() explicitly disables an inherited GL depth test during its draw and restores the previous enable state afterward. Its color quads remain visible from either side and do not modify world depth.
