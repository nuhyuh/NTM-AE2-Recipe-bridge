# NTM AE2 Recipe Switcher (Minecraft 1.21.1, NeoForge)

An AE2 pattern provider next to an NTM Continuum machine selects the machine's recipe by pushing a pattern.

## Build
1. Put the 1.21.1 NeoForge jars in `libs/`: AE2, NTM Continuum (hbm), Golem Core.
2. Run `gradlew build` with JDK 21. The jar is in `build/libs/`.

## How it works
- Registers AE2's `CRAFTING_MACHINE` capability on every `hbm` block.
- Folded multiblock cells resolve to the owning machine core, so providers can touch any cell.
- A pushed pattern is matched to one of the machine's recipes; on match the lane switches (if idle) and items/fluids are inserted.

## Notes
- A lane switches only when idle.
- Fluid amounts must match the recipe.
- Blueprint-pooled recipes need a matching blueprint.
- Non-matching patterns are refused.