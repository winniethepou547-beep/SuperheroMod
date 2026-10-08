# SuperheroMod — notes for Claude

Forge 1.20.1 mod (Java 17, package `com.FIRNI.superheromod`). Heroes: **Ghost Rider**, **Cyclops**, **Sandman**, **Thor**, **Hulk**, **Zed**, **Black Panther**, **Magneto**, **Batman**, **Iceman**.
Older background and per-feature notes live in `docs/` (Turkish); `docs/PROJE_DEVIR_DOKUMANI.md` is the original handover.

## Working with the user
- The user is not a programmer and writes in **Turkish**. Answer in Turkish, plainly, without code jargon.
- They test everything themselves **in game** (IntelliJ `runClient` on their PC) and send screenshots or video
  timestamps. Nothing counts as done until they have seen it in game; say honestly what you could not check.
- Quality bar: Fisk's Superheroes level or better. Real-feeling physics, smooth easing (no snapping),
  sound, camera shake, effects anchored to the body, data-driven tuning.
- Change only what they asked for. When they say "don't touch anything else", don't.
- **Git workflow (cloud sessions):** do every request on its own branch and open a pull request against `main`.
  Never push straight to `main`. The user tests the branch locally; merging is their "yes".
  Explain at the end of each request, in Turkish, how to try it (branch name) — see "How the user tests" below.

## How the user tests — give them ONLY copy-paste terminal lines
The user does not want menus or steps. At the end of every request, give exactly these three blocks (in
Turkish labels), with the real branch name filled in. They paste them into IntelliJ's Terminal (PowerShell,
project folder), then press Run in IntelliJ as usual. Their PC never has local edits, so forced switches are safe.
- **Denemek için** (try the new branch):
  `git fetch origin; git switch -f -C <name> origin/<branch>` — `<name>` describes the work so the user can tell
  branches apart (e.g. `hulk`, `thor-cekic`), never a bare `deneme`.
- **Naming:** commit and PR titles start with the hero they are about ("Hulk: ...", "Thor: ...").
- **Beğenmedim, eski hale dön:**
  `git switch -f main`
- **Beğendim:** the user just writes "beğendim" in chat; YOU merge the PR into `main` on GitHub, then give them:
  `git fetch origin; git switch -f -C main origin/main`
If they want an already-merged change undone, revert it on GitHub yourself and give them the "Beğendim" line again.

## Checks to run before handing anything over
```
./gradlew build ghostChainPhysicsCheck ghostMotionCheck shaderCheck
```
`./gradlew backdropPreview -Pout=<dir>` renders the film backdrop shader offscreen to PNGs (needs a GPU; may not
work in the cloud). Keep everything compiling; tests live in `src/test/java/com/FIRNI/superheromod/`.

## The user's taste (learned from their corrections — apply everywhere)
- Real physics and real objects over drawn tricks: the cape block uses the real cloth (a drawn curtain was rejected), the
  sword is planted INTO the ground, debris is real blocks, left-behind ice is solid.
- Nothing pops in or out, nothing snaps: things form and break in stages, poses ease, effects stay anchored to the body.
- No darkening/colour filters over the screen to fake mood (rejected in Thor's film); no see-through "glass" materials.
- Not "mathematical/AI-looking": no perfect shapes, uniform repeats or clean splines; organic variation with control.
- Scale must read in game: primary effects big enough to see at once; secondary details subtle, never particle spam.
- Body language matters most in fights: attacks must read as attacks, both hands on what they hold, weight in every move.
- Their reference (video/image) is the brief: follow it closely, then adapt to Minecraft.
- When the user corrects something, write the lesson here (general) or in the hero's `docs/claude/*.md` (specific)
  in the same commit, so no later session repeats the mistake.
- Time is not a problem for them; they prefer slower work with fewer tokens to faster work that wastes them.

## Where the details are (read the one you need, not all)
Per-area notes, moved out of this file so every turn stays small. ALWAYS read `docs/claude/lessons.md` (the general
know-how: the user, the process that works, rejected designs, Minecraft rendering/collision gotchas, the tools) and the
matching file before working on a hero or system; update them (not this file) when that area changes:
- `docs/claude/film.md`: the film engine + the cinematics (Ghost Rider, Cyclops, Sandman, Thor) and what the user expects.
- `docs/claude/thor.md`, `hulk.md`, `zed.md`, `panther.md`, `magneto.md`, `batman.md`, `iceman.md`: each hero (keys, classes,
  look, the user's rules and rejections, its X film).
- `docs/claude/champion_select.md`: the P menu.
- `docs/*.md` (Turkish) are the user's own docs per hero.

## Techniques already solved — reuse them (look at the code before inventing anew)
- Hero skeleton: `XAction` (ids/timings/FX kinds) + `XConfig` + server `XController` + State/Fx/Input packets; clients animate
  from the synced clock. Own-client prediction on a local clock: Panther (`PantherClient.action/clock`, `localFrom`).
- Pure path functions shared by server and client (no position packets): `PantherPath`, `TakedownPath`, `BatmanFlash.step`.
- Bodies: box bodies + layer + pure motion (`PantherMotion.Pose/Track`, Hermite key poses, sign conventions at its top) used by
  Panther, Magneto, Batman, Iceman; player model hidden via `HeroArmPose`; first-person arms from the same pose.
- Hands onto an object: `SpikePull` (Magneto), `IcemanWeaponReach` (two-hand solver).
- Cloth: `client/render/cloth/CapeCloth` (verlet; Batman, Magneto; held/grabbed/glide wings).
- World effects: draw at AFTER_TRANSLUCENT_BLOCKS (`IceStage` pattern); `FilmFx` additive/soft/solid batched buffers; custom
  render types via a `RenderType` subclass (`FilmFx`, `IceTypes`); vanilla `eyes()` culls back faces: use a no-cull type.
- Screen effects: `RenderGuiEvent.Pre` + frame copy (`BatmanFlashFx`); world matrices captured at AFTER_TRANSLUCENT_BLOCKS and
  redrawn on the HUD (`BatmanThermal`; PostChain and AFTER_LEVEL never showed in game).
- Entities: hittable trap `SonicEmitterEntity`; invisible solid collision box `IceSolidEntity`; real flying blocks
  `HulkBlocks.launch` with break rules/budget in `HulkBlocks`.
- Wheel UI with camera lock: `BatmanWheel`, `IcemanWheel`. Skill list: `HudStyle.skill`.
- No Gradle/Minecraft here: `sh tools/claude/typecheck/typecheck.sh` (stub type-check, baseline "issues: 8") and
  `sh tools/claude/preview/preview.sh out.png views` (offline body render) live in the repo.
- Textures/art from Python: skins (`tools/skins`, `tools/textures/iceman_skin.py`), icons (`tools/icons`), splash (`tools/splash`),
  sounds (`tools/sounds/synth.py`); offline model preview harness (see `docs/claude/iceman.md`).
- Ultimates: the film engine (`docs/claude/film.md`), `FilmSessions` on the server.
- Known traps: menu matrices are mirrored/scaled (`ThorLayer.rotationOf`); vertex colours over 1 wrap (clamp); a stale pose
  sample froze Ghost Rider's arms; brace-less ifs doubled sounds.

## Working economically (the user asked: fewer tokens, never less quality)
- Do the work yourself, in order; use helper agents only when a task truly needs parallel work, and say so first.
- Read only the parts of files you need (grep, then ranges); do not re-read what you already have.
- Keep previews/renders to what decides something. Commit/push only finished work to the request's branch.

## Other state
- Ghost Rider gameplay: chain combos with real chain physics, R hellfire breath (damage every second), Hell Cycle
  bike, F hell-pit slam. Keep the physics checks passing. `GhostChainRenderer.pose` sets the model arms only from the same fresh
  pose the layer draws (`motion()`, 20-tick staleness) — a stale sample used to freeze the arms up; the vanilla click swing is off
  for him (`GhostClientEvents.noSwing`). `GhostRiderLayer` eases the arms per hit: a new blend starts only when the TARGET
  arm jumps (target vs target, `Drawn.target`), never target vs the lagging drawn arm (that restarted it every frame and froze them). Watch brace-less ifs when layering sounds (two whips once played every tick).
- Sandman: Colossus ultimate is parked ("later"). `ColossusPose.couple` keeps torso and dune one body: the dune leans about its
  foot and squashes/widens to follow the waist (min 55%, then it lifts the torso); the slam and the forming crouch bend at the waist. Cyclops: sounds still unfinished.
- Large local-only folders are gitignored: `tmp/`, `references/` (reference videos), `logs/`.

## Sounds and the skill HUD
- The mod's own sounds are synthesised by `tools/sounds/synth.py` (numpy/scipy + ffmpeg libvorbis; nothing recorded or
  ripped from games/videos — the user asked for "sounds from the internet", the cloud blocks sound sites and ripped audio
  would be copyrighted). It writes `assets/superheromod/sounds/<group>/<name>.ogg` + `sounds.json`; `core/sound/ModSounds`
  registers every `<group>.<name>` (constants `GROUP_NAME`, regenerate both together). They are LAYERED with the vanilla
  sounds at each call site (abilities, FX, film cues), never replacing them. Groups: fx, magneto, panther, thor, hulk, zed,
  cyclops, ghost, sandman, batman. Check new sounds as spectrograms (you cannot listen).
- Skill list on the left: `HudStyle.skill` (dark pill, hero-colour stripe, cooldown fill + seconds, ready sweep, `active`
  pulse); each hero client's `hint(...)` routes through it with its accent. Thor/Cyclops/Sandman/Ghost Rider have no list yet.
