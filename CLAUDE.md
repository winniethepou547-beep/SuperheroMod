# SuperheroMod — notes for Claude

Forge 1.20.1 mod (Java 17, package `com.FIRNI.superheromod`). Heroes: **Ghost Rider**, **Cyclops**, **Sandman**.
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

## How the user tests (explain this to them when relevant)
1. In IntelliJ: `Git → Fetch`, then the branch widget bottom right → `origin/<branch>` → **Checkout**.
2. Run `runClient`, try it.
3. Likes it → merge the PR on GitHub (or ask Claude to). Then in IntelliJ checkout `main` and `Git → Pull`.
4. Doesn't like it → just checkout `main` again; the PR stays unmerged or is closed. Nothing on `main` changed.
   If something was already merged and they want the old version back, revert that PR (GitHub "Revert" button).

## Checks to run before handing anything over
```
./gradlew build ghostChainPhysicsCheck ghostMotionCheck shaderCheck
```
`./gradlew backdropPreview -Pout=<dir>` renders the film backdrop shader offscreen to PNGs (needs a GPU; may not
work in the cloud). Keep everything compiling; tests live in `src/test/java/com/FIRNI/superheromod/`.

## The film engine (all three finishers use it)
`client/render/film/`:
- `FilmDirector` plays a `Film` for the local player: camera along Catmull-Rom `FilmShot`s, input locked, HUD hidden,
  no letterbox bars. Segments are either the real world or a **virtual stage** the film draws itself
  (backdrop shader `shaders/core/film_backdrop.fsh`: scenes clouds, hell plain, abyss, ARENA (Cyclops), DESERT (Sandman)).
  Fades, impact frames, sound cues (vanilla sounds only, no copyrighted audio), grade, on-screen captions.
- `FilmCast` draws performers: players and zombies as jointed puppets (11 joints, `ActorPose`, degrees in `j()`);
  other mobs through `FilmPosedModel`, which poses their own model limbs (villagers/witches get two free arms);
  arm-less creatures react with the whole body. `FilmCast.Track` = pose keys with blend times and per-joint delays.
- `FilmFx` = additive glows, beams, streaks, dust, rings, shadows, solid cubes. Always fetch buffers fresh.
- Server side: `core/film/FilmSessions` holds attacker + target in place and syncs the clock (`FilmSessionPacket`).
- Ghost Rider's Penance film is older and has its own server class `PenanceStare` + `PenanceClient`.
- `ActorPose` sign notes: positive CHEST/HEAD x = lean forward; arm x negative = raise forward;
  right arm z positive = outward; `body(roll, pitch)`: negative pitch tips the body forward.

## The three cinematics and what the user expects
**Ghost Rider — Penance Stare (X), `client/render/ghost/PenanceFilm.java`, 20.5 s.** Beat-for-beat remake of the
2XKO Thresh trailer 1:19–1:26 in Ghost Rider fire colours with our models: grab, into his eye, descent with
chain-bound soul, highway ride end to end, bike slides in, he jumps off and whips the chain into the lens, ride the
chain tip, soul in a pillar of fire, chains bind every bone, burning hand closes, web of chains (impact), dark iron
cage slams down, skull laughs, the soul's light spirals into one point in front of his face, rays, white flash,
back to the world. Reference look: "The Shooting Star" Minecraft cinematic. Longer is fine; no fast cutting.

**Cyclops — MAXIMUM POWER (X), `client/render/film/MaximumPowerFilm.java`, 21 s.** Near-black opening, only the
visor's red bloom in the fog; a thin blast at the target's feet; Cyclops emerges, light gathers. Beam leaves from
the **eyes** (`eyes(t)` follows his pose). The target must visibly struggle: arms crossed in an X, blown back off
the ground, skid, recover, trembling, two hard steps forward into the beam. Hand to the visor, beam cut, silence,
"MAXIMUM POWER", visor torn off, **very thick** giant beam, guard breaks, launched into the fog, distant explosion.
Damage: 85% max health at the explosion, knockback when the film hands back (`MaximumPowerSession`).

**Sandman — Sand Army, `client/render/film/SandArmyFilm.java`, 20 s.** Still a no-damage rehearsal started with the
**H** key (op) or `/cinematic preview sand_army <target>`. Desert at golden hour, storm building. Ten sand soldiers
rise, two attack and the defender breaks both (straight right, spinning backhand — attacks must read as attacks,
one arm, not the guard), the rest seize and beat them, mound, pillar lifts them, a giant sand soldier rises behind
and slams them into the sand, the giant then **crumbles into sand** clumps. The user said this one is nearly perfect;
the defender's body language matters most.

## Other state
- Ghost Rider gameplay: chain combos with real chain physics, R hellfire breath (damage every second), Hell Cycle
  bike, F hell-pit slam. Keep the physics checks passing.
- Sandman: Colossus ultimate is parked ("later"). Cyclops: sounds still unfinished.
- Large local-only folders are gitignored: `tmp/`, `references/` (reference videos), `logs/`.
