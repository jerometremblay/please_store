# Please Store

`Please Store` is a NeoForge mod for Minecraft 1.21.x that adds a redstone-controlled villager storage controller. You bind a controller to one storage block, place it where you want the wiring, and then pulse it to call nearby villagers carrying matching items so they unload and return to normal AI.

![CurseForge thumbnail](docs/media/curseforge-thumbnail.png)

## Demo

- [Watch the demo video](https://youtu.be/WTRLtzeL9do)

## How It Works

- Use the Please Store Controller item on a chest, barrel, or any other inventory-capable block to store the target.
- Place the control block where you want the wiring.
- Pulse the block with redstone to start one controller run.
- Nearby villagers who already have items when the pulse happens can be called.
- Villagers walk to the target inventory, unload what they can, and then resume normal behavior.
- If item frames are attached to the target chest or barrel, only framed items are accepted there.
- If multiple controllers match the same villager, those controller jobs are handled one after the other instead of merging them into one unload.

## Settings

- The mod uses NeoForge's built-in config screen.
- Open the Mods list, select `Please Store`, and press `Config` to adjust settings.
- `Villager Search Radius` controls how far from the target storage the mod searches for villagers to call.

## Recipe

```text
R Q R
W C W
P I P
```

- `R` = Redstone Dust
- `Q` = Comparator
- `W` = Any wooden slab
- `C` = Chest
- `P` = Any planks
- `I` = Iron Ingot

## Requirements

- Minecraft `1.21.x` (developed against `1.21.1`)
- NeoForge `21.1.x` (developed against `21.1.221`)

## Development

```bash
./gradlew runClient
./gradlew build
```
