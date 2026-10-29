# Camera feed elements

`ViewFeedControls` in `com.rieno.gadgetsandgizmos.lib.client.view` fits the standard interactive camera overlay to a resizable GUI or world feed. Retain one instance per element. `render(...)` and `renderSurface(...)` accept a `ViewReference` and a `BiConsumer<ViewControlAction, ViewControlInput>`; the host authorizes and transports commands. `layout(width, height)` exposes the shared drawing and pointer scale. Pass normalized element coordinates to `mouseClicked` and `mouseDragged` after undoing the host's rotation and scale; call `mouseReleased` when pointer capture ends, focus is lost or the element disappears. Source changes and unavailable sources release drags. `isDragging` exposes current stick or zoom capture. These controls share existing scene textures and allocate no render target.

`ViewControlPanel` adds a `GuiGraphics` overload for `renderOverlay` and exposes `isDragging()`. Existing Canvas and compact rendering APIs remain unchanged. `DisplayWidgetProjection.Point.verticalFraction(bounds)` complements the existing horizontal fraction for two-dimensional control regions.

`com.rieno.gadgetsandgizmos.lib.view.ViewControlAction` provides `CONTROL`, `TAKE_CONTROL` and `INTERACT`. Its `apply(ServerPlayer, ViewReference, ViewControlInput)` method uses the existing control lease, remote view and hit interaction APIs. Hosts must validate player access and current source selection before calling it; the enum does not authorize arbitrary references.

`com.rieno.gadgetsandgizmos.lib.client.view.ViewFeedRenderer` draws a reusable camera render target inside the host's transformed bounds. `draw(GuiGraphics, Font, ViewReference, int x, int y, int width, int height)` fits the current capture to a GUI element, flushes its scene geometry, and shows a bounded unavailable or waiting label. `drawSurface(ViewReference, int width, int height, float z, PoseStack, MultiBufferSource)` draws a world-mounted feed and renews capture demand through the existing depth visibility checks. A null reference or nonpositive size is safe; `drawSurface` returns whether a completed texture was drawn. Hosts own selection, transforms, hit testing and authorization. Capture resources, resolution and refresh scheduling remain shared through `ViewSceneRenderer`.

`GraphTargetSelection.resolveNamedBinding(String type, CompoundTag payload, Function<String, CompoundTag> names)` resolves Target and target-map payloads through the existing binding API and delegates string or string-valued `any` inputs to the host's name lookup. The host returns a normalized target compound or null. Results are copied; missing or unsupported inputs produce an empty selection instead of retaining a stale target. Existing binding APIs and saved identifiers are unchanged.

# Server performance APIs

Reusable storage, spline preparation and block entity synchronization belong to the library. Addons supply their live members, controls and block entities through these APIs.

## Indexed item handlers

`com.rieno.gadgetsandgizmos.lib.inventory.IndexedItemHandler` extends NeoForge's `CombinedInvWrapper` and retains its item transfer and simulation behavior. It selects equal-sized members directly and uses binary search for mixed sizes. Its constructor copies the supplied member array; inventory contents remain live. Rebuild a view when member order or capacity changes.

`IndexedItemHandler.Cache.get(List<? extends IItemHandlerModifiable>)` returns the same view while ordered member identities and slot counts remain unchanged. Call it with the current loaded members on the owning game thread. It replaces the view when membership, order or capacity changes. Retained older views remain snapshots of their original membership.

## Spline queries

`WaypointSpline.distanceTo(Vec3)` returns the same distance as `project(Vec3).distance()` without calculating tangent or distance along the route. Empty splines and invalid positions return positive infinity.

`SplinePathNetwork.followsPrimaryLane(List<Vec3>)` checks whether every control lies less than 0.5 blocks from the first authored lane. Empty networks or control lists return true. Results depend on geometry only and use bounded caches; callers may subsequently change their lists.

Route preparation now reuses immutable directed edges and samples. Every route request still evaluates the supplied traffic callback against current conditions. Callback inputs remain independent mutable lists. Editing a network creates a new network and its own prepared geometry. Routing, endpoint identity, direction and the 0.5-block threshold are unchanged.

## Block entity updates

`BlockEntityDataSync.enqueue(BlockEntity)` coalesces requests until the owning server's tick ends. The library registers a lowest-priority `ServerTickEvent.Post` listener. A component serializes its latest state once per flush and sends it to current chunk observers. Removed, replaced, relocated and unobserved components are discarded. Ordinary gameplay updates are sent at the end of the same tick; requests made after the flush are sent at the next flush.

`BlockEntityDataSync.flush(MinecraftServer)` requires that server's game thread and flushes only its pending components. The library calls it automatically. `clear(MinecraftServer)` discards queued components for that server and runs automatically when the server stops. Native immediate control synchronization paths continue using their existing APIs.

These changes do not alter saved data, identifiers, actuator solve tolerances, physics frequency or storage transfer rules.


## World terrain hologram API

The additive `com.rieno.gadgetsandgizmos.lib.hologram` API is independent of addon content:

- `HologramRegion(center, width)` selects a square by total chunk width, including negative world coordinates. Width 3 selects 3?3 chunks; even widths include the extra chunk on the negative side.
- `SurfaceHologramSampler.sample(serverLevel, chunkPos, obfuscateOres)` samples loaded root-world terrain only. It omits hidden block faces and enclosed underground geometry. Missing chunks produce an empty snapshot and are never loaded or generated. Ore masking happens before serialization and covers `c:ores`, ore-named blocks, and ancient debris; buried ores become stone/deepslate. Block entities, inventories, and entity NBT are excluded.
- `SurfaceHologramCache(capacity, refreshTicks, chunksPerTick)` shares a bounded sampling budget per server level. `get(...)` returns an empty Optional when the caller must retry on a later tick. Masked and unmasked entries are separate. Call it on the owning server thread.
- `HologramChunk` and its immutable `SurfaceBlock` records contain chunk-local X/Z, world Y, block states, visible face masks (`1 << Direction.ordinal()`), and biome colors. `encode`/`decode` use synchronized block-state IDs and reject oversized snapshots. Consumers own their payload channel and viewer authorization.
- `HologramEntity.of(entity, worldPosition)` captures only UUID, type, position, yaw, and a player's profile name. Its codec omits equipment and private data.

Client-only rendering lives under `com.rieno.gadgetsandgizmos.lib.client.render`:

```java
private final HologramProjectionRenderer terrain = new HologramProjectionRenderer();
private final HologramEntityRenderer entities = new HologramEntityRenderer();

// Supply a camera-relative mounting pose, then translate and scale the projection.
poses.pushPose();
poses.translate(0.5, 2.25, 0.5);
poses.scale(scale, scale, scale);
terrain.render(poses, sourceWorldOrigin, snapshots, resourceGeneration);
entities.render(poses, buffers, sourceWorldOrigin, entitySnapshots, partialTicks);
poses.popPose();
```

The renderer combines Minecraft's active model-view matrix with the supplied mounting pose. It rejects off-screen chunks, uploads at most two changed meshes per call, removes occluded directional faces before upload, and uses GPU back-face culling and depth writes. Models retain their block textures and biome tint under a full-bright cyan hologram tint. Fluid surfaces are included; block entity renderers are not invoked. Keep unchanged snapshots stable to reuse their GPU meshes. Increment `resourceGeneration` after a resource reload. Call `close()` on the render thread when the owner, world, or cached resources are released. `HologramEntityRenderer.clear()` releases its isolated model instances; its predicate overload can filter models immediately without another network round trip.

`com.rieno.gadgetsandgizmos.lib.client.ui.SteppedSlider` provides reusable finite-range, stepped, draggable slider controls with caller-owned labels and change callbacks.

Pass `MultiBufferSource` as the second argument to `HologramProjectionRenderer.render` to also use the existing asynchronous `RenderSurfaceVisibility` API. Hidden chunks keep a conservative depth-only bounding-volume probe while their terrain meshes are skipped. This avoids GPU stalls when testing visibility. The overload without buffers retains frustum, face, and depth culling. Fluid surfaces use their registered client fluid textures and tint.

## Sound picker and local preview API

`com.rieno.gadgetsandgizmos.lib.client.ui.SoundPickerModal` provides an embedded, client-only sound selector. The modal shows three columns and two rows, scrolls through the complete catalog with the mouse wheel or draggable scrollbar, stages the selected registry ID, and applies it through `Consumer<ResourceLocation>` when confirmed. Escape and the close control cancel. Forward render, mouse, keyboard and character events while `isOpen()` is true; call `close()` when the owning screen is removed.

The default constructor accepts `Appearance`, `Font`, an initial sound ID, an acceptance callback and a close callback. It uses `SoundCatalog.registered(...)` and owns a `SoundPreview`. An additional constructor accepts a custom list of `SoundCatalog.Entry` and a caller-provided preview. `Appearance`, `Layout` and `EntryLayout` describe host-owned panel, entry, tick and control regions. The entire panel and its hitboxes share a uniform canvas scale.

`SoundCatalog.registeredIds()` returns the sorted sound-event registry IDs. `registered(SoundManager)` resolves searchable entries from those IDs, current subtitles, owning mod names, actual sound-event registry tags, and matching item icons where available. It falls back to a readable sound ID and a note-block icon. Searches reuse `ItemSearchQuery`: plain names and IDs, `@namespace` or `@mod name`, `#tag`/`$tag`, quoted phrases, combined terms, `-exclusions`, and `|` alternatives. A sound with no registry tags does not match a tag query.

`com.rieno.gadgetsandgizmos.lib.client.sound.SoundPreview` owns a single non-positional local sound instance. `toggle(id)` starts a preview, stops the same active preview, or stops the previous preview before playing another. Finished previews can be replayed. `isPlaying(id)` queries its current state; `stop()` and `close()` release only the owned instance. The picker stops preview playback on confirmation, cancellation and owner cleanup.

The addon supplies its painted modal, square entry and template-atlas tick via `CTSoundPickerAppearance`. Its ACC screen opens the picker for the Play Sound input, records an undo checkpoint for a changed selection, and stores the existing sound registry ID format. No server playback or saved-data format changes are required.

### Sound catalog labels and owner icons

`SoundCatalog.registered(SoundManager)` now reads the current client's synced jukebox-song registry. Track labels use the song's description, including artist and title, and disc icons are linked through the item's jukebox-playable component. These labels also populate the searchable text. `registered(SoundManager, HolderLookup.Provider)` offers the same behavior with an explicit registry provider; null leaves registry-independent matching available.

Other sounds resolve icons from their named block or item, the entity's spawn egg, equipment sound associations, and block sound types. Shared vanilla material families use representative blocks. Unscoped sound IDs can match their owning item before trailing action words. Sounds without a known owner retain the note-block fallback.

`SoundCatalog.registerIcon(ResourceLocation, Supplier<ItemStack>)` lets an owning mod supply an explicit icon when registry IDs differ. Register during client setup; duplicate registrations throw. Nonempty custom icons take priority over automatic matching. Gadgets & Gizmos registers its ratchet click against the ratchet cogwheel item; its disc titles and thruster icons resolve automatically.
`SoundCatalog` also matches generic item-family sounds, such as hoe tilling, to a representative registered item from the sound owner's namespace when no exact item or component association exists.


## Hologram slices, moving bodies and Flywheel visuals

- `SurfaceHologramSampler.averageHeight(level, region, fallback)` averages all loaded non-void columns in the region, ignoring invisible light blocks. No chunk is force-loaded.
- `HologramSlice.SURFACE` keeps the exterior terrain. `HologramSlice.at(averageY, offset)` selects that surface at zero offset, otherwise a 64-block vertical slice ending at `floor(averageY) + offset`. `contains` uses inclusive bottom/exclusive top coordinates.
- `SurfaceHologramCache.get(level, pos, obfuscateOres, slice)` shares the existing sampling budget while separating cache entries by slice. The original overload keeps surface behavior.
- Cutaway boundary faces are rendered; ore masking still uses each column's actual surface, so underground cutaways cannot reveal ore IDs while masking is enabled.
- `HologramChunk.kineticSpeeds()` adds bounded public RPM data for exposed Create block entities. The five-argument constructor remains available. The binary codec appends RPM rows, so both sender and receiver must use the updated codec. No block entity inventory/NBT is transferred.
- `SubLevelClientRenderApi.worldFrame` returns a fresh interpolated root-world position, orientation and scale, following nested body frames without mutating Sable's shared render pose.
- `ProjectionSmoothing` smooths projection anchors using elapsed seconds rather than a fixed per-frame factor.
- `HologramProjectionFrame` maps source blocks to the projector's interpolated world frame, subtracting the Flywheel render origin before converting coordinates to floats.
- `HologramBlockEntityRenderer` owns dynamic geometry for projected terrain and bodies. Call `beginFrame`, render exposed chunks, then `endFrame`. Supply the projection frame function and an optional block entity predicate to exclude recursive displays. `close` releases its native effect, detached models and caches.
- `FlywheelHologramVisuals` queues a real Flywheel effect and uses native registered block entity visualizers within `VisualEmbedding`s. It retains their instance types, partial models, mesh textures and animation plans. Projection materials are translucent and unlit; colored instances receive a full-bright cyan tint. Visualizers requiring a companion BER retain that BER path. When Flywheel is unavailable, the shared Create partial helper and ordinary BERs supply the geometry.
- `HologramProjectionRenderer(UUID sourceBody)` resolves live block model data in a selected Sable body. The original constructor keeps root-world behavior. Dynamic block model data refreshes every second while ordinary terrain retains its GPU cache.
- `SubLevelHologramRenderer` projects tracked client bodies intersecting the selected root-world region, applies the selected Y slice, culls enclosed faces, and updates body poses every rendered frame. Its geometry capture budget is two chunks per call. Close it on world changes, resource reloads and owner removal.

World and body projections use the same `HologramBlockEntityRenderer` so partials share one native Flywheel effect. No addon implementation classes are exposed by these APIs.


## Painted slider and checkbox controls

`client.ui.PaintedSliderStyle` accepts caller-owned fill and thumb `GuiTextureRegion`s plus a thumb width. It crops the fill, preserves the thumb edges, and shares the thumb travel calculation between painting and pointer input. The host screen supplies its painted rail and value label.

`SteppedSlider` has an additive constructor overload ending in `PaintedSliderStyle`. Its existing constructor retains vanilla rendering. Painted sliders retain stepped dragging, arrow-key adjustment, tooltips, and their translated labels for narration. `fraction()` returns the normalized selected value.

`CompactCheckbox.Appearance` accepts matching unchecked and checked texture regions plus a label gap. The additive constructor overload ending in this appearance paints a left-aligned checkbox and label. The original constructor retains the centered vanilla appearance. Selection remains caller-owned and updates through the existing callback.


## Inline numeric input

`client.ui.InlineNumberField` renders a caller-owned value label until clicked or focused, then reuses `InlineTextEditor` to edit a number in place. The host supplies bounds, text inset, finite minimum/maximum, whether whole numbers are required, current-value supplier, label and number formatters, and a write callback. Enter, focus loss, an outside click, and `commit()` accept finite input; Escape cancels it. Invalid or nonfinite text leaves the current value intact. Values clamp to the range, whole-number controls round, and decimal controls preserve typed precision. The host should dispatch keys to a focused editor before its own close shortcuts, and commit a focused editor before routing clicks to another control.

`SteppedSlider.synchronize(double)` silently synchronizes an exact finite value within its range without snapping it to a drag step or invoking its write callback. It keeps labels and thumb positions current when an inline editor or external control changes a value. Existing pointer and arrow-key adjustments retain fixed steps.

## Searchable painted selection grids

`com.rieno.gadgetsandgizmos.lib.client.ui.PaintedSelectionGrid<T>` provides a reusable three-column, two-row selection grid. Its host supplies labels, item icons, tooltips, `ItemSearchQuery.Entry` metadata, the selected predicate, and a selection callback. The host owns selection state and modal acceptance/cancellation.

`Appearance` supplies local search, grid and scrollbar bounds plus `CardAppearance` for the painted entry background, selected tick, checkbox, icon and label regions. `open(Font)` starts a selection session and resets search; `setEntries(List<T>)` updates available targets while retaining the active query. `filteredEntries()` exposes current matches. Forward mouse and keyboard input using the same `GuiCanvasTransform` passed to `render(...)`; wheel and scrollbar scrolling advance complete rows and reach the final partial row. `mouseReleased()` releases scrollbar drags, including when switching tabs or closing the host modal.

Search reuses `ItemSearchQuery` for plain text, quoted terms, `@mod`, `#tag`, exclusions and alternatives. Selection remains with the caller when search hides a chosen entry. `entryBounds(...)` and `drawCard(...)` are shared with `SoundPickerModal`, keeping sound and target card geometry, item rendering and labels consistent without changing the sound preview API.

The ACC supplies the updated `target_modal.png`, `target_modal_3D.png`, square entry texture and template-atlas buttons. Graph source/target pickers and worker endpoint pickers use the library grid. The addon retains target discovery, selected faces, aliases, merging, live 3D filters, and graph edits. Tab icons and tooltips replace text labels; saved target identifiers are unchanged.
The addon resource task packages the supplied `target_modal_3D.png` artwork as `target_modal_3d.png` to satisfy Minecraft resource-path validation while preserving the source asset filename.

`GuiFocusGuard.update(GuiEventListener)` reports true only when the focused listener changes. Use it to guard a screen's `super.setFocused(next)` call when a parent repeatedly reassigns focus while querying it. This prevents composite inline editors from committing and reopening during ordinary key dispatch.

## Shared painted item selectors

`PaintedSelectionModal` wraps `PaintedSelectionGrid` with the established searchable six-card panel, selected ticks, single or multiple selection, and explicit acceptance/cancellation. `Entry` carries a stable caller-owned ID, label, item icon, searchable metadata and tooltip. `Entry.item(id, stack)` builds item search metadata without discarding the caller's entry identity. Selection changes remain local until accepted. Saved IDs survive filtering and catalog refreshes, including entries currently unavailable. Hosts forward render, mouse and keyboard input while `isOpen()` is true and call `close()` when removed.

`PaintedSelectionModal.Appearance.standard(namespace)` references the standard panel, square entry and template-atlas tick in the given owning namespace. The library supplies the same assets under `gadgetsngizmos`; the addon installs its `createthrusters` appearance through `ItemPickerScreen.setDefaultAppearance(...)`. `defaultAppearance()` allows embedded hosts to share that skin. The existing `ItemPickerScreen` constructors remain compatible, including the item predicate used for fluid-container restrictions. The screen now stages its selection and applies it with the footer tick or Enter; Escape and the close control cancel. Window resizing retains pending selection and search.

`ItemPickerScreen.inventory(...)` supports inventory-only selectors with a stack callback. `inventoryEntries(...)` and `inventorySelection(...)` expose the same catalog to embedded hosts. Counts are normalized to one without changing source stacks; equal item/component variants merge, and different components remain separate. Inventory-backed IDs identify the snapshot entry rather than replacing item components with a registry default. `PaintedSelectionGrid.ensureVisible(predicate)` initially reveals the selected card without changing selection.

The addon reuses these APIs for tablet item and fluid-container dialogs, schedule item fields, ACC worker item inputs and multi-item filters, and inventory-backed frequency choices in both ACC and tablet editors. Worker availability limits and item predicates remain owned by the addon. Multi-item worker filters commit one staged selection on confirmation. Native inventory interactions, recipe selection, non-item resource selectors and the graph's drag-and-drop registry sidebar retain their separate roles. No saved item IDs or graph serialization formats change.


## Exterior hologram visibility and surface anchor

`SurfaceHologramSampler.sample` now scans the loaded chunk and a one-block border for air connected to the sky or border. Full occlusion faces stop traversal; partial shapes retain their open sides. Floating platforms and slabs no longer discard terrain beneath them, and sealed interior cavities remain excluded. Snapshots still retain only exposed faces, honor ore masking, never request unloaded chunks, and respect the existing packet size cap. Chunk-border air is treated conservatively to avoid holes where a larger overhang extends outside the sampled volume.

`hologram.HologramFaceVisibility.shouldRenderFace(state, level, pos, direction, neighborIncluded)` shares Minecraft's shape-aware culling across terrain, cutaways, and moving sub-levels. A neighbor outside the projection's bounds exposes the cut face. Fluid source interiors remain culled without treating waterlogged partial blocks as full solid cells.

`HologramSlice.SURFACE_PRE_OFFSET` is four blocks. `projectionOriginY(averageY, offset)` places the default surface anchor four blocks below the average height and retains the existing origin for nonzero cutaway offsets. Consuming renderers should use this origin consistently for terrain, entities, and sub-level transforms.


## Hologram render context and live feed suppression

`client.render.HologramRenderScope` marks a miniature rendering pass on the current thread. Enter it with `try(var scope = HologramRenderScope.enter())` and query `isActive()` in expensive screen or feed renderers. Nested scopes restore the previous state when closed, including exception paths. Keep each scope on the thread that entered it.

The library terrain, block entity, entity, world-block, and Flywheel hologram render paths enter this scope. Native Flywheel visual creation, animation, and tick plans inherit it. `ViewFeedRenderer` and `ViewSceneRenderer` skip feed draws, capture allocation, source resolution, and visibility/retention renewals in these passes. `ViewSceneRequests` also skips renewing cached GUI feed demand inside a hologram. This policy is independent of addon classes; content owners use it to skip their own live screen surfaces while retaining their static meshes.

## Electrical connector polarity and cable current limits

`ConnectorOutputLimits.polarity(Direction)` returns +1 by default and `setPolarity(Direction, int)` accepts only +1 or -1. `signedVolts(Direction)` combines the saved voltage magnitude with that polarity. The additive `Polarities` NBT compound preserves existing watt and voltage keys; saves without it use positive polarity.

`PowerGridDeviceConnectors.setPolarity(ElectricalStorage, BlockPos, Direction, int)` validates the real attachment, persists the setting on its member, and refreshes the circuit. Connector snapshot rows now include `Polarity`; `Volts` remains a magnitude, while `ActualVolts` and `ActualAmps` retain their physical signs. Clients should group energy direction using `TransferWatts` and display signed voltage/current.

Converters inspect native loaded wire types and connected series/junction wiring on the server. The weakest current rating is reduced by a 10 percent reserve, reduced for measured non-converter demand, and shared among battery converters using that wiring. Unknown or unloaded cable ratings disable transfer until resolved. A native solver coupling imposes the current limit during electrical convergence in either current direction, rather than discarding received FE after excessive current has already flowed. Full storage blocks incoming power while retaining output; charging accepts either voltage polarity. Physical power is converted using Power Grid's FE-per-watt ratio without the old additional voltage-based receive cap.

## Painted GUI controls

`com.rieno.gadgetsandgizmos.lib.client.ui.PaintedGuiStyle` shares painted modal frames, recessed sections, buttons and separators. Hosts supply nine-slice artwork, a separator region, and text colors. `slice(region, left, top, right, bottom)` constructs a `GuiNineSlice` from a contiguous atlas region and rejects borders without a center.

Use `renderPanel`, `renderInset`, `renderButton`, `renderTextButton`, and `renderSeparator` with the host layout. Text buttons fit labels inside the painted border; selected buttons use the supplied selected artwork, while disabled and hovered controls receive independent shading. The API contains no addon or controller dependencies.


# Tumbling particles and contained entity rendering

`com.rieno.gadgetsandgizmos.lib.client.render.TumblingSpriteParticle.Provider(SpriteSet sprites, float size, int lifetime)` creates particles for an owning mod's `SimpleParticleType`. Register it with `RegisterParticleProvidersEvent.registerSpriteSet`. The mod supplies its particle type, particle-atlas textures and launch position/velocity. Size is the nominal quad half-size in blocks, including transparent sprite padding; lifetime is the minimum lifetime in client ticks. Size varies from 70% to 130%, and lifetime varies up to approximately 150%. Inputs must be positive and size must be finite. Each particle selects one supplied sprite, uses normal world lighting, slows under gravity, flutters and fades during its last twelve ticks. Motion clips against solid root-world and Sable geometry through `WorldParticleCollision`. Each provider caps live particles at 2048 and new particles at 128 per tick using `ClientParticleBudget`; rejected spawns return null.

`com.rieno.gadgetsandgizmos.lib.client.render.ContainedEntityRenderer<T extends Mob>` renders a client-only display mob without spawning or ticking it. Construct with an `EntityType<T>`, then call `render(Level level, int age, float partialTicks, float yaw, PoseStack poses, MultiBufferSource buffers, int light)` on the client render thread. The caller owns local translation, orientation, scale, age, yaw in degrees and packed light. The helper disables AI, gravity and sound, sets animation age and body/head yaw, and invokes the entity renderer directly without dispatcher shadows. A weak cache avoids retaining an unloaded level. Null or server levels are ignored. This API owns no gameplay entities or addon classes, and introduces no registrations or saved-data changes.

## Isolated player rendering in hologram previews

`HologramEntityRenderer` now owns separate cached `PlayerRenderer` instances for wide and slim skins. Miniature players no longer mutate the shared renderer used by normal world players and first-person arms, including renderer-local skin-layer state from optional mods. Player skin textures and vanilla model selection are retained; non-player entities continue through the entity render dispatcher. `clear()` also retires these renderer instances, so consumers must continue calling it when their world or render resources change. No addon-specific types are used and the public rendering signatures remain unchanged.


# Contained entity setup

`ContainedEntityRenderer<T extends Mob>(EntityType<T> type, Consumer<? super T> setup)` adds an optional setup callback for display mobs. It runs on the client render thread after AI, gravity and sound are disabled, before the newly created mob is cached and rendered. The owning mod can equip its own items with `entity.setItemSlot(EquipmentSlot.HEAD, stack)`, allowing existing entity render layers to fit and animate the equipment. The callback is invoked for every new display instance, including after a level change or weak-cache collection; it does not run on every render. Both arguments must be non-null. The original one-argument constructor remains available and uses a no-op callback. Rendering age, pose ownership, weak caching and saved-data behavior are unchanged.
## Incremental graph presentation

The experimental library adds reusable change tracking under `com.rieno.gadgetsandgizmos.lib.util`:

- `ValueChangeJournal<K>` records added, changed, and removed keys after the owning host changes its authoritative values. Each reader supplies its own last revision to `changesSince`. Reads do not consume history. A new reader, explicit `reset`, or history overflow requires a complete baseline. History retains keys only and is bounded by the constructor's capacity.
- `ValueProjection<K, V>` retains a selected read-only view and applies only changed keys between baselines. Supply the source identity, that reader's journal changes, and the current authoritative map. Changing source identity also replaces the baseline, even if numerical revisions match. Recreate a projection when its selection changes.
- `ValueDelta<K, V>` carries a full baseline or changed values and removed keys. `between` compares already-selected snapshots, and `applyTo` updates a reader-owned map.
- `ValueUpdateSequence` accepts an initial complete baseline and matching ordered deltas. Missing, duplicate, or out-of-sequence deltas require another full baseline. `reset` discards the stream after a lifecycle change.

`lib.graph.GraphValueSelection` selects existing `node:port` keys by node id, including generated node ids and compound port names. `lib.nbt.CompoundTagCopies.equalsExcept` compares top-level fields without visiting explicitly omitted payloads. Hosts must separately validate the omitted payloads' identities or revisions.

All operations run on the owning game thread. Keys and values must be non-null. Change sets and deltas detach their map/set containers; values are borrowed and must be treated as immutable. `ValueProjection.values()` is a read-only borrowed view that changes at the next projection update. Journal mutation and authoritative map mutation belong to the host. Record removals as well as replacements; reset after bulk restoration. Polling sources, timers, and control-state execution keep their existing scheduling.

ACC Displays consume these APIs to cache selected encoded ports, compose frames when their dependencies change, and publish value deltas without copying unchanged graph topology. HUD-only observers receive selected values through the optional `createthrusters:advanced_controller_runtime_delta` channel, with independent connection baselines and complete recovery updates every 100 ticks. Clients without that channel receive the existing complete runtime payload. Controller menus and remote tablet diagnostics retain complete graph values. Existing full payload codecs, graph data, saved identifiers, and node execution timing are unchanged. Use the addon and experimental library built together for these new APIs.

## Dual-thumb range sliders

`DualThumbSlider` stores two ordered finite values within caller-supplied bounds. `setRange`, `setLower`, and `setUpper` synchronize typed or external changes; individual endpoints stop at one another. `beginDrag(fraction, jumpToPointer)` selects the nearest endpoint, preserving the pointer offset when grabbing a thumb. Equal-distance picks alternate to keep coincident thumbs usable. `dragTo(fraction, sensitivity, step)` supports fine movement and optional snapping; `endDrag()` ends the gesture. Fractions increase from the minimum to the maximum.

`PaintedRangeSliderStyle` accepts cropped fill and equal-sized thumb regions, a horizontal or vertical axis, and a reversed flag. `draw` fills only the selected interval and paints the highlighted thumb last. `fractionAt`, `contains`, and `isOverThumb` share the render geometry, including thumb overhang and end travel. Hosts supply rail artwork, screen placement, labels, modifier-key policy, and persistence.

## Centered painted dropdown labels

`GuiDropdownControl.drawContents` has an additive overload with a final `boolean centeredValue` argument. Pass `true` to center the selected value within the value compartment while retaining the separate arrow, clipping and canvas scale. Existing calls retain their right-aligned value layout. Hosts continue to supply their painted background and colors.


## Portable block entity drops

`com.rieno.gadgetsandgizmos.lib.item.BlockEntityItemDrops.create(BlockEntity blockEntity, CompoundTag data)` creates the block's item with a detached copy of host-supplied portable data, the registered block entity type and its collected components. Source block coordinates are removed. Blocks without an item return an empty stack. The host chooses the data, persists any external references and owns custom names and removal policy. The supplied tag is not mutated.

`replace(List<ItemStack> drops, ItemStack retained)` returns a new list, replacing matching item types with independent copies of the retained stack while preserving each loot count and unrelated entries. Empty retained stacks leave entries unchanged. It does not create loot when the original list is empty. Unrelated stacks are borrowed.

Call these APIs on the owning game thread. Capture data before destructive cleanup. Hosts must retain referenced storage for the lifetime of dropped items and decide when creative-mode removal needs a separate item drop. The APIs contain no addon types and introduce no registrations or serialized keys. G&G controllers use compact manifest references to retain bindings, applied and draft graphs, and the inserted linker when broken and placed again. Embedded backing blocks remain at their original position.


## Authoritative Create schematic snapshots

`lib.schematic.SchematicSnapshotProvider` is an opt-in contract for block entities whose complete configuration lives on the server. Implement `schematicSnapshot(HolderLookup.Provider)` to return portable, approved NBT without the source database identity. The owning mod remains responsible for importing configuration into independent storage, retaining pending data until import succeeds, transforming its own references, and clearing temporary payloads afterward.

The library defers Create's schematic-and-quill save when a selected block implements this contract, requests only the selected provider positions, and resumes Create's original save and optional upload after every snapshot arrives. Capture runs on the server game thread, rejects unavailable or out-of-range chunks without loading them, limits selections to 512 providers, and caps total compressed data at 64 MiB. Segments are at most 32 KiB, requests are limited to one per player per 20 ticks, and client requests expire after 600 ticks or a world change. Failed or incomplete transfers do not resume the export. Selections without providers use Create's existing path. This adds the required library channels `gadgetsngizmos:schematic_snapshot_request` and `gadgetsngizmos:schematic_snapshot_segment`; install matching addon and library builds on both sides.

`SchematicSnapshotScope.enter(Map<BlockPos, CompoundTag>)` scopes immutable borrowed snapshot values to a synchronous export on the current thread. `data(BlockEntity)` returns detached NBT with the current registered block entity type for opted-in providers. Vanilla `saveWithId` uses these snapshots only inside the scope. Nested scopes restore the previous map when closed. `SchematicSnapshotPackets.capture(ServerPlayer, List<BlockPos>)` exposes the same server capture validation and returns position-indexed encoded envelopes. The client entry point is `ClientSchematicSnapshots.request(Level, BlockPos, BlockPos, Runnable)`; its return value indicates whether the exporter should defer its normal action.

`lib.nbt.ChunkedTagPayload.encode(CompoundTag)` and `decode(CompoundTag)` share bounded compressed NBT handling. Version-one envelopes retain `Version`, `CompressedBytes`, `Chunks`, and each chunk's `Data` keys, preserving existing controller schematic files. The compressed limit is 16 MiB per payload and decompression accounting is capped at 128 MiB. Both methods return independent data and throw `IllegalArgumentException` for invalid, incomplete or unsupported payloads. Controller-specific plot cleanup and item sanitization remain in G&G.

`SchematicSnapshotTransfer(UUID, List<BlockPos>)` validates the request's exact position set, ordered bounded segments, and the successful final terminator independently of any client classes. `isComplete()` becomes true only after every block has decoded; `snapshots()` is unavailable before then and returns a detached map of borrowed immutable values afterward. Unknown positions, changed byte counts, duplicate/trailing chunks, failed responses and incomplete terminators are rejected. The client exporter uses this same assembly contract.

## Port property bindings and tiled GUI textures
- PortPropertyBindings.properties(bindings, port) returns every property currently using a port, including existing shared bindings.
- PortPropertyBindings.assign(bindings, port, property) returns a copy with that port reassigned; an empty property clears only that port. Saved maps remain property-to-port.
- GuiTextureRegion.drawTiled(graphics, x, y, width, height, tileWidth, tileHeight) repeats a cropped atlas region and clips edge tiles using the current GUI pose.


## Hologram reference frames, ownership filters and checkbox alignment

`HologramProjectionFrame.alignSource(Quaternionfc referenceOrientation)` returns a new frame that applies the inverse of a source-world reference orientation before the frame's existing projection rotation. Target, scale and source origin are retained, and neither input quaternion is mutated. Supplying an interpolated craft orientation with its configured local axes produces a craft-relative projection. Apply the same resulting frame to terrain, entities and dynamic Flywheel embeddings.

`SubLevelHologramRenderer.render(..., HologramBlockEntityRenderer dynamic, Predicate<ClientSubLevel> visible)` filters each loaded body before sampling or allocating meshes and dynamic visuals. Excluded bodies release their cached meshes through the normal lifetime pass. The original overload includes all bodies.

`HologramEntity` adds nullable `subLevelId`, captured by `of(Entity, Vec3)` from the entity's containing body or, when outside a plot, its tracked/ridden body. The original five-argument constructor retains a null owner. Encoding includes the optional body UUID, so communicating endpoints must use the same snapshot layout.

`HologramEntityRenderer.render(..., float partialTicks, BiPredicate<HologramEntity, Entity> visible)` exposes both server provenance and the isolated model to visibility filters. Existing category predicates and unfiltered overloads retain their behavior. Excluded snapshots do not update or render their models.

`CompactCheckbox.setRightAligned(boolean)` anchors a painted checkbox at its hitbox's right edge and places its full label immediately to the left using the configured gap. The default painted alignment remains left aligned, and unpainted controls retain their existing layout.

## Connected surfaces and canvas navigation
- ConnectedSurfaceLayout(columns, rows).cells(x, y, width, height) partitions a surface with shared rounded boundaries and LEFT/RIGHT/UP/DOWN connection flags. Hosts choose connected atlas regions from each cell's flags.
- GraphViewport.zoomAt(x, y, nextZoom) preserves the authored point beneath the pointer; panBy(dx, dy) moves by visible viewport pixels.
- GraphViewport.snapCoordinate(coordinate, step) shares configured grid alignment between graph editors and other design canvases. graphDistance continues to convert visible arrow-key movements into authored coordinates.


## Fixed-colour oriented kinetic visuals
- ColoredOrientedRotatingVisual<T extends KineticBlockEntity> extends Create's OrientedRotatingVisual and retains the supplied RGB colour after kinetic updates.
- Its constructor accepts the visualization context, block entity, partial tick, model's original facing, target facing, Flywheel model, and RGB colour.
- Lighting updates, speed changes, crumbling instances and deletion retain Create's existing lifecycle. Register the visual from the host addon, which owns its partial model and block entity type.

## Tracking support blocks

`ViewTrackingTargets.IGNORED_BLOCKS` identifies the block tag `gadgetsngizmos:view_tracking_ignored`. Owning mods extend `data/gadgetsngizmos/tags/block/view_tracking_ignored.json` with their support blocks. The library supplies an empty tag and contains no addon block IDs.

`ViewTrackingTargets.isTrackable(SubLevel)` checks loaded server plot blocks without loading chunks. Null, removed, empty and client bodies return false. A body containing only air and tagged blocks is ineligible; any other block keeps it eligible. Names, translations and custom names do not affect eligibility. The check reads current states on every call, so adding or removing mounted blocks and reloading tags take effect without clearing a cache.

Ray-hit acquisition, cone candidates and retained target resolution share this check. Entity targets and ordinary raycast results retain their existing behavior. G&G supplies aileron, thruster and vector bearing links plus Simulated's optional swivel bearing plate through the tag. Further bearing links and head plates can opt in through the same tag. Use matching addon and library builds to apply the filter.

## Heading alignment without cancelling the mounted projection up axis

`HologramProjectionFrame.alignSourceHeading(Quaternionfc referenceOrientation)` aligns only the source-world heading about world Y. It projects the reference forward vector onto the horizontal plane and falls back to the reference right vector when the forward axis is vertical. The destination frame retains its own up axis, pitch and roll. Target, scale, origin and input quaternions are unchanged. `alignSource` remains available when full source-frame inversion is intended.

For mounted maps, construct the destination orientation from the interpolated carrier rotation multiplied by the configured local forward/up rotation, then call `alignSourceHeading` with that destination orientation. Pass the resulting frame to Flywheel and apply the same relative rotation to ordinary terrain, block-entity and entity rendering.

## Optional world-up locking for projections

`HologramProjectionFrame.lockWorldUp()` returns a frame whose destination orientation has only its original horizontal heading, so projected source-world up remains world up. Target, scale and origin are retained; the original frame and its quaternion are not mutated. It shares the vertical-forward fallback used by `alignSourceHeading`. Apply world-up locking before source-heading alignment when the destination must stay level while map heading still follows a carrier. The resulting frame can be shared by terrain, entities, ordinary partials and Flywheel embeddings.

## Deterministic CPU tasks and retained resources

`lib.util.ParallelTasks.evaluate(jobs)` completes detached, independent supplier calculations and returns an immutable list in input order. It waits for every accepted job before returning or propagating the first failure by input order. Interruptions are restored after completion. The shared pool has at most two daemon workers, reserves at least two available processors, has no task queue, and releases idle workers after 30 seconds. Saturated and nested batches execute locally. `parallelism()` reports the worker limit; hosts should batch only sufficiently expensive independent work. Jobs must not access live levels, block entities, inventories, rendering state or shared mutable scratch data. World reads, mutations and application of results remain on the owning game thread.

SCM uses this API for independent signed authority solves and sufficiently large articulated carriage allocations. Results remain within the current control tick. Floating-point reductions, iteration limits, tolerances and actuator application order are retained. Scratch arrays are confined to each executing thread. Model/allocation caches remain bounded by their existing actuator budgets, with room for up to 64 independent entries. Spline arc-distance samples retain up to 128 segments (approximately 4 MiB per active thread).

`lib.util.CompatibleMethods.find(type, name, arguments)` caches compatible declared/inherited methods and missing signatures using class-scoped storage. Keys include exact argument classes and null markers. Primitive-wrapper compatibility and existing superclass search order are preserved. It does not change method accessibility. Each owner cache is limited to 256 signatures and may be cleared transparently.

`lib.inventory.LocalResourcePool.get(items, fluids, energy)` retains one immutable physical capability description while all supplied handler identities are unchanged. Null handlers represent absent capabilities. Call it with the current handlers on the owning thread; it never caches contents, transfers, capacity values or availability. Handler replacement/removal immediately produces an updated description. Existing `ResourceRouting` fast paths preserve identity deduplication, immutable handler lists and live transfers. Container routing retains the last matching slot/tank range while preserving per-access removal checks and tick/revision/movement invalidation.

`lib.util.ResourceLease(idleNanos)` tracks idle lifetime using a caller-supplied monotonic clock. `renew(now)` refreshes demand and `isExpired(now)` checks elapsed time, including clock wraparound. Unrenewed leases are expired. Camera feeds retain idle resources for five seconds independently of FPS; active source limits stay at eight. New sources can retire an unrequested feed at the following frame boundary, and world/resource cleanup retains its existing ownership rules.

## Optimization regression commands

- `gradlew test` in the library runs the scalar-output fixtures, concurrent/shared-model SCM tests, worker rejection/interruption/nesting tests, routing pool tests, exact spline cache tests and hologram tint checks.
- `gradlew runAccPresentationTest -Pacc_presentation_validation=true` in G&G runs the isolated ACC, storage, schematic and camera server regressions.
- After the server tests, `gradlew runClient -Pacc_presentation_validation=true -Prendering_validation=true` copies the generated test world to `run/rendering-validation`, opens it and automatically checks real camera GPU resource lifetime, expiry, source limits, idle replacement and resource reload. The result is written to `run/rendering-validation/result.txt`. This opt-in test config changes only that isolated test world's blocks and client options. Test classes are not packaged in distribution jars.

## Cached base-model holograms

`HologramProjectionRenderer.baseModels()` creates a retained GPU renderer that uses `ModelData.EMPTY`, performs no live block entity lookup and does not periodically rebuild meshes for block entity animation. The additive `(UUID sourceBody, boolean liveModelData)` constructor exposes the same choice for scoped sources. Existing constructors retain live model-data behavior.

`SubLevelHologramRenderer.renderBaseModels(..., Predicate<ClientSubLevel> visible)` projects cached base models with live interpolated ship transforms and the supplied ownership filter. Existing `render` overloads retain their dynamic renderer behavior. Switching modes disposes the previous GPU resources.

`HologramChunk.hasSameSurface(other)` compares chunk position, biome colors and exposed blocks/faces, independently of kinetic animation metadata. Record equality and the network format are unchanged. Base-model consumers retain unchanged mesh identities across resampling and kinetic updates. Block edits, exposed face changes, biome changes, resource reloads and owner removal retain their invalidation/cleanup behavior.

G&G projectors select base models for terrain and ships. Animated partials, live screens/tablets and block entity fluid interiors are omitted; blocks with no baked model may be invisible in projections. Ordinary world rendering and ACC/SCM simulation are unchanged. The opt-in rendering validation also checks real GPU retention, model-data isolation, block edits, resource generation invalidation and disposal.

## Retained hybrid holograms

G&G projectors now select a hybrid presentation, superseding the base-model-only integration described above. `HologramProjectionRenderer.retainedModels(UUID)` retains live baked model data and checks NeoForge's model-data manager for requested refreshes, instead of rebuilding every block-entity chunk every second. Block, face, biome and resource generation changes still invalidate meshes; kinetic metadata alone does not. Default constructors and `baseModels()` preserve their earlier behavior. Sources must use the normal `requestModelDataUpdate()` contract when their model data changes.

`SubLevelHologramRenderer.renderRetainedModels(..., HologramBlockEntityRenderer, Predicate<ClientSubLevel>)` applies the same retained mesh policy to ships and preserves ownership filtering and live interpolated poses.

Registered block entity renderers may implement `HologramRendererProvider.createHologramRenderer(BlockEntity)` and return an owner-local `RetainedHologramRenderer`, or null for ordinary rendering. Retained instances are closed on source replacement, geometry removal and projection cleanup. The provider owns invalidation of any live presentation state. Unknown renderers retain the existing native/partial/BER compatibility path, including blocks with no baked model. Detail lists retain resolved positions across equivalent surface snapshots; each loaded source chunk is resolved once per presentation and its live entity map still detects replacement immediately. No chunk tickets are acquired.

`RetainedTankRenderer(FluidTankBlockEntity, BlockEntityRenderer<FluidTankBlockEntity>)` caches local vertices and original render types. It validates handler identity, fluid contents/components, capacity, exact interpolated fill, controller, window state, structure dimensions and light/overlay before reusing geometry. Atlas animation continues independently of tessellation. Vertices are still submitted to normal render buffers each presentation. `MultiFluidTankRenderer` supplies this provider; virtual dispatch retains subclass decorations such as G&G's tablet housing. `RenderGeometryCache.clear()` releases retained CPU geometry.

Flywheel memberships are published only when they change. Child frame/tick plans are retained. Stable embeddings and hologram lighting no longer dirty their GPU data each frame. Simple rotating visual updates occur each client tick or speed change; per-frame dynamic plans remain active, and other visual types retain per-frame update behavior. Client-only caches and adapters do not change simulation, control solver scheduling, storage transfers or saved/network data.

Validation includes instance tint upload unit tests, all SCM/ACC/storage regressions, real GPU model-data invalidation and lifecycle checks, native Create visualizer instance retention/speed updates, renderer-only chest fallback/replacement and tank material/amount/structure/interpolation changes in the isolated development client.

Unknown subclasses of `MultiFluidTankRenderer` keep the live fallback unless they explicitly opt in by overriding `createHologramRenderer` and calling the protected final `createRetainedTankRenderer(FluidTankBlockEntity)` helper. G&G's smart tank renderer does this because its hologram tablet housing follows the same controller/dimension invalidation. Only the exact standard Create `SingleAxisRotatingVisual` class receives reduced update frequency; subclasses retain their original per-frame updates. Native scene idle retention is five seconds to avoid deleting and recreating visuals between slow frames; explicit removal and projection cleanup still dispose them immediately.
## Optional color effect adapters

`ColorEffectTarget.acceptsColorEffect()` defaults to true and lets an owning addon reject new applications through its local policy. `colorEffectTarget()` defaults to the receiver and may resolve a multiblock controller; applicators must validate access to the resolved block entity before applying.

`ColorEffectState` stores presets under `GadgetsNGizmosColorEffect`. Call `read` and `write` from the owner's persistence and update-tag paths. `apply` marks the owner changed and queues its normal block-entity packet on the server game thread. Native color settings are independent. Missing effect data restores the default preset, including when clearing an effect over the network.

`ColorEffectVertexConsumer` preserves geometry, alpha and UVs while sampling a preset by caller-supplied spatial progress or texture V. Shader mode carries tint independently from native vertex color in an extra Normal attribute. `ColorEffectShader.withTint` and `create` derive a separate shader from the active resource provider, preserving source style selection, brightness, transparency and discard before tinting. Use a shader id in the consuming mod's namespace. Sources must have no Normal attribute, a `void main()` entry point and a `fragColor` output. `ColorEffectRenderTypes` supplies alpha/additive blending and float uniform copying.

`ColorEffectParticles.wrap` transports the original particle options, policy id and preset together. Register each policy once with `registerPolicy` using the owning mod's namespace. Policies are local and are not synchronized. Client providers create the native particle and attach the optional effect without replacing motion, collision or Sable hooks. Native particle adapters select `ColorEffectParticleRendering.renderType`; single-quad rendering supplies the independent tint attribute. Disabled policies preserve the native pixel path. Blend changes affect newly spawned particles; existing particles expire normally.

The addon exposes `common.dye_applicator.flightControl` and `common.dye_applicator.propulsionSimulated`, both enabled by default. On a server they govern new applications and particle effect emission; on a client they govern display. No registry entries or mixins are disabled by these settings. Flight Control saves and synchronizes presets separately from its native flame color, including cluster controllers. Propulsion Simulated retains native dye ids and applies presets to shader plumes, adaptive trails and fire/plasma/ion particles. The applicator resolves multiblock controllers before permission checks. No other blocks or entities are added by this integration.

## Retained geometry and visibility

Hologram details apply each registered renderer's conservative bounds before rendering or requesting native visuals. Explicit offscreen renderers and nonfinite bounds retain their existing behavior. Burner models, docking connectors and kinetic previews retain their normal renderer paths.

`MultiblockPanelLayout.panels` now shares immutable layouts in a bounded 2048-entry cache. Cache keys include immutable origin, normalized dimensions, axis and mounting flags, so moved/resized multiblocks receive new panels. Smart container tab lookups avoid deriving a UUID or resolving the controller twice just to identify the current tab.

ACC surfaces use the existing display frame scheduling contract: changed network frames and block states refresh immediately; unchanged nearby content refreshes at the existing 50 ms projected-GUI cadence. Retained feed textures continue receiving visible demand between surface redraws. Secondary cameras render separately because they omit recursive feeds and must never replace the player's cached surface or feed demand. Interaction picking is renewed on every presentation, independently of geometry refresh. This changes client presentation work only.

`RenderGeometryCache` replays through the standard packed vertex API, preserving transformed positions and normals, packed colors, overlay and light coordinates. Hologram tint consumers forward packed vertices directly while keeping their existing tint and lighting rules. Consumers without a packed implementation retain the standard `VertexConsumer` fallback. Geometry, draw order and animation inputs are unchanged.

## Clearing stored propulsion lights

`PropulsionLight.clear(Level, BlockPos)` clears the stored propulsion light level and auxiliary source, and queues relighting when the saved level changes. Call it on the owning game thread. Null and client levels are ignored. G&G uses it once when thrusters load to retire old light states; the saved `light_level` property remains compatible with existing worlds. Thrusters no longer emit block, outlet or plume world light, and their lighting config entries have been removed. Shared lighting APIs remain available to other consumers.

## Hologram surface resolution

`HologramResolution` is a common-side quality enum (`FULL`, `HIGH`, `MEDIUM`, `LOW`). `level(projectedBlockPixels, previousLevel)` chooses textured geometry (0) or one of three averaged surface levels (1–3). High starts around 2 pixels per source block. Medium keeps at least level 1 and Low keeps at least level 2, including near-plane intersections reported as positive infinity. Smaller projected blocks simplify further, with 20% hysteresis. Full, NaN and negative sizes select level 0. Enum IDs and method signatures are unchanged; no value is stored in world data or sent to the server.

`HologramProjectionRenderer.render(..., generation, resolution)` and `SubLevelHologramRenderer.renderExteriorModels(..., visible, resolution)` add optional quality selection. Existing overloads select Full. Detail uses the chunk's conservative projected block size, including the current pose and projection. Mesh changes follow the existing per-frame budget. Averaged surfaces share the original terrain buffer and render pass, using the library's neutral atlas sprite. No extra draw pass is added to mixed chunks. Buffers are released on replacement, resource generation changes, removal and close.

`ProjectionDetail.blockPixels(clipMatrix, minY, maxY, width, height)` estimates the largest projected source-block edge for a local 16×height×16 chunk. Invalid viewports and near-plane intersections return infinity; the selected resolution policy applies its near-view minimum.

`SurfaceColorMesh.build(cells, tileSize)` merges occupied coplanar RGB face cells into immutable quads. Face IDs follow Minecraft direction ordinals; `plane`, `u` and `v` are integer coordinates. Tile sizes 1–16 average only occupied cells. Missing cells stay missing, opposite faces and different planes stay separate, and output order is deterministic. No level or renderer access is required.

`OpaqueColorAverage.rgb(count, pixelLookup)` averages opaque NativeImage ABGR pixels into RGB, returning -1 when any pixel has transparency or count is empty. This strict API remains available independently of the alpha-aware renderer. Rendering caches sprite averages from the loaded resource pack, including animation frames. Hosts call `HologramProjectionRenderer.clearColorCache()` when reloading resources; G&G's world projector reload listener does so.

`TextureColorAverage.argb(count, pixelLookup)` adds alpha-weighted averaging of NativeImage ABGR pixels into ARGB. Transparent pixels do not dilute visible RGB; an empty or transparent source returns 0. `over(foregroundArgb, backgroundArgb)` composes foreground over background. The renderer now combines solid/cutout/cutout-mipped layers on validated unit cube faces, including grass side overlays and unculled cube quads. Cube validation tolerates baking offsets within 0.00001 blocks, including rotated grass variants. All six faces must contain an opaque base; transparent windows and non-cube geometry retain their normal rendering. Resource reload clears the sprite and model caches as before. The existing strict `OpaqueColorAverage` API is unchanged.

G&G supplies the client config and defaults it to Full. Hidden container housing clicks use the existing server-owned menu and permission checks without changing presentation state.

## Exterior hull and static models

`ExteriorHull` accepts inclusive integer voxel bounds and an `Occlusion` callback. It flood-fills exterior nonopaque cells, treating transparent windows and openings as passages. `visibleFaces(x, y, z)` returns Minecraft direction bits for faces connected to that exterior. Callbacks must report only opaque cells; missing cells are transparent. Bounds include a padded exterior. The grid is limited to 1,048,576 cells; invalid or larger bounds conservatively return all face bits. Instances retain detached visibility only and can be queried without accessing a level.

`SubLevelHologramRenderer.renderExteriorModels(...)` adds this culling across loaded sublevel chunks before mesh generation and detail requests. It preserves world-space clipping, ore masking, ownership predicates and moving ship transforms. Hulls refresh at the existing one-second sampling cadence, with at most one hull build per presentation. Clipped cells are transparent to the hull calculation so cutaway interiors remain visible. Existing live and retained-model entry points preserve their behavior.

`HologramProjectionRenderer.staticModels(UUID)` selects closed Create/Smart Tank housings while preserving their connected model data. `HologramBlockModels.closedTank(BlockState)` returns a presentation-only state with `Shape.PLAIN`, leaving other properties intact. `opaque(state, level, pos)` treats those closed tank housings and solid-rendered blocks as occluders; glass and other transparent blocks remain open. Hosts should exclude tank block entity renderers when using closed models; no source state or stored fluid is changed.

`HologramBlockEntityRenderer(frame, allowed, animated)` and `FlywheelHologramVisuals(level, frame, animated)` add an explicit static mode. Existing constructors still animate. Static fallback geometry is recorded once per entity/state and invalidated when the entity or surface is replaced. Native visuals retain their partial models and moving-source transforms but run initialization once, skip subsequent frame/tick animation plans and zero projection-owned Create rotational, scrolling and actor speeds. Source instances and kinetic speeds are untouched. Resource and visibility cleanup retain the existing lifecycle.

`HologramRenderTypes.terrain()` now enables existing atlas mipmaps; it does not change global mipmap settings or resource-pack textures.

## Mounted display presentation and distance

`MultiFluidTankBlockEntity.trySetWindows(boolean visible)` safely applies Create's tank window layouts on a server controller. It returns false while the controller's complete saved footprint is unavailable or still points at another controller; it never loads chunks or creates missing member entities. A successful call updates block models, marks the controller changed and synchronizes its window flag. Active boilers retain closed housings. Existing `setWindows` behavior is unchanged. Call on the owning server thread and retry after members become available.

Smart Tank presentation now drives these window models as well as its contents renderer: contents/both open the windows, while tablet/none close the housing. Toggles apply immediately; initialization and multiblock changes repair older mismatched saves, deferring model changes until members are installed.

`MountedTabletState.presentation()` and `setPresentation(Presentation)` expose saved presentation independently of the selected app tab. Stable IDs are `contents`, `tablet`, `none` and `both`, persisted as `MountedTabletPresentation`. Missing or unknown IDs use `CONTENTS` (contents visible, tablet hidden); `MountedTabletTab` is unchanged. `Presentation.next(true)` cycles contents → tablet → none → both; `next(false)` toggles tablet and none. Hosts choose when to cycle, synchronize members and send updates on the owning server thread.

`DisplayDistancePolicy(liveRange, renderRange).mode(distanceSquared, enabled)` returns `LIVE`, `STATIC` or `HIDDEN`. Ranges must be finite, nonnegative and ordered. Range boundaries are inclusive; disabled or invalid distances are hidden. G&G uses 32 and 64 blocks for mounted container tablets. Static previews retain the last image or use a shared image without allocating a screen or framebuffer.

`ClientRenderDistance.distanceSquared(BlockEntity)` measures the current camera's physical distance, transforming sublevel coordinates into the world. Removed, unavailable or other-level owners return positive infinity. Use it on the client render thread.

`DisplayFrameDemand.request(frame, live)` combines all presentations in a frame, so a static presentation cannot cancel a live one. `live(frame)` carries live demand through the next frame's update phase and expires it after one absent frame. Secondary views may present the retained image without renewing live demand.

`RetainedTankRenderer` adds a constructor accepting a `LongSupplier` presentation revision. `MultiFluidTankRenderer.createRetainedTankRenderer(tank, revision)` exposes the same additive opt-in for subclasses. Revision changes invalidate geometry immediately while retaining the existing fluid, light, shape and interpolation checks. Existing constructors remain supported.

## Saved multiblock loading

`LoadedBlockEntities.get(Level, BlockPos)` returns only an installed, non-removed entity from an already available full chunk, or null. It never loads a chunk, promotes pending NBT or creates an empty block entity. Call on the owning level thread; unavailable entities are a normal result.

`ConnectedVaultBlockEntity.getControllerBE()` now waits for installed controller data. Its protected `getLoadedMember(BlockPos)` also verifies the block-entity type. Connectivity rebuilds wait for the saved footprint and pending adjacent members. Missing controller entities do not break links while the matching block is still present; genuinely removed controllers still recover normally. Adjacent FE transfer skips pending block entities before querying their capabilities. Container network identity and root resolution also use installed entities, preventing automation from creating an empty replacement.

ACC rendering uses controller-only dispatch and retains bounds for the current frame, state, geometry and game tick. Text-only display-link, unavailable and external-text surfaces retain geometry until a new frame or state arrives; animated modes keep their existing cadence. Camera passes share a separate geometry cache, preserving secondary-camera isolation and immediate network updates without rebuilding the same surface for every feed.
## Shared tooltip lines

`TooltipComponents.appendOnce(List<Component>, Component)` appends a line only when the same component is not already present. Existing descriptions, ordering and formatting are preserved. Callers own item matching, translation keys, styling and feature/config checks; the library has no dependency on the consuming addon or optional mods.

# Empty sublevel bounds and beacon compatibility

Sable tracking packets and plot assignments preserve inverted empty bounds, including `BoundingBox3i.EMPTY`. Empty schematic bodies remain empty on clients until their first occupied bounds arrive. Occupied coordinates, packet sizes, saved data and public method signatures are unchanged. The integration targets the existing Sable 2.0.3 and 2.0.6 bounds APIs.

When Contraption Lights is installed, its supported `SubLevelBeaconBeams.discover(ClientSubLevel, LevelPlot)` routine uses `SubLevelBlockEntityCollector.getBlockEntities(body, BeaconBlockEntity.class)`. Discovery visits loaded plot contents instead of walking every chunk address inside the bounding rectangle. It retains bounds filtering and leaves beam creation, colour, refresh, removal and caching with Contraption Lights. The optional injection is skipped when that routine is absent. The library does not require Contraption Lights.

Digisable remains an addon consumer of `SubLevelSchematicBuild`; worker presentation, permissions, materials and construction pacing stay with their existing owners. No new public API is required.
# Clock and schematic placement APIs

`ClockTime.world(long dayTime)` returns a 24-hour clock with Minecraft's six o'clock day origin. `ClockTime.real(Instant, String timezone)` uses Java region or UTC/GMT zone IDs directly. Region IDs retain daylight-saving rules. The existing three-argument overload remains available for API compatibility; its signed hour offset must be finite and within -18 to 18 hours. `text()` formats `HH:mm:ss`; `hour()`, `minute()` and `second()` expose numeric components. `serverTimezone()` returns the JVM's configured zone ID.

`TimeSource(LongSupplier dayTime, Clock clock)` supplies live world time and an injectable wall clock. Hosts expose it through the additive `GraphHostServices.TIME` service. `TimeGraphNode.definition(namespace)`, `presentation()`, `initializeTimezone(data, serverTimezone)`, `inputs(data)` and `execute(context, inputs)` provide a reusable Time node. The consumer owns its namespace and registration. Modes are World and Real; Real exposes only the timezone input and ignores legacy offsets. `RETIRED_INPUTS` identifies the removed offset port: hosts should call `GraphNodePresentationRegistry.removeInputs` for these ports and remove their incoming wires and host-specific retained values when loading saved graphs, including functions. Outputs are time, hour, minute, second and valid. Missing hosts or invalid settings return an empty time and valid=false. Initialize new nodes with the authoritative server zone; transfer that zone to client editors instead of reading their operating-system default.

`GuiDropdownControl.filterOptions(options, query)` provides the existing dropdown search as a reusable library API. It matches trimmed, case-insensitive substrings while preserving labels and order. Empty queries return all choices; missing choices return an empty list. The ACC enables its existing search field for the Time node's timezone selector and delegates filtering to this API.

`CuckooClockDataAdapter` adds a read-only string `time` output for Create cuckoo clocks while retaining existing data adapters. It follows the owning world's day time and Create's accelerated clock in dimensions without a natural day cycle. The consuming mod registers the adapter through `BlockEntityDataAdapterRegistry`.

`SchematicPlacementScope.enter()` marks a synchronous copy operation and restores enclosing state on close, including exceptions. `isActive()` distinguishes Create schematic or vanilla structure placement from normal saved-block loads and dropped-item placement. Registered library mixins establish this context around those existing placement APIs. Consumers remain responsible for converting their own storage references into portable copies; the library contains no addon database implementation. Existing saves, public APIs and item restoration remain compatible.

## Ship entrance discovery

- `ShipDoorApi.discover` and `exteriorFacing` trace both halves of full-size doorways to the owning hull's bounds using block collision shapes. Steps, floor coverings and panels beside the opening do not hide a hull door. Other full-size doors remain closed barriers during discovery, and internal doors behind hull walls remain excluded.
- Native `DoorBlock` implementations, tagged doors and modded blocks named as doors with facing and open state properties are supported across namespaces. The generic name fallback does not require vanilla's half property. `DoorAdapter` retains a full-size door mod's custom root, orientation and native animation or multiblock operation. Trapdoors and fence gates are excluded from dock door control, including trapdoor names without vanilla tags or inheritance.
- North follows the SCM-supplied forward vector. East follows forward crossed with up. Door placement facing does not reverse the hull side. Existing cardinal mask values remain compatible.
- Native door operations preserve their existing animation and paired-door behavior. The generic two-part fallback updates both halves. Consumers use `ShipDoorControl` to apply live dock selections and close doors before native connector release.
- `ShipDoorApi.isFullSizeDoor(BlockState)` exposes the common full-size door predicate for configuration pickers and server validation.
- `ShipDoorApi.Reference(UUID subLevelId, BlockPos pos)` retains a player-selected block. `selected(root, bodies, selection, origin, forward, up)` maps only those selected doors. `origin`, `forward` and `up` use the root body's plot frame. Either half can be selected; duplicate halves are normalized to one door. Classification uses the door plane and its position relative to `origin`, with `forward` defining North. This API bypasses automatic exterior and obstruction discovery; the player explicitly identifies the hull doors. Trapdoors, unavailable bodies and non-door blocks remain excluded.

### Dock traffic braking and capture

- `DockTrafficGeometry.clearance(List<Vec3> route, double range, List<AABB> relativeHull, Collection<Obstacle> obstacles)` measures stopping room along an ordered lane, including bends. Hulls are relative to the tracked connector; obstacles use current world bounds. Initial overlap permits translation away from a follower behind the hull. Motion into an overlapping hull still returns zero clearance, and later route legs remain checked. Duplicate controls do not create a zero-length blocking leg. Physically separate lanes remain usable.
- `ScmCommandRouting.allowsCollisionAvoidance` excludes `ship_dock` from autonomous avoidance. Connector capture and reverse back-out retain docking control rather than escaping their intended platform. Existing method signatures remain available.
- `RouteTrafficPriority` retains the physical order of a following queue through short corrections toward the lane centreline. A lead vessel does not yield to a follower because the correction temporarily points across the lane. Close followers still yield to their leader; opposing and crossing traffic retain coordinated right-of-way.

## Input capture requests

`com.rieno.gadgetsandgizmos.lib.control.InputCaptureState` retains an immutable snapshot of source IDs mapped to input IDs. `replace(Map<String, String>)` returns whether the requests changed. `captures(input)` remains true until the last source requesting that input releases it; `requestedBy(source)`, `requests()` and `inputs()` expose the current snapshot.

`write()` and `read(CompoundTag)` serialize a complete snapshot for the owning mod's transport. Update the state on its owning game thread. The consuming mod evaluates its capture conditions, authenticates sessions, synchronizes changes and controls resets separately from capture. This API has no dependencies on addon controllers or client input classes.

## Weighted model colour gradients

`WeightedColorGradient` in `com.rieno.gadgetsandgizmos.lib.color` accepts an ordered list of `Color(rgb, amount)` values. Amounts must be non-negative; zero amounts are omitted. `bands()` returns each colour's normalized start and end according to its share of the total amount. `sample(progress)` blends through the band centres and shared boundaries. Empty gradients have no bands; a single colour remains solid.

`PaletteDyeStorage.gradient()` builds this gradient from the sixteen stored dye counts in vanilla dye order. Glow ink does not affect colour proportions. Existing storage and preset serialization are unchanged.

`GradientBakedModel` in `com.rieno.gadgetsandgizmos.lib.client.render` wraps a baked model, a target atlas sprite identifier, a `Direction.Axis`, start/end coordinates, and a weighted gradient. Coordinates use the baked model's block units before item centering. The wrapper splits matching textured faces at band boundaries and centres, preserving UVs, vertex opacity, face direction, shading, and ambient occlusion. Other faces pass through unchanged. Empty gradients hide matching faces. Retain a wrapper while its model and gradient remain unchanged; create a new wrapper after a resource reload or gradient change. Item render passes retain the gradient wrapper. Rendering remains client-only and has no addon dependencies.
