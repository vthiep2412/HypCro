# HypCro Workspace Rules & Architecture Target

## Project Specifications
- **Target Minecraft Version**: `26.1.2` (Strictly 26.1.2 unobfuscated Mojang mappings)
- **Mod Loader**: Fabric Loader (`0.16.0+`)
- **Language**: Kotlin 2.4.10 (`fabric-language-kotlin:1.13.13+kotlin.2.4.10`)
- **JDK Runtime & Toolchain**: Azul Zulu 25 (`Java 25.0.1`) with Gradle `9.2.0`
- **Gradle Plugin**: `fabric-loom:1.15.5` with `fabric.loom.disableObfuscation=true`

## RULES
### Minecraft API Research & Ground Truth Protocol
- **Never guess Mojang/Fabric API signatures**: Minecraft 26.1.2 has major internal refactors. Do not rely on outdated memory or search results from older versions (1.8 - 1.20).
- **Inspect local Loom deobf bytecode**: When investigating vanilla classes, methods, or fields, always inspect the local deobfuscated JAR located in the Loom cache (`~/.gradle/caches/fabric-loom/.../minecraft-merged-deobf-26.1.2.jar`) using `javap -p` or class decompilation to get exact, guaranteed method signatures.

### DO NOT ATTEMPT TO REMVOE THESE
- Correct Comment, non-duplicated comment
- Print telemetry, commented telemetry