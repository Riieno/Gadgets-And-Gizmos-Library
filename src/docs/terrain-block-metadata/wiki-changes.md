# Terrain block metadata

`TerrainBlockMetadata.renderBlock(level, state, pos, vertices, draw)` supplies a secondary terrain mesh with the active shaderpack's block material id and section-local block centre while `draw` writes its vertices.

Call it once per block during terrain compilation. Positions must use the same section origin as the vertex positions. Recursive calls restore the outer block's metadata, and cleanup also runs when drawing throws. Writers without Iris's terrain contract, and clients without an active block material map, run `draw` directly.

The library applies this API to Sable plot blocks compiled through Minecraft's block renderer. Sodium compiles world terrain separately. This supplies the lamp and lantern identities shaderpacks need for coloured lighting and fixes missing block centres in ship meshes. The addon receives this integration through the bundled library.
