# Contributing to the Gadgets & Gizmos Library

Thanks for taking the time to contribute.

This library exists to provide re-usable API surfaces for Gadgets & Gizmos and any other mods I decide to make in the future.

## Branches
- `main`
    - Release-ready tested code.
    - Merged alongside CurseForge/Modrinth releases.
- `inDev`
    - Active development branch.
    - May contain unfinished, unreleased, or experimental API changes.

## Before opening a pull request

- Make sure the project builds successfully with:
    - Bash
```bash
./gradlew clean build
```
    - Windows
```bat
gradlew clean build
```

- Test your changes in-game.
- Do not commit build output, IDE files, temporary files, backups, or decompiled reference code.
- Keep changes focussed on the problem being solved.
- Avoid unrelated refactors in the same pull request

## Library Boundaries
Code in the library must not depend on Gadgets & Gizmos addon classes or other external mod implementation classes unless stated otherwise.

Library code belongs under
```com.rieno.gadgetsandgizmos.lib```

Do not import addon classes from packages such as:
```java
import com.rieno.gadgetsandgizmos.content;
import com.rieno.gadgetsandgizmos.compat;
import com.rieno.gadgetsandgizmos.mixin;
import com.rieno.gadgetsandgizmos.neoforge;
```

If a reusable feature needs information from the addon, add a generic interface, callback, registry, value object, or API contract to the library and let the addon implement it.
Do not expose addon block entities or implementation classes through public library APIs.

## API Changes
The public API is used by other mods so compatibility matters.
When changing an existing public API:
- Do not rename or remove public methods without good reason.
- Do not change stable IDs, serialized values, NBT Keys, or saved identifiers without considering migration.
- Preserve existing behaviour where possible.
- Prefer additive changes over breaking changes.
- Deprecated compatibility aliases should remain until they can safely be removed in future API versions.

If you add or change a public API surface, include a `wiki-changes.md` containing the documentation as part of the same pull request.
`wiki-changes.md` is used for review and documentation and will not be included in the repository after the changes are merged.

## Registries and IDs
Anything registered through the library must use the owning mod's namespace.
for example:
```java
ResourceLocation.fromNamespaceAndPath(
    "your_mod",
    "your_feature"
)
```
Do not register over another mod's namespace.
Use strict registration methods unless replacement is explicitly required.

## Code style
The library and G&G main use a specific and intentionally explicit style, please try to stay consistant with that.

## Formatting
Opening braces should stay on the same line
```java
if(condition){
    doSomething();
}
```

Short guards should be used and remain on one line when they remain readable.
```java
if(value == null) return;
```

Avoid reformatting unrelated files to match a different format.

## Naming
Use clear names, but don't over-expand simple local variables for no reason.
Examples of variables used in the project include:
```java
context = ctx
previous = prev
result = res
index = idx
value = val
```

Public API names should be descriptive and stable.

## Comments
Comments should explain what the code is doing in plain language and not be over descriptive.
For example:
```java
// Get the retained sublevel ids
public Set<UUID> retainedSubLevelIds(){
    return Set.copyOf(retained.keySet());
}
```

or:

```java
// Remove the old ticket before retaining renamed ticket
invokeTicket(subLevel, LEGACY_TICKET, owner, false);
```

Do not add comments that read like task notes, placeholders, or unfinished work.
`TODO`, `FIXME`, `DO FIX`, and similar comments are not accepted in pull requests and may result in the request getting denied.
Pull requests containing unfinished work WILL NOT be merged.
A pull request should be completed working code, not a partial implementation.
Avoid comments like:
```java
// TODO: Add support for x
// Need to fix this later
// DO FIX
// FIXME: This breaks under x
```

## Section Headers
The library and G&G use large section headers in larger classes.
```java
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/


/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/


/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

```

Use the same pattern when adding a new major section to a class that already follows it.

Do not add large section headers to tiny files where they would add noise.
For example:
```java
package com.rieno.gadgetsandgizmos.lib.client;

import com.some.dep.someDependency;

public final class someClass{
    // Do something
};

```

## Public API Design
Prefer small reusable contracts over feature specific hooks.

Good:
```java
interface TelemetryProvider
interface DisplaySource
interface ControlProbe
```

Bad:
```java
interface VerySpecificThingForOneBlockInOneMod
```

The library exposes capabilities not internal implementation details.
Before adding a new public API, consider whether an existing interface, registry or callback can support the use case cleanly.

## Threading and logical side

Respect Minecraft and NeoForge logical-side rules.

- Physics, topology, storage, shipping and control state are server-side systems.
- Rendering belongs in client-only packages.
- Do not load client-only implementation classes from common/server code.
- Mutate levels and block entities on the owning game thread.
- Treat unloaded SubLevels and missing block entities as normal unavailable states.

## Pull Requests
A good pull request should explain:
- What changed.
- Why the change is needed.
- What API surface is affected.
- Whether existing  behavior or saved data changes.
- How the change was tested.

Screenshots and/or videos are useful for rendering, GUI, physics, or interaction changes.

## Bugs and Issues

When reporting a bug, include as much information as possible:
- Library version.
- Minecraft Version.
- NeoForge Version.
- Relevant Dependency Versions.
- Reproduction steps.
- Expected Behaviour.
- Actual Behaviour.
- Logs and/or crash reports.
- Screenshots/video if useful.

If the issue involves another mod, include that mod and version as well.

## Licensing
By contributing code to this repository, you agree that your contribution may be distributed under the repository's MIT license.

Do not submit code or assets that you do not own or have permission to redistribute.

## Questions
If you are unsure whether something belongs in the library or in a consuming mod, open an issue first.
In general:
- If multiple mods could reasonably use it, it probably belongs in the library.
- If it only makes sense as part of one mod's gameplay/content, it probably belongs in that mod.