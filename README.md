# NTM AE2 Recipe Switcher (Minecraft 1.21.1, NeoForge)

An AE2 pattern provider next to an NTM Continuum machine selects the machine's recipe by pushing a pattern.

## Build
1. Put the 1.21.1 NeoForge jars in `libs/`: AE2, NTM Continuum (hbm), Golem Core.
2. Run `gradlew build` with JDK 21. The jar is in `build/libs/`.

## How it works
- Step 1: Slap a AE2 Pattern provider onto the desired machine of choice (must use the recipe system, examples are the assembly machine and chemplant)
- Step 2: Put as many patterns in as you want
- Step 3: Enjoy

## Notes
- Machine must be empty for the thing to work
- Fluid amounts must be exact
- Blueprint recipes need the blueprint inserted in machine (duh)
- No match no craft its that simple