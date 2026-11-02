# Atmosphere feasibility: vanilla resource pack paths

Status: **distance/height fog and volumetric atmosphere are not implemented or verified on a graphics device**. The [product spec](vanilla-only-handoff.md) and [current evidence](vanilla-feasibility.md) replace the earlier mod-renderer proposal.

## Source-backed paths to investigate

- Post inputs can bind a target's depth texture. World post effects run before the main depth clear, with hand depth integration when effects request consistent depth. Main depth does not automatically describe transparent surfaces or separate the hand from world geometry.
- PostPass installs an orthographic Projection block. Inverting that block cannot recover the original world projection. Prove reconstruction from available vanilla inputs or use a suitable world/core stage; do not introduce a mod-supplied camera matrix as a shortcut.
- Terrain core shaders already compute spherical/cylindrical vertex distances and apply WORLD fog. They also have geometry/chunk positions and terrain matrices. This is a candidate resource-only route for distance/height atmosphere, with different scene coverage from a full-screen post effect.
- Camera Y is available in Globals, independently of depth. A camera-altitude condition must not be confused with fog varying by each surface's world height.
- Post-stage fog is set to NONE by render3dHud before applyPostEffects in the inspected renderer. A fog uniform declaration alone does not make world fog available there. Core-stage fog is biome/weather influenced and must be inspected at its actual use point.
- Depth is cleared to 0.0. Test actual depth conventions, sky, view bob/hurt/portal transforms, water/translucency, the hand, OIT variants, resize, reload and dimensions on both backends.

## Required proof

Start with the generated raw-depth and terrain-fog packs in the evidence document. Validate known distances/heights and source signals in an unmodified client. Define where authored atmosphere combines with vanilla fog and grading, and whether it affects sky, entities, hand, precipitation and GUI. A terrain-only result must be described with its actual coverage.

For volumetric atmosphere, establish which depth, position and lighting inputs a vanilla resource pack can use, then assess bounded ray marching or other feasible techniques. Measure cost and distinguish unlit density effects from light/shadow scattering. This remains research work; missing lighting in a post uniform block is not proof that all vanilla lighting paths are absent.

Frame-graph lifetime, current texture dimensions and shader variants remain constraints for any proposed technique. A resource pack cannot acquire private engine hooks or create arbitrary engine attachments simply by declaring shader inputs. Any cross-stage encoding must be demonstrated without a mod, companion data pack or server loop.

The historical graphics launch attempt failed before initialization with Gradle's wildcard-IP error in a restricted environment. It established no depth/render result. The current revision validates diagnostic JSON and shader compilation only; device tests remain open.
