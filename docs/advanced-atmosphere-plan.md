# Advanced grading and atmosphere: roadmap replaced

The authoritative plan is now [Shader Maker: vanilla resource pack shader authoring spec](vanilla-only-handoff.md). [Vanilla feasibility evidence](vanilla-feasibility.md) contains the current source findings, diagnostic packs and validation limits.

The earlier plan split a mod-driven live renderer from standalone static export. That architecture is retired. Apply and Export must use the same generated vanilla resources; biome/environment, altitude, daylight, weather, lightning and atmosphere remain requirements to investigate through vanilla post/core shaders and textures.

The implementation order is evidence fixtures, exportable layer compilation, remaining context paths, the visible stack workspace, atmosphere, and clean-client release validation. A missing post uniform does not justify abandoning a feature or implementing it only in a mod renderer. Core shader overrides are eligible version-pinned resources and need their own compatibility and backend proof.

The current implementation still exports base grades only. The existing layer editor, sample-image workflow, history and persistence remain useful foundations. Preserve biome and other conditional project data while export is investigated. See the authoritative spec for exact blend semantics, interface decisions, migration rules and acceptance criteria.
