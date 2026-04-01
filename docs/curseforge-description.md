# Please Store

`Please Store` is a NeoForge 1.21.1 mod that adds a redstone-controlled villager storage controller. You bind one controller to one storage block, pulse it with redstone, and nearby villagers carrying matching items will walk over, unload, and then resume normal behavior.

## Demo

- [Watch the demo video](https://youtu.be/WTRLtzeL9do)

## How It Works

- Use the `Please Store Controller` item on a chest, barrel, or any other inventory-capable block to store the target inventory.
- Place the controller where you want the wiring.
- Pulse the controller with redstone to start one delivery run.
- Nearby villagers carrying matching items are called to that storage.
- Villagers unload into the target inventory, wait for the chest to close, and then either continue to another queued matching controller or return to their normal AI.

## Storage Filtering

- A controller still targets exactly one storage block.
- If the target chest, barrel, or connected double chest has item frames attached, the framed items define what may be stored there.
- One frame means one allowed item.
- Multiple frames mean multiple allowed items.
- No frames means the storage accepts anything the villager can unload there.

This lets you split storage naturally:

- one chest for seeds
- one chest for wheat
- one chest for carrots

If a villager is carrying items for more than one matching controller, those deliveries are handled one after the other as separate chest visits.

## Settings

- The mod uses NeoForge's built-in config screen.
- Open the Mods list, select `Please Store`, and press `Config`.
- `Villager Search Radius` controls how far from the target storage the controller searches for villagers.

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

- Minecraft `1.21.1`
- NeoForge `21.1.221`
