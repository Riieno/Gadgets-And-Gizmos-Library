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

## Banded item fluid previews

`BandedFluidMesh.build(bounds, up, fill, colors)` in `com.rieno.gadgetsandgizmos.lib.geometry` creates solid colour layers inside an axis-aligned box. `colors` uses `WeightedColorGradient` as an ordered collection of weighted colours; mesh colours are never interpolated. Each layer retains its share of the liquid volume at every orientation. `fill` is a finite fraction from zero to one; values below one leave headroom with a closed free surface. Empty colours or zero fill produce no faces. Faces expose immutable vertices, outward normals, a texture projection direction, and one RGB colour. This API does not change storage or preset data.

`FluidSurfaceMotion` in `com.rieno.gadgetsandgizmos.lib.client.render` follows a supplied local upward direction with damped motion. `update(target, time)` accepts render time in ticks and returns a normalized surface normal. Identical times do not advance motion; long gaps and time resets settle immediately. `localUp(modelView, viewUp)` converts an upward direction from view coordinates into item coordinates, including item scale and GUI reflections.

`ItemFluidPreview<K>` in the same client package replaces one baked liquid cuboid selected by its atlas sprite and bounds. Construct it with the owning mod's sprite identifier, uncentered baked model bounds, and fill fraction. Call `render` with a stable item owner, display context, original baked model, weighted colours, emissive flag, render time, poses, buffers, Create partial item renderer, light, and overlay. The shell retains ordinary lighting. The liquid retains the original face UVs, renders crisp layers, tilts with camera/item orientation, and settles after movement. Emissive liquid uses Create's glowing item shader at full brightness. Owner states are weakly retained, contexts animate separately, and new baked model identities invalidate cached geometry after reloads. Use only for an axis-aligned, six-face liquid cuboid and call on the client render thread.

The addon now uses this API with 85% visual fill to leave room for surface motion. Stored glow ink enables emission for the liquid; it contributes no colour band. Dye amounts continue to determine the relative layer volumes, independently of this visual headroom.


## ACC runtime sessions and snapshot optimisation

Reusable APIs belong to `com.rieno.gadgetsandgizmos.lib`; consumers supply graph hosts, value codecs and world adapters.

- `GraphDraftSession<G>` owns revision checks, continuous owner-thread ticks and session closure. `Host<G>` reconciles mutations and executes the graph without requiring a save.
- `GraphRuntimeJournal<V>` publishes ordered input, output and variable changes, removals and individual execution pulses. Each event retains its sequence, server tick, graph revision and player context. A reset explicitly indicates a new stream or expired history; it restores current values and does not fabricate old pulses.
- `GraphRuntimeNbt` serialises journal updates using caller-supplied value codecs.
- `CompoundTagDelta` transfers changed compound fields and changed list entries. Apply patches only after validating the expected document revision. The target is caller-owned; malformed patches should be rejected atomically by the host.
- `CompoundTagHistory` retains one complete document and differences for older revisions. Materialised revisions are detached. The ACC preserves its existing saved-history format.
- `GraphEvaluationState.fork` borrows owner-thread state and detaches only accessed values. Diagnostic writes and removals stay within the evaluation. Existing snapshot entry points remain available for compatibility.
- `GraphEventScheduler.visitImmediate` and `visitScheduled` visit queued work on its owning thread without allocating queue copies. Visitors must not mutate the source scheduler.

The ACC sends unsaved graph patches to a menu-owned server session. Matching execution graphs observe the applied runtime. Diverging drafts inherit compatible state and queued continuations through direct runtime access, then run independently. Stale mutations are rejected; closing the menu releases the session. Keyboard input to unsaved drafts is isolated from applied bindings.

These changes do not replace ship approach/departure guidance, saved recovery state, worker reservations, or remote geometry tracking. Physical effects in draft execution remain subject to the existing simulation policy; this is not a separate physics-world predictor.

`DocumentHistory<T>` adapts compact tag history to detached editor documents through host codecs. `push` creates a checkpoint, `pop` restores it, and collection iteration and `addAll` preserve newest-to-oldest restore order. ACC undo/redo uses this API.

### Validation and remaining scope

- Library clean build: 121 unit tests passed, including mutation revisions, repeated same-tick pulses, isolated evaluation, list insertions/removals, compact history, undo/redo and history restoration.
- Main clean build and ACC/presentation GameTests: 78 required tests passed, including applied-state inheritance, unsaved counters, keyboard events absent from the applied graph, cancelled continuations, saved-history compatibility and isolation of simulated dock commands.
- Dock/door suite: all 29 required tests passed in a fresh isolated world. Repeated testing also exposed intermittent Sable fixture/body-removal and native shutdown failures. A physics fixture failure reproduced with the original ACC sources in an isolated build; this pass does not change production ship approach/departure guidance.
- Existing initial-state, recovery, worker and SCM preview snapshots remain. External event sources retain their existing controller routing. Draft-only external listener coverage and a complete model of physical world effects are outside this pass; full simulation parity and complete snapshot removal are not claimed.

## Density layers and temporary fluid mixing

`DensityColorPalette` in `com.rieno.gadgetsandgizmos.lib.color` accepts up to sixteen distinct RGB layers with non-negative amounts and finite visual densities. It omits empty layers and orders the rest by descending density, from the bottom toward the surface. `gradient()` exposes the same volume weights used by `BandedFluidMesh`; it does not interpolate layer colours. `mobility(color)` makes dense layers resist mixing, and `mixedColor(color)` returns an amount-weighted colour from the layer and its immediate neighbours.

`PaletteDyeStorage.densityPalette()` assigns each dye's vanilla index as its visual density. Higher indices settle lower. Glow ink enables emission in the addon and contributes no layer. `gradient()` and the saved storage/preset formats retain their existing behaviour.

`DensityFluidMotion` in `com.rieno.gadgetsandgizmos.lib.client.render` accepts local upward gravity, view-space item position, and time in ticks. The overload accepting world orientation also stirs on horizontal turns; opposite quaternion signs describe the same orientation. It runs at twenty updates per second with at most four catch-up steps per presentation. Its immutable `Frame` interpolates surface direction, two damped wave amplitudes, and temporary mixing. Changes of orientation and movement excite the effect; a stationary settled preview sleeps. Repeated timestamps do not advance it, and long gaps reset it instead of replaying missed motion.

The additive `ItemFluidPreview.render` overload accepting `DensityColorPalette` enables this behaviour. The existing `WeightedColorGradient` overload remains available for plain bands. The density renderer skips fluid work outside the view, refreshes each changing base mesh at most twenty times per second, and allows at most eight mesh refreshes per render frame across preview instances after initial presentations. It caches texture projections and wave weights. Matching mesh topologies interpolate their positions; topology changes use the new clipped mesh directly. Moving liquid uses a small tessellated mesh, bounded to fewer than 4096 vertices for the supported sixteen-layer box. Settled liquid uses the smaller original band mesh.

Waves are a visual approximation: a shared bounded deformation keeps all layers inside the bottle, while vertex colours blend temporarily and return to solid bands after settling. Base layer volumes remain amount-weighted; there is no change to dye inventory. The implementation uses ordinary Java and Minecraft/Create rendering, without vendor-specific GPU extensions or a new shader requirement. Emissive liquid continues to use the existing glowing item material independently of shell lighting.

`FluidPreviewBenchmark` under the library's test sources can be run as a Java main with the test runtime classpath. It measures mesh preparation, cached vertex submission, and settled motion CPU time after warmup. It does not measure GPU time or guarantee an FPS result.


## Loaded-only block access and external graph inputs

- `LoadedBlockAccess.blockState(level, subLevelId, pos)` inspects an already available world or sublevel chunk. `null` means unavailable; air is a valid available result. A missing explicit sublevel never falls back to the world.
- `LoadedBlockAccess.blockEntity(...)` returns only an installed, nonremoved entity or matching sublevel actor. The typed overload also checks its class. These calls do not load chunks, retain tickets, wait for futures or construct block entities. Use them on the owning game thread for passive monitoring.
- `LoadedBlockEntities.get(level, pos)` now uses `getChunkNow`, avoiding waits on pending chunks while retaining its installed-entity contract.
- `CompoundTagTraversal.visitPostOrder(root, visitor[, descend])` visits nested compounds and returns the number for which the visitor returns true. The visitor can mutate its own compound after its children have been visited; it must not mutate ancestors or sibling containers. The optional `(parent, key)` predicate prunes schema branches. Primitive lists are skipped.
- `GraphInputEvents<V>` forwards individual external events with their value and player UUID to independent consumers. Publish and subscribe on the owning thread; each returned subscription closes independently and idempotently. The supplied error handler receives observer failures and should not throw. Mutable values must be copied by consumers that retain or mutate them.

## World-space fluid inertia and concentration transport

The density overload of `ItemFluidPreview.render` now samples world position and orientation through `FluidMotionInput.capture`. Camera-relative held-item transforms are combined with camera position and rotation, so strafing and ordinary yaw turns produce inertia even when the held pose is unchanged. World positions retain double precision. GUI coordinates do not drive physical translation. First-person state is retained by player and hand, surviving replacement item-stack objects; model reloads and player changes reset it.

`DensityFluidMotion.update(FluidMotionInput)` differentiates world translation into velocity and acceleration, transforms forces into the container's coordinates, and includes signed angular motion. Effective gravity drives the surface, with counter-impulses on stopping and damped motion afterwards. Existing vector and orientation overloads and the six-component `Frame` contract remain available. Updates stay capped at twenty steps per second with four catch-up steps. Teleports, long gaps, and time resets reset inertia.

`DensityFluidField` is a client-only, fixed 6 by 8 by 16 concentration grid for up to sixteen dyes. It transports dye concentrations through recirculating flow, normalizes each cell, corrects numerical dye loss, and relaxes each dye toward its density-ordered target. Dense colours settle faster. `update(frame, time)` returns whether the field changed, `sample(x, y, z, alpha)` reads the interpolated shared colour, `fraction(color)` reports its represented amount share, and `settled()` permits suspension. Construct it with local bounds, positive fill, and a density palette. Call it on the render thread. This is a bounded visual fluid approximation and does not change stored dye inventory.

While disturbed, the preview renders one continuous liquid surface sampled from this shared field. Colour is transported across former layer boundaries instead of tinting isolated band polygons. Geometry, colours, and motion interpolate between updates; contact points at bottle walls stay fixed so the free surface remains closed. Once the field and inertia settle, rendering returns to exact solid density bands. Concentration arrays are allocated only after motion starts. The existing emissive liquid material remains separate from ordinary shell lighting.

`BandedFluidMesh.Projection` exposes the volume mapping used for both band geometry and concentration targets. Construct it from bounds and an upward direction; `height(fraction)` gives the corresponding plane and `fractionAt(x, y, z)` gives the volume share below a local point.

Validation includes camera-relative poses with world strafing at large coordinates, yaw-only turns, stop impulses, transport across a resting dye boundary, amount conservation, density recovery, held-stack replacement, closed wall contacts, and emissive rendered vertices. `FluidPreviewRenderCheck` exports actual rendered vertices over a repeatable movement/settling sequence for visual inspection. `FluidPreviewBenchmark` now measures the concentration field and continuous mesh together.


## Independent signal receivers and runtime projections

- `RedstoneLinkReceiver` owns a passive Create redstone-link subscription without requiring an applied controller channel. Supply its local block position, call `bind(rootLevel, first, second)` on the owning server thread, and read `strength()` in the range 0?15. Frequency and dimension changes release the previous registration; `close()` is idempotent and releases the receiver. Closed receivers cannot be rebound. It implements the existing `DistanceIndependentLink` contract, matching controller frequency routing, and never transmits power.
- `BlockRedstoneSignals.readNeighbours(level, pos, face)` reads one incoming neighbour, or the strongest surrounding source when `face` is null, using the existing physical-source signal interpretation.
- `GraphRuntimeProjection<V>` consumes a shared journal update with per-channel personal overrides and produces an independent ordered observer stream. It retains source cursors and personal overrides instead of copies of shared port maps. The supplied lookup restores the current shared value when an override disappears. Pass metadata changes to advance the stream even when no port changes. Read `sourceStream()` and `sourceSequence()` for the next shared journal request, and retain the projection's previous `sequence()` as the outgoing base. Resets establish a new stream and current baseline; repeated pulses retain their tick, revision and player.
- `GraphRuntimeReplica<V>.accept(base, update, pulseConsumer)` applies an ordered update and delivers each pulse separately. Gaps require an explicit reset; duplicates are rejected. `values(channel)` is a read-only live view. `clear()` requires a new baseline. Keep values immutable while retained by a journal, projection or replica, and use all three APIs on their owning thread.
- `GraphRuntimeJournal.read` now walks only the new event suffix for an up-to-date reader while preserving execution order.
- `UuidStringTags.write/read` encodes UUID-keyed string metadata; reads skip malformed identifiers.

The regression render can also be replayed by the test-only `FluidPreviewGpuCheck` main using the exported JSON and a hidden OpenGL 3.2 context. It checks depth/culling and vertex colour interpolation without opening or modifying a game world. The current validation passed all 85 frames on the local GPU. This verifies the emitted geometry and colours; it does not measure live Minecraft frame rate or establish performance on other GPUs.


## Runtime status and ordered document publication

- `GraphRuntimeJournal.Channel.STATUS` publishes host-defined node status values through the same ordered stream. Status removal uses a null value, and resets restore current statuses without inventing execution pulses.
- `CompoundTagPublication.publish(document[, acknowledge])` returns a detached update only when fields change, or when an explicit acknowledgement is requested. It retains one baseline and applies patches in place, preserving unchanged subtrees. An acknowledgement of unchanged data sends an empty delta. `reset()` starts a fresh baseline stream. `Update.toTag/fromTag` serializes the stream, base, sequence, full flag and patch.
- `CompoundTagReplica.accept(update)` rejects missing or replayed updates and merges accepted changes. `document()` is a live owner-thread view for reading; callers must not mutate it. `clear()` requires another full baseline.
- `CompoundTagDelta.affectsPath(patch, path...)` identifies changes to a field or replacement/removal of an ancestor, allowing clients to avoid rebuilding unrelated presentation data.
- `GraphInteractionOutputs.visit(visitor)` visits retained submissions with their player ownership without copying the map. Do not mutate its entries while visiting. Existing snapshot APIs remain available for compatibility.

## Palette-preserving fluid agitation and quicker settling

`DensityFluidField.samplePalette(x, y, z)` is an additive colour-sampling API. It selects an original dye colour using the transported concentration weights and a coherent spatial pattern. It produces moving colour patches without averaging dyes into new RGB colours. The existing `sample(x, y, z, alpha)` method continues to expose averaged concentration colour for other consumers.

The density item preview now uses palette sampling and assigns one source colour to each small rendered primitive. Its temporal colour selection also retains a source colour, so vertex interpolation does not turn agitated dyes into muddy averages. Fluid motion, concentration transport, stored dye proportions, density order, and glow-ink emission are unchanged in purpose.

Mixing now decays by 22% per simulation step rather than 3.5%, wave damping is stronger, and density restoration is faster. The repeatable camera shake render returned to exact solid density bands 2.5 seconds after stopping. Regression checks require field recovery within two seconds after flow stops, complete motion/field recovery within four seconds after the camera stops, and palette-only colours during agitation and colour transitions.

## Continuous fluid colour edges and native emission

`DensityFluidField.sampleSurface(x, y, z)` is an additive sampling API for moving fluid surfaces. It preserves source dye colour cores and blends only where the leading concentration scores approach one another. It is a continuous position-based function shared by adjacent faces. Existing `sample` and `samplePalette` APIs retain their behaviour.

`ItemFluidPreview` now uses these shared vertex samples and interpolates colour updates smoothly. Flat per-primitive colour switching was removed. Dynamic surface tessellation is denser, and clipped faces no longer morph between unrelated polygon layouts when an intersection crosses a box edge. Wave motion still interpolates. These changes remove triangle-shaped colour seams and prevent folded intermediate faces. The recent quicker settling and density ordering remain in effect.

Both density and plain-band previews now select Minecraft's native `entityTranslucentEmissive` material on the block atlas when emission is enabled. This replaces Create's custom glowing item material. The native emissive shader does not depend on the lightmap sampler; ordinary fluid retains the normal item material and world lighting. Shell rendering and dye storage are unaffected.

The test-only `FluidPreviewMaterialGpuCheck` loads the actual Minecraft ordinary and emissive shader sources from a supplied client resource jar. It replays exported fluid vertices in a hidden OpenGL context with a black lightmap and validates that ordinary fluid darkens while emissive fluid remains visible. Shared-vertex colour continuity, palette colour cores, narrow smooth edges, and fractional-frame surface bounds also have regression coverage. This is shader and geometry validation, not a live Minecraft session.

## Full-bright fluid depth and one-second resting transition

`ItemFluidPreview` keeps ordinary and emissive liquid vertices in `Sheets.translucentCullBlockSheet()`, alongside the barrel shell. Emissive vertices use `LightTexture.FULL_BRIGHT`; the shell keeps its supplied world light. This preserves depth writes and sorting instead of drawing a separate depthless glow pass that a later shell draw can cover.

`DensityFluidMotion.restProgress()` returns zero while the container moves and reaches one after twenty game ticks without movement. Residual surface tilt and waves ease out during that second. New movement cancels the return and continues from the visible surface.

The dynamic liquid mesh includes the resting dye boundaries. Its vertex colours ease into those exact band colours over the same second, allowing the cached resting mesh to take over without a colour or geometry jump. Subdivision counts scale with each face's size, and the completed transition releases the dye field.

Validation covers fractional render times, the final band colours, renewed motion, full-bright vertex light and delayed front/rear shell rendering. The standalone GPU check reproduces the previous depthless glow failure and checks the corrected item material with a dark lightmap except for its full-bright texel. It does not replace a live modpack test.

## Selected-colour dye recipes and shared payment checks

`DyeColorRecipe.recipe` charges one matching dye for an exact `DyeColor.getTextColor()` colour, including solid `FF0000` as one red dye. Other RGB colours retain the two-dye quarter-step approximation, reduced to the smallest whole ingredient ratio. Gradient and cycle recipes use the distinct colours in their stops; repeated stops do not charge the same colour again. `additional` continues to charge only positive ingredient differences from the confirmed preset. Glow ink remains a separate cost when enabling additive blending.

`PaletteDyeStorage.canPay` and `pay` require only the consumed ingredients. They permit spending the last required dye and no longer require reserves of unrelated colours. Existing method signatures and stored item data remain unchanged. The additive `canPay(int[] cost, int glowCost, IntUnaryOperator available)` overload applies the same validation to a synchronized count view, so the applicator screen and server agree. Dye and glow payments remain atomic.

The applicator requirement list and missing-count highlights show only actual ingredient shortages. Its English labels no longer instruct players to keep a reserve of every dye. Regression tests cover all sixteen exact palette colours, custom mixtures, repeated gradient/cycle stops, previously paid presets, one-red-only payment, glow costs and failed payments.

## Planar screen geometry

`com.rieno.gadgetsandgizmos.lib.geometry.PlanarSurface` is a common-side rectangular screen API. Supply corners in texture order: top left, bottom left, bottom right, top right. Corners must describe a nondegenerate rectangle.

- `transform(UnaryOperator<Vec3>)` maps all corners without changing their texture order. Preserve rectangular geometry when using pointer projection.
- `rotate(Quaterniondc, Vec3)` rotates the screen around a model pivot.
- `normal()` returns the outward normal implied by the corner order.
- `project(Vec3)` returns normalized `Point(x, y)` coordinates, allowing a 1.5% border tolerance; outside or degenerate projections return null.
- `intersect(Vec3 start, Vec3 end)` intersects a finite ray segment with the screen front. Back faces, parallel rays, intersections outside the segment, and points outside the screen return null.

Consumers own model IDs, block properties, wrench behavior, panel dimensions, and model pivots. Use the same transformed surface for rendering and pointer intersection. The library has no addon dependencies or client-only classes in this API.

## Ordered docking approach progress

`SplineRouteTraversal.remainingDistance(Vec3 position)` reports unvisited travel from the retained ordered cursor. Before capture it includes travel to the entry and the complete spline. After capture it includes the remaining arc distance and separation from the current segment. It never projects onto a later crossing or updates the cursor; call `update` through normal guidance first.

Schedule consumers can use this distance for progress watchdogs on long, curved or vertical approaches. Rebase a watchdog when switching from direct navigation to this metric, and suspend its stall timer while lane traffic requires a stop. Existing traversal methods and saved route keys remain compatible.
## Base-centred docking route tracking

`SimulatedDockingApi.basePosition(Vec3 tip, Vec3 facing)` and `tipPosition(Vec3 base, Vec3 facing)` convert between a Simulated connector's base block centre and its extended contact point, 1.5 blocks along its facing. Inputs share one coordinate frame; facing is normalized. These pure methods do not inspect extension state or modify blocks.

`DockTrafficGeometry.assess` and `refreshApproach` have additive overloads accepting a final `dockingPosition`. Supply the final position of the point used to track the route, with hull bounds relative to that same point. The connector tip still defines the authored network frame. Existing overloads retain the tip as their docking position.

`DockingDeparturePlan.select` has an additive overload accepting `departurePosition` after `arrival`. This separates the tracked vessel origin from the connector frame used to resolve lanes. The returned plan's existing `tip()` accessor and saved frame origin hold this supplied departure position; lane geometry and clearance endpoints stay in their original world frame. Existing overloads retain their original tip-based behaviour.

Consumers choosing a base-centred frame must use that origin consistently for telemetry, point velocity, traffic footprints, progress and completion. Convert to the native contact-point frame when handing control to a tip-targeting docking API. The addon uses native contact-point tracking for both authored lanes and physical docking.
## Storage network boundaries

`ContainerStorageNetworks.connected(entry)` and `connected(entry, viewer)` now restrict each controller's linked storage to its own root dimension and containing SubLevel. World storage cannot join SubLevel storage, and unrelated SubLevels cannot share a pooled network. This applies before overlapping networks merge, including player-facing manifests and cached item, fluid and FE automation. Existing saved links and identifiers are retained.

The additive `track(source, storage, relatedSubLevels)` overload accepts two non-capturing adapters. `storage` supplies loaded physical endpoints. `relatedSubLevels` supplies the current loaded bodies belonging to the source's physical parent-child assembly; the source's own body is always allowed. Return an empty collection when no related bodies are available. This adapter must describe physical assembly ownership, never controller links, loading dependencies, proximity or docking peers. It is evaluated only when linked storage includes another SubLevel. All callbacks run on the owning server thread. Existing `track` overloads remain available and default to the source's exact world or SubLevel. Call `invalidate()` after explicit storage or assembly link changes requiring a same-tick refresh; ordinary topology refreshes each tick.

The addon supplies its existing filtered ship assembly discovery, including optional articulated child relations, through this overload. Docking remains an explicit resource transfer bridge between separate networks: locked physical connector pairs retain their bidirectional item, fluid and FE transfer paths. Docking does not merge those networks into one pool.

## Native connector tracking on docking lanes

Configured approach and departure lanes use the same native Simulated connector contact point as direct docking. Enabling a lane no longer switches telemetry from that point to the base block centre. Route following, point velocity, braking, traffic footprints, docking sweeps and endpoint completion now share this frame. Departure back-out targets are used directly; they no longer receive a second 1.5-block offset before joining the exit spline.

`SimulatedDockingApi.basePosition` and `tipPosition`, and the additive tracked-origin geometry overloads, remain available for callers explicitly using a base-centred frame. The addon docking flow uses the native contact-point frame consistently. Authored lane coordinates and saved routes are unchanged.

## Provisioned lane choice and curved arrival

`DockTrafficGeometry.refreshApproach` re-evaluates an uncaptured approach against current hulls and forecast traffic, preferring the primary lane when both are clear. A route provisioned during an earlier departure is no longer committed before capture. Captured routes retain clear ordered travel and the existing connected-continuation restrictions.

`SplineRouteTraversal.arrived(Vec3 position, double tolerance)` requires capture and compares the ordered remaining distance, including cross-track separation, with a finite non-negative stopping tolerance. Arrival can occur before the final sampled segment on dense curves. A nearby endpoint or later crossing cannot bypass unvisited travel. Update the traversal through normal guidance before checking arrival. Existing traversal methods and saved cursor fields are unchanged.

The addon uses this arrival API to hand configured approach lanes to both schedule and graph docking. When a provisioned lane changes, its active command endpoint changes with it.

## Physical hulls in coordinated traffic

`RouteTrafficPriority.Participant` has an additive constructor accepting `List<AABB> hull` after `expectedSpeed`. Supply detached world-axis bounds relative to the same tracked point used by `position` and `route`. The original eight-argument constructor remains available and retains conservative radius-based behavior. Hulls and route intents are transient; saved routes and schedule identifiers are unchanged.

When both participants publish hulls, coordinated right-of-way uses the first actual shared swept region rather than a spherical proximity estimate. The stopping distance already includes the hull extent and configured clearance buffer, so the vessel radius is not subtracted again. Queues require overlapping transverse hull corridors; a future merge cannot create a current queue between separated lanes. Lead vessels retain priority over followers. Separate routes without shared hull space contribute no yielding dependency, regardless of dock or schedule.

`DockTrafficGeometry.sweptIntersection(start, end, hull, otherStart, otherEnd, otherHull, margin)` returns the earliest directed `SweepIntersection` with both route fractions and anchor positions. Hull bounds share the same world-axis frame and are relative to their respective route anchors. The finite non-negative margin is applied once between hulls. This reuses the existing swept-hull clipping geometry used by `routesConflict`.

The addon publishes hulls with its live route intent. Failed departure admission retains undocking and altitude support instead of falling through into another route. Schedule departure state can be reconciled after an interrupted handoff only when no departure owns motion, the physical connector is released, and the complete hull has cleared the previous docking sweep.

## Dock-clearance recovery and displaced lane sweeps

`SplineRouteTraversal.remainingControls(position)` now starts at the supplied physical position, follows its correction to the retained segment projection, and then includes the unvisited directed controls. An obstacle at a distant projection therefore cannot report zero stopping distance for a ship displaced above or beside the lane. The correction remains subject to hull collision checks. This query does not alter capture, segment, fraction or ordered remaining-distance state.

The addon uses `DockingDeparturePlan.clearanceBounds(hull)` for configured final docking reservations. This protects the connector-to-lane axis rather than reserving the ship's remote navigation path. Its clearance maintenance rebuilds this local sweep from the saved plan and live hull before testing release, including after loading an oversized reservation from an older save. A separated ship releases the old berth and clearance automatically; a locked ship retains its physical occupancy, and a prepared departure retains the docking sweep until its hull clears it. Existing NBT keys, authored lanes and schedules remain unchanged.


## Departure admission and pending resource transfers

`DockingDeparturePlan.selectReady(...)` chooses an available departure crossing using the supplied braking acceleration and current movement speeds. Ships already inside the local merge, or too close to stop before it, must clear before admission. Traffic farther back can be stopped by the admitted reservation.

`reservationControls(relativeHull)` and `reservationBounds(relativeHull)` cover the reverse docking leg and a hull-sized prefix of the ordered exit lane. They leave remote parts of the route unreserved. Keep the chosen plan and connector identity through departure, and release the merge reservation once the physical hull has cleared it without discarding the remaining exit traversal. Existing `clearanceBounds(...)` retains its docking-axis semantics.

`departureBlocker(hull, obstacles, traffic, brakingAcceleration)` reports the physical obstacle or committed movement preventing that admission. Publish actual movement speeds for stopping checks.

`DockTrafficGeometry.routePrefix(route, distance)` exposes the existing ordered distance clipping operation for local reservations.

`ResourceRouting.canMove(source, target, itemFilter, fluidFilter)` simulates item, fluid and FE transfers without changing either pool. Shared handlers are excluded. A refuelling host can use this to keep filling engine buffers before accepting a full reserve container as completed refuelling.


## Fleet traffic recovery

`RouteTrafficPriority.resolveFleet(participants, settings)` computes a shared set of right-of-way decisions, resolving each vehicle once and breaking circular yielding from those decisions. Existing single-vehicle APIs remain compatible.

`TrafficCoordinator` shares traffic decisions with physical recovery steering beyond docking lanes. Supply one `Vehicle` per rigid assembly with its current tracked point, relative hull boxes, ordered route, movement permission, observed blockage and permission to detour around immovable obstacles. Docked or parked vehicles must publish `movable=false`.

Call `update(tick, vehicles, settings, EscapeProbe)` once per server level tick, then read `directive(vehicleId)`. `FOLLOW` uses the retained route and the included right-of-way decision. `WAIT` holds while an escape is being checked. `MOVE_ASIDE` supplies a retained `Motion` with a temporary target and the ship whose passage it clears. These targets do not replace saved destinations or advance authored route cursors.

`EscapeProbe.test(vehicleId, start, target)` returns `CLEAR`, `BLOCKED`, or `PENDING`. The coordinator tests other fleet hulls and reserved recovery sweeps first; the host must also validate terrain, native collision shapes and actual propulsion authority before returning `CLEAR`. One probe is issued per level frame and pending probes retain their target. The host still applies immediate physical collision checks while moving.

Recovery begins after a sustained physical stall, preserves ordinary passing traffic, holds the clearing position until the waiting vessel passes, and removes reservations when vessels disappear, routes change or recovery cannot progress. `invalidate(vehicleId, tick)` reopens recovery after the host detects a new obstruction. State is transient and local to one server level; no schedule or saved path migration is required.

## Remote controls and privileged graph commands

- `view.ViewBodyAnchor(Entity)` captures a world position in the entity's tracked or vehicle SubLevel. `position()` returns that point through the current logical pose, retaining the last resolved world position after removal. Create and read anchors on the owning game thread; anchors retain their frame until discarded.
- `view.RemoteViewSessions.open` accepts seated players. Sessions retain their original body frame, leave passenger placement to the vehicle, and return unseated players to the current frame position on exit. Game mode and inventory are unchanged.
- Remote view and authenticated camera replay disable held-item use, block placement and block destruction. Native block interactions and menus remain available. Server movement/use handlers reject ordinary use and destruction packets while a full camera session is active.
- `control.MouseAxisState` owns a bounded finite axis sample and its last movement tick. `expire(tick, timeout)` clears the same accumulator consumed by subsequent movement; expiry also handles clock rollback. Use one state per independent axis and owner.
- `graph.CommandGraphNode.definition(namespace)` defines `<namespace>:command` with `exec` and `command` inputs and `exec`, `output` and `success` outputs. Register its executor through `GraphApi.runtimes()`. The host must expose `GraphHostServices.BLOCK_ENTITY` only during applied server execution. Passive reads retain the last result; previews without a server host have no effects.
- Commands run with permission level 2 at the host's transformed world position. Optional leading `/` is accepted. Feedback and errors are captured as plain text, bounded to 32767 characters; success is true when a command callback succeeds. Commands do not use the triggering player's permissions or require an online operator after installation.
- `graph.edit.PrivilegedGraphEdits.run(ServerPlayer, Runnable)` scopes synchronous graph edit authorization to the server-authenticated player. Permission level 2 is required. Nested scopes restore their caller even on failure; absent authorization fails closed.
- `canReplace(previousTag, nextTag, privilegedTypePredicate)` compares protected nodes, positions, labels, data, incident edges, upstream dependencies and variable defaults. Graph NBT uses `Nodes`, `Edges`, `Variables` and `Functions`; nested protected functions retain the enclosing graph. `contains` recursively detects protected node types. Callers must check replacement before changing graph state or history. All returned comparisons are read-only and retain no caller-owned NBT.
- Consumers own privileged node IDs, editor access policy and authenticated request scopes. The library imports no addon implementation types. Existing node IDs and saved graph versions are unchanged.

## Player graph values and command availability

- `graph.PlayerGraphValue.TYPE` is `player`. `capture(ServerPlayer)` copies authenticated interaction-time information into an immutable `GraphValue` map; the overload accepts an explicit remote-interaction flag. It stores no entity reference. Fields include `uuid`, `name`, `display_name`, `dimension`, `position`, `x/y/z`, `look_direction`, `yaw/pitch`, `velocity`, health, armor, food, movement flags, held item IDs, game mode, experience, score, team and server tick. Existing `EntityTelemetryApi` field names are reused.
- `reference(UUID)` retains an offline identity with `available=false`; a null UUID produces an empty Player. `id(GraphValue)` validates and reads a Player UUID. `resolve(MinecraftServer, GraphValue)` resolves only an online server player. Snapshot fields do not grant permissions and are never used as live authority.
- `PlayerGraphValue.isMapType` recognizes Player maps for field breakout. Player values round-trip through `GraphValueText`, graph variable defaults and nested lists/maps. Consumers may wire Player outputs to map inputs for inspection or to list inputs as a single Player element.
- `chat.ChatPlayers.identifiers` accepts a Player directly or a list containing names, UUID strings and Player values. Invalid Player references remain nonempty restrictive identifiers so they cannot accidentally select an unrestricted audience. Empty legacy lists retain their existing semantics.
- HUD port sections include `PlayerPort` beside each control's execution and value ports. The host assigns stable per-control port IDs and publishes the authenticated event snapshot before following execution.
- `graph.CommandExecutionPolicy` is an optional host contract exposed through `GraphHostServices.COMMAND_EXECUTION`. `CommandGraphNode.execute` reads the policy on every call, clears stale results when disabled and avoids requesting a block-entity host. Hosts without a policy retain the existing enabled behavior.
- `graph.GraphNodeAvailability.canReplace(previousTag, nextTag, disabledTypePredicate)` rejects additional disabled node instances, including nested functions, duplicate copies and changed scoped identities. It permits retention or removal of existing disabled nodes. Permission checks remain the host's separate responsibility.
- The browser `GadgetsGraphNodes.encode(value, declaredType, previousEnvelope)` API owns the shared typed default encoder. It preserves Player envelopes and nested Player elements; `decode` recognizes Player maps. The addon consumes this library API through its bundled distribution copy.


## Registered item picker catalog

- `RegisteredItemCatalog.items()` exposes every registered item and block item except air. `contains(id)` validates a nonempty registered ghost reference without inventory ownership. Use these APIs after item registration, on the client or server.
- `ItemPickerScreen.registryEntries()` and its predicate overload expose the shared registry catalog as sorted painted picker entries, including registry IDs, names, mod names, tags and tooltips.
- `ItemPickerScreen.registrySelection(entries, stack)` finds the selected registry ID even when an existing stack has components. Registry entries represent default item stacks; inventory-specific APIs retain their existing component-sensitive behavior.

## Loaded ray queries and managed attachment frames

- `SableEntityQueryApi.localEntities(level, bounds, filter)` queries only the supplied coordinate space, including NeoForge multipart targets whose parents are outside the queried bounds. Bounds and returned entity positions remain in that space. It does not expand into neighboring SubLevels, load chunks or retain results. Call it on the owning level thread after selecting the intended body. `ViewRaycast` now uses this API to avoid repeatedly expanding each plot query into every overlapping plot; its filters, ray budgets and result format are unchanged.
- `RayBounds.clip(bounds, from, to)` returns the intersecting finite segment or null. It retains inside starts and supports parallel rays. `ViewRaycast` clips plot block traversal to loaded plot bounds and the nearest accepted hit while continuing to test entities in their own space.
- `SubLevelBlockEntityPresence(predicate)` caches positive positions and one tick of negative results behind weak body keys. Values retain no level, body or block entity. `matches(body)` reads only loaded block entities. Predicates should not capture a world or block entity when the cache has a longer lifetime than that world.
- `SableFrameConstraint` now releases managed handles before Rapier removes either SubLevel or disposes the pipeline. `close()` clears Java ownership before native removal and remains safe to repeat. `releaseBody(pipeline, body)` and `releasePipeline(pipeline)` let other backend integrations apply the same lifecycle. Call all operations on the owning physics/game thread, before native teardown. An unchanged target does not rewrite frames or wake bodies; a later update can attach a fresh handle after unloading.
- `CardinalRotation.between(from, to)` supplies a deterministic quarter-turn or half-turn frame mapping. Its constructor requires a nonnull axis and a multiple of 90 degrees; degrees are normalized into 0 through 359. `quaternion()` returns an independent value.
- `SableAttachmentPoseApi.alignedPose(parentPose, childPose, parentAnchor, childAnchor, orientation)` copies the child pose and aligns both attachment anchors, preserving its rotation point and scale. Inputs are not mutated. Invalid attachment frames throw `IllegalArgumentException`. `align(level, parent, child, ...)` moves a loaded detached attachment tree through that pose, resets velocity and wakes the moved bodies. Release the parent joint first. Missing bodies return false; the method never loads them.
- `SubLevelAssemblyApi.disassembleOriented(level, body, origin, anchor, cardinalRotation, playSound)` uses Simulated's normal block, entity and tracking transfer with an oriented block-grid transform. It refreshes plot bounds before transfer; empty plots transfer the origin tracking point without querying sentinel bounds. Its synchronous scope restores an enclosing transform on success or failure. Existing disassembly overloads retain their behavior. `disassemblyTransform(fallback)` is the integration bridge used by the registered Simulated mixin.
- `DynamicKineticPartsVisual<T>` extends the existing colored kinetic visual with owned transformed GPU instances. Subclasses supply model choices through `addPart(model)` and transforms through `animate(partialTick)`. The library handles frame callbacks, lighting, crumbling and deletion of every created instance. Use it only in client packages, and register an appropriate vanilla renderer for backends without visualization.
- The addon owns bearing model offsets, tilt policy, wrench behavior and display face interaction. No addon implementation types or IDs are exposed by these library APIs. Saved IDs, head plot identities, existing NBT keys and network formats remain unchanged.

## Media playback and playlists

- `media.MusicDiscCatalog.tracks(registries)` discovers every registered item whose jukebox component resolves in the current world. `resolve` resolves one item without scanning the catalog. Tracks expose the item ID, sound, translated song description and duration.
- `media.MediaSession` owns ordered, named playlists, selection and playback. It supports create, rename, delete, membership toggles, transport controls and sequential looping. Playlist IDs are UUIDs; playlists retain insertion order. Limits: 32 playlists, 512 discs per playlist, names up to 48 characters. Names are unique without regard to case.
- `MediaSession.advance(gameTime, lengths)` catches up after unloading in bounded work, skips missing discs and loops selected playlists. Standalone tracks stop at their end. Supply current registry-backed track lengths (zero for missing discs). Save and restore with `toTag` and `fromTag`.
- `media.MediaPlaybackState` is the small synchronized timeline: disc ID, playing flag, offset, start tick, duration and revision. Mirror it to tracking clients only when it changes; progress is derived from game time.
- `media.MediaSources.setObserver` connects common hosts to a client backend without loading client classes on a dedicated server. Hosts call `observe` each client tick with their stable source ID, timeline, transformed world position and whether playback is listener-relative.
- `client.sound.MediaPlaybackEngine` streams those observations, seeks on a background executor, downmixes placed stereo streams for positional audio, updates positions and releases sounds when observations stop. Call `tick` each client tick and `clear` when disposing the owning host. It never retains block entities or source levels.
- `client.tablet.PaintedMediaPlayerRenderer(atlas, template)` supplies the shared 320×180 player, search, transport, Playlists sidebar, create/select controls, pen editor and membership checkboxes. The player atlas is 320×400 and the control template is 508×268. The app snapshot is `MediaSession.toTag()` with an optional `Error` string. Actions are `play` (disc item ID), `pause`, `previous`, `next`, `playlist_create` (name), `playlist_select` (UUID or empty for all discs), `playlist_delete` (UUID), `playlist_rename` (UUID|name) and `playlist_toggle` (UUID|disc item ID). The consuming app owns authorization, storage, synchronization, registration and pricing.
- `MusicDiscCatalog.lengths(registries)` supplies a per-operation cached duration resolver for session operations; it returns zero for missing tracks and holds no global world cache.

## Painted vehicle dashboards and shipping resource summaries

- `ContainerNetworkSnapshot.capturePool(pool, type, filter, offset, limit)` captures a read-only, paged summary of an authored `ResourceRouting.Pool`. `type` is `item`, `fluid`, `energy`, or empty for all resources. Search matches names and registry IDs without regard to case. Pages contain `Rows`, `Total`, `Offset`, and `Handlers`; rows expose `Type`, `Id`, `Name`, `Amount`, and `Capacity`. Pool identity deduplication prevents repeated handlers from inflating totals, and summed amounts saturate at long range. Call on the owning server thread with resources the viewer may inspect.
- `client.render.LoadedTerrainMap(owner)` paints a 256-block area using only already loaded client chunks. It samples at most 256 surface positions per client tick, uploads complete 64×64 frames, and requests no chunks. `pixelX` and `pixelZ` project world markers onto its current frame. The caller owns it and must call `close` to release the dynamic texture. Texture IDs use the caller's namespace.
- `client.tablet.PaintedVehicleDashboardRenderer(atlas, mediaApp, mediaTab)` supplies a 320×180 dashboard using a 320×400 atlas. It draws live terrain, position, target, ETA, facing, speed, a fuel gauge, transport controls, an installed-app launcher, ship statistics, and an internal shipping manifest. The client state is `AutoCloseable` for hosts using `TabletAppClientSession`.
- Dashboard snapshots include `Available`, `Ship`, `X/Y/Z`, `Facing`, `Yaw`, `Speed`, `HasTarget`, `TargetX/Y/Z`, `EtaSeconds`, `Fuel`, `FuelInfinite`, `Pilot`, `Cargo`, `Fluids`, and `Energy`. `MediaInstalled` and `ManifestInstalled` gate launcher entries. `Media` is a `MediaPlaybackState` tag for this physical device. The consuming addon supplies authorization, ship selection, telemetry and resources.
- The renderer uses the existing `drive` and `stats` tabs plus an internal `manifest` tab; it never opens the standalone Manifest application. Manifest refresh values are `category|offset|filter`, with categories `items`, `fluids`, and `fuel`. Its `Manifest` snapshot is a resource page plus the echoed `Category` and `Filter`. Navigation actions remain `navigate`, `hover`, and `dock`. Embedded playback uses `media_previous`, `media_pause`, and `media_next`; opening Media uses the host's existing app navigation API.
- `Yaw` rotates the map marker continuously (north is zero, east is 90 degrees). Optional `SampleTick` and `VelocityX/Y/Z` let the dashboard interpolate the ship position for at most one second between server snapshots.

## Stable streamed playback and shared cargo cards

- `client.sound.MediaPlaybackEngine.observe` now collects observations; `tick` applies one selected source per tablet. Call it after client block-entity observations and portable inventory observations. The latest timeline wins independently of location, a portable observation takes priority over a placed copy, and otherwise the nearest world source is used. This prevents Creative copies with the same tablet ID from repeatedly cancelling each other's audio. Pausing the client does not age out placed sources. Failed decoders release their sound channels and retry with a bounded delay.
- `client.sound.PcmAudioStream(source, mono)` adapts signed 16-bit decoded PCM for OpenAL. It retains decoder overread, fills requested buffers with complete frames, converts byte order, optionally downmixes positional audio, and returns null at EOF. `seekTicks(ticks, cancelled)` performs frame-aligned seeks without discarding the retained tail. Call seeks on a worker thread. `close` is idempotent and releases the owned decoder.
- `client.tablet.PaintedItemCard(atlas)` renders the shared 72-by-66 cargo card with the original item icon, count placement and wrapped name. Smart-container manifests and Auto's item manifest both use this renderer. The vehicle dashboard's additive four-argument constructor accepts a separate manifest-contents atlas; its existing three-argument constructor remains available. Auto paginates item cards in four columns, translating scrolled grid rows into server item offsets; fluid and fuel summaries remain separate.

## Heading-up navigation maps

- `navigation.NavigationMapProjection` gives terrain, ship markers, destination pins and route lines one reversible heading-up frame. North is zero degrees and east is 90 degrees. The vehicle remains centred and forward points up. `clip` clips projected route segments before creating GUI geometry.
- `client.render.LoadedTerrainMap.render(graphics, center, heading, x, y, width, height)` is an additive rotating-map overload returning its projection. A 128-by-128 cached terrain texture supplies a 512-block overscan region, sampled in batches of at most 1024 loaded surface positions per client tick. Only complete frames are uploaded. Moving views immediately begin their next frame; stationary views retain the existing refresh interval. The original north-up overload and pixel projection methods retain their 256-block viewing span. No chunk loading is requested.
- `navigation.NavigationMapData` serializes up to 64 nearby markers and 512 route points. Its spline builder samples the actual remaining curve from the retained monotonic cursor over a 512-block horizon; its ground-curve builder samples actual circular steering maneuvers. Planner polylines retain their ordered straight legs. The destination is stored separately so bounded geometry never substitutes an invented chord for the rest of a spline. `routeFrom` trims only the short display prefix while interpolating vehicle motion.
- `physics.SableMapMarkers.capture` uses the loaded Sable spatial index. It excludes caller-owned body IDs, combines connected actors into one craft marker, and sorts nearby craft by distance without loading terrain or SubLevels.
- `client.render.NavigationMapLines` draws clipped route chords using that same projection. The painted vehicle dashboard reads the optional `NavigationMap` snapshot, keeps the own-ship icon fixed, rotates terrain and other markers with the vehicle heading, and draws actual route geometry when supplied. A direct destination line remains available when no retained geometry exists.

## Map marker eligibility

`SableMapMarkers.capture(level, center, excluded, range, eligible)` accepts a `Predicate<? super SubLevel>` supplied by the consumer. Each connected craft selects its marker only from eligible loaded bodies; craft with no eligible bodies are omitted before the nearest-64 limit. The original overload preserves its existing all-bodies behavior. G&G Auto supplies its SCM readiness check so only initialized SCM bodies appear, while the library remains independent of addon controller classes. Eligibility checks use loaded block entities without requesting chunks.

## Continuous navigation heading

`navigation.NavigationHeading.normalize(degrees)` converts signed or accumulated headings to 0 inclusive through 360 exclusive. Each `NavigationHeading` instance tracks a continuous displayed angle: `update(heading, elapsedSeconds)` follows the nearest equivalent turn with a frame-rate independent 100 ms response, preserving continuity through both the signed 180-degree seam and the compass 360-degree seam. The first sample and gaps of at least one second settle immediately; invalid samples do not alter the current angle. `reset()` starts a fresh track after vehicle loss. The painted dashboard uses one tracked heading for terrain, route geometry and ship markers. Projection and GPU rotations reduce accumulated turns before trigonometry to retain precision. G&G Auto now publishes its existing `Yaw` field in the full compass range; readers continue accepting signed values.

## Painted wireless control cards

`tablet.ControlCardStyle` stores reusable Single, Wide and Tall grid spans, RGB appearance and a registered item icon independently of live channel output. Its NBT fields are `Size`, `Color` and `Icon`; missing legacy metadata defaults to a single neutral card. `tablet.ControlCardGrid.arrange(sizes, columns)` deterministically packs spans into free cells without overlap and fills holes with later cards.

`client.tablet.PaintedControlCardsRenderer` consumes a host-supplied atlas, frequency dialog background and `ColorPickerModal.TextureAppearance`. It draws a three-column grid, Controls/Edit/Scan/Refresh sidebar, add/edit/remove controls, draft card preview, name, size, color, type and item icon editors, toggle/button controls and draggable 0-15 sliders. Editable drafts use `TabletAppClientState.saveDraft/loadDraft` and remain separate from incoming channel snapshots. `card_save` carries a bounded SNBT draft with optional channel `Id`, frequency item IDs/colors, `Label`, `Mode`, `Strength` and nested `Card` metadata. Hosts validate and persist this atomically, then acknowledge with `SavedCard` plus `UpdatedAt`, or an `Error` snapshot.

`client.ui.LinkFrequencyModal` shares the existing ACC frame and frequency item picker through `frame(...)` and `itemPicker(...)`. Its embedded host draws the same two ghost slots and 36-slot inventory, preserving selected inventory stack colors/components without moving or consuming physical items. Registry choices reuse `ItemPickerScreen` and `PaintedSelectionModal`; card colors reuse `ColorPickerModal`, and icons reuse the registry picker. Native ACC menu inventory transfers remain owned by the controller menu; only its frame and registry frequency selector call the extracted shared API.

The G&G addon supplies Redstone Link runtime actions, card validation, ownership-scoped SQLite storage and resources. Presentation lives under app data `Cards/<channel UUID>` and channel saves preserve it; removals prune it. Existing serialized channel IDs, frequency fields, modes and runtime output remain compatible. The tablet action value limit is 4096 characters to carry complete card drafts. Common code has no dependency on the client renderer.

When `MediaInstalled` is true, the wireless control grid includes the atlas media card, reads the existing `MediaPlaybackState` snapshot from `Media`, and forwards `media_previous`, `media_pause` and `media_next` to the host. The addon checks Media installation and entitlement before forwarding to its existing playback session; no additional audio engine or signal channel is created.

The card designer retains explicit 0-15 output strength for button and toggle modes as well as slider controls. Invalid values stay in the editor with an error; repeated Save clicks are ignored while its atomic save is pending. The media tile uses the supplied circular transport partial and forwards playback to the existing Media session.

## Control card interaction corrections and clock cards

`client.ui.PlayerInventoryCursor` bridges the active server menu using Minecraft's normal PICKUP clicks. The frequency dialog now picks up, splits and returns real inventory stacks; ghost placement copies one item without consuming the carried stack. Closing the modal returns held items to inventory, and refuses dismissal if inventory cannot accept them. `LinkFrequencyModal` has an additive constructor accepting red and blue slot texture regions and a coordinate-aware release method for dragging held items onto ghost slots. The original constructor and release method remain available. Inventory clicks no longer assign a frequency automatically, and the dialog uses the supplied colored slots instead of A/B labels.

`PaintedControlCardsRenderer` no longer inserts a Media card from installation state. Media and Clock are explicit stored card types. Media title clicks open the installed Media app's player tab; transport continues using its existing session. Empty and single-item frequency pairs are accepted and transmitted through Create; empty item colors canonicalize to -1. Media and Clock cards always have zero redstone output. Button cards show held and released states, and active toggles, pressed buttons and slider fills use each card's RGB color. The sidebar is inset six canvas pixels inside the frame.

`tablet.ClockCardSettings(digital, realTime, timezone)` stores clock configuration and resolves either world day time or real time through Java ZoneId rules. Its NBT fields are Digital, RealTime and Timezone. Invalid zones fall back to UTC. The additive ControlCardStyle.clock() component is stored under Clock, while the original three-argument constructor remains available. The editor offers analogue/digital and game/real time controls; real time exposes a bounded, searchable timezone dropdown using the reusable TabletSearchDropdown. Clock previews use world day time with Minecraft's six-hour offset; real clocks follow the selected zone including daylight-saving rules.

Edit mode supports dragging cards by their non-control areas. Span packing previews the new order, and release sends one card_reorder action containing every card UUID. The addon validates uniqueness and ownership, preserves existing IDs and appearance metadata, and persists the ordered Channels list. Card geometry and interactive controls remain on the existing grid.

## Exact painted-control tint bounds

`GuiTextureRegion.drawRecolored(graphics, x, y, width, height, rgb, sourceRgb)` recolors a painted region relative to its source RGB while retaining the original per-pixel alpha outline and transparent padding. Source channels must be nonzero. Color modulation resets to white after drawing. `GuiNineSlice.centerBounds(x, y, width, height)` returns the same centre rectangle used by its default-scale draw, including reduced borders for small controls.

The control-card switch now recolors its original atlas partial directly. It no longer approximates the silhouette with separate circles and rectangles. Slider fills use the actual nine-slice centre, clipped to the current strength, so they fill the track cavity without covering its border or leaving a mismatched row. The sidebar and its controls are inset three canvas pixels.

## Data-driven tablet app fallback icons

`TabletAppIconStyle` describes the square's RGB color, registered item/block item ID, and `forceFallback` preference. `fromJson` supports partial overrides over supplied defaults; colors use `#RRGGBB`, item IDs use `namespace:item`, and `force_fallback` is a boolean. `toJson`, `toTag`, and `fromTag` preserve these fields for configuration and synchronization.

`TabletAppIconStyles.apply(Map<ResourceLocation, TabletAppIconStyle>)` replaces the current presentation snapshot; `style(TabletAppDefinition)` resolves an app, and `clear()` removes a departed server's settings. Unconfigured library consumers retain their registered icon renderer or texture, using an accent-colored paper fallback when artwork is unavailable.

`TabletAppIcons.render` respects the configured preference across all consumers. Forced fallback draws an opaque colored square with the configured item centered inside. When disabled, registered renderers and available app textures take precedence; missing artwork still falls back. Texture availability is cached and cleared on resource reload. Common settings and synchronization do not reference client implementation classes.

G&G supplies all 14 bundled app defaults in `src/main/resources/createthrusters/default-tablet-apps.json`, with `force_fallback` true for each app. The live server/instance file is `config/CT_tablet_apps.json`. Each existing `apps[appId]` entry gains an `icon` object independently of its purchase `item` and `count`, for example:

```json
"icon": {
    "color": "#49B9A2",
    "item": "minecraft:music_disc_creator",
    "force_fallback": true
}
```

Older configs gain missing icon objects without replacing prices or existing icon overrides. Server reloads (`/reload`) and login synchronize settings to all clients, including launcher, settings, app header and store icon consumers. Disabling an app's flag restores its registered artwork without changing assets or installation data.

## Painted block inspection surface

`PaintedBlockInspectorRenderer(ResourceLocation texture)` provides a reusable full-tablet inspector using a Settings-compatible 320 by 300 atlas. The left sidebar displays a large block image and Inspect button; the right panel displays the name, registry ID, position, dimension, hardness, block-entity status, direct-control support and every supplied property row. Wrapped property values, hover text, wheel scrolling and a draggable painted scrollbar keep long documents accessible. Inspect and signal callbacks are protected methods that consuming mods override to use their existing actions. The default callbacks send `inspect` and `signal`.

The snapshot contract uses `Available`, `Name`, `Block`, packed `Pos`, `Hardness`, `BlockEntity`, `Controllable`, `Signal`, and a `Properties` list of compounds with `Name` and `Value`. Optional `State` is vanilla block-state NBT, `Dimension` is a dimension ID, and `SubLevelId` is a UUID. The state model and document are cached per screen snapshot; changing targets resets scrolling. The renderer retains no live level or block entity.

`GuiBlockPreview.render(GuiGraphics, BlockState, x, y, size)` draws a block's item model, a fluid bucket when the block has no item, or the baked block state as a fallback. It creates no block entities or world renderers.

`TabletAppClientUi.placed()` distinguishes a placed tablet's full-screen menu from its projected screen and from a handheld tablet. Its default delegates to `projected()` for existing hosts. G&G's host reports its actual placed source, so NFC Inspect refreshes the attached block on placed tablets while handheld Inspect begins the existing reader scan.

G&G's `Nfc` renderer supplies `settings.png` and maps the shared callbacks to the existing `nfc_scan` reader action and `nfc_signal`. The server adds state, dimension and sublevel identity to the existing readable block snapshot without replacing its property or direct-control providers.

## Shared painted card artwork and device browser

`PaintedCardArtwork(ResourceLocation atlas)` exposes the background, size-aware card frame, button, plus, edit and delete partials of a 320 by 400 control-card atlas. `frame` retains the existing RGB normalization and nine-slice borders. `PaintedControlCardsRenderer` now consumes this shared artwork for its existing visuals and controls.

`PaintedDeviceCardsRenderer(ResourceLocation atlas)` uses the same artwork and `ControlCardGrid` for a full-tablet device browser without a sidebar. A wide Add new card remains visible on every page beside up to three device cards. Wheel scrolling browses additional devices, with per-screen pagination retained through drafts. Device cards show their name, rename and delete icons, online status and Control, Graph and Plot buttons. Rename uses the host's existing bounded text dialog; offline or unsupported operations consume clicks without sending actions. Projected interactions use the same controls.

The `Devices` snapshot list supplies `Key`, `Name`, and `Online`. Optional `ControlAvailable`, `GraphAvailable` and `PlotAvailable` flags disable individual actions. Consumers override `addDevice`, `renameDevice`, `removeDevice`, and `openDevice` (with the public `View` enum) to retain their own binding, permission and session handling.

G&G's `RemoteDesktop` supplies `redstone.png`, maps Add new to the existing Reader mode pairing flow, and retains `rename_controller`, `remove_controller`, `remote_control`, `open_graph`, and `open_plotter`. RDP snapshots report graph/plot availability for Advanced Controllers, preserving ComputerCraft's control-only behavior and existing server access checks.

## Device card colors and final Add new placement

`PaintedDeviceCardsRenderer` appends Add new after every bound device, using four card slots per page. The last card remains available on the final page, including when no devices are bound. Control, Graph and Plot use distinct blue, purple and green button tints; unsupported and offline actions remain disabled.

The renderer accepts an optional `ColorPickerModal.TextureAppearance` through an additive constructor overload. Each device's optional `Color` field supplies its 24-bit RGB card color, defaulting to `0x303030`. The eyedropper opens the existing color picker with live preview. Accept invokes the protected `colorDevice(ctx, key, rgb)` callback once; Cancel restores the prior appearance without writing. Preview entries are acknowledged against snapshots and pruned with removed device keys. Pointer dragging, keyboard input, Escape and scroll capture are routed to the active modal.

`ColorPickerModal.renderButton(graphics, x, y, size, hovered)` exposes the existing graph-header eyedropper artwork; graph headers and device cards share it. `PaintedCardArtwork.button` has an additive RGB overload that tints the painted nine-slice rather than placing an approximate overlay over the control.

G&G maps the callback to `controller_color` with `binding-key|RRGGBB`. The server accepts colors only for a device bound to that tablet, validates six hexadecimal digits, and saves the value in the RDP app's `DeviceColors` compound under the stable binding key. Colors survive rename and reopening; removing a device removes its saved color. Existing bindings without color metadata keep the original appearance.


## Graph transitions

`com.rieno.gadgetsandgizmos.lib.graph.GraphTransitions` provides stateless numeric and signal operations for graph hosts:

- `risingEdge(prev, current)` returns true only on a false-to-true transition. The host retains the previous condition.
- `approach(current, target, step)` advances by the absolute finite step without passing the target. Zero or invalid steps hold the current finite value; an invalid target also holds it.
- `interpolate(start, target, progress)` blends finite endpoints with progress clamped to 0?1. Invalid progress uses the start, an invalid start uses zero, and an invalid target uses the start.

Hosts own node state, pulse counts, tick scheduling and persistence. Interpolation reads do not advance progress. A stepped host can advance progress once per pulse with `approach(progress, 1, amount)` and then call `interpolate(a, b, progress)`.


## Styled text rendering

`com.rieno.gadgetsandgizmos.lib.client.render.StyledText` exposes shared single-line label formatting for client renderers.

- `literal(text, fontStyle)` returns a fresh component with the selected style. Supported style IDs are `regular`, `bold`, `italic`, `bold_italic`, `underline`, `strikethrough` and `obfuscated`. Style IDs are case-insensitive and accept spaces or hyphens in place of underscores. Unknown styles use regular text; null text becomes empty.
- `fit(font, text, fontStyle, availableWidth)` measures styled glyph widths and clips to the supplied font-pixel width. The returned component retains the selected font style. Non-positive widths return empty text.

Pass the returned component directly to GUI or world text rendering, and use `Font.width(component)` for centering. Converting it to a plain string discards its font style.


## Target graph nodes

`com.rieno.gadgetsandgizmos.lib.graph.TargetGraphNodes` supplies passive Target and Get Targets definitions under the consuming mod namespace. Register each definition in the host node registry and register the corresponding `target` or `getTargets` executor. Target exposes one `target` output; Get Targets exposes one `targets` List output. Neither requires an execution pulse.

- `select(data, discoveryTarget)` persists exactly one selection in `Target`, `TargetLabel` and `TargetData`. Passing null clears that selection. Hosts provide their own picker UI.
- `reference(payload)` creates an immutable typed Target value. `payload(reference)` returns a detached NBT copy. Packed block positions, sublevel UUIDs, numeric tag kinds and nested arrays survive conversion exactly.
- The Target executor reads the selected reference through `GraphExecutionContext.configuration("TargetData")`. Hosts should return `reference(savedTargetData)` for that setting. An unset selection yields an empty typed Target.
- Expose `GraphTargetSource` through `GraphHostServices.TARGETS`. Its `targets()` method supplies the current list of typed Target values in host order, with availability and access restrictions determined by the host. Get Targets samples this service on evaluation and returns an immutable List snapshot. Missing services produce an empty List. List entries retain their Target type for iteration, indexing and wiring into target inputs.

`GraphDataNbt` converts between Minecraft NBT and immutable `GraphDataValue` values without losing numeric or array tag kinds. `fromTag` / `toTag` support individual tags; `fromCompound` / `toCompound` support complete compounds. The addon `AdvancedGraphDataCodec` remains a compatibility delegate to this API.

## Widget designer and projected display parity

`WidgetElementLayout.resolve(data, canvasWidth, canvasHeight, constrain)` resolves authored bounds, finite rotation and scale, and optional canvas fitting before surface scaling. Its `Element.project` and `Element.unproject` share the rendering transform with pointer picking. `WidgetCanvasProjection.canvasWidth` and `canvasHeight` provide consistent 320 by 180 defaults for missing canvas dimensions, including resizing.

`HudWidgetRenderer` paints text, values, boxes, buttons, toggles, sliders, progress controls and text inputs through a generic `Canvas`. The host supplies resolved values and a `Style` containing its palette, typography, themes and optional text-input nine-slice. All dimensions, font sizes, border sizes and radii are authored canvas units. Call `transform` after establishing the canvas-to-surface transform, then paint the element at its authored width and height. `gui` and `surface` adapt the same layout to GUI graphics and buffered world rendering. Surface depth and layer direction are supplied by the caller. No intermediate widget framebuffer is required.

`GuiNineSlice.Painter` and `paint` expose the same atlas patch geometry to additional render targets; all existing GUI overloads remain available. `HudControlRenderer.panel` shares bounded borders and rounded geometry. `HudWidgetRenderer.placeholder` shares missing-image artwork.

`PlanarQuadClipBuffer` wraps a `MultiBufferSource` and clips complete QUADS to an authored canvas rectangle using the supplied canvas pose. Vertex colors, texture coordinates, lighting, overlays and normals are preserved. Obtain each consumer from this wrapper, complete its quad before switching render types, and close the scope before ending the caller's buffers. Closing submits the final pending quad without ending or owning those buffers. Unclipped holographic canvases can use the original buffer source directly.

`ViewFeedRenderer.background` and `placeholder` share camera letterboxing and unavailable-state artwork. `ViewSceneRenderer.drawVisibleFitted` preserves the captured aspect ratio while retaining the existing visibility-based refresh behavior; `ViewFeedRenderer.drawSurface` uses it to match GUI previews. Existing `drawVisible` remains available for callers that want a stretched feed.

`RenderGeometryCache(boolean ordered)` adds an opt-in ordered replay mode. Use `true` for overlapping transparent elements that alternate between solid, texture and font render types. Consecutive requests of one type share a batch while later requests retain their submission position; refresh and retained replay preserve this sequence. The no-argument constructor retains its existing grouped behavior.

`WidgetCanvasProjection.Layout.project` maps authored points onto the rotated, potentially nonuniform surface. Use it with `WidgetElementLayout.Element.project` to compute render bounds from the same transformed corners used by painting and picking, including holographic overflow.
