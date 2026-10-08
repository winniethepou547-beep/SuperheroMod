# Notes for Claude (moved out of CLAUDE.md to keep every turn small; read this before working on it)

## The film engine (all three finishers use it)
`client/render/film/`:
- `FilmDirector` plays a `Film` for the local player: camera along Catmull-Rom `FilmShot`s, input locked, HUD hidden,
  no letterbox bars. Segments are either the real world or a **virtual stage** the film draws itself
  (backdrop shader `shaders/core/film_backdrop.fsh`: scenes clouds, hell plain, abyss, ARENA (Cyclops), DESERT (Sandman),
  PLAIN (Hulk), CITY (Panther), WASTELAND (Magneto)). The shader can be previewed in the cloud with Python moderngl on EGL
  (llvmpipe; `apt-get install libegl1`, `pip install moderngl`).
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

**Thor — God of Thunder: Aerial Punishment (X), `client/render/film/GodOfThunderFilm.java`, 18 s.** Shot in the real
world. Both bodies follow `AerialPath` (stage space); `ThorFx.performers` draws Thor (proxy render, real body held)
and the target as a jointed `FilmCast` puppet (`AerialPath.targetPose`). Beats in `ThorAction.ULT_*`: lunge, two quick
hits back to back (L→R bends the body, R→L breaks the X guard), crouch + uppercut sends the target straight up, a real
cloud ceiling closes in (no darkening filter — the user disliked it), Thor whirls and flies up, flings Mjolnir on into
the clouds (it sprays lightning and vanishes; the clouds keep flashing there), catches the target empty-handed in a
two-arm lock (forearms folded behind their back), cry with lightning out of his eyes, columns, whiteout + silence,
let go, the hammer falls back out of the clouds into his raised hand, target driven into the ground (crater, 70% max
health), Thor glides down beside it. Server `GodOfThunderSession` holds both to the end, then teleports Thor to the
landing spot and the target to the crater.

