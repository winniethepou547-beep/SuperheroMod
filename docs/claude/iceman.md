# Notes for Claude (moved out of CLAUDE.md to keep every turn small; read this before working on it)

## Iceman (PvP hero, `heroes/iceman/`, `client/render/iceman/`) — look after the user's comic cover (X-Men suit + ice), solid ice like Marvel Snap's
- Id `iceman`. `IcemanAction` = ids, animation timings, FX kinds (`FX_*`, packet field meanings in the comments), input kinds;
  `IcemanConfig` (`superheromod-iceman.toml`, `-client.toml`). `IcemanController` (server) routes keys and ticks the
  subsystems: `IcemanWeapons` (mace/spear/sword, press -> CHARGE wind-up: released under `HOLD_TICKS` = a swing, held = the
  hold; thrown spears), `IcemanBrush` (RMB stream on a body / sculptures from the aim path, solid: push bodies, stop
  projectiles), `IcemanSlide` (SHIFT surf + CTRL sub-zero slide; his own client steers), `IcemanShell` (Q: shell HP absorbs,
  heal, break -> tired landing, Q again = burst), `IcemanGround` (R: crack front sent every tick, eruption), `IcemanFrost`
  (every body's frost meter 0..100, decay, slow modifier, DEEP FREEZE at 100: held, no attacks, a blow shatters it for
  `shatterBonus`), `IcemanTest` (`/iceman test brush|frost [n]|slide|shell|shellhit [n]|shellbreak|shellburst|weapons|mace|spear|sword|shatteredground`,
  training dummies = NoAI husks). Packets `IcemanStatePacket` / `IcemanFxPacket` / `IcemanInputPacket`.
- Keys: LMB weapon, RMB brush, E held = Ice Armory wheel (E has NO other mechanic — user's rule), SHIFT held = ice slide,
  CTRL = sub-zero slide, Q shell / burst, R shattered ground, X = "not designed yet" (do not invent an ultimate).
- Look: MINECRAFT BLOCKY (the user's latest reference: a blocky Minecraft Iceman riding a beam of bright blue ice blocks).
  The user rejected see-through ice ("saydam olmamalı"), then rounded/high-poly models and crystal-looking ice ("küp
  modellerden vazgeçme", "kristal gibi durmasın, buz olduğu belli olsun"). `IceMesh` is THE material for everything: TEXTURED
  pixel ice (`textures/entity/iceman/ice.png`, `frost.png`, his `skin.png`, all drawn by `tools/textures/iceman_skin.py`),
  mapped from the side a face looks to (16 texels a block, 1 a model pixel; `Ctx.texel`, `Ctx.origin` pins it on moving
  pieces), soft light, an additive wet gloss per face (`gloss()`), opaque (alpha only for forming/melting). Everything is
  gathered and emitted at `Ctx.end()` per texture (ICE, FROST_ICE, SKIN, then GLINT = `IceTypes`, additive no-cull);
  `IceMesh.endBatches`. `Mat` tints the texture (milky/pale -> frost texture); CLOTH = plain colour. Call `ctx.at(pose)`
  after every stack change.
  `IcemanBody` (pixels): Minecraft boxes via `IceMesh.skinBox` (Minecraft box-UV layout; the `SKIN_*` u,v,w,h,d table must
  match the Python script): ice cube head with a pixel face + a frosted hair layer + a crest of 4-sided ice spikes, chest /
  abdomen / pelvis in the X-Men suit (red V, black neckline, X, grey belt), red shoulders then ice arms (gauntlet box, fist,
  shoulder/forearm spikes, icicles), black shorts to mid thigh then ice shins/feet with knee spikes; the shell = glacier
  boxes over each part; hooks WEAPON*, SHELL_*, HAND_GLOW. `IcemanLayer`, `IcemanMotion`
  (+ `IcemanMoveMotion`, `IcemanWeaponMotion`, `IcemanShellMotion`), `IcemanFirstPerson` (draws the real arms from the same
  pose under the camera). World effects go through `IceStage` (AFTER_TRANSLUCENT_BLOCKS: ice, then FilmFx) and the shared
  pooled `IceParticles` (`shatter()` = the one break language: flash, big pieces, shards, mist, dust, melting).
  `FrostFx` = frost on bodies by stage + deep-freeze encasing + the victim's screen frost / slide lens.
- Sounds group `iceman` (synth). Splash `tools/splash/iceman.py` (composes `tools/splash/iceman_model.png`, the in-game model
  rendered by the offline preview harness below; re-render it when the body changes). Docs for the user: `docs/ICEMAN.md`.
- ICE LANGUAGE (the user's latest spec, DoFP reference): ice is grown by the cold, never spawned finished and never "mathematical"
  (no perfect cubes/ramps/tubes/glass). `IceGrowth` = the shared shapes (uneven leaning `crystal` with a broken top, `cluster` with
  per-crystal growth delays, irregular walkable `section`/`slab`, broken-off `chip`, `grow(t, delay, dur)`); `IceParticles` layered cold
  (`cryo` plume, `coldMist`, `crystalDust`, `freezeTrail`) and staged breaking (`breakApart` zone by zone + crack/shatter/shard-rain/
  frost-hiss sounds, `later`). Mostly blue, white only as frost; each ice face its own shade. New sounds `crystal_ticks`, `grow_rumble`,
  `shard_rain`, `cold_whoosh`, `frost_hiss`. Weapons form per weapon (mace branches, spear line, sword layers); the sword HOLD now
  plants the sword in the ground (`PLANT_*`, `FX_SWORD_PLANT`, a server-fixed point that pulls bodies in within `swordPullRadius`, his
  speed pinned; `IcemanWeaponReach` solves both hands onto a weapon); any other power (SHIFT/CTRL/Q/R/RMB) breaks the weapon in hand
  (not the E wheel). E = a frozen shrine (`IcemanWheel`: mace left, spear middle, sword right). `/iceman test swordplant`.
- Left-behind ice is SOLID: `heroes/iceman/IceSolidEntity` (invisible synced box, `canBeCollidedWith`, soft until no body is
  in it, never saved, dies with its owner/time) under the raised slide track (`IcemanSlide`) and along sculptures
  (`IcemanBrush`); `/iceman test solid` builds a test bar and ramp.
- Body preview offline (no game needed): a /tmp scratch harness renders IcemanBody with fake MC classes (fakes for
  PoseStack/VertexConsumer/RenderType/Vec3/Mth/Minecraft + the real JOML jar, a Dump class writing the quads, a Python
  painter's-algorithm renderer that samples the textures per texel). Rebuild it if /tmp was wiped.

