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
- MK1 ICE STANDARD (user's latest spec, Mortal Kombat 1 Sub-Zero freeze reference; "the air froze, crystals grew and connected"):
  world ice is MILKY pale blue with clear blue pockets inside (no longer bright cyan-blue): `tools/textures/iceman_skin.py`
  `world_ice/world_frost` (his own skin.png unchanged), and `IceMesh.vert` reads two slow noises pinned to the piece: clear
  pockets darker/bluer and a little see-through (Mat.clear = opacity there; 1 = never), cloudy parts milky/opaque; faces turned
  up take the frost texture (`Ctx.frostUp`, false in his body's model space), the outline a cyan edge (`qr`). Partial
  translucency is now WANTED in places ("milky, some semi-transparent, never fully transparent / glass").
  `IceGrowth.crystal`: ~40% have a snapped slanted top with a stub point (no pencil tips).
- DEEP FREEZE = `FrostShell` (game: lifecycle, held pose, particles, sounds, chunk flight) + `FrostShellMesh` (pure geometry,
  previewable: `Dump.frozen`, run `java ... Dump out.txt frozen`). A shell of rings round every part of the body's OWN pose
  (FrostBodies.parts/rig), each vertex its own thickness/ridge/stagger and growth time (spread from the contact point on the
  side the cold came from, `way` from the server, and climbing from the feet); shoulder masses, hand crystals, struck-side
  clusters, scattered curved thin spines on the far side (alone or in 2-3s, never a row), medium crystals all round, ground
  frost + foot crystals. Server `IcemanFrost`: `DEEP_SEIZE` 14 ticks of slowing before the hold (players: input faded in
  `FrostFx.hold`; mobs: drift wound down), the hold time unchanged on top; FX_DEEP_FREEZE/BREAK carry the cold's / blow's way.
  Held pose: `FrostShell.pose` from `HumanoidModelMixin` and the new `LivingEntityRendererMixin` (after setupAnim, require 0;
  zombies' own arm animation runs after HumanoidModel's), body turn held in `turnPre`. Break: crack network (multi-source
  Dijkstra over the shell's vertices, across joints) then chunks cut from the rings (2-3 points wide, 1-2 bands, caps whole)
  letting go as the cracks reach them (some hold longer), flying, bursting into chips on landing; glass sounds swapped for
  amethyst. Screen frost now closes over DEEP_SEIZE.
- Spear impact (`IcemanSpearFx.spikes/erupt`): central ~1.6x bigger, two distinct medium FORMATIONS, smaller side pairs,
  thin icicle needles (also in the mace's eruptions).
- NOT DONE YET from that spec (user said stop for now): CTRL track volume + hit burst, SHIFT path irregular crystalline sides /
  icicles / staged formation, SHIFT+SPACE path erupting from the ground, sword-plant inward mist/snow/frost, per-weapon swing
  trails, a close-range review of brush/shell/ground/weapons in the new material.
- Walk trail `IcemanWalkFx` (client, everyone sees it): as he walks, a light trail of the brush's vapour + a few ice crystals rolls
  off his back/legs and sinks behind him (about a puff every 2 ticks vs the brush's 3-4 a tick; user: "sağ clicktekinden daha az").
  Follows horizontal speed, eased in/out; off during brush/slide/dash and in water; kept low in his own first-person view;
  amount = client config `walkMist` (0 off..2).
- Body preview offline (no game needed): `tools/claude/preview` (fake MC classes, shadow stubs, JOML jar, Dump.java views,
  render2.py samples the textures per texel); run `sh tools/claude/preview/preview.sh out.png "front,face"`.

