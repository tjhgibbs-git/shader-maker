# Resource-pack export contract

The released mod must generate a working `resources.zip` for the Minecraft version the mod targets. The browser review harness is temporary and is not the exporter.

The version-specific implementation must:

- Generate the correct pack metadata, resource paths, shader sources and any activation resources required by that Minecraft version. Place them at the ZIP root, with no enclosing directory.
- Use an actual vanilla-compatible render pass and activation mechanism. A resource pack that merely contains a shader but never activates it does not satisfy this requirement. Verify the result in a clean instance without the mod installed.
- Translate supported editor settings to the exported effect and test the result against the in-game preview. Preserve existing project data when exporting.
- Identify effects that cannot be expressed using inputs available to the selected vanilla pass. Explain the limitations before export and offer an explicit choice to adjust the project; never silently drop conditions or present a nonfunctional pack as working. In particular, biome, altitude, facing and time are dependent on the inputs and activation available in the target version.
- Write the ZIP to a user-selected location, report errors clearly, and avoid leaving a partial file on failure. When intended for a custom map, document the correct placement and activation for the target version.
- Test ZIP structure and generated files automatically, then perform an in-game smoke test covering installation, activation, visible effect and removal. Repeat for each supported Minecraft version.

No version-specific exporter exists yet. The CPU reference and temporary preview do not establish that a vanilla export is possible for every designed effect. Select and validate the target version and render path before implementing it.
