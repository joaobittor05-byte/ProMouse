# Realistic Universal Shader — v0.2.1 Heavy Realism

This update corrects the existing shader rather than rebuilding it.

## Required correction targets

1. Water must show continuous, clearly visible procedural wave motion.
2. Water must use animated color/normal/MER assets so reflections and refraction visually break across the moving surface.
3. Water material roughness stays very low to maximize the reflection response exposed by Vibrant Visuals / RenderDragon SSR and IBL.
4. Shadows use the official `soft_shadows` path instead of `blocky_shadows`.
5. Existing lighting, atmosphere, biome bindings and quality profiles are preserved unless directly related to these corrections.

## Heavy asset strategy

The package intentionally exceeds 100 MB through useful visual data rather than filler.

- LOW: lightweight water/core deferred settings.
- MEDIUM: denser wave settings and higher-resolution animated water.
- HIGH: 128px high-resolution PBR material subset.
- ULTRA: 256px expanded PBR material subset.
- EXTREME/CINEMATIC: full 512px PBR material library, with 1024px upgrades for major terrain, vegetation, polished surfaces, glass/ice, metals and emissive blocks.

The heavy material library contains local color textures, normal maps, and MER maps so every texture set resolves inside the same resource pack.

## Renderer-owned limitations

The Bedrock resource-pack API does not expose custom source-code hooks for planar reflection, custom PCF/PCSS kernels, custom cascaded-shadow implementation, or replacement SSR algorithms. These are renderer-owned systems. The shader therefore maximizes the official path through water waves, animated surface normals, low roughness, PBR material response, atmosphere, soft shadows, and engine SSR/IBL rather than adding JSON switches that RenderDragon would ignore.

## Extreme water target

EXTREME uses the maximum 30-octave wave configuration used by this project, four-frequency animated water surface assets, strong caustics, and very low water roughness. It is intentionally expensive.
