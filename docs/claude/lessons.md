# Lessons — read this before any new feature or any fix

Everything learned building this mod with this user, so a new session works at the same level as the old ones.
Hero-specific details live in the hero files next to this one; this file is the general know-how. Add to it whenever
the user corrects something or a technique works / fails (in the same commit as the change).

## 0. Starting a new chat (the user opens a new chat for every new task — do ALL of this first)
1. Make sure you have the CURRENT notes: if `docs/claude/lessons.md` is missing or CLAUDE.md is the old 50 KB one, run
   `git fetch origin claude/laughing-noether-xy6z56 && git checkout origin/claude/laughing-noether-xy6z56 -- CLAUDE.md docs/claude tools/claude`
   (until those notes are merged into main; then main has them).
2. Read CLAUDE.md, this file, the hero's `docs/claude/<hero>.md` and the user's `docs/<HERO>.md`. Look at the hero's code
   only in the parts the task touches (grep first). Check `## 7. Project state` below for open branches/PRs.
3. Branch: continue an open PR's branch when the task continues that work (e.g. Iceman → PR #6's branch); a new request
   gets its own branch from main and its own PR (titles start with the hero: "Iceman: ..."). The cloud may assign a branch
   name: base it on the right starting point and push there; never push to main; merge (GitHub merge of the PR) only
   when the user writes "beğendim", then give the main line.
4. The user's reference images/videos are attached in the chat (the `references/` folder exists only on their PC); if a
   task needs a reference you do not have, ask for a screenshot/frames instead of guessing.
5. Work yourself, in order (helper agents only when quality truly needs them; no need to ask, never just for speed). Type-check with
   `sh tools/claude/typecheck/typecheck.sh`, preview bodies with `sh tools/claude/preview/preview.sh`, Python art/sounds
   scripts in tools/. Gradle and the game cannot run here — say so honestly.
6. Finish: update the notes (hero file + this file for general lessons), the Turkish user doc, commit, push, then reply in
   Turkish: what changed, what was not checked, what to look at in game, the three copy-paste blocks.

## 1. The user and how they judge work
- Non-programmer, writes Turkish, tests everything in game (IntelliJ runClient) and answers with screenshots, video
  timestamps or a long written spec. Their written specs are the brief: read every line, follow the order of priorities they
  give, and do not "fill in" things they said are undecided (Iceman's E has no combat mechanic, X is undesigned).
- They judge by FEEL in game, against a reference (film scene, game trailer, comic cover, Minecraft render). The reference
  is the target; adapting it to Minecraft is our job, not an excuse to drift from it.
- They notice: snapping, pop-in/out, things floating near hands instead of held, attacks that don't read as attacks, effects
  too small to see, fake screen filters, "AI-looking" geometry, see-through materials, anything that looks drawn instead of
  physical. They love: real physics (cloth, debris, collision), staged formation/destruction, weight, body language,
  layered sound, effects anchored to the body.
- Honesty matters: always say what could not be checked (nothing is "done" until they saw it in game). Never claim visuals
  are right from code alone; say "önizlemede baktım" vs "oyunda göremedim".
- They read the reply as a non-programmer: plain Turkish, no class names unless needed, end with the three copy-paste
  blocks (see CLAUDE.md). Keep the reply proportional; list what to check in game.
- Pushing to the test branch is NOT merging. They once asked "beğendim demedim, neden pushladın" — explain that the branch
  is a separate box; merge only on "beğendim". When agents are mid-edit, don't commit half work; push finished work.
- Time is not a problem for them; tokens are. Prefer doing work yourself in order over many parallel helper agents (one
  Iceman request with 5 helpers cost ~1.6M tokens). Never lower quality to save tokens; when an agent is truly needed for quality, use it without asking.

## 2. The process that gave the best results (Magneto, Batman era)
1. Read the hero's notes + the user's spec/reference. List each point; map it to a file.
2. Look for an already-solved technique (CLAUDE.md list, other heroes' code). Copy the pattern, not reinvent it: e.g.
   Iceman's wheel came from BatmanWheel's camera lock; the two-hand weapon solver from Magneto's SpikePull; the collision
   box idea from SonicEmitterEntity; poses from PantherMotion.
3. Design the data first: Action (ids, timings), Config (gameplay numbers, data-driven), the synced state, the FX kinds and
   what each packet field means (comment them in the Action file).
4. Put shared math in PURE functions used by both server and client (paths, physics steps) so no position packets are
   needed and nothing desyncs (PantherPath, TakedownPath, BatmanFlash.step).
5. Server decides (damage, pulls, collisions); clients animate from the synced clock; the hero's own client steers his
   body for movement moves (prediction on a local clock to avoid lag).
6. Build the look offline: preview harness (tools/claude/preview) for bodies/meshes; spectrograms for sounds; Python
   (Pillow) for textures/icons/splash. Check the picture before handing over.
7. Type-check (tools/claude/typecheck/typecheck.sh), then re-read the diff adversarially: null/removed entities,
   logout/death/hero-change cleanup, level change, unbounded lists, per-frame allocations, partial-tick interpolation.
8. Add a `/<hero> test ...` command for every new feature so the user can see it alone (dummies = NoAI adult husks
   with high HP, removed after a while).
9. Update the hero notes + the Turkish user doc (docs/HERO.md) in the same commit.

## 3. Design lessons (what was rejected, what worked)
- Iceman: see-through ice rejected → solid; rounded high-poly body rejected → Minecraft boxes with a pixel skin (kept);
  crystal-looking ice rejected once ("kristal gibi durmasın") then the next spec wanted organic CRYSTALLINE ice but not
  "mathematical" (no perfect cubes/ramps/tubes, no glass) — the resolution: textured blue ice in irregular, broken,
  leaning crystal shapes that GROW in stages, white only as frost. Effects that were too small (CTRL trail, spear impact)
  were called out: primary formations must have presence.
- Batman: a drawn cape "curtain" for blocking was rejected → the real verlet cape grabbed by the hand. Thermal vision via
  PostChain and at AFTER_LEVEL never showed in game → captured matrices + HUD redraw. Melee blocks must vary and never
  repeat back to back; ranged = cape.
- Thor: a darkening filter for the cloud scene was disliked → real geometry (cloud ceiling) instead of screen tricks.
- Ghost Rider: arms froze because a stale pose sample was used, and the easing restarted every frame when comparing the
  target to the lagging drawn arm → compare target to target.
- Hulk: the face read as a troll → square heroic face; everything scaled to his 1.5x body (effects from fists/chest).
- Sandman: the defender's body language in the film matters most; attacks must read as attacks (one arm, not the guard).
- Iceman ice (MK1 spec): "see-through" was rejected earlier, but the user's real target is MILKY ice with clear blue depth and
  SOME semi-transparent areas (never glass, never fully transparent, never uniform white or uniform blue). A frozen body must
  keep its silhouette (ice grown over the pose), not sit in a block. Small detail pieces must be scattered, never a row/comb
  (checked in the preview: the first version of the spines read as a comb from the side).
- General: when a spec names a reference move (Garen spin, Marvel Rivals Wolverine, God of War flurry, Elden Ring roll),
  match its rhythm and silhouette; the user checks against it.

## 4. Technical lessons (Minecraft 1.20.1 / Forge)
- Immediate BufferSource: asking for a different non-fixed RenderType ends the previous batch; a VertexConsumer cached
  across another getBuffer() call writes into the wrong batch. Gather vertices per type and emit at the end (IceMesh.Ctx),
  or use a source with fixed buffers (FilmFx.batched()).
- `RenderType.eyes()` culls back faces (default CULL): camera-facing quads with the wrong winding vanish. Make your own
  additive NO_CULL type by subclassing RenderType (FilmFx, IceTypes).
- entityTranslucent: NO_CULL, sorted, discards alpha < 0.1; vertex normal feeds Minecraft's own two-light shading
  (minecraft_mix_light) — pass the normal of the side you SEE or faces go dark. Textures loaded by SimpleTexture REPEAT by
  default (no .mcmeta) — tiling UVs > 1 works; nearest filtering gives crisp pixels.
- Vertex colour bytes wrap above 1: always clamp (Magneto's "blue face" flicker).
- Menus (champion select) use mirrored/scaled matrices: getNormalizedRotation ignores scale (Thor's hammer blew up) —
  unmirror/unscale (ThorLayer.rotationOf). Body code that derives texel size or light from the matrix must not assume
  world scale (IcemanBody fixes `texel = 1`).
- World drawing at AFTER_TRANSLUCENT_BLOCKS: translate by -camera, reset the model-view stack, restore in finally (IceStage).
  Call ctx.at(pose) after every push/translate/rotate when the context caches the matrix.
- Collision without mixins: an entity with `canBeCollidedWith()` true is a wall/floor for players and mobs on both sides
  (vanilla Entity.collide). Sync size with SynchedEntityData + getDimensions + refreshDimensions; spawn it soft and harden
  when no body overlaps (else it traps/throws them); never saved; dies with its owner (IceSolidEntity).
- Speed pins/slows: transient AttributeModifier on MOVEMENT_SPEED with a fixed UUID; remove on every exit path.
- Cleanup paths to always cover: logout, death/respawn (PlayerEvent.Clone), hero change, level change, client
  ClientPlayerNetworkEvent.LoggingOut for client lists.
- Hero attacks look for living bodies only: anything else hittable needs an explicit hook (SonicEmitterEntity.struckBy).
- Vanilla sneak/sprint keys are taken over per hero (HeroSneakSuppressor, raw key reads) — check how before binding SHIFT/CTRL.
- Sounds: synthesise (tools/sounds/synth.py), register in ModSounds + sounds.json + lang subtitles; LAYER with vanilla,
  never replace; check spectrograms; beware duplicate function names in synth.py (an Iceman `s_dash` once shadowed
  Panther's). Generate only the new sounds (`synth.py group.name`).
- Particles: pooled, capped, LOD by distance and the effects setting (IceParticles.count); no entity spam.

## 5. Tools (in the repo, so every session has them)
- `sh tools/claude/typecheck/typecheck.sh [letter]` — javac against stubs; prints real errors; baseline "issues: 8".
  It does NOT check Minecraft/Forge calls: grep the repo for an existing use of every MC API you call.
- Preview now alpha-blends (render2.py) and has `Dump.frozen` scenes (args: `out.txt frozen`).
- `sh tools/claude/preview/preview.sh out.png "front,three-quarter,face"` — offline render of IcemanBody (fake MC classes,
  shadow stubs for heavy classes in preview/src, Dump.java views). Extend Dump.java for new views or other meshes; the
  same approach works for any box hero.
- Python art: tools/textures/iceman_skin.py (skins in Minecraft box-UV layout, tiles), tools/icons/*, tools/splash/*.
- The film backdrop shader can be previewed with moderngl on EGL (see film.md).

## 6. Open / unverified (update as the user reports)
- Iceman: everything after the Minecraft body (organic ice, slide, CTRL, weapons incl. sword plant and the two-hand spear,
  E shrine with left/middle/right picking, Q cocoon size, R formations, frost meter visuals, solid ice) is unseen in game.

## 7. Project state (keep current)
- Open PR: #6 `claude/laughing-noether-xy6z56` — Iceman (whole hero). Also carries these notes and tools/claude until merged.
- Open PR: #7 `claude/superheromod-setup-abnmd0` (base: #6's branch) — Iceman walk mist + MK1 ice rework (material, deep
  freeze, spear impact); the rest of the MK1 spec is listed as not done in iceman.md.
- Parked / unfinished by the user's word: Sandman's Colossus ultimate ("later"); Cyclops sounds unfinished; Thor, Cyclops,
  Sandman, Ghost Rider have no left-side skill list yet; Iceman X is undesigned (do not invent it).
- Everything else (Ghost Rider, Cyclops, Sandman, Thor, Hulk, Zed, Black Panther, Magneto, Batman) is merged in main.

## 8. Reply checklist (every answer to the user)
- Turkish, plain words, short; what was done; what could NOT be checked (game not run); what to look at in game.
- Ask only when a decision is truly theirs (and use one clear question).
- The three blocks with the real branch: Denemek için / Beğenmedim, eski hale dön / Beğendim (see CLAUDE.md).
