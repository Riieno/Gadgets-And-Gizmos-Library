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
