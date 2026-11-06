# Shader Maker: non-negotiable runtime requirements

- Exported shaders MUST work in an unmodified vanilla Minecraft client. No client mod, shader loader, mixin, companion runtime, custom uniform service, or other client dependency is permitted. The Fabric mod is an authoring tool only.
- Never ask to relax this rule or propose a client dependency as a solution. Implement within vanilla resource-pack capabilities. Optional, explicitly selected server datapack integration is a separate delivery mode, never a silent dependency of standalone packs.
- Vanilla post effects, core shader overrides, textures and combinations of those resources are eligible. Apply and Export must use the same generated resources.
- Exact biome IDs are NOT required where vanilla exposes only shared visual signals. Shared biome/environment groups are acceptable: clearly list all known members in the selection UI and disclose ambiguity, blending, weather/time/dimension sensitivity and actual scene coverage. Do not label a visual group as exact camera-biome detection.
- Preserve older exact-ID project data. Converting an existing selector to a visual group must be an explicit user action showing the expanded match set. No silent widening, deletion or flattening.
- Wire supported controls through persistence, preview, compiler and export. Do not advertise diagnostics, native shader compilation or a mod preview as clean-client graphics validation.
- Preserve unrelated uncommitted work. Run the applicable project and native shader contract checks; report remaining graphics validation honestly.
