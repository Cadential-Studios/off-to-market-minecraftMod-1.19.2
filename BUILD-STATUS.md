# Build status — Phase 1 skeleton (honest record, 2026-11-01)

**The skeleton has NOT been compiled. The build cannot run on this VM.**
This file records exactly where it stopped and why, with evidence, plus the
commands that run it on any normal machine.

## What was attempted

- No JDK existed on the VM; Temurin OpenJDK 17.0.21 and Gradle 8.8 were
  fetched into `.tools/` (gitignored) and verified working
  (`java -version`, `gradle --version` both clean).
- `gradle build` (and even `gradle wrapper` in an empty test project) fails
  in under 5 seconds, before any project configuration:

```
FAILURE: Build failed with an exception.
* What went wrong:
Could not receive a message from the daemon.
Caused by: org.gradle.internal.remote.internal.MessageIOException:
  Could not read message from '/127.0.0.1:<random port>'.
Caused by: java.lang.IllegalArgumentException: Unexpected type tag 109 found.
```

## Root cause (proven, not guessed)

This sandbox intercepts TCP connections made by JVM processes. A minimal
Java program connecting to a local Python HTTP server on 127.0.0.1 received
this banner instead of the server's response:

```
muse: Other TCP connections is turned off for this assistant
```

(First byte `m` = 109 — the exact "unexpected type tag" Gradle choked on.
Non-JVM clients — curl, Python — reach the same server fine.)

Gradle 8.8 runs every build in a (single-use) daemon process and talks to
it over a loopback socket on a random port, so **no Gradle build of any
kind can execute in this environment** — the failure is in the
client↔daemon handshake, before Loom, Fabric maven, or Mojang downloads
are even reached. Artifact downloads are separately proxied here (a local
fetch proxy on 127.0.0.1:18900 with Gradle init-script mirrors; Fabric
maven was added to the mirror set in the project-local Gradle home), but
the daemon block makes that moot on this VM.

## To build on a full machine (Scotty's Victus / Tritium / any dev box)

```powershell
# needs JDK 17 on PATH or JAVA_HOME set
cd off-to-market-fabric
gradle wrapper --gradle-version 8.8   # any Gradle 8.8+ install; generates gradlew
./gradlew build                        # or: gradle build
```

Expected result: Loom downloads MC 1.20.1 + Fabric Loader 0.16.14 +
Fabric API 0.92.2, compiles the 6 skeleton classes, and produces
`build/libs/offtomarket-fabric-0.1.0-alpha.jar`. First run downloads
several hundred MB and takes minutes. `./gradlew runClient` should then
show the three coins in the "Off to Market" creative tab (coin textures
are still to be copied from the Forge repo's assets — 21 PNGs — so items
render with the missing-texture pattern until Phase 2 copies them; names,
tooltips, rarities and the gold coin's foil shimmer are all in code/lang).

## What IS verified about the skeleton

- All registry names, lang strings, rarities, stack sizes and model JSONs
  were copied 1:1 from the Forge source at v0.6.7 (commit `151fb97`) —
  see PORT-AUDIT.md for the full mapping.
- `CoinItem`/`CoinType` bodies are the Forge classes with only the package
  changed (both sides use Mojang mappings).
- Code was written against the real 1.20.1 Mojmap + Fabric API 0.92.2
  surface (`BuiltInRegistries`, `FabricItemGroup`, extended patterns
  documented in the audit) — but per the fleet rule, "compiles clean"
  remains UNVERIFIED until the command above is run somewhere.


---

## Tritium build verification — 2026-11-01: ✅ BUILD SUCCESSFUL

The skeleton **compiles and packages clean** on Tritium. The "compiles
clean remains UNVERIFIED" caveat above is now closed.

- **Where:** `/home/scott/off-to-market-fabric/` on Tritium (transferred
  from this VM as a tarball, `.tools/` excluded; remote SHA-256 matched
  the local tarball exactly: `96a5de32…b4eb`).
- **Toolchain:** Tritium already had JDK 17 —
  `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` (the system default
  `java` there is 25, so the pin matters). Gradle 8.8 was provisioned
  user-space at `/home/scott/.tools-otm/gradle-8.8` and used to generate
  the project wrapper (`gradlew`), which ran all builds.
- **Artifact:** `build/libs/offtomarket-fabric-0.1.0-alpha.jar`
  (10,615 bytes) + `-sources.jar` (8,117 bytes). Contents verified: all 6
  skeleton classes, lang file, 3 coin model JSONs, `fabric.mod.json` with
  version expanded to `0.1.0-alpha`, LICENSE renamed in. Full Loom
  pipeline ran, including `remapJar`.

### Fixes applied during verification (2, both flagged per protocol)

1. **`CoinItem.java` — wrong `@Nullable` import (source fix).** First
   attempt failed: `package javax.annotation does not exist` (2 errors).
   The Forge original gets JSR-305 from ForgeGradle's classpath; stock
   Loom/MC 1.20.1 uses JetBrains annotations. Changed
   `import javax.annotation.Nullable;` →
   `import org.jetbrains.annotations.Nullable;` (line 10). Applied to
   both the Tritium copy and this VM's source of record.
2. **`build.gradle` — jar rename closure (build-file fix).** Second
   attempt: `compileJava` passed, `:jar` failed at line 54 —
   `String.get()` no-signature error: in this DSL context
   `project.archivesBaseName` already resolves to a `String`. Dropped
   the `.get()`: `"${it}_${project.archivesBaseName}"`. Applied to both
   copies.

Third attempt: **BUILD SUCCESSFUL**. Still outstanding (unchanged from
Phase 1): coin textures (21 PNGs) copy in Phase 2, and a `runClient`
smoke test has not been done yet.
