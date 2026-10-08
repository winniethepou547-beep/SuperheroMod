# Notes for Claude (moved out of CLAUDE.md to keep every turn small; read this before working on it)

## Champion select (P)
- `client/gui/ChampionSelectScreen` (key P; the dev Pose Studio moved to F8 under a new key id) + roster data
  `client/gui/Champions` (name, title, accent, skills per hero; `MIN_SLOTS` "?" cards for future heroes).
  Splash art PNGs in `textures/gui/champions/<id>.png`, drawn by `tools/splash/make_all.py` (Pillow; comic-poster
  style). `ChampionStage` draws a stand-in `RemotePlayer` (own UUID, never the real player) as the chosen hero
  (Hulk via `HulkClient.asHulk`). LOCK IN plays a show per hero (`ChampionStage.length`, sounds on beats in `cues`),
  then the "KİLİTLENDİ" band (`BANNER` ticks), then the screen closes. `Showcase` makes the hero layers play actions
  (also eye glow + empty hand for Thor; Cyclops' hand to the visor via `HeroArmPose.isFiring`). Shows: Cyclops sweep
  right across the floor then the RMB beam left; Thor CHARGE whirl → THROW (3D `Mjolnir` flying in the frame) → CATCH
  → two swings → faces the screen, eyes lit, bolt behind; Zed R (lock, two `ZedBody` shadow copies, X, strike, burst);
  Sandman: 4 `SandSoldierModel` soldiers either side + giant behind; Hulk thunderclap; Ghost Rider rides in, skull
  turns; Black Panther claw combo + E release with a 2D sphere. Everyone ends facing the screen. `ThorLayer.rotationOf` unmirrors and unscales menu matrices (the hammer
  blew up in menus: `getNormalizedRotation` ignores scale); in the world it is unchanged. Sandman skin: `tools/skins/sandman_skin.py`.
  LOCK IN sends `ChampionLockPacket` (server: same as `/superhero hero`, no op needed).
  New hero = add a `Champion` entry + a splash script.

