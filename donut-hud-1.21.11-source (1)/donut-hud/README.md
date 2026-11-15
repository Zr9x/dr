# Donut HUD (Fabric, Minecraft 1.21.11)

Right Shift opens the settings (rebindable under Controls -> Donut HUD).
J toggles the Sound Detector on/off (rebindable).

## HUD modules
FPS, Coordinates, Direction, Ping, CPS, Speed, Clock, Armor, Keystrokes.
Each has an ON/OFF toggle and a corner anchor; modules in the same corner stack.

## Sound Detector
Boxes blocks/entities that make sound inside a chunk range (slider, 1-16 chunks), through walls.
- Blocks: lit furnaces/blast furnaces/smokers, lit campfires, nether portals, bee nests/hives,
  beacons/conduits, jukeboxes with a disc, sculk sensors/shriekers/catalysts, note blocks/bells.
- Entities: players, hostile mobs, animals (incl. water creatures), villagers/traders, minecarts.
- Settings page (Sound Detector -> Settings) lets you toggle each category and labels.
- Range is capped by how many chunks your client has actually loaded (server view distance).

Heads-up: highlighting things through walls is treated as cheating on most multiplayer servers.
Check a server's rules before using it there.

## Getting the .jar
**GitHub Actions (no local setup):** upload this folder to a GitHub repo, open Actions ->
"Build jar", download the `donut-hud-jar` artifact.
**Local:** JDK 21 + Gradle 9.x, then `gradle build`. Jar: `build/libs/modularhud-1.0.0.jar`.
Put it in your 1.21.11 Fabric instance's `mods` folder with Fabric API.
Match `gradle.properties` to https://fabricmc.net/develop (1.21.11) if the build complains.
