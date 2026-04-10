Quick test
==========

Start the dev client from the project root:

```bash
./gradlew runClient
```

What is already prepared:

- NeoForge 21.1.x dev runtime on Minecraft 1.21.x
- Minecraft assets downloaded into the local Gradle cache
- Runtime folder initialized at `run/`

First in-game check:

1. Create a Creative world.
2. Search for `Please Store Controller` in the inventory.
3. Put items into one or more villagers by using a setup that gives them inventory.
4. Right-click a chest, barrel, or another inventory block with the Please Store Controller item to store the target.
5. Place the Please Store Controller near villagers.
6. Power it with a short pulse such as a button.
7. Eligible nearby villagers with items should walk to the assigned inventory.
8. When a villager reaches the target, their inventory should be inserted into the container over time as space allows.
9. After unloading, the villager should return to normal behavior.
10. Add one or two item frames to the target chest and confirm only framed items are deposited there.
11. If two controllers target different chests that both match the same villager inventory, confirm the villager handles those chest visits one after the other.
12. Hover the crosshair over a placed controller and confirm the target inventory is highlighted correctly.
13. Open the Mods list, select `Please Store`, press `Config`, and confirm `Villager Search Radius` can be changed in the NeoForge config screen.

Useful paths:

- Run directory: `run/`
- Built jar: `build/libs/PleaseStore-1.0.2.jar`
