# Fog rendering feasibility: 26.3 source inspection

Status: **not verified on a graphics device**. Neither distance fog nor volumetric fog is implemented or exportable. This inspection identifies candidates for the required rendering spike; it does not establish that an effect works on either graphics backend.

## Evidence from the cached 26.3 client

- `RenderTarget` exposes color and depth texture views, and `PostChain` accepts target inputs with `useDepthBuffer`. The main world target is imported into the level frame graph. These are API candidates, not proof that a depth texture can be sampled correctly at a particular hook.
- `GameRenderer.render` calls `renderLevel`, blends the entity outline and applies requested post effects before clearing the main depth and rendering the GUI. This is a possible world-stage integration point, distinct from the existing exported end-of-frame grade. Its post effects run only when a level is rendered.
- `renderLevel` modifies the camera projection for view bob, hurt and portal effects before uploading it. Reconstructing position with an unmodified camera projection would be incorrect. The actual per-frame matrix and backend depth convention must be transferred and tested.
- The renderer clears depth to `0.0`; do not assume a conventional forward depth range or treat a fixed value as sky without measuring actual content. The renderer optionally allocates separate cloud and terrain/water depth targets for improved transparency, so a single main depth sample cannot be assumed to describe translucent surfaces.
- Requested post effects affect whether the renderer preserves consistent depth for the 3D HUD. The hand and screen effects are rendered before those effects, so a post effect at that point must account for them rather than assuming every pixel represents world geometry.
- Frame-graph targets have scoped lifetimes; the level target bundle is cleared after graph execution. Resize recreates target dimensions. An implementation must acquire current resources each frame and not retain old views.

## Required proof before implementation

Run a prototype on the supported OpenGL and Vulkan backends. Verify depth sampling and inverse projection against objects at known distances, with camera bob and portal effects, resize and reload. Check sky, water, transparent surfaces, the hand, dimensions, vanilla fog and GUI ordering. Confirm the actual hook and uniform update mechanism, resource lifetime and failure handling. Benchmark bounded ray marching at representative resolutions before offering quality settings. The initial volumetric effect, if proven feasible without scene lighting or shadow inputs, must be described as unlit density fog.

Until this evidence exists, standalone automatic and command exports and live preview cannot promise either fog effect. Preserve any future fog settings when the renderer is unavailable and explain that the mod and a supported depth path are required; never replace distance fog with a full-screen tint.

## Local test attempt

No block-scraper client was running in this session. Starting one with a writable copy of the cached Gradle files failed before graphics initialization: Gradle could not determine a usable wildcard IP while creating its lock contention handler in the restricted environment. Therefore no graphics-device or depth-hook test was performed here.
