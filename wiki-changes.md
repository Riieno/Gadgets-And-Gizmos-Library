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

## Saved graph presentation updates

`GraphNodePresentationRegistry.synchronize(type, data)` adds newly registered defaults, selector choices, category ports and input order to a saved node. Existing values and category state are retained. Call it when decoding a node whose registered presentation may have gained fields. `inputOptions(type, port)` returns the immutable registered selector choices for editors that need a fallback before saved metadata has been updated.

## Sable camera references and anchored billboards

`SubLevelClientRenderApi.localCameraAnchor(level, pos)` creates a detached camera reference entity at a sub-level plot position. It does not spawn an entity or mount a player. This lets a consumer pass the containing Sable body to a native camera that ordinarily requires a vehicle.

`SubLevelClientRenderApi.billboardModelView(modelView, scale)` preserves the model-view translation and replaces its orientation with a camera-facing basis. Pass signed scale explicitly to preserve reflections. Calculate the anchor from the interpolated Sable pose before applying this method; craft telemetry should continue to use the configured orientation independently of the billboard.

## Optional translation intent in SCM wrench sources

`ScmWrenchSourceRegistry.Wrench` now accepts a sixth argument, `Set<String> translationActions`, alongside `rotationActions`. It declares actively authored translation axes independently of damping and environmental support. An optional height controller can declare `ship_ascend` and `ship_descend` so primary hover feedback yields while that controller owns height. Existing three-, four- and five-argument constructors remain available and default to an empty set. Sampling, `aboutCenter` and `ScmControlPriority.additionalWrench` preserve this intent. Inactive sources contribute no intent.
### Sable camera frames for unmounted interactions

`SubLevelCameraFrameRegistry.register(ResourceLocation, Provider)` supplies a local-to-world camera rotation for an interaction without mounting or spawning a player entity. Providers return null outside their active interaction. Registration is strict, uses the consumer's namespace, and follows registration order. `unregister(ResourceLocation)` releases the provider. `orientation(Entity, float)` returns a normalized copy and ignores invalid quaternions.

Sable's camera rotation helper consumes this registry only for camera frames and preserves its unlocked-camera option. Body positions, entity orientation and mounting are unchanged. An adapter that expresses player look angles in a physical SubLevel's frame must supply that same physical render rotation; its configured forward/up axes are used when resolving the control target.

## Timed item transformations

Both library branches now expose `com.rieno.gadgetsandgizmos.lib.item.TimedTransformingItem` and `TimedItemTransformations`.

Construct a `TimedTransformingItem` with item properties, a result-item supplier, a supplier for an owner-registered persistent and network-synchronized `DataComponentType<Long>`, and a positive duration in ticks. `beginTransformation(stack, now)` saves the completion tick once. `transform(stack, now)` returns the same stack until complete, then a replacement with its count and other components preserved and the timer removed. Callers must store the returned replacement. Copies, splits, dimension transfers, and reloads keep the original deadline.

`TimedItemTransformations.gameTime(level)` uses persistent overworld game time for every dimension. `transform(stack, level)`, `beginTransformation(stack, level)`, and `tick(handler, now)` are available to custom integrations; level overloads only mutate on the logical server. Register passive custom containers with `track(container, level)`. All calls that mutate stacks must run on the owning server thread.

The library automatically integrates player inventories, dropped items, vanilla block and entity containers, `SimpleContainer` inventories (including closed ender chests), NeoForge `ItemStackHandler` inventories, and Create basins, depots, and belts (including stopped belts and buffered basin outputs). Passive containers are checked every 20 ticks to discover timed items, then active containers tick every tick until completion. Both `tick` overloads report whether any items are still waiting; custom inventory implementations can call the same API. Unopened loot containers are skipped. Completion is measured in server game ticks, including time spent in an unloaded inventory while that server is running, and pauses while the server is stopped.

Consumers can override `onWaitingInventoryTick` for gameplay effects. The addon owns item/component IDs, scorching fuel damage, compacting ingredients, models, names, and thruster fuel values. It consumes four lava-bucket items plus blackstone, returns the buckets, and cools the result for 600 ticks. No addon implementation classes are exposed through the library API.

## Fluid completion for timed items

`TimedTransformingItem` now provides an additive fluid-contact API:

- Override `protected boolean canTransformInFluid(Fluid fluid)` to select fluids that immediately finish an item. The default returns false and preserves existing timed behavior.
- `transformInFluid(ItemStack stack, Fluid fluid)` returns the original stack on rejected contact, or a completed copy on accepted contact.
- `completeTransformation(ItemStack stack)` preserves stack count and other data components while removing the completion timer.

`TimedItemTransformations.transform(ItemStack, Level, AABB)` checks actual contact with source, flowing and waterlogged fluid surfaces, plus filled water/lava cauldron interiors, before normal timed completion. It does not transform items on the client. Entity inventories and dropped items use this overload automatically. Carried items can complete before the waiting inventory effect runs.

`transformInFluids(ItemStack, IFluidHandler)` and `transformInFluids(IItemHandlerModifiable, IFluidHandler)` check every nonempty tank without consuming fluid. The inventory overload replaces completed stacks and returns whether any slot changed. Fluid handlers expose no level, so callers must use the owning server thread.

Create integration applies contact detection to depot and belt stacks, including stopped belts, and checks basin input/output slots and buffered item outputs against the basin's combined fluid handler. Dry stacks still follow their saved completion deadline. Consumers own the gameplay choice of acceptable fluids; the library owns contact detection, conversion and storage integration.

## Processing recipes with a timed completion step

`com.rieno.gadgetsandgizmos.lib.create.ProcessingTransformationRecipes.create(ResourceLocation id, RecipeHolder<?> source, Component process, Component completion)` returns an optional viewer presentation of a live Create processing recipe followed by a `TimedTransformingItem` conversion. The ID belongs to the consuming mod. The source must have one guaranteed item result, no fluid results, and at most twelve item/fluid input slots. Unsupported recipes return an empty optional.

The returned `View` retains the source recipe, item ingredients, copies of matching fluid stacks including amounts and components, the intermediate stack, the completed stack, and caller-owned labels. Creating a view never changes the gameplay recipe or starts a cooling timer. Rebuild views from the current recipe manager after recipe reloads. Stack values are presentation snapshots; consumers should not mutate them.

Optional client adapters live under `lib.compat`: `jei.ProcessingTransformationJeiCategory`, `rei.ProcessingTransformationReiCategory` with its nested `Display`, and `emi.ProcessingTransformationEmiRecipe`. They show the real intermediate in a separate slot and index only the completed result as the chain output. Register them from the consumer's installed-viewer plugin, together with its own category ID, title, icon, and workstations. These adapters use viewer APIs as compile-only dependencies; the library does not require recipe viewers at runtime. EMI presentations disable automatic recipe transfer/tree crafting because passive completion is not a transferable machine recipe.

`lib.client.recipe.ProcessingTransformationLayout` shares slot positions and stage captions across the adapters. Its render method is client-only.

`TimedTransformingItem.durationTicks()` exposes the configured completion interval for accurate presentation text without starting or changing a timer.

## Create item Shift descriptions

`com.rieno.gadgetsandgizmos.lib.item.CreateDescriptionItem` extends `Item` and adds Create's normal Shift description to its tooltip. Supply ordinary item properties and translated `item.<namespace>.<item>.tooltip.summary` keys, with optional Create condition/behaviour keys. The consuming mod owns content and fuel rules; the library owns the tooltip wiring.


## Performance API additions

### RevisionSnapshotCache

`com.rieno.gadgetsandgizmos.lib.util.RevisionSnapshotCache<T>` retains one detached snapshot on the owning game thread. `get(Object source, long revision, Supplier<? extends T> loader)` reuses it only when the source is the same object and its revision is unchanged. Replacing a source at the same saved revision also reloads the snapshot. The loader must complete successfully before the new entry replaces the old one; null values can be cached. `invalidate()` releases the source and snapshot on unload or explicit lifecycle changes.

The cache owns no synchronization and performs no copying itself. Its consuming mod supplies the copy operation and must advance the revision when content changes. Consumers should keep their own cache rather than exposing its mutable snapshot as shared host state. The ACC's native integration uses this API while the existing `getActiveGraph()` method continues returning a fresh detached copy.

### Phased view refresh

`ViewRefreshSchedule.captured(long now, long phase)` is an additive overload that schedules the next capture on a phase within the feed's refresh interval. Both arguments use the same nanosecond clock as `ready(long now, int refreshRate)`. Rate changes still take effect immediately, and missed deadlines produce one future capture rather than a burst. The existing `captured(long now)` overload retains its original behavior.

`ViewSceneRenderer` assigns separate phases to retained camera sources. Each source retains the configured refresh rate; simultaneous feeds can render on different main frames without reducing resolution or introducing a global capture limit.

The SCM solver optimizations change private storage and iteration work. Existing public APIs, saved throttle values, identifiers, NBT, and controller tick cadence are preserved.

## Depth texture snapshots

`com.rieno.gadgetsandgizmos.lib.client.render.DepthTextureSnapshot` owns a reusable scene-depth texture. Use `capture(x, y, width, height)` on the render thread with a complete draw framebuffer containing depth and a rectangle inside its bounds. It returns a texture id containing normalized depth, with nearest filtering, clamped coordinates and comparison disabled. Non-positive dimensions return zero.

The first capture and size changes allocate depth storage; later captures update the same texture. Copies accept differing source depth formats without a framebuffer depth blit. The read/draw framebuffer, active texture unit and existing 2D texture binding are preserved. The source must be single sampled.

Call `close()` on the render thread during resource teardown. Closing releases storage and is repeatable; a later capture can allocate again. Soft particle and surface floodlight rendering use this API. Temporary recipe containers passed to `TimedItemTransformations.track(container, null)` are ignored without reading their inventory.

## Filtered loaded ship entities

`SubLevelBlockEntityCollector.getBlockEntities(body, type)` returns only loaded entities assignable to the requested `Class<T>`. `getBlockEntities(body, predicate)` accepts a side-effect-free filter. Both read current plot contents without loading chunks or retaining a cache, deduplicate positions and preserve actor precedence over chunk entities even when an actor is excluded by the filter. Returned lists remain mutable, and same-tick replacements and removals are visible to the next call.

Gantry carriage discovery and SCM relation discovery use these overloads to avoid collecting unrelated block entities. The existing single-argument method retains its behavior.

### Secondary scene framebuffer compatibility

`SceneRenderTargets.createLike(source, width, height, clearError)` creates an owned `TextureTarget` using the source's standard Minecraft depth and NeoForge stencil settings. This supports depth copies between secondary scenes and effects that retain a primary-view depth buffer. Custom attachment formats and multisampling are outside this contract.

`hasMatchingDepth(first, second)` compares depth use and stencil settings without a GPU query. Recreate a secondary target if these settings change. `wrap(target)` creates a Veil framebuffer borrowing the target's colour and depth/stencil textures; free the wrapper before resizing or destroying its target. Freeing the wrapper never deletes borrowed textures. Creation, wrapping and disposal belong on the render thread. These methods follow Minecraft target allocation and binding behaviour; callers restore their surrounding render state.

## Moving terrain residency

`MovingChunkResidency.lease(ServerLevel)` creates an independent, temporary terrain owner. Call `Lease.synchronize(Collection<Region>)` on the owning server thread; each `Region` contains current world bounds and velocity in blocks per second. Prediction covers up to two seconds, clamps travel to 64 blocks per axis, and limits direct full-chunk tickets to 256 per lease. Stationary bodies request no extra terrain. Chunk generation proceeds through the normal asynchronous ticket scheduler; synchronization never reads or waits for a chunk.

Tickets use the library's `gadgetsngizmos_moving_terrain` type, an independent lease UUID, and full-chunk level without requesting block or entity ticking. Footprint changes remove only the lease's previous tickets. Shared ship, player and loader tickets remain intact. Tickets refresh every 20 ticks and expire after 40 ticks if their owner stops updating. Close the lease when its owner detaches, changes level or shuts down.

`SableSubLevelResidency.Lease.prefetchMovingChunks()` applies this API to retained Sable bodies once per server tick. Ship residency continues through `ServerSubLevelContainer.addForceLoadTicket` and `removeForceLoadTicket`; Sable handles collision terrain, ticking and its own expiring per-ship world tickets. Both `close()` and `detach()` release temporary terrain predictions; `detach()` preserves the existing saved ship tickets for restart. Restored force tickets are adopted even when Sable's add method reports that the ticket already existed, allowing the lease to release them later.

## Complete textured quad vertices

`TexturedQuadWriter.drawDoubleSided(...)` submits a double-sided textured quad through Minecraft's complete-vertex API. Supply the current `PoseStack.Pose`, consumer, four local points, UV bounds, endpoint light values and overlay. Positions are transformed once per corner and normals once per winding. The writer preserves vertex order and attributes and supports ordinary or wrapped vertex consumers; `BufferBuilder` can use its native complete-vertex fast path. Geometry remains identical under translated, rotated and nonuniformly scaled poses.

## Optional mixin selection

`com.rieno.gadgetsandgizmos.lib.compat.MixinCompatibility` provides shared checks for addon and library mixin configuration plugins.

- `isModLoaded(String modId)` uses NeoForge's discovery mod list during bootstrap, then the runtime mod list when discovery is unavailable. It returns false if neither list is available.
- `canApply(String targetClassName, String... requiredMods)` requires every named mod and an accessible target class resource. It uses Mixin's resource lookup without requesting bytecode, loading or initializing the target class, or running transformers. Missing or inaccessible class resources return false. This supports NeoForge's ModLauncher, which cannot retrieve untransformed bytecode.

Call `canApply` from `IMixinConfigPlugin.shouldApplyMixin` before Mixin resolves an optional target:

```java
return MixinCompatibility.canApply(targetClassName, "your_optional_mod");
```

Consumers own their mod IDs, target mappings, version checks, and compatibility behavior. The library does not depend on optional mod implementation classes. Existing APIs and saved data are unchanged.

### Mixin array-clone compatibility

`MixinArrayCloneCompatibility.register(targetClassName, mixinClassName)` registers a workaround during a Mixin configuration plugin's `onLoad`. It applies only to the named target and mixin. On Minecraft 1.21.1 / NeoForge's Mixin 0.8.7, array-owned `clone` calls can fail when Mixin resolves a detached superclass. The workaround substitutes `Arrays.copyOf` in the in-memory mixin bytecode before application. Primitive and reference arrays retain their runtime type, length, contents and shallow-copy behavior. No mod files are modified, and the mixin's interfaces and injections remain enabled.

`MixinArrayCloneCompatibility.replaceArrayClones(ClassNode)` provides the same bytecode normalization for callers that already own a preparation hook. It returns whether any call was replaced and is safe to repeat. The library contains no addon or Flight Control implementation references; consumers select the affected target and mixin.


## Operator protection bypass API

`com.rieno.gadgetsandgizmos.lib.access.ProtectionBypass` provides session-scoped server protection bypasses. All calls belong on the owning server thread.

- `REQUIRED_PERMISSION_LEVEL` is `2`.
- `setEnabled(ServerPlayer, boolean)` returns the resulting state. Enabling requires an operator at level 2 or higher.
- `isEnabled(ServerPlayer)` rechecks operator permissions and revokes the session when they are lost.
- `isEnabled(MinecraftServer, UUID)` resolves the current online player on that server.
- `clear(ServerPlayer)` ends one player's session; `clear(MinecraftServer)` ends all sessions on a stopped server.

Bypasses start disabled, are cleared on logout or server stop, and do not persist in NBT. A replacement player session does not inherit the old state. WorldAccessPolicy access checks, all ShipPermission actions and optional AeroClaims/Open Parties and Claims player protection integrations consult this API. The bypass does not change automatic unclaimed checks or physical staff safety checks.

`ShipPermissionManager.canManage(UUID subLevelId, UUID playerId)` allows the saved owner or an online operator with an active bypass to edit a claimed ship's permissions. Obtain the manager through `get(MinecraftServer)` to associate UUID-based checks with the server. `allows` and `set` honor active bypasses; `owner` and `isOwner` retain their saved ownership meaning. Saved owners, bindings and permission IDs remain unchanged.


## ACC physics staff authorization

`PhysicsStaffInteractionGuard.authorizeTarget` and `authorizeMovementTarget` now authorize staff actions exclusively through `ShipPermissionManager` and the existing `ShipPermission.PHYSICS_STAFF` (`physics_staff`) permission. The staff guard does not consult `WorldAccessPolicy` or external claim mods. Both Simulated staff action and drag packets continue to call this shared guard. Owners and permitted members can use the staff; unowned ships retain ordinary staff access. Operator session bypasses continue to use `ProtectionBypass`. Initialization, unloaded-target and movement safety checks remain in effect.

The addon ACC permissions modal already exposes the Physics Staff checkbox, using the existing saved permission ID and bit position. Enabling or disabling it applies to bound sublevels and is rechecked during dragging.

Built-in AeroClaims and Open Parties and Claims integrations, dependency declarations and mixins have been removed. `WorldAccessPolicy` retains its public provider registration and access methods for consumers. Registered policies still govern other API consumers, and `unclaimed` additionally rejects ships owned through `ShipPermissionManager`, including bound sublevels; operator bypasses do not authorize automatic cleanup of owned ships.


## Mechanical crafting in EMI

`com.rieno.gadgetsandgizmos.lib.compat.emi.MechanicalCraftingEmiRecipe` is a client-side, optional EMI API for displaying Create mechanical crafting recipes at their actual grid dimensions. Construct it with an `EmiRecipeCategory`, a `RecipeHolder<MechanicalCraftingRecipe>`, and the current `HolderLookup.Provider`.

`MechanicalCraftingEmiRecipe.createCategory(ResourceLocation id, EmiRenderable icon)` preserves the caller's namespace and ID and uses Create's localized `create.recipe.mechanical_crafting` title. The consuming mod registers the category, workstations, and recipes and applies its own content filters. Load this API only when EMI is available on the client.

The original addon recipe-display constructor remains as a deprecated compatibility wrapper. Controller draft saves no longer clone graphs or image assets into an inserted linker. Existing stored linker graphs, shared graph import/export, controller persistence, database schema, and saved identifiers are preserved.

## Entity render role snapshots

`com.rieno.gadgetsandgizmos.lib.client.render.EntityRenderRoleCache` caches one supplied `Predicate<LivingEntity>` on the client render thread. `get(rendered)` resolves `HeadwearRenderSource` proxies to their real entity and reuses the role while the world tick, entity age, level, mount UUID and seat position remain unchanged. Both positive and negative results are cached, including while single-player simulation is paused. `invalidate(rendered)` discards an explicitly changed role; `clear()` releases all snapshots. Cache keys are weak, levels are weak references, and snapshots do not retain entities or vehicles.

The addon pilot-cap mixin consumes this cache. Pilot/controller discovery consumes the existing `BlockEntityLookupApi.findLoadedExact` API in a bounded area beside the seat, preserving the existing front/back seat distance rule and SCM checks without collecting an entire ship or requesting chunk tickets. Explicit pilot-cap equipment is checked before role discovery.

Validation: both clean builds succeeded, and native checks in a copied Youtube world passed for scheduled pilot caps, render proxies, explicit caps without seats, nearest-controller selection, cached positive/negative roles, explicit invalidation, tick/mount/seat changes, dismounting and entity removal. The 10,000-call pilot-cap lookup benchmark fell from 45,668,336 allocated bytes to 480,072 bytes (about 99% less). Full-scene frame measurements did not establish an overall FPS improvement or reproduce the reported two-second cadence consistently; a remaining paused-frame stall was sampled in a native graphics-buffer upload through ImmediatelyFast.

## Telemetry orientation and scene update APIs

`OrientationMath.pitchYawRollDegrees(Quaterniondc)` returns a `Vec3` containing pitch (X), yaw (Y), and roll (Z), in degrees, using YXZ rotation order. Yaw spans -180 to +180 degrees; a flat ship can turn through every heading while its pitch and roll remain zero. The input quaternion is normalized without mutation. Missing, zero-length, or non-finite components return zero. Pitch spans -90 to +90 degrees; Euler heading and roll are ambiguous at the vertical pitch singularity.

`ViewSceneRenderer.updateSections(LevelRenderer, minX, minY, minZ, maxX, maxY, maxZ)` forwards an inclusive section range to retained secondary scene renderers. It immediately returns when there are no scenes or the source belongs to a secondary scene. Single-section and chunk update APIs remain available. Sodium integration uses this batch method for chunk updates.

SCM authority calculations reuse their synchronized scratch buffers and reject a candidate only when actuator component bounds prove that it cannot meet the existing residual tolerance. Control limits, solver iteration limits, and authority search resolution are preserved. Airship navigation omits a preliminary force calculation when the authored propulsion branch replaces it.

`ReflectionMethods.find(Class<?>, String, int)` caches compatibility method discovery by class, method name, and parameter count. It preserves declared-method order and prefers the supplied class over its superclasses; interfaces are not traversed. The same method object is reused, and absent methods remain inexpensive. Methods retain the previous best-effort accessibility behavior. Callers must not change the returned method's accessibility. The class-scoped cache allows class loaders to be collected. `ReflectionMethods.invalidate(Class<?>)` rebuilds an entry when a host changes class metadata.

## Loaded face support lookup

`BlockEntityLookupApi.findLoadedBehindFace(Level, UUID, BlockPos, Direction)` resolves the block entity one block behind the supplied face, using the opposite direction in the target's root or Sable plot coordinates. A null face, missing SubLevel, unloaded chunk, or missing block entity returns null. It neither loads chunks nor adds tickets, and never falls back from a missing SubLevel to a root block at the same coordinates. Consumers retain their own binding identity and retry policy.

## Independent client interpolation

`SubLevelClientRenderApi.interpolatedPose(ClientSubLevel subLevel, float partialTicks)` returns an owned `Pose3dc` snapshot interpolating position, orientation, rotation point and scale from Sable's last and logical poses. It does not call `ClientSubLevel.renderPose(float)` or alter the shared cached render pose. Use this client API for lenses or shape/model calculations that sample a different interpolation time from the current world render.

The client `ViewTransforms` pose provider now uses this snapshot API. Remote feed lens poses use `getGameTimeDeltaPartialTick(true)`, matching Sable's terrain interpolation while paused. Existing view controls, serialized source identifiers and server transforms are unchanged.

## Bounded client text input

`com.rieno.gadgetsandgizmos.lib.client.ui.TextInputScreen` is a client-only abstract screen for collecting one text value. Its protected constructor accepts a nullable parent `Screen`, title `Component`, nullable initial text, nullable `Consumer<String>` confirmation callback, and maximum length (clamped to at least one).

Consumers create their own widgets in `init()` and call `initializeInput(EditBox)` to register and focus the field. The limit is applied before the initial value, and current edits survive widget rebuilding on resize. Call `confirm()` from a Save button; Enter and keypad Enter also confirm. Escape or `onClose()` returns to the parent without invoking the callback. Confirmation supplies the field text unchanged, including whitespace, on the client thread; consumers own normalization and packet submission.

The dialog inherits its parent's pause behavior; a null parent uses normal screen pausing. The API does not load addon classes or register content. Existing controller dialog constructors and trimming behavior remain available through the addon's `ControllerTextInputScreen`.


## Native schedule authoring and live SCM drive geometry

`lib.client.schedule.CreateScheduleInputFields` builds the registered Create input's own widgets. `data(input)` includes native defaults, `choices(key)` exposes the native ordinal labels, and `edit(input, key, val)` applies the native text filter and numeric bounds. This API is client-only.

`CreateScheduleInputScreen` retains its existing data-only constructor and adds a complete-input constructor accepting `Consumer<IScheduleInput>`, a destination suggestion callback and a type predicate. The editor handles registered instruction/condition types, native widgets, destination completion, ghost item parameters and configured inventory items. The caller owns the detached input and applies the accepted result to its own graph or schedule.

`MountedSeatRegistry.discover(bodies, maximumBlocks)` discovers seats in loaded body plots without forcing chunks. `isMountedAt(level, pos, mount)` accepts plot mounts and world-space mounts tracked on the seat's body. Player occupancy excludes dead players, spectators and NeoForge fake players, deduplicates player UUIDs and seat addresses, and retains ordinary entity occupancy separately for seat availability.

`KineticGraphHelper.getDownstreamNeighbours(block)` follows Create's actual connection rules while excluding the source side and unrelated powered branches. Zero-ratio outputs retain compatible reverse connections where Create exposes them.

`PropellerForceGeometry` supplies an unsigned thrust axis and application point in the owning block's local frame. An addon implements it for propellers with moving heads; the consumer applies the provider's signed thrust separately.

`ScmSpeedGroupAllocator.Influence.at(force, pos, center)` derives the current moment arm. `allocatePhysical(influences, force, torque, maximumPower)` allocates independent positive efforts against a physical wrench. The existing normalized allocator remains available. Hosts remain responsible for drivetrain ownership, calibration curves and selector routing.

`ScmGeometryFrame` retains an origin and orthonormal axes, transforms saved positions/directions into a live frame, remaps its origin, and reads/writes additive NBT. Invalid or missing stored frames return null so callers can retain legacy geometry.

`ScmPoseControl.advance(current, target, degreesPerSecond, tickSeconds)` bounds continuous joint setpoint motion without changing the requested pose.

These APIs do not depend on addon classes or own addon blocks, schedules, maps or saved identifiers.


## Ground route recovery and steering API

- `ScmGroundSteering.update(headingError, curvature, forwardSpeed, yawRate, steeringFeedForward, tickSeconds)` retains the selected turn across opposing-heading noise and advances a physical yaw setpoint at 1.6 normalized units per second. Keep one instance per active vehicle command; discard it when command ownership changes. Reverse steering is converted once by the host at its actuator boundary.
- `ScmControlAxes.routeSteeringDemand` blends curvature tracking with heading recovery. `groundRecoverySpeedLimit` reduces speed through a large heading correction while preserving an already stopped command. `steeringSignal` rounds signed position inputs symmetrically, including a neutral deadband; propulsion selectors continue to use `ScmSpeedControl.quantizedSignal`.
- `ScmControlPriority.navigationOwnsYaw` keeps ground transit yaw available for connected assemblies and hands it to an authored final heading only during final approach. Flight retains its existing translation and final-heading authority.
- `GroundPathPlanner.forwardSplineControl` derives recovery lookahead from horizontal displacement and the vehicle heading. Height differences no longer dilute ground steering, and large lateral offsets use a longer forward merge.
- `SplineConstraintFrame.canGuide` has an additive overload accepting maximum heading angle and lateral speed, so hosts can wait for tyre alignment before attaching a route guide. `canAdvance` requires physical endpoint capture on the selected axes before committing an ordered spline cursor. Existing overloads remain available.
- `ScmPoseControl.steeringTarget` interpolates generic `SteeringPose` samples from neutral through measured positive or negative yaw authority. Apply the resulting angles with `advance` for a bounded physical setpoint. Missing directional response returns `null` so the host can retain its other actuator strategy.
- `ScmSteeringMode.wheelDemand` retains the configured single steering axle when mass moves the centre of mass across it; multiple axles continue to use their relative positions. No serialized IDs or saved data keys change.

The addon supplies live world geometry, calibrated responses, route state and actuator bindings. The library owns reusable recovery, authority, interpolation and signal math.

## Explicit signal value types

`com.rieno.gadgetsandgizmos.lib.control.SignalValueType` exposes `REDSTONE` and `SCALAR` with stable ids `redstone` and `scalar`. `optionIds()` returns the dropdown order; `fromId(String)` defaults missing, null, or invalid ids to `REDSTONE` without inspecting the numeric value.

`toRedstoneStrength(double)` rounds and clamps to 0-15: Redstone 1 gives strength 1; Scalar 1 gives strength 15. `toNormalizedRedstone(double)` divides the quantized strength by 15 for transport. `toScalar(double)` preserves continuous 0-1 precision for Scalar values and converts quantized Redstone strengths to 0-1 for analogue receivers. Negative values clamp to zero, positive infinity to full output, and NaN to zero.

The addon saves `SignalValueType` on Configured Key Output, Direct Target Output, Linker Face Output, Local Redstone Output, Redstone Link Output, and Set Block Data nodes, including nested functions. Missing selections now become Redstone; existing graphs using 0-1 fractions must select Scalar. Set Block Data applies the selection only to signal fields; RPM, angles, arbitrary data, and raw Nixie values keep their existing units. The library has no addon dependencies or registrations.


## SCM vehicle and steering availability

`ScmBuiltinControlModes.setGroundSeaEnabled(boolean)` and `setPlaneEnabled(boolean)` add or remove the respective built-in control modes from the shared registry. `isGroundSeaEnabled()` and `isPlaneEnabled()` report their availability. Both remain enabled by default in the library for API compatibility; a consuming mod owns its server defaults and configuration synchronization. Disabled modes resolve to Airship through `ScmControlModeRegistry.resolve`, while the stable `car` and `plane` identifiers remain unchanged.

`ScmVehicleClassifier.isSelection(String)` recognizes saved built-in identifiers even while disabled. `isAvailableSelection(String)` validates current selectable modes, and `suggestAvailable(boolean wheels, boolean liftingSurfaces)` restricts automatic detection to enabled modes. Wings retain precedence over wheel discovery.

`ScmSteeringMode.availableSelections()` omits Front Wheel, Rear Wheel, 4-Wheel and Tank while Ground/Sea is disabled. `isAvailable()` and `isAvailableSelection(String)` expose the same policy. `availableOrFallback(ScmSteeringMode)` resolves live steering without rewriting a disabled saved choice. Rudder, Custom and Auto remain available. The existing `selections()`, `fromId(String)` and `isSelection(String)` methods retain their complete serialized selection set.


## Live spatial obstacle bypasses

`com.rieno.gadgetsandgizmos.lib.navigation.ObstacleDetourPlanner` prepares a complete local bypass through a requested route rejoin. `queue(Request)` retains its candidate and leg cursors between ticks. A request supplies the world start/rejoin, travel direction, horizontal and vertical clearance offsets, maximum offset, vertical movement availability, and upper/lower preference.

Call `QueuedPlan.advance(Probe, maximumProbes, workAvailable)` on the owning game thread. The host probe must validate the full swept vehicle hull against current loaded world and vehicle geometry, and reject directions its controls cannot execute. The boolean work supplier lets callers use a measured tick allowance. `waypoints()` remains empty until every leg of one candidate is clear; `finished()` with empty waypoints means no candidate succeeded. Accepted routes start with a pure sidestep or vertical step, pass the obstacle, then return to the requested rejoin. Discard/requeue the plan when its scene, start, or rejoin becomes incompatible, and keep checking the next live movement segment after adoption. This API does not mutate saved routes or vehicle state.

`RouteTrafficPriority.requestsDetour(Decision)` requests a bypass for a yielding conflict or either participant in a head-on encounter. `prefersUpperPass(subjectId, otherId)` gives opposing UUID pairs complementary vertical preferences. Each vehicle's right-hand direction similarly gives opposing traffic separate lateral passages. Existing traffic decisions and serialized routes are unchanged.

The SCM consumer supplies its connected compound hull, loaded Sable collision policy, and live acceleration authority. It commits accepted prefixes to the vehicle's transient route, retains the authored schedule spline, restores normal traffic yielding after the prefix is traversed, and can reopen planning when another obstacle closes the bypass. Full path search remains the fallback when local spatial candidates fail.


## Saved equipment model offsets

`com.rieno.gadgetsandgizmos.lib.item.ItemModelOffset` stores bounded integer steps in an item-owned custom NBT key through `DataComponents.CUSTOM_DATA`. Its constructor accepts the key, minimum and maximum steps (including zero), and positive finite model pixels per step. `readSteps`, `writeSteps`, `adjustSteps`, and `offsetPixels` clamp the saved value, avoid arithmetic overflow, and preserve other item data. Zero removes only this API's key; an empty custom component is removed. Mutating methods return whether the stack changed. The host owns item eligibility, server authorization and synchronization.

`com.rieno.gadgetsandgizmos.lib.client.ui.EquipmentSlotScrollHandler.matchesSlot` identifies a player's own humanoid armor slot by its backing inventory and slot index, including creative slot wrappers. `handle(ScreenEvent.MouseScrolled.Pre, Player, EquipmentSlot, Predicate<ItemStack>, IntConsumer)` consumes finite vertical wheel input only over an eligible equipped stack with an empty carried stack, supplies one signed step and cancels the event. Register it on the client event bus; the host sends the adjustment request.

`com.rieno.gadgetsandgizmos.lib.client.render.HeadOffsetArmorModel` wraps a posed humanoid head armor model. Positive pixel offsets move armor upward along the rotated head; its render calls preserve the original model parts and restore the caller's pose even when rendering fails. Return it from an item's client `getGenericArmorModel` hook only for the head slot. Existing textures, tint, trim and glint passes continue using the wrapped model. Keep client APIs off the dedicated server.

The addon uses quarter-pixel steps from -4 to +4 model pixels, saves `PhysicsGogglesHeight` on the goggles stack, validates signed steps and the current menu on the server, synchronizes the inventory, and relies on normal equipment packets for tracking players and later joins. Existing goggles default to zero; binding data and HUD settings are preserved. Both stable and experimental consumers register the helpers independently.


## Display source selection and linked text sizing

`com.rieno.gadgetsandgizmos.lib.display.DisplaySourceSelection.available(sources, presentations)` merges payload and presentation-only sources in order, preserving the payload when both lists contain the same ID. `selected(sources, selectedId)` returns the currently available source or null so the host can choose a fallback. The host must resolve a manual selection before automatic presentation preferences.

`DisplayTextScale` defines the additive `LinkedTextScale` NBT key, a default multiplier of 1.0, limits of 0.25-4.0, and a 0.25 adjustment step. `normalize` and `read` validate network or saved input. `supportsFrame` permits display-link text, schedules, and external text/CRN frames while excluding content owned by a graph node and non-text sources. `layout` returns fitted font scale, row spacing, vertical position, maximum text width, and the visible row count. `canvasSize` computes the virtual schedule viewport for the requested multiplier. Hosts render within that viewport and keep source selectors outside the scaled content.

The addon persists the selected source as `AccDisplaySelectedSource` and text size as `LinkedTextScale`, copying both settings across connected display panels. Existing saves use their previous default text size. The display wrench GUI provides decrease, reset-to-100%, and increase controls; the scale packet updates the setting without changing source selection. Authored ACC nodes, graphs, plotters, cameras, tablet mirrors, and CC:Tweaked retain their own sizing. Source tabs include presentation-only schedules and remain usable while a terminal source is active.

## Element inspector layout

`com.rieno.gadgetsandgizmos.lib.display.ElementInspectorLayout.forType(type)` supplies the shared font, color, and style rows for element property inspectors. Text inputs, buttons, and toggles share paired style columns; values retain their separate port row. `hasFontControls`, `pairedStyles`, offset accessors, `colorLabelOffset`, and `fontStyleWidth` let hosts draw controls and calculate hitboxes from the same policy. The API has no client or addon dependencies.

The HUD designer consumes these rows for drawing, font/style selection, and color/font sliders. Long button labels use the existing `GraphTextLayout.ellipsize` API. Canvas previews are clipped to their bounds, and canvas selection cannot capture sidebar clicks.


## TextInputScreen background rendering

`TextInputScreen.renderBackground` now leaves the world visible without Minecraft menu blur or dimming. Consumers draw their own dialog background in `render`; bounded input, resizing, focus, Save/Enter delivery and cancellation use the existing library API.


## GraphValueMirrors

`GraphValueMirrors.merge(live, sampled, authoritative)` returns a new map without changing either input. Preview samples fill absent ports and replace ordinary live values. For ports selected by the predicate, existing executed values take precedence, including empty strings, false and zero. The host supplies its policy for authoritative ports; the library has no addon dependency.


## GUI partial grids

`GuiTextureRegion` defines a validated rectangle inside a texture atlas and draws it at native or requested dimensions. `GuiNineSlice` takes nine regions in row order (top-left through bottom-right), requiring matching column widths and row heights. `draw(graphics, x, y, width, height)` keeps native corners and stretches edges and center. The overload with `borderScale` scales the corners with the host layout; small destinations reduce opposing corners proportionally without overlapping. Nonpositive destination sizes do not draw; a positive destination requires a finite, positive border scale. Each region is sampled separately, so atlas gutters are excluded. Texture IDs, partial coordinates and widget text remain owned by the consuming addon.


## Authoritative physical fields during controller recovery

`com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagCopies.copyReplacing(source, replacements, keys)` returns an independent copy of the source with an authoritative field group replaced. A key missing from `replacements` is removed from the result; other source fields remain. Both retained payloads and replacement values are copied. Null source/replacement snapshots are supported; null keys leave the source unchanged in the returned copy.

The addon separates physical controller state from retained SCM programs. The additive `ControllerPhysicalState` block-NBT compound stores the actual embedded block, material, block entity data, and current placer identities, including an empty snapshot for standalone controllers. Explicit block snapshots take precedence over database attachment data on reload. Existing saves without this compound retain their legacy data; standalone controller block states reject embedded backing data.

SCM persistence recovery, publication, and sublevel rebinding require a physical Ship Control Module. Retained records cannot create an SCM on an ordinary ACC. Recovering graphs/settings preserves the current mount, orientation, backing data, and placer identities. Controller items clear their physical snapshot while retaining their program metadata, and schematic payloads retain their existing placement/remapping behavior.

## ARGB opacity API

`com.rieno.gadgetsandgizmos.lib.color.ColorOpacity` exposes `opacity(int color)` as a normalized 0?1 value and `withOpacity(int color, float opacity)` to replace alpha while preserving RGB. Replacement values clamp to 0?1; non-finite values become transparent. Colors remain packed ARGB integers.

HUD editors can combine this API with `ColorPickerModal`: the modal owns color selection, live preview, accept and cancel, while the host owns its preview hitbox, opacity slider, model updates and undo history.

## Camera scene compatibility â€” 11 October 2026

`ViewSceneRenderer` captures now retain their owned entity and damage buffer sources ahead of optional deferred buffer replacements. Remaining scene geometry is flushed before its view matrix is restored, using the capture framebuffer and supplied projection/view matrices.

When Entity Culling is installed, secondary captures ignore cached player-view occlusion and leave player out-of-camera history unchanged. Lens frustum and depth tests still apply. The ordinary player view retains its optional render optimizations.

No public methods, saved data, identifiers, or addon-facing contracts changed. Existing ACC and CCTV consumers receive the protection through the current capture API. These changes address identified compatibility gaps; the reported partial-player screenshot still requires an in-game retest in RE_Rotaria.

## Camera capture render stage isolation

The existing ViewSceneRenderer API now preserves its framebuffer bindings and viewport across NeoForge world render callbacks. Callbacks that clear screen-sized post-processing targets can no longer change the viewport used by subsequent camera terrain and entity draws. This applies to display and tablet feed captures; direct camera control keeps the normal world render path. No new public methods or addon imports are required.

In RE_Rotaria, KubeJS 2101.7.2-build.321 clears its highlight targets after the sky pass, then rebinds the main target without restoring the viewport. Live inspection measured terrain using 2560 x 1369 while camera entities used 264 x 168. Restoring the capture target after the callbacks made the inspected terrain and entity viewports agree at 264 x 168.

In-game verification: the user confirmed correct alignment after the capture viewport was restored, including zoom. The fix does not change camera gameplay, saved data, or display/tablet callers.

Validation: library and addon clean builds passed (62 tests total). The addon bundle contains the freshly built library with ViewSceneRenderStageMixin registered for the client. The equivalent live viewport correction was confirmed by the user, including camera zoom.


## Persistent strand anchors

`com.rieno.gadgetsandgizmos.lib.physics.StrandAnchor` retains a strand UUID, arc distance,
optional owning block position, and optional owner SubLevel UUID. It has no addon or
Simulated dependency. Owner positions are copied to immutable `BlockPos` values.

- `write(CompoundTag)` and `read(CompoundTag)` retain the existing `AnchorRopeUUID` and
  `AnchorPathPosition` keys. `AnchorOwnerPos` and `AnchorOwnerSubLevelUUID` are additive.
  Old saves load with an unknown owner; use `withOwner(...)` when the strand reappears.
- `sample(...)` / `samplePath(...)` sample current world-space points by arc distance,
  skip collapsed segments, and clamp to the endpoints. Empty or nonfinite paths return
  `null`; the point list is read synchronously and is not retained or mutated.
- Call `isBroken(ownerLoaded, ownedStrandId)` only when the strand is unavailable.
  `ownerLoaded` must mean the owning chunk/SubLevel and its saved block entity data are
  available. Unknown or unloaded owners never count as a broken strand. A matching owner
  UUID also remains valid while registration with the strand manager is pending.

Consumers keep their last attachment position while the source is unavailable, save
owner information when it is discovered, and remove attachments only after confirming
that a loaded owner no longer owns the saved strand. Gameplay interactions, content
registration, and dependency-specific physics operations remain in the consuming addon.

## Scene texture precision and presentation

`SceneRenderTargets.createPreciseLike(RenderTarget source, int width, int height, boolean clearError)` creates an owned RGBA16F texture target with linear filtering. It retains the source's depth/stencil settings, including through resize and stencil changes. Existing `createLike` behavior is preserved.

`SceneRenderTypes.opaqueTexture(ResourceLocation texture)` creates a render type for an already lit, opaque scene. Retain it with the texture. It samples RGB without using intermediate framebuffer alpha, player lightmap, or world fog, and distributes final 8-bit rounding error with stationary noise below one color step. Register its shader through the library client bootstrap. The vertex format is POSITION_TEX_COLOR.

`ViewSceneRenderer.draw` and `drawFitted` now use these capabilities. This preserves the vanilla fog background at the horizon and sunrise colors, which previously disappeared or blended incorrectly through the text shader. Public view signatures and camera source contracts are unchanged.

Validation: both clean builds passed, with 68 tests passing. A hidden GPU context compiled and linked the actual scene shader, confirmed preservation of fog RGB across intermediate alpha values, and measured approximately seven times lower average-column gradient rounding error with precise storage and final dithering. The complete in-game feed still needs the requested visual recheck.

The actual compiled SceneRenderTargets API also passed a hidden-context lifecycle check: creation with depth on/off, resize, stencil enable and source matching, framebuffer completeness, and disposal preserved precision/filtering without GPU errors.

## Physical shaft angle accumulation

`com.rieno.gadgetsandgizmos.lib.kinetics.KineticAngleHelper.advanceRotationAngleDegrees(double angleDegrees, float rpm, long elapsedTicks)` advances a caller-owned shaft angle using Minecraft's 20 ticks per second and returns a normalized angle in (-180, 180]. It adds no model alignment or position-dependent phase offset. Zero RPM retains the angle; zero or negative elapsed ticks add no motion.

Consumers retain their last sample tick and supply elapsed ticks since that sample. Use zero elapsed ticks for the first sample after placement or loading so saved angles survive reloads without a world-time jump. Explicit held angles and precise source angles can still replace the accumulated angle through the existing APIs.

```java
long elapsed = lastSampleTick == Long.MIN_VALUE ? 0L : now - lastSampleTick;
angle = KineticAngleHelper.advanceRotationAngleDegrees(angle, rpm, elapsed);
lastSampleTick = now;
```

The Aileron Bearing consumes this API for shaft motion. Selecting Computer mode without a target command retains each head's current angle, and an idle fresh bearing keeps a neutral target.

## Configurable action input API

`lib.client.input.ClientActionKey` is a client-only configurable NeoForge key mapping. The owning mod supplies its translation key, category and default key, and registers it through `register(event::register)`. `isHeld()` reads the configured keyboard or mouse button and NeoForge modifier, independently of sprint/sneak movement. `sample(active)` returns press/release transitions and releases inactive contexts. `matchesKeyboard` and `matchesMouse` support actions intercepted before vanilla handling. `label()` and `hint(translationKey)` resolve the current assignment every time they are displayed, including unbound and modified keys.

`lib.control.ActionInputState.sample(previous, current)` derives held, pressed and released action masks. Owning mods define the action bits, validate received masks, and choose how to apply actions on the server.

`lib.control.ActionKeyLabels` registers lazy label providers keyed by the owning mod's key translation ID. `ClientActionKey` installs its label provider. Common-side tooltips can use `label(action)` or `hint(translationKey, actions...)` without loading Minecraft client classes. Duplicate IDs are rejected. Labels are looked up live; unregistered IDs display the unbound key label.

`lib.control.PlayerActionInputs.update(player, held)` retains validated server-side player actions in memory. `isHeld(player, action)` expires samples after 40 player ticks; callers refresh active input within that interval and send zero when controls release. Keys and input state are not saved. Owning mods define and validate their action masks and call these methods only on the owning game thread.

## Dynamic scene-buffer readiness

`com.rieno.gadgetsandgizmos.lib.client.render.DynamicRenderBuffers.enableForFrame(VeilRenderer renderer, ResourceLocation owner, DynamicBufferType... buffers)` requests the owner's scene buffers on the render thread and returns whether the caller can use them in this frame.

- `false`: the buffer configuration changed, or one or more required buffers remain inactive. Skip the dependent render pass and retry on a later frame.
- `true`: the request did not change the configuration and every required buffer is active.

Use an owner ID from the consuming mod's namespace. Release the owner's request through Veil's `disableBuffers` API when the effect no longer needs it. This helper does not create support for buffers disabled by another renderer.

The library's Veil lighting compatibility consumes this API before binding or clearing the light framebuffer. Veil 4.0.0 and 4.3.2 were checked. When Iris prevents Veil's albedo and normal buffers from becoming active, the unsupported deferred-light pass is skipped; supported rendering and idle buffer release continue. No addon-specific types are exposed.


## Retained interaction data outputs

`GraphInteractionOutputs<V>` retains the latest submitted value per host-defined data-port key independently of player-scoped UI storage. `submit(key, value, playerId)` publishes a non-null value and an optional player UUID; submissions from different players replace the shared graph value for that port. `valueOrDefault(key, fallback)` returns the latest submission, including empty strings, or the supplied fallback before a submission exists. `submission(key)` returns the value and owner, or null if absent.

The host chooses when graph evaluation reads submitted outputs and when personal UI evaluation reads personal state. `snapshot()` returns an immutable map for host-owned serialization; restore entries through `submit`. Call `retainKeys(predicate)` after compiling topology changes and `clear()` when resetting the runtime. Keep mutation and evaluation on the owning thread. This API does not depend on addon nodes, NBT, or Minecraft player classes.


## Numeric comparisons and pulse counters

`GraphComparison.numbers(a, b, operator)` compares numeric values across integral and floating-point representations. Equality uses numeric equality, including signed zero; NaN is unordered and only satisfies inequality. `normalizeOperator` resolves symbolic and named operators to `==`, `!=`, `<`, `<=`, `>`, or `>=`, returning an empty string for unsupported operators. `matches(operator, equal, ordering)` applies the same operators to host-defined nonnumeric equality and ordering.

`GraphCounters.wholeCount(value)` truncates finite positive limits to a nonnegative int, saturating at Integer.MAX_VALUE and treating nonfinite values as zero. `limitedPulse(count, limit, reset)` returns the new one-based execution count and whether the pulse passed. A true reset restarts before processing that pulse; the host owns reset-only execution paths. There is no per-loop iteration cap on retained pulse counts.

`GraphCounters.step(value, amount, direction)` adds or subtracts one step. Counters start at zero in the host, permit negative results and fractional steps, and retain the previous finite value for nonfinite steps or overflow. `Direction` exposes stable `increment` and `decrement` IDs; missing or unknown IDs default to Increment. Hosts own graph state, persistence, node registration, controls, and execution dispatch.


`GraphDataDependencies.compile(graph, dataEdgePredicate)` captures data dependencies separately from execution edges. `affectedNodes(nodeId, port)` returns an immutable set of direct and transitive consumer node IDs for targeted evaluation-cache invalidation, stopping safely at cycles. Recompile after topology changes; keep cached traversal on the owning graph thread. The host decides which retained value changes invalidate evaluation results.


## Ship access modes and ownership transfer

`lib.scm.ShipPermissionManager.isEnabled(MinecraftServer)` enables ship permissions on multiplayer servers and integrated worlds opened to LAN. Private single-player worlds ignore saved restrictions, reject permission edits and do not create new claims. The check uses common server APIs and is evaluated on every action, so opening to LAN immediately activates the saved mode and permission list. Existing owners, members and modes remain saved between sessions.

`isOpen(UUID)` reads the saved access mode through connected-body bindings. Unclaimed ships, new claims and legacy claims without an `Open` tag default to Open. `allows(...)` ignores the permission list while Open or while permissions are disabled. Closed claims continue to grant all actions to the owner, honor session-based operator protection bypasses, and apply each member's saved rights. These changes apply equally to interaction, graph editing, placement, destruction, physics staff use and storage. External protection providers retain their rules.

`setOpen(UUID subLevelId, UUID actorId, boolean open)` changes the mode only for the owner or an active protection-bypass operator. Closing an ownerless ship creates a claim for the actor; leaving it Open creates no claim. Both access modes preserve the permission list. The `Open` boolean is saved alongside the existing `Ship`, `Owner`, `Members` and `Bodies` tags without changing those keys or permission IDs. Legacy claims become Open until manually closed.

`transferOwnership(UUID subLevelId, UUID actorId, UUID ownerId, String prevOwnerName)` requires management rights and a different, non-null new owner. It updates the root claim, preserves its mode and connected-body bindings, removes the new owner's redundant member entry, and retains the former owner as a member with every ship action. The former owner loses management rights unless an operator protection bypass is active. The supplied previous-owner name is saved for offline member display.

Call these APIs on the owning server thread. `members(...)` continues to return immutable snapshots of explicit members; consumers should add the current `owner(...)` to any player list and show its implicit full access separately. The addon permission screens now consume these APIs, label the owner, and expose Open/Closed access and ownership transfer. Permission controls are unavailable in private single-player worlds.

`lib.util.PlayerNames.resolve(MinecraftServer, UUID)` resolves an online player's name, then the local UUID profile cache, then the UUID text. It performs no remote name lookup. A null player ID returns an empty string; a null server uses the UUID fallback.


## Counter reset API

`GraphCounters.step(double value, double amount, Direction direction, boolean reset)` adds reset control without changing the existing three-argument overload. When reset is true, it returns zero without applying the step, including when the step is non-finite. With reset false, it uses the existing increment/decrement behavior. The host owns pulse timing and retained state.


## Chat API

`ChatArguments.capture(startWord, message)` returns an optional immutable list of string arguments. Matching is literal and case-sensitive at the start of the message; the prefix must end at whitespace or the end of the message. Separating whitespace is collapsed. A prefix-only match returns an empty list, while a missing or blank prefix never matches.

`ServerChatApi.listen(server, receiver)` observes accepted raw player chat with the sender UUID on the server thread. Close the returned subscription when the host unloads. Server shutdown closes all subscriptions. Messages sent through `ServerChatApi.send(server, playerId, text, global, allowGlobal)` are literal system chat: private mode requires an online recipient; global mode broadcasts only when the host-provided gate allows it. Rejected global delivery does not fall back to private delivery. Call send on the owning server thread. System messages are not republished as player chat.

## Worldless container tracking

`TimedItemTransformations.track(Container, @Nullable Level)` accepts a null level and skips worldless recipe probes and client-side containers. It does not inspect the container or change its inventory in either case. A non-null server level retains the existing tracking behavior. The block-entity `setLevel` hook follows the same nullable contract.

Run the library's container lifecycle regression tests with `gradlew runContainerGameTest -PcontainerGameTests=true "-Pneoforge_version=21.1.228"`. The runtime version override meets the local Sable dependency's minimum; it does not change the project's version settings. The tests reproduce temporary chest recipe probes calling `setLevel(null)` and a placed chest detaching and reattaching. They use an isolated world under `build/container-gametest`; test classes and structures are excluded from release jars.



## Nearby chat audiences

`ServerChatApi.sendNearby(BlockEntity source, String text, boolean allowed)` sends literal chat only within the source root dimension. For a source on a Sable sublevel, recipients must be tracking/riding or contained in that exact sublevel; world-space distance does not add recipients. For a source outside a sublevel, player positions are projected to world space and must lie in the source chunk or one of its eight adjacent chunks. Unavailable sources, disabled delivery, empty messages and empty audiences return false. Call on the owning server thread. Experimental hosted components resolve their real host before choosing the location.

`NearbyChatRange` exposes the same pure UUID/chunk selection rule for other hosts. Its three-by-three chunk bounds include corners, handle negative chunk coordinates and reject other dimensions.

The addon now exposes Broadcast Nearby. It reads legacy BroadcastGlobally NBT values as nearby mode. The existing server config key `server.advanced_controller.allowGlobalChatBroadcast` remains the compatibility gate for nearby delivery so previously disabled servers stay disabled; its description and Java accessor now refer to nearby chat. Private delivery remains independent of that gate.


### Chat capture and sender filters

- `ChatPlayerFilter.matches(players, playerId, playerName)` accepts an empty list for all senders, or matches complete account names (case-insensitive) and UUID strings. Nonempty invalid lists match nobody.
- `ServerChatApi.intercept(server, Predicate<Message>)` subscribes a receiver that returns `true` only after capturing a message that should be hidden. Every active receiver sees the message even if another requests hiding. Close the subscription when its host unloads.
- `ServerChatApi.receivePlayerChat(server, message)` completes all receivers on the server thread and returns whether the original player message should be canceled before broadcast. Off-thread callers wait for server-thread processing.
- `Message` now includes the authenticated `playerName`; the original two-argument constructor remains available. `listen` remains an observing subscription and never requests hiding itself.
- The NeoForge adapter cancels only successfully captured messages; already canceled events remain excluded.


### Explicit chat recipients

- `ServerChatApi.sendToPlayers(server, players, text)` sends a literal system-chat message on the server thread to online players whose account name or UUID appears in the list. Names match case-insensitively. Each player receives the message once, including when listed by both name and UUID. Empty lists, blank messages, and lists containing only unknown/offline players deliver nothing. This API never broadcasts or falls back to another recipient.
- `ChatPlayers.identifiers(GraphValue)` reads raw or typed string entries from a graph list. Invalid entries remain empty identifiers so an invalid nonempty list cannot become an unrestricted audience.
- `ChatPlayers.parse(text)` and `format(players)` provide comma-separated player-list editing, accepting whitespace between entries as well.


### Title announcements and player selection

- `ServerTitleApi.send(server, players, fallbackPlayer, action, text, animation)` sends the vanilla title, subtitle, actionbar, times, clear, or reset packet to listed online account names/UUIDs, once per recipient. Empty lists use only the optional fallback player; unmatched lists never broadcast. Invoke on the owning server thread.
- `Action.options()` supplies all six command modes. `TimeUnit` converts ticks, seconds, or Minecraft days into bounded nonnegative animation ticks. `Animation.DEFAULT` uses vanilla timings (10/70/20 ticks).
- `Text` supports literal or JSON components, optional RGB color, and inherit/true/false options for bold, italic, underlined, strikethrough, and obfuscated styles. JSON component data is resolved per recipient, without running a command or reporting command success in chat.
- `OnlinePlayerSelection` retains saved names/UUIDs, toggles online players by UUID, supports select-all/clear, and preserves offline entries until explicitly cleared.
- `PlayerSelectModal` is a reusable client modal with online-player checkboxes, skins, scrolling, and All/None/Cancel/Apply controls. It accepts saved identities and returns a typed selection through a callback; client code remains under `lib.client.ui`.


### Host-styled player selection modal

- `PlayerSelectModal.renderStyled(..., Appearance, ...)` accepts the host's established panel, button and input-field renderers, plus its selection and text colors. The library retains reusable modal layout, roster rendering and interaction behavior without importing addon UI classes.
- Player rows use the established worker-selector dimensions (50-pixel cards, 54-pixel spacing). All/None stay on the left; Cancel/Apply use the established compact footer buttons on the right, with a compact fallback on small screens.
- The original `render(..., ColorPickerModal.Chrome, ...)` remains available as a deprecated compatibility adapter.

## Graph data operations

`com.rieno.gadgetsandgizmos.lib.graph.GraphDataOperations` provides reusable, host-independent operations:

- `int length(GraphValue value)` counts top-level list entries, map entries, or UTF-16 string units (matching the established String Length node). Null and unsupported values return zero. The raw value determines the result, including values wrapped with type `any`.
- `String stripJson(String text)` decodes JSON primitive strings and recursively joins array/object values with a single space. Object keys are omitted. Numbered object keys are visited in numeric order; other objects retain source order. Null and empty values contribute no text. String escapes are decoded by the JSON parser; punctuation inside string values is retained.
- The decoder also accepts unquoted numbered keys and missing colons immediately before quoted values in numbered objects, such as `{0"Hello",1:"World!"}`, returning `Hello World!`. Serialized graph `Type`/`Payload` wrappers are unwrapped so list/map text does not include type metadata.
- Plain text and malformed input are returned unchanged. A null input returns an empty string. This is value extraction, not a JSON-to-JSON conversion or a Minecraft text-component renderer.

The consuming addon owns node IDs, schemas and UI defaults. Gadgets & Gizmos registers `createthrusters:length` (`value:any` -> `length:number`) and `createthrusters:strip_json` (`value:string` -> `value:string`) through the existing graph node/runtime registries. Both are stateless data nodes and require no execution pulse.

## Color picker host appearance

`ColorPickerModal.renderStyled(GuiGraphics, Font, int screenWidth, int screenHeight, Appearance, int mouseX, int mouseY)` renders the reusable picker using host-supplied panel, button and field drawing callbacks. `Appearance` supplies `PanelRenderer`, `ButtonRenderer`, `FieldRenderer`, and the backdrop, title, value, label, border and cursor colors. Callbacks run on the client render thread and receive the final screen coordinates and dimensions.

The styled layout centers the HSV controls, uses a 20-pixel hex field, reserves a gap above the footer, and groups Cancel/Apply buttons at the lower right with the established 62/64-pixel widths. Rendering and mouse hit regions share the same bounds. Live preview, six/eight-digit hex input, HSV dragging, alpha, Enter/Apply and Escape/Cancel semantics are retained.

The existing `render(..., Chrome, ...)` API and its original layout remain available. Passing null appearance to `renderStyled` uses that default renderer. The addon supplies its existing modal panel, option-field and button implementations, so classic textures and V2 theme customization remain host-owned.

## Opt-in view target tracking

The library provides reusable target selection and framing under `com.rieno.gadgetsandgizmos.lib.view`:

- `ViewTargetLock<K>` retains one target identity. `update(origin, range, hits, resolver)` first resolves the current target, then acquires the nearest eligible hit when that target is unavailable or outside range. Distance is measured to the target's nearest bounds surface, allowing large bodies whose centre lies beyond the range. `clear()` releases the transient lock. The caller controls which detected hits and categories are eligible.
- `ViewTrackingTargets.hits(level, hits, filter, excludedBody)` adapts ray hits on players and blocks belonging to SubLevels into world bounds. `resolve` follows a retained player/body identity using loaded geometry and reapplies the existing ray filter. Ordinary terrain and non-player entities are not tracking targets. A caller can exclude its own body. Player bounds are transformed through their containing body; body bounds use the existing assembly-hull API.
- `ViewTargetFraming.aim(pivot, target, mount, frame)` resolves a target bearing in a rig's local or global frame. `fieldOfView(pose, bounds, aspect)` fits every bounds corner in the horizontal and vertical axes, including 25% framing space, within the supported 5–120 degree vertical field of view. Targets enclosing the lens or too large to fit use the maximum field of view.
- `ViewRig.track(angles)` permits full target pitch below the manual hinge limit. Ordinary mouse control limits remain unchanged. `frameTarget(fov)` widens immediately and eases inward until it reaches a stable field of view.

`ViewControlState` adds `autoTracking`, serialized as `AutoTracking`; absent fields and the retained three-argument constructor default to false. `ViewControlInput` adds a tri-state tracking command: `-1` leaves it unchanged, `0` disables it and `1` enables it. Older commands and the retained five-argument constructor preserve the current setting. Invalid tracking values are rejected.

`ViewControlLayout` and `ViewControlPanel` provide a separate reticle toggle in projected overlays and compact tablet controls. The former layout constructor is retained. Control hosts continue sending the existing NBT command envelope; no host-specific packet format is introduced.

The addon Camera consumes these APIs only in Sentry mode with tracking enabled and no direct controller. It acquires from fresh ray samples, retains a target while it remains eligible and within `Max Ray Length`, and restores patrol zoom when released. The graph's `auto_tracking` boolean is labelled `AutoTracking` and defaults to false. An explicit display/tablet choice persists until the graph changes that boolean. Target identities are transient; configuration and tracking overrides survive saves.

Capture hosts call the additive `ViewSource.viewPose(partialTick, aspect)` overload. Its default forwards to the existing pose method, preserving existing providers. Tracking providers can use `ViewTargetFraming.fit` to widen a pose for the actual capture shape. `boundsTag` and `boundsFromTag` synchronize transient bounds and reject absent/non-finite geometry. The Camera sends these bounds only in client updates and uses capture-specific fitting for portrait feeds. Older Camera nodes receive the disabled boolean and its `AutoTracking` label when their graph is read, without replacing an existing value or custom label. The lazy `ViewTargetLock.update` overload resolves candidates only after a retained target becomes invalid.

## Wider tracking acquisition

`ViewAcquisitionCone(origin, forward, range, radius, halfAngle)` is a reusable swept-volume API. It finds the intersection of target bounds with a widening forward radius; an angle of zero gives a cylinder. `point(bounds)` returns a target point inside the sweep and radial range, or null. Large targets can qualify by their nearer edges even when their centre falls outside the cone. Targets fully behind the observer, outside range, or outside the volume are rejected. `bounds()` provides the conservative world envelope for querying loaded bodies.

`ViewTrackingTargets.acquire(level, pose, length, filter, excluded, samples)` combines existing precise ray hits with cone acquisition. The default cone starts at a 1.5-block radius and widens by a 15-degree half-angle. `coneHits` accepts a caller-supplied cone. Players and SubLevels are tested as candidates, then probed against the existing filtered loaded world/SubLevel geometry before becoming tracking targets. The camera's body remains excluded, target eligibility and range remain enforced, and ordinary ray outputs retain their original line casts. Candidate checks are bounded to 32 nearby entries and run only when acquiring; a valid retained lock does not repeat the search.

`ViewRaycast.traceToward` adds a reusable directed query through the same loaded-space traversal. It validates the direction and retains the existing category filter and source exclusion semantics. The camera invokes widened acquisition only on fresh nonempty ray-sampling ticks; its ray rate still gates acquisition and zero rays disable new acquisition.

## Dynamic target selection and an explicit lock

`ViewTargetLock.update` has additive overloads accepting `boolean lockTarget` after the resolver. False selects the closest eligible target from the supplied fresh hits, even when another identity was previously selected. True retains a valid identity and avoids resolving candidates until that identity becomes invalid. Equal-distance hits prefer the current identity. The original overloads continue to retain targets. Callers should use retained updates between sampling ticks and dynamic updates only when supplying fresh detections; an empty dynamic hit set releases the selected identity.

`ViewControlState` adds `lockTarget`, serialized as `LockTarget` and defaulting to false. Its previous constructors remain available. `ViewControlInput` adds a lock command with values -1 (preserve), 0 (unlock), and 1 (lock); invalid values are rejected. Previous constructors and absent fields preserve the existing lock setting. Shared manual-control transitions preserve both tracking options.

`ViewControlLayout.targetLock` supplies a separate button region, with the previous layout constructors retained. `ViewControlPanel` draws an open/closed padlock and sends only the target-lock command when it is clicked. Projected displays and tablet controls consume this same library control surface and their existing command transport.

The addon defaults Camera tracking to dynamic acquisition on every fresh ray-sampling tick. `LockTarget` on the Camera node and the display/tablet padlock retain the current target until it becomes unavailable or out of range. An unlocked Camera selects the nearest fresh ray/cone detection; between samples it follows the retained identity. The graph default and explicit UI override persist independently and old saved nodes gain a false default without replacing an existing value or label.

# Assembly targeting and schematic APIs (2026-10-12)

This addition documents the APIs introduced by the fresh assembly/targeting discovery pass. Existing documentation was not used to prepare it.

## Assembly lifecycle

`SableSubLevelLifecycleApi.transfer(ServerLevel, ServerSubLevel, Runnable)` retains a source plot while a block/tracking-point transfer runs. Native bounds removal and zero-mass plot destruction are deferred during the scope. Nested calls balance correctly, including exceptions. The native bounds callback runs when the outermost scope ends. Call only on the owning server thread and outside a physics step.

`isTransferring(ServerSubLevel)` lets lifecycle hooks recognize an unfinished transfer. `removeEmpty(ServerLevel, ServerSubLevel)` removes an owned, loaded body only when every loaded chunk is empty. Existing `remove` unloads all selected plots before removing their bodies, so block-entity callbacks can still resolve other bodies in the selection.

## Preview geometry and connected bodies

`SubLevelConnectionApi.connectedBodyIds(Level, Collection<UUID>)` expands live roots through native actor connection chains on client or server. Results are deduplicated and sorted; removed or unavailable bodies are excluded.

`SubLevelPreviewRenderer.setConnectedBodies(UUID, Collection<UUID>)` adds connected child bodies before configuring the preview. Existing `setBodies` remains available for explicit lists. Consumers provide content-specific visibility predicates; those predicates also govern picking.

`SableTransformApi.relativeBlockOrigin(Pose3dc source, Pose3dc reference, BlockPos referenceOrigin, BlockPos pos)` transforms the block center through both frames and returns the origin needed for center-based model rotation. It retains double precision until the client renderer converts the result.

Detached preview snapshots retain child orientation for rendering and picking. Mutable quaternion accessors return copies.

## Body telemetry

`SablePhysicsTelemetry.sample(Level, UUID)` returns a body snapshot cached for one game tick. Sampling does not require a block entity at a diagram or target anchor. An unavailable/removed body returns `Snapshot.available() == false`.

`Snapshot` exposes mass, inverse mass, inertia, local/world center of mass, pose, linear/angular velocity, total local/world force and torque, and individual `ForceSample` values. Vector, matrix and quaternion values are copied at construction and access. Recorded impulses are divided by the physics substep duration; gravity is included at the center of mass. Optional velocity or force failures leave the remaining body data available.

## Portable schematic connections

`SchematicConnectionData.BodyFrame` describes source/template body IDs, source bounds and an optional shared world-space export origin. The four-argument constructor defaults the world origin to zero. `reference` encodes internal endpoints; its owning-source overload disambiguates imported bodies with overlapping local coordinates. `resolve` maps a portable endpoint to its fresh destination plot.

`capture` supplements normal safe block data with portable connections. `restore` runs after every schematic endpoint exists. The placement overload receives target position and rotation for world spline geometry. Built-in adapters handle Create chains and belts.

`SchematicLinkData.register(ResourceLocation, Predicate<BlockEntity>, Schema)` adds an integration-owned schema. Duplicate IDs are rejected. Register once during common setup under the consuming integration's namespace. Optional mod predicates can use `OptionalTypeMatcher` without loading optional classes.

A `Schema` declares:

- `keys`: permitted top-level connection configuration.
- `references`: paths to endpoint-containing compounds, including paths through compound lists.
- `identityKeys`: UUID fields to replace consistently across related endpoints on export and each placement.
- `worldPointLists`: geometry translated relative to the shared export origin, then rotated/translated during placement.
- `detachedKeys`: runtime objects omitted when creating detached export/material models.
- `fieldPaths`: selected nested fields, overlaid without copying other fields in the same persistent-state container.

The two-argument and five-argument constructors remain convenient for endpoint-only and complete-field schemas. All schema collections are copied.

`Reference(path, positionKey, subLevelKey, PositionFormat)` declares each saved endpoint's encoding. Formats are packed `LONG`, `BLOCK_POS`, `VECTOR`, `RELATIVE_VECTOR`, or body-only `BODY`. An empty position key selects inline vector coordinates. Compound vectors with `V`, XYZ compounds, coordinate arrays and vector lists preserve their input encoding. Fractional coordinates remain intact. Body IDs support native UUID tags and string UUIDs.

Endpoints outside exported bounds are discarded; incomplete nested connection entries are pruned before upstream readers receive them. The pipeline preserves existing portable descriptors during repeat sanitizing. Restoring clears the native schematic context while loading already-mapped coordinates, then restores the caller's context.

These schemas supplement safe configuration only. Integrations must not declare inventory, stored energy or running network data as connection configuration.

`SubLevelSchematicFiles.displayName(String)` returns the basename without a supported schematic extension. Catalogue IDs and paths stay unchanged.

## Remote client models and control

`ClientBlockEntitySnapshots<B>.resolve(Level, UUID, BlockPos, Supplier<B>)` retains a detached client block entity keyed by body and block position. It clears when the client level changes; call `clear()` on disconnect. Consumers supply authorized server snapshots and apply their own runtime data. The cache grants no server permissions.

`DistanceIndependentLink` marks a Create `IRedstoneLinkable` endpoint whose signal range should be independent of separation. The library's range mixin bypasses only Create's distance comparison when either endpoint opts in. Existing frequency registration and world/network separation still apply.

## Validation

New regression tests cover rotated preview origins, portable endpoint mappings, repeated sanitizing, paired spline identities, fractional geometry, nested state overlays, dropped external links and exceptional nested transfer cleanup. Veil is available on the unit-test runtime classpath because native Sable body signatures reference its packet API.

Headless Minecraft validation: all five required game tests passed against NeoForge 21.1.228, including real chain/belt safe export and placement, anchorless native mass/gravity telemetry, final-block transfer protection and empty-plot cleanup. Both repositories now target the NeoForge minimum required by installed Sable and Simulated dependencies. Both clean builds passed with 164 library and 17 addon unit tests. Visual previews, remote multiplayer behavior and optional-mod spline formats still need interactive world checks.

# Live schematic capture and compatible browsing (2026-10-12)

`SubLevelSchematicCapture.capture(ServerPlayer, UUID, double range, int maximumBodies, int maximumBlocks)` copies a loaded connected world assembly directly into a portable template. It reads the actual live block entities and their safe configuration; it never calls storage, extraction, allocation, block placement or removal. The original body IDs, plot addresses, block entities, links and stored contents remain in the world. Each export receives fresh template body IDs. Existing portable connection capture and retained weld mapping apply to the copied assembly.

Call capture on the owning server game thread, outside a physics step. All connected bodies must be loaded, within the specified range, accessible through world protection and allowed by the ship STORE permission. Construction in progress, moving Create contraptions, unavailable chunks/block entities and exceeded size limits reject capture without removing the source.

`SchematicCatalogue.catalogue(Path, String idPrefix, HolderGetter<Block>, int maximumBodies, int maximumBlocks)` returns only files that the real block schematic decoder accepts. Malformed, empty, unsupported-version, entity-containing and over-limit files are omitted before browser lists are built. `Entry` exposes the unchanged file identity alongside its detected `SubLevelSchematic.Format`. A bounded metadata cache revalidates files when their size, modification time, registry or limits change; it retains no decoded block data.

`ClientSchematicFiles.catalogue(int maximumBodies, int maximumBlocks)` applies the connected server limits to private client folders before returning entries. Private names stay on the client until a selected file is uploaded. Client entries now expose their validated format; the previous four-argument Entry constructor remains available for native files.

`TabletMultiSelectDropdown` provides a compact reusable checklist dropdown for tablet apps. Supply stable Option IDs and labels, call render after other content, route mouseClicked before underlying controls, and consume keyPressed before the host handles Escape. All options start selected; selectedIds returns an immutable set. close releases page-specific hit areas. It supports normal screen clicks and projected tablet right-click interaction.

Digisable consumes these surfaces for world copying through Reader mode, source-format filters and compatible shared/private lists. Its search bar uses the existing TabletAppPanel inline input. Saving now belongs to the Schematics tab and is independent of the stored-archive workflow.

Regression coverage includes malformed/incompatible catalogue entries, content-based Photomancy recognition, file and limit invalidation, and a Minecraft live capture test that exports twice while preserving source body/block-entity identity, chest contents and chain links.

Live export and import share `SubLevelSchematicFiles.MAXIMUM_BODY_SIDE` (512 blocks), so capture rejects an oversized body before creating a file that the browser cannot decode. Capture clears an enclosing native schematic context while reading source NBT, then restores it after success or failure.

## Contact-aware hull clearance

`SubLevelParticleOcclusion.findEnvelopeBlockingDistance`, `findSubLevelEnvelopeBlockingDistance`, `findAirOnlyBoundsBlockingDistance`, `findSweptBoundsBlockingDistance`, and `beginSweptBoundsBlockingDistanceScan` have additive overloads accepting a final `double initialContactAllowance` argument. Supply only the uniform padding already included in every moving hull bound. Existing overloads retain their strict behavior.

The contact-aware overloads retain the original physical hull separately from its padded envelope. An obstacle already touching that padding permits parallel or outward translation only while an axis continues to separate the physical bodies. Inward motion, physical overlap, and newly encountered obstacles remain blocked. Short departures need not leave the whole padding envelope in one tick. Padding still applies to every later collision. Invalid, non-positive, or excessive allowances fall back to strict contact handling. Exact scans transform the physical and padded bounds independently into each SubLevel's local frame.

`SablePathfinder.compoundCollisionValidator(options, boundsAtStart, initialContactAllowance)` applies the same policy to complete loaded route segments. Keep world queries and control mutation on the owning server thread. Excluded body IDs, loaded-chunk validation, and incremental scan budgets retain their existing ownership rules.

`ReactiveCollisionAvoidance.retainForwardPreference(requested, escapeActive)` suspends a preferred forward heading throughout a live escape or committed bypass. Restore the preference after the bypass has completed; projecting escape force onto the old heading can remove all sideways propulsion.

The SCM consumer supplies its existing hull padding to live clearance, exact safety scans, and route validation. A local obstacle bypass also takes over an already queued destination search, retains its own validation progress, and preserves the authored schedule spline and destination.

Final validation for live schematic capture and browsing: both isolated clean builds passed with 176 library and 18 addon unit tests. All eight headless Minecraft tests passed, including repeated source-preserving capture and rejection at the configured block limit. Reader and browser UI behavior still requires interactive client verification.
World-space separation is checked before a rotated SubLevel local AABB can enlarge the hull. A body is skipped only when its whole world envelope proves that the padded sweep cannot enter it, or when initial padding contact preserves physical separation throughout the segment.

## Refined spatial navigation and short bends

`SablePathfinder.queueRefining(Request, double minimumNodeSpacing)` returns the existing `QueuedPlan` surface. For requests whose movement modes are all spatial, unsuccessful searches halve their grid spacing toward the supplied minimum, for at most eight refinements. Every stage shares the original maximum expansion allowance; `advance` remains bounded by its supplied node budget, `result().expandedNodes()` includes all stages, and endpoints retain their originally resolved world coordinates. Accepted coarse prefixes remain available while a finer search prepares a replacement. Every candidate leg still uses the caller's validator; refinement does not reduce the hull or permit unloaded geometry. Existing `queue(Request)` behavior is preserved.

`ObstacleDetourPlanner` now also checks offsets smaller than the requested horizontal and vertical clearance scale. This permits a complete over/under pass inside a low tunnel when larger offsets hit its walls or ceiling. The host must continue validating every swept hull leg and its translation authority. Opposing participants can choose complementary vertical passes as well as complementary horizontal passes.

`SablePathfinder.advanceWaypointProgress(waypoints, nextWaypointIndex, previousPosition, currentPosition, captureRadius)` advances only ordered waypoints reached by the actual control-point movement segment. Use it for short spatial bends which a large hull can overlap before completing the required climb or descent. The list is not mutated; the returned `WaypointAdvance` retains the existing cursor contract.

The Airship SCM consumer enables refinement down to its collision padding, allows initial pure ascent/descent without a forward commitment, and applies its live translation authority to every searched leg. Live Airship routes and temporary schedule bypasses capture bends by control-point progress, approach them at a stopping speed, and use a matching local control tolerance. Reactive emergency escapes retain their independent motion authority. Ground/sea and plane search policies retain their existing forward commitment.


## Schematic producer identity and live capture

- `SubLevelSchematicFiles.encode(schematic, source)` and `save(server, name, schematic, source)` add the caller's ResourceLocation to the root `gadgetsngizmos:source` string. Existing overloads preserve unmarked exports.
- `readTag(bytes)` uses the existing compressed/raw NBT size and allocation limits.
- `SchematicProvenance.source(tag, format)` recognizes Photomancy and Toolgun coordinate formats, then requires an explicit producer tag for native structures. Missing or invalid producer tags return `UNKNOWN`.
- `SchematicCatalogue.Entry.source()` and `ClientSchematicFiles.Entry.source()` retain provenance separately from import format. Existing constructors remain available. A native structure layout alone does not identify its producer.
- `SubLevelBlockEntityCollector.getOrCreateBlockEntity(body, pos)` resolves an existing actor first, then promotes pending configuration or initializes the entity inside an already loaded plot chunk on the owning server thread. It does not load source chunks or create detached copies.
- `SubLevelSchematicCapture.capture(level, rootId, maximumBodies, maximumBlocks, validate)` lets trusted server consumers check access to every physically connected source body. The player overload retains range, permission and protection checks.

## Physical collision ownership and proximity escape

- `SablePhysicalConnectionApi.track(first, second, handle)` registers live body-to-body constraints. The native Rapier integration records attachments including stickers. World anchors are excluded.
- `connectedBodyIds(level, roots)` expands collision ownership through valid handles without changing SCM control-map partitions. `revision(level)` changes when attachments are added, removed or invalidated; dimension unload releases retained handles.
- `ReactiveCollisionAvoidance.proximityHazard(bounds, velocity, obstacles, clearance, responseSeconds)` detects nearby or approaching bodies independently of requested motion. `resolveProximity(hazard, candidates)` prefers directions which increase separation and can use available vertical clearance.
- `SubLevelParticleOcclusion.findSubLevelProximityHazard(...)` supplies current native plot poses and velocities to that selector. Collision queries merge the authoritative container even when the spatial index is incomplete. Empty plots have no collision envelope.
- `findAirOnlyDepartureBlockingDistance(...)` permits outward recovery from forced overlap with another body. Every overlapping compound-hull piece must permit departure, and separated pieces must avoid new contact. Terrain and unrelated bodies remain blocking. Existing strict sweep APIs preserve their contact policy.
- `allowsOverlapDeparture(...)` exposes the geometric departure predicate for other collision adapters.

## Text confirmation transitions

`TextInputScreen.confirm()` returns to the parent before invoking the acceptance callback. A callback may then close the tablet for reader mode or open another screen without the dialog restoring its parent afterward. Cancel still returns to the parent without invoking confirmation.

## Graph preview value sampling

`GraphValueMirrors.sample(live, key, authoritative, fallback)` reads a retained value when the host marks its key authoritative. Otherwise it evaluates the supplied fallback once. Zero, false and structured values are preserved without conversion; authoritative values skip fallback evaluation. The method does not modify either source.

Graph hosts can use this API to retain execution-owned outputs during read-only observer sampling while reevaluating ordinary data expressions. Gadgets & Gizmos uses it for graph previews and live variable lookup. Preview compilation stays separate from the running program; sampling does not reinitialize its events or reconcile its retained ports.

## Editor preview snapshots and state isolation

`GraphPreviewPlan.compile(source, draft, sameNode)` identifies nodes whose runtime configuration and incoming wiring still match the source graph. `unchanged(nodeId)` excludes changed nodes and all data or execution consumers, including cycles and removed sources. Hosts supply a comparison that ignores editor position, labels and presentation metadata.

`GraphEvaluationState<V>` wraps the host's state map. `snapshot(copy)` creates private state using the supplied value copier. `evaluate(values, sample)` directs reads and writes into that private map and restores the enclosing scope on completion or failure. `evaluating()` identifies that scope. These operations run on the host's owning thread. Hosts may retain private maps across editor samples without modifying authoritative runtime or personal state.

The addon now transmits complete typed port values and observer-scoped variables, separately from truncated display summaries. Both editor input and output calculations use the same seeded evaluation frame. Layout edits preserve live values; semantic edits invalidate affected snapshots. Stateful calculations and library callbacks write to isolated preview state, and preview callbacks cannot access mutable block entity services. Controller packet encoding now includes a compressed snapshot, so clients and servers must use matching addon versions.

The addon's existing `AdvancedGraphDocument.Value.toTag()` and `Value.fromTag()` adapters are public so the controller transport can preserve exact value types and payloads. Existing serialized keys and saved graph formats are unchanged. Editor snapshots are matched against the applied graph, separately from unsaved graph or layout changes.


## Collision query performance and physics frames

- `SableAssemblyHullApi.worldBounds(body)` returns an immutable world envelope cached on the calling thread. It compares occupied plot coordinates and all pose components on every access, so same-tick translation, rotation, pivot, scale and block edits are visible immediately. Empty and removed bodies remain absent.
- Assembly snapshots reuse these world envelopes and retain independent root-frame bounds for articulated shape changes.
- `findSubLevelProximityHazard(..., ProbeCache)` shares body identities with directional probes while reading live poses and velocities. Cache membership refreshes when bodies are added or removed, including same-size replacement. Connected-body exclusions are applied before envelope transforms.
- Air-only collision queries resolve other-body envelopes first, then limit terrain probes to the nearest possible hit. Strict shape sweeps, terrain policy, compound hulls and contact allowances are unchanged.
- `ReactiveCollisionAvoidance.resolveProximity(hazard, directions, probe)` returns `ProximityEscape(response, clearance)` and stops probing only when directly outward motion achieves the greatest possible score. Constrained departures retain the full alternative search, including over and under paths.
- `SablePhysicalConnectionApi` polls native handle validity once per game/physics frame and caches connected components until membership changes. `invalidate(level)` requests validation after the next published physics state. Native physics ticks invoke it automatically.
- `untrack(handle)` and `untrack(body)` remove ownership immediately. Rapier handle removal and body removal invoke these methods, so a detached body does not remain excluded until a polling timer expires. Custom physics integrations can use the same callbacks.
- Block and world reads still run on the owning game thread; cache invalidation observes completed physics frames without moving those reads into native simulation.

### World schematic capture with optional block entities

`SubLevelSchematicCapture.capture(...)` retains block states whose block entity factory intentionally returns null, including plain Create tracks. Pending entities are still resolved through the loaded plot lookup before capturing safe configuration. Capturing remains a copy operation and preserves source blocks, entities, inventories and connections.

### Player schematic directory

`ClientSchematicFiles.directory()` returns the current client's game-directory `schematics` folder. `ClientSchematicFiles.openDirectory()` creates that folder when necessary and opens it through Minecraft's operating-system integration. Opening failures are exposed through the existing `status()` API. The folder is local to the player and independent of server/world schematic catalogues.

## SCM collision authority

`ScmCommandRouting.allowsCollisionAvoidance(commandType, requested, scheduleControlled, manualControlActive)` grants collision checking and avoidance only to `ship_navigate`, `ship_follow`, and `ship_climb` with `requested=true`. `ship_dock` also requires `scheduleControlled=true`. Manual control, hover, stabilization, and facing commands cannot acquire collision authority. `manualControlActive=true` suspends avoidance for every command.

`ScmCommandRouting.isManualControl(commandType)` identifies direct translation, rotation, acceleration, deceleration, brake, and stop commands. Hosts should combine this classification with current command activity and external controller inputs. Hover and stabilization do not count as direct manual input, so they can support opted-in autonomous travel.

The addon consumes this policy before path planning, reactive proximity checks, collision speed limiting, and schedule spline capture. It skips background schedule hazard scans during manual input. Idle and manual SCM ships no longer synthesize proximity escape commands. Climb to Y Level exposes an `avoid_collisions` boolean defaulting to false; enabled climbs retain their initial world column as the destination and can route laterally around blockers using the existing spatial navigation API. Existing serialized command keys remain unchanged.
## Named docking workflow

`NamedDockingOperation<T>` exposes a host-independent docking lifecycle. The host resolves an exact dock name, validates its current target, reserves a berth, navigates to its approach, engages docking, and reports physical connector lock. Completion requires a locked pair; an existing lock is adopted without navigation. The operation retains its reservation while complete, releases it on cancellation or failure, and stops failed docking attempts after the configured timeout. Cancellation preserves completed docking, while incomplete attempts and failures disconnect their endpoints. Host callbacks run on the owning game thread; no addon types are exposed by the library.

`findNamed` performs a trimmed, case-insensitive exact-name lookup and selects the nearest valid match. Names are not wildcard expressions.

The SCM `ship_dock` graph node now exposes `dock` (string) and `speed` instead of XYZ and orientation inputs. Its existing ID, success output, and completion wire remain. The addon resolves registered docks in the ship's current dimension, uses the shared berth scheduler, selects compatible ship and station connectors, and drives the SCM's existing approach and magnetic docking commands. Unknown names or unusable connectors are rejected. A valid named request pauses an active shipping schedule before taking over movement. Existing coordinate nodes retain their IDs and speed but need an explicit dock name; obsolete coordinate wiring is removed on load. The internal coordinate command remains available for shipping schedules.

## Autonomous SCM avoidance and shared dock provisioning

`ScmCommandRouting.allowsCollisionAvoidance(commandType, requested, manualControlActive)` now accepts Navigate, Follow, Climb and Dock commands. The four-argument overload remains compatible and delegates to the same policy; Dock commands no longer require a schedule-specific command id. `isActiveManualControl(commandType, amount, strength)` separates actual manual signals from zero inputs and retained Stop markers. Hosts should exclude their own autonomous support commands when resolving manual authority.

`ShipDockScheduler.request(server, requester, candidates, occupied, owned, priority, vessel)` uses the Minecraft server tick for lease expiry. All clients of the same scheduler must use this clock, including clients in dimensions with different world times. Existing explicit-tick overloads remain available. `owned` means confirmed physical connector ownership; committing an approach does not confer physical ownership or allow displacement of another lease.

`NamedDockingOperation.Host.waitForReservation(target)` is an additive default callback for maintaining a safe holding movement while a berth is provisioned to another vessel. Reservation loss during an unfinished docking attempt now returns to WAITING instead of abandoning the request. Completed operations still require a granted lease and a physical lock. Hosts must withdraw unfinished connector capture when entering their holding movement.

The addon uses these APIs for both graph Dock At nodes and shipping schedules. Named requests offer all compatible connectors at docks with the exact requested name, retain a stable size-aware holding location outside approach corridors, and keep collision-enabled Follow active while queued. Schedule docking renews and rechecks its lease throughout capture, reports physical ownership only for a reciprocal connector pair, and preserves committed approach priority without claiming occupied connectors. Collision queries continue to exclude the own assembled hull and selected docking target, while other loaded ships remain visible during docking and world terrain remains blocking. Genuine manual inputs retain their avoidance override; schedule reset markers and support controls do not disable autonomous safety.
Escape authority is evaluated in the actuator model's ship frame after converting the requested world direction. Optional attitude and altitude controllers no longer suppress all autonomous collision queries: the addon keeps their owned force and torque axes out of reactive movement and route validation while allowing avoidance along free axes. Direct manual SCM commands still bypass collision checks and override autonomous avoidance while actively driven.

# Library API changes for stable feature integration

## Dependency versions

The addon and library have independent versions. Consumers use library_mod_version for the NeoForge dependency minimum and nested library metadata, and library_artifact_version to locate the built library jar. The shared feature API requires Minecraft 1.21.1, NeoForge 21.1.228 and Sable 2.0.3.

## Graph snapshot compatibility

GraphDocumentSnapshot includes scmActionFunctions, mapping public SCM action ids to graph function ids. The original constructor remains available and supplies an empty mapping. Hosts supporting action functions must serialize, validate and execute those bindings alongside their graph revision.

## Physics Staff authorization

PhysicsStaffInteractionGuard.TargetAuthorization is a server-side callback taking the player and target sublevel UUID. Register a provider with registerTargetAuthorization(ResourceLocation, TargetAuthorization) during addon initialization. Use the owning mod namespace; duplicate ids are rejected. Every registered provider must authorize the target. Initialization protection, ship permissions and target availability still apply. Optional-mod implementation classes stay in consuming addon adapters.

## Shared feature APIs

The stable library now exposes the existing experimental APIs for adaptive SCM state, control axes, speed and precision allocation, steering, articulated/IK control, ship permissions, native physical connections and telemetry. Navigation contracts provide hull geometry, collision and ground-support checks, route planning, splines and bounded work scheduling. View and client rendering contracts provide camera sessions, target tracking, scene capture, detached previews, projection and particle rendering. Inventory, archive, graph reconciliation and display helpers retain typed ownership and immutable snapshot contracts.

Content ids, blocks, item registrations, network entry points, graph hosts, screens and compatibility adapters remain owned by the consuming addon. The stable addon does not register worker content or new tablet apps. Those optional library APIs remain available to other consumers.

## Saved data

Existing library method signatures and serialized ids are retained where possible. The car id remains an alias of Ground/Sea. Addon graph and SCM map migrations remain the host's responsibility. Missing or unloaded sublevels are normal unavailable states; all world mutation stays on the owning game thread.


`ItemFoilRenderScope.overrideFor(Item)` returns a nullable per-item foil override on the current render thread. `withoutFoil(Item, Runnable)` scopes a draw call, restores nested overrides after exceptions, and does not affect other items or threads. Addon item implementations may consult the override before their ordinary foil policy.


`ReadOnlyBooleanDataAdapter<T>` exposes one read-only `boolean` data port for a block entity type. Construct it with the target class, port ID, and a `Predicate<? super T>` that reads the current state; register it through `BlockEntityDataAdapterRegistry`. Each sample calls the probe again, and Set Data cannot write this port. Owning addons supply any external-mod access.


## Exact energy probes and bulk-processing previews

`lib.power.EnergyStorageProbe.findExact(Level, BlockPos)` finds a loaded block entity implementing `LongEnergyStorage` without loading chunks. `read(IEnergyStorage, LongEnergyStorage)` prefers exact FE totals and returns a `Reading(long stored, long capacity)`, retaining integer-capability fallback and zero values for unavailable storage.

`lib.client.recipe.BulkProcessingLayout` exposes shared recipe-viewer dimensions, input/output slot positions and `render(GuiGraphics, int left, int top, BlockState)` for a static block preview. Addons supply their block state and recipe categories; the library creates no addon block entities or particle effects.


## Inline graph group editing

`lib.client.ui.GraphGroupHeader` provides zoom-aware title and eyedropper button rendering and hit bounds. Use `layout(x, y, width, scale, titleWidth)` for screen coordinates, then `render` and the layout's `titleContains` / `colorContains` methods. Empty header space remains available for dragging.

`lib.client.ui.InlineTextEditor` edits text without replacing the screen. Supply a Font, accessible message, initial text, maximum length, accept callback, and cancel callback. Set screen bounds and scale before rendering or input. Route mouse click/drag/release, key, and character events while `isOpen()`. Enter or an outside click accepts; Escape cancels. An outside click returns false so the owner may handle it. Explicit `accept()` / `cancel()` are safe to call once on resize or close. The owner stores values and manages undo. Reuse `ColorPickerModal` for color preview, confirmation, and cancellation.


## Scaled control previews

`lib.client.ui.GuiControlPreviews` draws GUI previews in the host canvas coordinate space. `drawItemSlot(graphics, stack, x, y, color, renderStack, scale)` scales an 18-unit slot, its border and the inset 16-unit item together. `drawColorSwatch(graphics, x, y, color, scale)` scales a 14-unit swatch with its border and color inset. Coordinates are screen coordinates in the current pose; scale is the host canvas zoom. Use scale 1 for fixed-size inspector previews. Both methods restore the incoming pose, omit invalid/non-positive scales, and leave document data and host layout ownership with the consumer.


## HUD control and progress styles

`lib.display.HudStyleCatalog` exposes six `ControlStyle` looks (Material Filled/Outlined, Neumorphic Raised/Inset, Skeuomorphic Beveled/Glass), 14 `ProgressStyle` looks, stable IDs/labels, saved-style migration, switch/checkbox shape normalization, and safe range normalization with `amount`. Previous style IDs map to a new style and are absent from the designer options.

`lib.client.ui.HudControlRenderer` renders controls through a generic `Canvas` quad callback. Supply a `Rect` and `Palette` with custom background, fill, gradient end, track and border colors, border width/radius in host units, and layout scale. `button`, `toggle`, and `progress` return the available label rectangle; `slider` draws its track and thumb. Text and interaction dispatch remain with the consumer. `gui(GuiGraphics)` and `textured(VertexConsumer, Matrix4f, depth, layerStep, light)` adapt GUI and world surfaces, respectively. Text should be drawn above geometry layers (GUI z +1, or the host's foreground text plane). Half-circle and ring styles retain their proportions inside the supplied rectangle. The `checkbox` argument selects a checkbox variant of all six toggle looks.

## Graph control data and kinetic state writes

`lib.control.BlockStateControlReceiver` lets a block entity handle writable `state_*` ports through `applyControlledBlockState(BlockState)`. `BlockStateDataAccess` invokes this contract before its default state update. The handler returns whether the update changed the world and must run on the owning server thread.

`lib.kinetics.KineticBlockStateControl.apply(KineticBlockEntity, BlockState)` detaches the old kinetic network, publishes a same-block state with client synchronization, and immediately reattaches the network. It rejects client calls, removed targets and block replacements. This also works when scheduled block ticks are unavailable on moving sub-levels.

`lib.probe.ScrollValueDataAdapter<T>` accepts a target class and a callback returning named Create `ScrollValueBehaviour` instances. Numeric ports use the behaviour's clamped setter and existing callbacks; enum ports expose lowercase option names. Writes require a live server-side target. `fieldBehaviours(BlockEntity)` supplies deterministic snake-case field IDs for existing behaviours and excludes single-option screen launchers. Consumers register adapters with `BlockEntityDataAdapterRegistry`; declared provider ports keep precedence. Inventory mutation ports remain excluded.

The addon exposes vector-bearing `max_tilt` and `control_mode` writes, plus readable stabilization settings. The vector-bearing tilt range remains symmetric. Thruster and aileron bearings retain their existing minimum/maximum ports. Simulated directional gearshifts expose independent boolean `left_powered` and `right_powered` controls and immediately rebuild their kinetic networks after graph or face-redstone changes.

`BlockEntityDataAdapter.isSupplemental()` defaults to false. Supplemental adapters add settings alongside existing direct controls. `BlockEntityDataAdapterRegistry.hasPrimaryWritableData(BlockEntity)` identifies dedicated writable schemas after access-policy filtering; the addon uses this instead of treating any supplemental setting as a replacement for direct control.


## SCM motion damping, residency and projected controls

`ScmMotionDamping.stop(velocity, angularVelocity, strength)` returns opposing linear and angular acceleration in the caller's shared coordinate frame. `holdYaw(angularVelocity, up, strength)` damps only rotation about the supplied normalized yaw axis. Hosts convert acceleration into mass/inertia-scaled actuator demands and keep ownership of command lifetimes. Strength is clamped to 0..1 and non-finite inputs are sanitized.

`ScmCommandRouting.supersededByMotionHold(incoming, active)` identifies retained commands replaced by Brake or Hold yaw. Manual yaw commands release Hold yaw; other axes are preserved.

`SableSubLevelResidency.release(owner, subLevels)` removes only that owner's current and legacy residency tickets, including tickets restored from disk before a lease adopts them. Terrain leases are still closed by the host.

`ViewControlPanel.Canvas.layer(index)` defaults to the original canvas. `ProjectedViewControlCanvas` provides separated foreground depths while preserving its existing constructor and accessors. Projected camera controls use these layers to prevent coplanar overlays.


`GuiViewportScale.forWidth(preferred, framebufferWidth, minimumWidth)` preserves the preferred GUI scale when its controls fit, reduces it for smaller windows and returns at least one. Hosts own applying the scale and restoring player preferences when their editor closes. A non-positive minimum leaves the preferred scale unchanged.


## Latched yaw angle control and unsigned strength

`ScmControlInputs.strength(value)` clamps finite control strength to 0..1; invalid values produce zero. Zero disables control rather than requesting full power. Directional amounts retain their signed range.

`ScmMotionDamping.holdYaw(angularVelocity, up, currentForward, targetForward, strength)` combines heading correction and yaw-rate damping. All vectors must share a coordinate frame. The host captures and retains the target direction until explicitly released. `yawError(currentForward, targetForward, up)` returns the shortest signed rotation in radians about the supplied axis, projecting forward directions onto its plane. The existing rate-only overload remains available.

`ScmCommandRouting.supersededByMotionHold` retains yaw holds when manual yaw is requested; a new hold replaces another hold while retaining manual yaw requests. Hosts give the hold exclusive yaw demand until explicit release, after which retained yaw requests can resume.


## Linear braking and client camera quality

- ScmMotionDamping.stopLinear(velocity, strength) returns counter acceleration without angular damping. The existing stop API retains its full motion damping behavior.
- ScmCommandRouting.supersededByMotionHold preserves hover, stabilization and rotation controls during Brake; a new yaw or pan command automatically replaces Hold Yaw.
- ViewSceneRenderer.setResolution(IntSupplier) supplies a live camera longest-edge size (64-2048 pixels) for displays and tablet feeds. ViewFeedResolution.fit retains the viewport aspect ratio. Lowering the setting also shrinks existing render targets. The existing setMinimumRefreshRate supplier controls feed updates at 1-60 FPS.

Completed Brake commands retain SCM gravity support with zero braking force, zero brake-group output and no manual-control priority. A new movement command replaces this settled brake state; yaw, pitch and roll remain independent. Explicit Stop Ship Control still releases control.


## Hold Yaw rotational braking

The addon Hold Yaw node uses ScmMotionDamping.holdYaw(angularVelocity, up, strength) to oppose angular velocity only. It does not capture a heading or call initialization. ScmMotionDamping.yawRate(angularVelocity, up) returns the signed normalized-axis yaw rate in radians per second for completion checks. Once settled, the node applies no torque and releases manual yaw priority. The captured-heading overload remains available for other library consumers.

## Retired container controls

Global container locks, Create filters, and adjacent push/pull toggles are retired.
The library no longer replaces block item capabilities or intercepts container menus,
hopper transfers, player interaction, or block breaking for these controls.

`ContainerAccessRegistry` and `ContainerAutomationPolicies` remain as deprecated,
inactive compatibility APIs. Access checks allow access; policy registration has no
effect; `wrap` and `unwrap` return the supplied handler unchanged, including null.
`sameStorage` compares handler identity only.

`ContainerAutomation` retains stock targets, their owner, and controller bindings.
Its retired lock, filter, push, and pull setters have no effect; their getters return
false or an empty filter, and `accepts` returns true. Existing saved values for these
controls are ignored and are omitted on the next save. Stock replenishment remains
available without changing third-party capability objects.


## Safe SCM motion-stop completion routing

ScmCommandRouting.activeActions(type, completed, actions) returns immutable active action IDs without modifying the supplied set. Completed Brake and Decelerate release their scalar outputs; completed Hold Yaw releases yaw routing. Other command routing remains unchanged. ScmCommandRouting.isMotionStop(type) identifies Brake, Hold Yaw and Decelerate. The new isActiveManualControl(type, amount, strength, completed) overload releases manual priority when these requests complete; the existing overload remains available. Addons retain their active hover, attitude and movement command state independently.


## SCM declared actuator geometry
`ScmActuatorGeometry` transforms positions/directions around a host frame and supplies rotary, tilt, and tangent-space gimbal geometry. These read-only helpers let addons use provider capacities without firing propulsion or sweeping physical bearing angles during setup. Native limits and force application remain provider-owned.

`PropellerForceGeometry.maximumPropellerThrust()` optionally declares full-speed thrust without driving the shaft. The default NaN preserves existing implementations.


## Incremental SCM navigation collision checks
`SablePathfinder.budgetedCompoundCollisionValidator` shares exact hull scan work through `CollisionBudget` (block visits and nanoseconds per level tick). `Validator.incremental()` defaults to false. Incremental validators return `Traversal.pending()` / `TraversalResult.PENDING`; queued searches retain the current node and edge until validated. Pending segments never become accepted paths, and synchronous validator behavior is retained.

`ScmArticulatedFlightControl.requests` now dissipates relative transverse motion and all-axis joint rotation with the reduced inertia tensor while preserving collective rigid-body rotation. Reaction targets remain bounded by opposing provider capacities; joints stay free. Allocation stops repeating load redistribution once error no longer improves. Authority solves start from proven component bounds without discarding feasible capacity.

`SableAssemblyDynamicsApi.Tensor.add(Tensor)` is now public for additive full-tensor joint response calculations. `QueuedPlan.cancel()` / `Validator.release()` release discarded scan ownership.

Queued plans now check optional long destination shortcuts at a sparse node cadence. Near-target segments and every accepted grid edge still require full validation; an unchecked shortcut is never returned.

## Collision occupancy and vector control
- CollisionBlockIndex.nextOccupiedY caches exact section occupancy columns. Revision invalidates edits immediately; untracked sections are read afresh. The section mixin supplies revision tracking.
- SableAssemblyHullApi.localBounds distinguishes construction changes from carriage articulation.
- ScmVectorThrustAllocator.allocate jointly solves bounded force vectors and throttle for fixed and cone-limited actuators; project exposes the same physical bounds.
- ScmActuatorGeometry.gimbalCoordinates inverts the native tangent gimbal basis.
- SweptBoundsScan.isCurrent checks inspected section revisions and chunk availability so unrelated distant block edits do not discard verified local clearance.
- Corrected gimbalDirection/gimbalCoordinates to retain the native reversed tangent basis on vertical nozzles.

## Graph input fallback selection
- GraphInputDefaults.selectPort(data, preferred, fallback, connected) selects a connected preferred input or an explicitly authored literal before the legacy fallback port. Reads typed Defaults and legacy primitive properties; false, zero and empty strings remain authored values. This keeps runtime assignment and graph export consistent.

## HUD control ports and canvas projection
- HudInteractionPorts synchronizes persistent per-control Exec/Value sections, orders their ports, and compares encoded values for change events. Existing port IDs are retained.
- WidgetCanvasProjection describes display-sized or independent world canvases, inverse pointer transforms, and world-ray intersections. Offsets and sizes are in blocks; positive Y is up and positive Z projects outward.
- WidgetCanvasProjection.resizeCanvas rescales element rectangles when their hosting surface changes size.


## GraphReadView

`lib.graph.GraphReadView.of(GraphModel)` retains ordered, unmodifiable node and edge lists and an indexed `node(id)` lookup without serializing or cloning node payloads. Node and edge objects are borrowed and must be treated as read-only on the source owner thread. Cache views by source identity and revision with `RevisionSnapshotCache`; replace them when either changes. Live values and interaction events remain separate from topology. Editing must use a detached document instead.


## Named controller targets

`ControllerDiscoveryService.firstNamed(Iterable<ControllerDiscoveryNode>, String)` returns the first candidate whose complete display label matches the requested name, ignoring case and surrounding whitespace. Null or blank queries and unmatched names return null. Caller order determines duplicate resolution; hosts supply aliases as additional candidates and restrict the iterable to authorized bindings.


## Item icon buttons

`lib.client.ui.ItemIconButton` extends the vanilla client `Button` with a centered 16-pixel item icon. Its constructor accepts bounds, an accessible message, an ItemStack and an OnPress action. It copies the supplied icon once, retains vanilla hover/focus styling, and uses the message for its tooltip and narration. Hosts choose menu placement and the action; the library has no dependency on addon registries or config screens.


### GraphTargetPortLayout independent target ports
`compose(targets, mergeLikePorts, stableTargetIds, targetSpecificPorts)` keeps the supplied source port IDs in each target section while merging other like ports. Existing overloads preserve their original behavior. Use this for controls that must remain individually adjustable.


### ReceiverSignalScope
`sampleSources(IntSupplier)` evaluates physical outgoing power while `allowsReceiverSignals()` is false. Input-only virtual signal providers can omit their receiver contributions in that scope, preventing feedback through solid conductors while preserving ordinary receiver queries. Scopes nest and restore on exceptions; state is local to the calling thread.


### Named target collections
`ControllerDiscoveryService.allNamed(targets, name)` returns distinct binding IDs matching a name or supplied alias, preserving caller order and ignoring case. `ControllerTargetMap.encode(targets)` encodes an ordered NBT map of typed Target values; `read(payload)` accepts either a single discovery target or that map; `first(payload)` resolves the first valid target. Stable padded map keys preserve selection order through serialization and ordinary graph map operations. Invalid entries are ignored.


## Remote assembly storage

`SubLevelArchiveApi.canStoreRemotely(player)` checks creative mode or operator permission level 2. `storeRemote(player, level, rootId, limits)` archives a loaded connected assembly within `limits.locateRange()` without moving the player. It retains the existing archive transaction, owner, ship storage permissions, world protection, capacity and construction checks. The original `store` API retains `limits.storeRange()` and its existing access behavior. Missing or unloaded assemblies remain unavailable. Callers continue to enforce their app configuration and operation cooldown.


## Graph port rows

`GraphNodeLayout.portRows(portIds, rowHeight)` returns immutable `PortRows` with an ordered map of port IDs to vertical offsets and the total occupied height. Null, blank and duplicate IDs are ignored; row height is at least one unit. Consumers can cache this layout and use the same offsets for drawing, wire anchors, label editing and hit testing. The API is independent of node types and game-side classes.


## SCM recovery speed controls
- Additive `ScmStateFeedback.recoveryAcceleration(error, rate, strength, tickSeconds)` supplies discrete, critically damped position/attitude recovery. The overload accepting positive/negative acceleration authority limits approach speed by braking capacity and bounds output. `recoveryVelocityAcceleration(desiredRate, rate, strength, tickSeconds)` requests a rate change within one interval at strength 1. Strength is clamped to 0..1; zero returns no correction. Control intervals must be finite and positive.
- `ScmAdaptiveStateModel.feedback(axis, angular, error, rate, strength)` and `angularFeedback(error, rate, strength)` use recovery speed and live directional capacity. Existing overloads preserve autonomous navigation tuning.
- Additive `ScmMotionDamping.stopLinear(velocity, strength, tickSeconds)` and `holdYaw(angularVelocity, up, strength, tickSeconds)` use recovery speed. The yaw overload damps only the supplied yaw axis; it does not capture a heading. Existing overloads retain their behavior. All acceleration and rate vectors must share a coordinate frame. Callers apply physical force/torque through propulsion and retain feedback while active.
- `ScmCommandRouting.usesRecoveryStrength(type)` identifies Hover, Stabilize, Decelerate, Brake and Hold Yaw. A completed Hold Yaw keeps its action groups available for residual counter-torque; completion still releases manual priority and explicit yaw supersedes the hold.


## Conditional graph inputs and sourced graph catalogues

`BlockEntityDataProvider.graphWritableInputConditions()` optionally returns conditions keyed by raw writable port id. Each `GraphInputVisibility.Condition(selector, values, fallback)` selects the permitted selector values and the current provider fallback. Existing providers keep every input visible by default.

Use `GraphInputVisibility.configure(data, targetId, conditions)` while capturing a target schema. Use an empty target id for uncomposed ports. `GraphInputVisibility.filter(ports, data, bindings)` resolves authored selector literals in `Defaults`, or the provider fallback, through composed Target/Port bindings. Merged inputs are visible if any bound target accepts them. Hidden defaults and identifiers remain intact.

`KineticTargetSpeed.percentageRatio(percentage)` clamps finite percentages to 0?100% and returns a 0?1 speed ratio; nonfinite values stop the output. Multiply the driven output ratio by this value while leaving the source shaft at full speed.

`SourcedGraphCatalog.merge(client, server, id, name)` builds a sorted catalogue whose entries retain CLIENT/SERVER identity and expose `[C]`/`[S]` display labels. Matching ids on different sides remain separate.

`GraphInputVisibility.filterValues(values, conditions)` evaluates a requested write batch using its selector values or each condition fallback. The default `BlockEntityDataProvider.writeGraphValues` applies selectors before dependent inputs and accepts an inactive-only batch as a no-op. This prevents merged graph controls from changing the modes of targets for which the control is hidden.


## Boolean logic and SR latch API
`com.rieno.gadgetsandgizmos.lib.graph.GraphLogic` exposes pure boolean `xor(a, b)`, `nor(a, b)`, `nand(a, b)` and `xnor(a, b)` gates. `srLatch(current, set, reset)` retains the current value when neither input is asserted, sets true for Set and false for Reset. Reset takes priority when both boolean inputs are asserted. The host owns storage, initialization (false for the ACC node), event ordering and persistence. The ACC SR-Latch has separate Set/Reset execution inputs, a State boolean output, and one Exec output emitted after each accepted pulse, including repeated Set or Reset pulses. Sequential pulses are processed in execution order.


## Bounded loops and pulse cadence

`GraphBoundedLoop.start(previous, iterations, maximum)` captures a bounded nonnegative integer limit and leaves an active run unchanged. `advance(state)` returns the next zero-based index, whether to emit an iteration, whether to emit completion, and the next retained state. Zero iterations complete without emitting an iteration. Hosts own scheduling, persistence, and player scope; hold the running state until iteration/completion callbacks return.

`GraphCounters.FlipMode` offers Every Pulse, Every Second Pulse, and Every Nth Pulse. `flipPulse(value, skipped, mode, requestedSkip)` retains the Boolean while skipping pulses and toggles after the requested skip count. Zero skips toggle immediately; Every Second Pulse skips one pulse. `GraphComparison.operandType(connectedType, literalType)` resolves a matching second-operand type, preferring the first operand wire and normalizing numeric aliases.


## Graph numeric display precision

`GraphNumberDisplay` owns the per-node `EditorShowPreciseValues` preference. `precise(data)` defaults to false and `setPrecise(data, enabled)` changes only that flag. `format(value, true)` uses lossless, round-trippable double text; false preserves the editor's existing compact two-decimal format. `numericType(type)` accepts number/integer/int/float/double. `supportsNumericPorts` reserves stable layout space for numeric or any ports; `hasNumericPorts(inputs, outputs, resolvedType)` checks declared types and asks the host to resolve any ports. `PRECISE_WIDTH_SAMPLE` reserves room for a full double.

Hosts should exclude this presentation flag from program fingerprints and preview configuration comparisons, preserve it during graph serialization, and continue transporting and calculating full numeric values in both modes.


## Ordered graph ports and fixed frame sections

`GraphPortOrder` builds immutable ordered schemas (`ports`, `entries`, `copy`), merges dynamic NBT types while honoring saved `InputOrder` / `OutputOrder`, and reads/writes order lists. `numberedKeys` recovers numeric indices and generated ordinals in older data without alphabetizing named fields. `withChildren` places inline fields beside their parent. `EntryOrder` can carry collection field order outside a value payload.

`GraphNodeDefinition`, graph document snapshots and `GraphValue` retain supplied map order. Math node schemas and component maps preserve their declaration order. `GraphDynamicPortSchema.retainConnectedOutputs` has an ordered map overload for live schema refreshes.

`GraphNodeLayout.frame` sizes title/body/footer sections from canvas scale; node width does not affect title height. Hosts can widen numeric displays without stretching the titlebar.


## Collection operations and complete node browsers

`GraphCollectionOperations` provides immutable `push`, `pop`, `contains`, and `indexOf` operations for lists and maps. Push appends a list value or inserts/replaces a map key. Pop removes the zero-based ordinal, preserving remaining keys and order, and returns a `Pop(result, value, removed)` result. Invalid indices leave the collection intact. Searches compare values (including nested typed collections); map keys are not searched, and missing values return -1. Numeric comparisons retain double precision.

`GraphCollectionNodes.nodes(namespace)` supplies four schemas, executors and defaults under the caller's owning namespace. These are data operations that return new collections; source values are never mutated. Push takes Collection, Value and Key (maps only). Pop returns Result, Value and Removed. Contains returns Result; Index Of returns Index.

`GraphNodeBrowser.categories(preferred, available, label)` retains preferred category order and appends every additional available category, preventing registered categories from disappearing in host palettes.


## Function graph browser: resolved execution ports

`GraphNodeBrowser.standaloneExecutionSource(inputs, outputs)` classifies a node as an execution source when it has an `exec` output and no `exec` input. Hosts must pass the resolved port maps, including ports generated from node defaults or the current configuration. This keeps dynamic flow nodes such as execution combiners available inside function graphs while retaining event-source filtering.


## Sub-level preview highlight buffer fix

`SubLevelPreviewRenderer` now draws translucent face highlights and outlines in separate passes, completing each render batch before requesting the next shared buffer. This fixes `IllegalStateException: Not building!` when highlighting a block face in a preview. Public signatures, caller-supplied highlights, and saved data are unchanged.


## Website graph catalog API

`GraphWebCatalog.node(definition, name, categoryName, color, defaultData, inputs, outputs)` exports a host-supplied node schema without depending on addon implementations. It preserves declared and resolved port order, copied default data, and primitive property types. `GraphWebCatalog.ports` exports an ordered port map. Hosts supply selector variants, matching operands, and their own property options.

The reusable browser API is packaged as `assets/gadgetsngizmos/web/graph-nodes.js` and exposed as `GadgetsGraphNodes`. It resolves default values, dynamic and conditional ports, ordered/collapsed map ports, bound target visibility, collection operations, and compact/precise numeric rendering. It never rounds stored numeric values. Website hosts copy this resource with their generated node catalog.

`EntityTelemetryApi.ports()` now retains its declared port groups in its immutable map, keeping tracker catalog exports consistent across JVM runs.


## Solver-driven mounted joints

`SableConstraintApi` adds three server-side APIs for Sable 2.0.3:

- `fixedFrameConstraint(pipeline, bodyA, bodyB, posA, posB, orientation)` returns a nullable `GenericConstraintHandle` with all six axes locked. `orientation` is the first local frame; the second starts at identity. Retarget through `setFrame1` and `setFrame2`.
- `rotaryConstraint(pipeline, bodyA, bodyB, posA, posB, axisA, axisB)` returns a nullable `RotaryConstraintHandle`. It copies the anchors and normalizes each body's local hinge axis.
- `setRotaryServo(handle, bodyA, bodyB, axisA, axisB, targetRadians, stiffness, damping)` scales the motor coefficients by local rotational inertia, with a minimum of 10. Pass unit axes in each body's own frame. The two-body case uses the larger inertia; a world attachment uses the finite body's inertia.

Anchors use each body's local coordinates. A null body represents the stationary world, so its anchor and axis use world coordinates. Creating a joint with identical bodies, including two null bodies, returns null. Other backend failures propagate to the caller. Joint creation copies its inputs and disables contacts between the attached bodies. Callers own the returned handle and must remove it when detaching or unloading.

These APIs leave motion and collision response to Sable's solver. They do not register pose correction callbacks, teleport bodies, or reset momentum. Thruster and aileron bearings consume the rotary APIs; vector bearings consume the adjustable generic joint. Existing public APIs and saved identifiers are retained.

## Sequenced shaft angles

`kinetics.SequencedShaftAngle` accumulates signed output rotation across relative instructions. `start(travelDegrees)` limits the next instruction to its requested travel (positive infinity allows continuous rotation), `advance(rpm)` advances one game tick, and `restore(angleDegrees, remainingDegrees)` resumes saved motion. The angle and remaining travel are available through `angleDegrees()` and `remainingDegrees()`.

Sequenced gearshifts now publish accumulated angles on their output side, clamp tick padding to the requested angle, retain completed turns while idle, and persist unfinished travel. They stop upstream precise-angle propagation at the gearshift boundary.


## Independent SCM recovery strengths

`ScmControlPriority.remainingForce(request, controlledAxis)` removes the component along an axis already owned by another control. Both vectors must use the same coordinate frame. The axis is normalized internally; a zero axis leaves the request unchanged. Existing overloads retain their behavior.

`ScmControlPriority.uprightRecoveryStrength(hoverStrength, stabilizationStrength)` chooses an enabled explicit stabilization strength before Hover's implicit leveling strength. A disabled or invalid stabilization strength falls back to Hover. Both strengths use the existing unsigned 0..1 normalization.

The addon demand builders use these APIs so Brake and Decelerate preserve active height recovery, and Hover cannot raise an explicit Stabilize setting. Without height control, braking still counters all three linear axes. Hold Yaw continues to own only yaw. Node IDs, graph defaults and serialized command strengths are unchanged.

`SequencedShaftAngle.angleDegrees()` retains full turns. Directional precise-angle propagation applies gear ratios before normalizing each target angle, preserving reduction-gear phase across repeated turns.


## Saved docking and departure recovery

`com.rieno.gadgetsandgizmos.lib.compat.simulated.SimulatedDockingApi` provides optional Simulated compatibility for persistent connector pairs. Its public entry points accept Minecraft block entities, not addon implementations. Call on the owning server thread.

- `isEndpointLoaded(level, bodyId, pos)` checks availability without retaining or loading chunks.
- `isAwaitingPeer(endpoint)` distinguishes an unloaded saved peer from a broken loaded connection.
- `restoreConnection(endpoint)` retries native joint restoration for a reciprocal saved pair; it does not connect unrelated endpoints or power them on.
- `hasPhysicalConnection(endpoint)` checks a live native constraint rather than the connector's saved logical lock state.
- `PhysicsState` is the typed compatibility contract implemented by the library mixin.

The optional connector mixin preserves links while peer chunks are unavailable and retries restoration when they load. Real undocking and broken loaded peers retain the native behavior. No addon types or new saved identifiers are involved.

`SubLevelParticleOcclusion.findAirOnlyDepartureBlockingDistance` now permits outward departure from initially overlapping world blocks as well as sublevels. The complete compound hull must move out of the original obstruction without another hull piece entering it. New terrain, ceilings, and other obstacles still block departure. The strict clearance APIs retain their existing behavior. Supply the safety padding used to inflate the hull so the query can recover its physical bounds.

`beginDepartureBoundsBlockingDistanceScan` is the bounded exact-sweep counterpart. It retains the same incremental work limits, contact-padding rules and obstacle invalidation as `beginSweptBoundsBlockingDistanceScan`, adding compound-hull overlap departure for recovery. The original scan remains strict.

`PhysicsState.isDockingConstraintPending()` distinguishes a newly queued native joint from a missing joint until Sable publishes it on the physics tick, preventing repeated recreation during load.


## Native buoyancy controls

`ScmControlProbe.hasForceModel()` defaults to false. A native physical force provider can return true and expose its current full-output force in newtons through `maximumForce()`. Consumers should refresh its live geometry/capacity, allocate absolute force (including gravity support), bypass redstone calibration curves, and avoid replacing allocated output with a face-action signal. Existing providers retain their calibration behavior.

`ScmBuoyancyControl` adapts a `Source` reporting available/current gas volume, force per volume, and body-local lift geometry. Its normalized control sets an absolute target gas volume; filling and cooling remain native. `volumeShare()` distributes one enclosure's capacity across multiple providers without multiplying available lift.

`AeronauticsHotAirApi.createProbes(blockEntity, adapterId)` optionally adapts Aeronautics adjustable hot-air burners. Supply the previous redstone adapter ID to preserve saved SCM block/face mappings. It samples live balloon capacity/center, gravity and air pressure without requiring Aeronautics at compile time.

`LiftingGasControl` exposes transient, exact target-volume control and release. The optional Aeronautics mixin implements this contract, preserves the wrench volume setting, synchronizes the exact target for clients, and resumes ordinary redstone input on release. The control override is not persisted as an owner across world reloads.

`ScmControlProbe.responseTimeSeconds()` defaults to zero. Slow gas sources expose their native filling/cooling response through `ScmBuoyancyControl.Source.responseTimeSeconds()`. `ScmStateFeedback.delayedRecoveryAcceleration()` limits altitude feedback bandwidth to that response, keeping buoyancy warmup from destabilizing Hover while other control providers retain existing feedback.

## Servo face angle overrides

`ServoMotionController.resolveAngleLimit(configured, defaultLimit)` resolves a finite per-output angle magnitude independently of its default. A missing/non-finite override uses the default; invalid defaults resolve to zero. Consumers can preserve their default gyro range while supporting explicitly configured face limits above it.

`ServoMotionController.getGeneratedSpeed()` now reports signed RPM from the angle actually traversed during the latest `update()`, including tolerance-sized moves and the final arrival step. Planned speed remains separate, preserving the position trajectory. `isMoving()` stays true for a step that moved and becomes false on the next stationary update; `stop()` and `snapTo()` still clear generated RPM immediately. This keeps virtual kinetic outputs and stress capacity active while a slowly updated servo target is rotating.

`VirtualKineticSourceLink` exposes the saved virtual source slot on driven Create kinetic block entities. `isSourcedBy(consumer, source)` compares the position and slot together, including after NBT reload. Removing or changing one virtual source now leaves consumers of other slots at the same block position connected.

## Connected storage and signed kinetic controls

`ConnectedVaultBlockEntity` extends Create's `ItemVaultBlockEntity` for custom vault-shaped storage. It queues native formation after initialization, asks nearby controllers to include newly available members, and retries standalone groups during lazy ticks. A controller waits for all chunks in its saved geometry before rebuilding. Missing controllers in loaded chunks recover without changing member contents; unloaded controller chunks remain unavailable without being forced to load.

`ConfigurableKineticScrollValueBehaviour` retains Create's kinetic speed and direction rows, signed value settings, formatting and saved scroll value. Its board maximum follows the configured behaviour range instead of Create's fixed 256 RPM board limit.

`EnergyTransfer.transfer(sources, targets)` supplies the demand reported by FE receivers, respects each handler's own limits, and returns the total transferred FE as a long. It has no network-wide transfer budget, de-duplicates shared handler instances, skips transfers back into the same handler, and supplies input-only machines before charging bidirectional storage. Connected cable implementations should call it once per network per tick.

## Angle from Coords

`RotationMath.rotationFromCoordinates(origin, target)` returns the shortest unit-quaternion rotation from +Z (Minecraft south) toward `target - origin`. Coordinates share one coordinate frame; translation does not change the result. Exactly opposite directions use +Y as a deterministic half-turn axis. Equal, non-finite or null coordinates throw `IllegalArgumentException`.

`MathGraphNodes.nodes(namespace)` now includes `namespace:angle_from_coords` in Math. Inputs are `origin` and `target` coordinate maps (`x`, `y`, `z`, with uppercase aliases accepted). Defaults are Origin `(0,0,0)` and Target `(0,0,1)`. Outputs are `euler`, `quaternion`, `tait_bryan`, `axis`, `angle`, and the standard `valid` flag. Euler maps use ZXZ `alpha/beta/gamma`; Tait-Bryan maps use XYZ `roll/pitch/yaw`; both retain XYZ aliases. The quaternion uses XYZW, the axis is a unit vector, and all angles are radians. The scalar angle is the shortest rotation magnitude in `[0, pi]`, paired with the axis. Invalid or coincident coordinates return empty maps, angle zero and `valid=false`.

## Scalar angle conversion ports

`MathGraphNodes.nodes(namespace)` now exposes an `angle:number` input and output on
`degrees_to_radians` and `radians_to_degrees`. Connect a scalar Angle output directly
to Angle; the converted Angle output remains a number. The input defaults to zero.
Negative angles and multiple turns are preserved without wrapping or clamping.

The existing `angles:map` ports retain their IDs and recursive conversion behavior,
so saved map connections continue to work. Scalar and map inputs convert separately;
`valid` is false if either conversion contains a non-finite input or result.

`MathGraphValues.convertAngle(double angle, boolean toRadians)` is the reusable
scalar conversion API, returning a numeric `GraphValue` and rejecting non-finite
inputs/results with `IllegalArgumentException`. `convertAngles` uses the same API
for numeric map/list fields. These are unit conversions; they do not project a 3D
rotation onto the X/Z plane or calculate a signed heading.

## Angle from Coords degrees output

`MathGraphNodes.nodes(namespace)` adds `degrees:number` to `angle_from_coords`.
It is the existing `angle:number` converted from radians to degrees, so a graph
can connect Degrees directly to a bearing Pivot Angle numeric input without a
conversion or multiplier node. The existing port IDs and radians-based rotation
outputs are unchanged. Degrees has the same unsigned 3D shortest-rotation meaning
as Angle, in the range 0 to 180 degrees; it is not a signed horizontal heading.
Invalid or coincident coordinates return Degrees = 0 and valid = false. Bearings
retain their configured range limits, swivel mode, inversion and local zero.

## Entity velocity telemetry

`EntityMotionTelemetry.velocity(Entity)` reads the most recently completed server
tick of actual world-space feet movement in blocks per second. It is sampled once
per level tick and shared between graph consumers. The first observation returns
zero while establishing a baseline. This includes carried movement and rotation on
Sable assemblies and ordinary passengers without interpreting gravity, drag or
blocked movement attempts as actual travel. Call on the owning server thread.

`EntityMotionTelemetry.reset(Entity)` clears the baseline after a discontinuous
move. Vanilla teleport events, dimension changes, entity removal and server stop
clear it automatically. Missing ticks and invalid positions also start a new
baseline. Entities and levels are referenced weakly.

`EntityTelemetryApi.sample` now uses this velocity for both velocity and speed.
Existing port IDs, map fields and units are unchanged. Native Sable ship velocity
remains in blocks/second and angular velocity in radians/second; predictor and
integrator nodes continue to use their supplied motion values and delta_time.

## Full-circle Degrees heading correction

The `angle_from_coords` node keeps its existing `degrees:number` port ID, but
Degrees now returns the horizontal X/Z heading in [0, 360), replacing the earlier
unsigned 3D degree magnitude. Targets continue through 180 degrees instead of
folding back. Positive angles rotate +Z toward +X about +Y: south = 0, east = 90,
north = 180, west = 270. Height does not affect the heading. No horizontal
displacement returns zero; coincident or invalid coordinates still set valid
to false. This supersedes the earlier description of the Degrees output.

`RotationMath.horizontalHeadingDegrees(Vector3 origin, Vector3 target)` publishes
the reusable calculation. Finite extreme coordinates are supported; non-finite
or missing coordinates are rejected. All inputs must use the same coordinate
frame. Connect Degrees to a bearing Pivot Angle in Swivel mode for a full turn.
Bearing inversion and its local zero still apply.

Euler, Quaternion, Tait-Bryan, Axis and the radians Angle output retain their
existing shortest 3D rotation meaning. To convert that 3D magnitude to degrees,
connect Angle to the numeric Radians to Degrees conversion node. Saved graphs
need no rewiring to receive the corrected Degrees heading.


## ACC editing, entity cameras and bounded schematic construction

- `GraphCanvasSearch.matches(nodes, query, label)` searches supplied nodes by type and host label, preserving order. `next(matches, currentId, previous)` wraps in either direction and returns null for an empty result.
- `GraphValueText.parse(type, text)` parses finite numbers, booleans, plain strings, JSON maps and JSON lists. `any` infers a concrete type. `format(value)` supports editing nested values without discarding zero, false or map ordering.
- The additive `GraphPlayerScopePlan.analyze(..., personalSource)` overload scopes UI dependencies only when they are reached from a personal source or UI interaction. Observing a shared controller value does not change its state ownership. The original overload remains available.
- `AccessoryHeadwearRenderer.armor(texture, offset)` and `.hat(model, offset)` render a supplied accessory stack against a humanoid model independently of its armor equipment. The addon owns Curios registration and visibility checks; the library has no Curios dependency.
- `ViewReference` adds an optional `entityId` and `of(Entity)` factory. Its existing three-argument constructor and block reference tags remain supported. Entity references add the optional `Entity` UUID tag and resolve loaded players on the client. `EntityViewSource` supplies a read-only eye camera. `ViewSource.viewEntity()` defaults to null and allows captures to omit the source entity's own head.
- `ViewRaycast.trace(..., ignoredEntityId)` can omit a lens carrier. `ViewRaySampler` shares rate-limited samples and typed hit values across consumers. `ViewTrackingTargets` now resolves all loaded pickable entities, including passive and hostile mobs. The existing `Reference.playerId()` accessor remains compatible and may identify any entity.
- `SubLevelSchematicBuild.prepare(player, schematic, target, rotation, sections)` defers destination validation and native allocation to subsequent ticks. Existing constructors retain immediate preparation. All builds share a measured server work allowance; placement batches are capped at 256 blocks and connection/joint restoration resumes across ticks. Call `tickSections` until `complete()` and `close()` to roll back an unfinished build. Slow builds retain their material reservation until completion or refund on cancellation/failure.

The addons consume these APIs for graph search, zoom-scaled inline values, independent HUD state, wearable camera targets and Digisable construction. Wearable selection and pair revocation remain addon gameplay contracts.

`GraphViewport.centerOn(graphX, graphY, canvasWidth, canvasHeight)` returns a centered viewport while preserving its origin and zoom. Pan values remain screen pixels.

`GraphPortValueEdits.mark(data, key)`, `contains(data, key)` and `clear(data)` distinguish explicit authored defaults from stale live snapshots. Hosts skip live-value merging for marked keys and consume the markers after merging. The addon uses these markers to preserve edited persistent defaults through Save/Apply.


## Rendering and schematic compatibility APIs

- `GuiRenderLayer.push(GuiGraphics, float)` returns an `AutoCloseable` overlay layer. Use it in try-with-resources. It flushes buffered geometry and text before entering and leaving the relative depth, then restores the caller's pose.
- `VeilPointLightBackend.install()` installs the optional client-only implementation of `ColoredLightBridge`. Call it only when Veil is present. The backend releases deferred point lights while Flywheel visualization is enabled because its shaders do not provide Veil scene attachments. Existing vanilla block and plume lighting remains available to callers.
- A block entity may implement `SchematicMaterialData.schematicMaterialData(CompoundTag)` to provide independent load data for detached material requirement previews. Keep every field used by Create's requirement callbacks and omit unrelated programs, database identities and runtime state. Ordinary sanitization, export and placement continue loading complete configuration.

The Voxy configuration class is no longer a mixin target, allowing Roxy and Voxy SeedGen to read it during startup. Camera capture still uses the existing Voxy viewport and native-terrain guards. Saved IDs, blockstate properties and schematic payload formats are unchanged.

## Attachment frames

`com.rieno.gadgetsandgizmos.lib.physics.AttachmentFrames.alignAxes(sourceNormal, sourceAxis, targetNormal, targetAxis)` returns a new normalized JOML quaternion mapping a source attachment frame into a target frame. Each frame consists of an outward `Direction` and a perpendicular, unsigned `Direction.Axis` tangent. Tangents always use the axis's positive direction, so changing a rail's travel direction does not introduce a half-turn. Null arguments and parallel normal/tangent pairs are rejected. The helper does not modify world or saved state.


## Graph saves, counters and Percentage

- `GraphSaveState.mergeVariables(live, saved, copy)` retains server values for names present in both maps, including their live types. New names keep supplied defaults; removed names stay removed. Call on the owning server thread before saving/applying an edited graph.
- `GraphSaveState.retain(values, keep, copy)` creates a detached snapshot of only explicitly retained runtime slots. Hosts select variables and opt-in counters and keep personal scopes separate.
- `GraphCounters.step(value, amount, decrement, reset)` accepts a boolean direction. False increments, true decrements, and reset takes priority. Existing Direction overloads and IDs remain supported.
- `MathGraphNodes.nodes(namespace)` adds `namespace:percentage`: numeric A/B inputs, numeric Value = A / B * 100, and Valid. Defaults are A = 0, B = 100. Zero B or a nonfinite/unrepresentable result returns Value = 0 and Valid = false. Negative values and values above 100 are supported.

## Graph port section ordering

`GraphPortOrder.grouped(order, groups)` returns an immutable port order with each section kept contiguous in its declared member order. Sections begin where their first available member occurs; ungrouped ports retain their relative order. Missing members are omitted, duplicates are removed, and the first section claiming a port owns it.

`GraphPortOrder.withDescendants(order, roots, parents)` returns an immutable section membership list containing its declared roots followed by their expanded children in depth-first order. Child order follows `order`; `parents` maps child IDs to parent IDs. Nested expansions are included, stale children are ignored, and cyclic mappings terminate safely. These helpers affect presentation only and do not change port IDs, bindings, or saved graph data.


## Inline editing and graph rendering

- `InlineTextEditor.setDecorated(false)` edits text using the host control background, without painting a second field or underline. `setTextColor(int)` matches the edited label/value. Focus, selection, clipboard, Enter and Escape behavior are unchanged.
- `GuiRenderLayer.pushFlattened(graphics, depth)` keeps container widgets, slots, stack decorations and carried items inside a shallow foreground layer. Use `restoreBackground(graphics, depth)` only inside that matching pass to draw normal backgrounds and 3D previews, then close both scopes. Both restore the caller pose and flush buffered draws.
- A client screen can implement `WorldRenderControl.suppressWorldRendering()` to skip the main terrain/entity/HUD render while covered. Client/server simulation and screen rendering continue; secondary View scene captures are preserved. `shouldRenderWorld(requested, screen)` exposes the policy. Projection renderers should return false.
- `GraphPortIndex.of(order, sections, members)` snapshots section ownership and section starts. `section(port)` and `startingAt(port)` replace repeated membership scans. Rebuild after structural changes; overlapping ports retain the first section.
- `GraphNodeLayout.rows(Collection<Row>)` computes immutable offsets and total height for variable-height controls plus anonymous spacers/headers. A blank port occupies height without creating a lookup entry.

- `InlineTextEditor.setRightAligned(true)` preserves the original right edge of a value while editing, including caret placement, drag selection and long text scrolling. Label editing remains left aligned by default.

## Portable block-state and block-entity snapshots

`BlockStateSnapshots.read(registry, data, fallbacks)` resolves a saved block state without converting missing registrations into air. It returns null for unavailable states, preserves properties, and leaves the source NBT unchanged. A caller-supplied resource-location fallback is used only when the source registration is missing; callers must choose property-compatible replacements.

`BlockEntitySnapshots.create(level, position, state, data)` validates the registered block-entity type against the backing block state before creating and loading a detached preview. Air, missing types and incompatible states return null without entering Minecraft's repeated error-logging path. Both APIs are in `com.rieno.gadgetsandgizmos.lib.nbt` and work with non-full blocks. Callers can memoize unavailable results through the existing `RevisionSnapshotCache` API and invalidate on physical snapshot changes.

## Graph evaluation context and action consumers

`GraphEvaluationCache<K, V>(Supplier<?> context)` provides a mutable evaluation map that discards cached ports when its context changes, including transitions to and from null. Use it for frames that change player ownership during execution.

The personal-source overload of `GraphPlayerScopePlan.analyze` scopes UI dependencies and individually reachable action nodes. Action consumers no longer make their ancestors personal; adding camera-like actions preserves producer state ownership. The original overload remains available with its existing behavior.

`PlayerScopedMap` reads and merged entries now honor the current personal-key predicate, so stale restored scopes cannot shadow keys that have become shared. Restored values remain available if their scope becomes personal before pruning.

## Face selections and first-person entity feeds

`GraphTargetSelection` supplies distinct face ids, legacy face matching, alias normalization, and group labels. Aliases change labels while preserving target and generated port ids.

`ViewSceneRenderer.hidesHead(Entity)` identifies the entity whose head, helmet and head accessories must be hidden during a capture. The scope clears after capture and restores model visibility even if rendering fails.


## External editor view ownership

`ClientViewOwnership.isReserved()` in `lib.client.render` identifies an external editor that owns the viewport without a Minecraft screen. Axiom is detected through its optional public `EditorUI.isEnabled()` method; installing Axiom is not required. Other integrations can register a live `BooleanSupplier` with `register(id, active)` under their own namespace and release it with `unregister(id)`.

Player HUDs and camera input should yield while reserved. Library remote-view overrides become inactive immediately, then the client tick ends the server session and defers restoration of camera preferences until the editor closes. Secondary camera feeds retain their last completed texture and suspend capture/allocation while reserved, then resume normally. Sable interaction camera-frame providers and physics-goggles overlay callbacks also yield.

## Remote camera controls and viewfinder

- `ViewControlPanel.setTakeControlAction(Runnable)` adds a takeover button to the floating icon row. The host sends its own authorized request and calls `RemoteViewSessions.open(player, reference)` after validating its selected source and access. Existing control rendering and layout constructors remain available.
- `RemoteViewKeys` registers configurable exit, flashlight, raycast, frame and grid actions under Camera Controls. Flashlight, frame and grid start unbound. Raw keyboard and mouse input honors the player's assignments; Escape remains an exit fallback.
- `RemoteViewSessions.toggleFlashlight(player)` updates only the active owned lens's flashlight setting. `RemoteViewSessions.raycast(player)` samples an active `RaycastViewSource`, at most once per server tick per action. No action accepts a client-selected source. `RemoteViewSessions.source(player)` exposes the acknowledged reference.
- Implement `RaycastViewSource.castViewRay()` to supply an explicit server-side lens sample. The camera block retains its range and filters and publishes this sample through its graph outputs.
- `RemoteViewActionPayload` and `RemoteViewRaycastPayload` carry discrete requests and small hit results separately from the existing mouse-motion protocol. Client callbacks stay side-safe.
- `RemoteViewHud` shows current bindings and hit feedback during first-person control. `ViewfinderOverlay.render(...)` draws optional camcorder brackets and a rule-of-thirds grid on a `ViewControlPanel.Canvas`. Its optional bottom inset reserves hint space without moving the lens-centre reticle.
- The frame and grid preferences persist in `gadgetsngizmos-camera-client.toml`; they do not change camera blocks, graph data or other players' views.

## Physical redstone sources and retained frame constraints

- `BlockRedstoneSignals.read(Level, BlockPos, @Nullable Direction)` reads a loaded block's own power (0?15). A null face selects the strongest output; faces point outward. Create Redstone Link receivers report received power, transmitters report transmitted power. Unloaded targets return zero without loading chunks.
- `BlockRedstoneSignals.readFace(Level, BlockPos, BlockState, Direction)` reads an already resolved block state using Minecraft's opposite query direction.
- `SableFrameConstraint.update(PhysicsPipeline, @Nullable PhysicsPipelineBody, @Nullable PhysicsPipelineBody, Vector3dc, Vector3dc, Quaterniondc)` creates or updates one fully locked native joint. Anchors use each body's local coordinates, or world coordinates for a null body. The quaternion is the first frame's orientation; the second is identity. Frames are copied, changed in place, and wake bodies only when changed. No body poses or velocities are rewritten.
- `isValid()` reports native joint availability; `close()` releases it idempotently. Hosts must close attachments when unloading/removing bodies and call the API on the owning server thread. A changed pipeline/body recreates the joint. Invalid frames and removed bodies release it and return false. Anchor jitter below 0.0001 blocks is ignored so stationary attachments can sleep.
- Saved block IDs, graph node IDs and zipline attachment data are unchanged.

## Entity nameplate visibility API

`com.rieno.gadgetsandgizmos.lib.client.render.NameplateVisibility` is an optional contract for client entities and rendering proxies. Implement `boolean isNameplateVisible()` and return false to suppress the entity nameplate, including when a player renderer would otherwise show it. Returning true preserves normal renderer, distance, team, and visibility rules. The library applies the contract through the client `RenderNameTagEvent` at lowest priority. Existing entities need no changes, and the API adds no saved data or networking.


## Camera hit interaction and overlay viewfinder controls

- `ViewControlLayout` adds `frame`, `grid` and `interact` bounds. Existing constructors remain available. `ViewControlPanel.renderOverlay` renders the saved camcorder frame/grid and their local toggle buttons.
- Hosts opt into the hit-use icon with `ViewControlPanel.setInteractAction(Runnable)` and authorize their selected source before acting. Frame/grid buttons use the same `ViewfinderConfig` preferences as first-person camera keys.
- `RemoteViewKeys.INTERACT` is an unbound, configurable camera action. `RemoteViewActionPayload.Action.INTERACT` and `RemoteViewSessions.interact(ServerPlayer)` operate only on the acknowledged remote session.
- `ViewBlockInteractions.interact(ServerPlayer, ViewReference)` recasts the source ray on the server and uses the real player, main-hand item, hit face and plot-local hit through `ServerPlayerGameMode.useItemOn`, including NeoForge right-click/protection events. Misses and entity hits do not use blocks. The caller must authenticate its source selection; world/ship access, loaded targets, exclusive control and repeated-use limits are checked again by the library.
- Menus opened through this interaction retain reach only for their original target (including a connected double chest), source, player and menu. Vanilla block identity and other validity checks still run; Create smart-block menu reach uses the same authorization. Access ends when the menu/source/target is lost, permission is denied, or the player's body moves away.
- First-person sessions continue their keepalive while a container screen is open and allow normal screen keyboard/mouse input.


## Camera native interactions and feed layouts
- `RaycastViewSource.interactionRange()` and `interactionFilter()` expose the lens sensor settings to native client interaction handlers; defaults preserve existing implementors.
- `ViewInteractionTargets.register(BlockEntity, Picker)` registers projected controls on each logical side. The stateless picker returns world geometry, owning block reference and whether native block use should run. Removed entities are discarded and nearer obstructions win.
- `ViewInteractionContext` exposes a server-authenticated camera cursor and exact-target reach for custom GUI and held-input handlers, plus lens eye, direction and fresh targeting. Access expires after source/target loss, permission changes, movement or inactivity. Player body coordinates do not change.
- `ViewInteractionClient.register(InputHandler)` adapts raw native use, release, key, mouse and scroll handlers. Server acknowledgements replay client use while suppressing duplicate vanilla use packets. Configured interaction keys preserve held/released state.
- `ViewControlLayout.overlay` stacks icon buttons on the left. `chin` and `ViewControlPanel.renderChin` reserve a separate control strip below feeds. `ViewSceneRenderer.fittedViewport` exposes the exact captured image rectangle for host overlays. `ViewFeedGrid` clips a separate viewfinder to each image, excluding feed headers and letterboxing.
- `ViewRig.wrapPan` normalizes horizontal angles without imposing a stop; mouse and absolute aim can cross either ?180? boundary repeatedly. Tilt retains its hinge limits.

- `ViewRaycast.traceInteraction` includes native interaction shapes (for example steering wheel rims) in camera use queries while ordinary sensor and lighting traces retain their existing shape rules.

- `ViewfinderOverlay.render` supports status text insets independently of image framing. Compact feeds retain their grid, corners and reticle while suppressing labels that cannot fit.


## First-person camera aim prediction

`PredictedViewSource` optionally extends `ViewSource`. Implement `viewAim()` to return the current authoritative `ViewRig.State`, and `viewPose(float, ViewRig.State)` to compose a supplied aim with the current interpolated mount pose. This overload must not mutate the source. Existing providers remain supported.

`ViewRig.state()` and `restore(State)` snapshot and restore manual pan, tilt and FOV. `ViewControlPrediction` previews unsent frame input and replays sequenced samples until acknowledged by the server. `RemoteViewInputPayload` and `RemoteViewAimPayload` carry these samples and acknowledgements; the existing input packet remains supported. The server shares the existing per-tick motion limits between flushed and regular samples.

`RemoteViewClient.pose()` uses prediction only for the first-person lens. `flushInput()` orders pending aim before raycast/use actions. `ViewInteractionTargets.pick` also accepts an explicit `ViewPose` for matching client hit testing. ACC Display/CCTV captures continue to use the replicated source pose and their configured feed refresh rate. No player FPS or VSync setting is changed.
