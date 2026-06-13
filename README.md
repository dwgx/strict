# strict

Fabric server access-control and mod verification experiment for Minecraft 1.21.x.

strict is a version-specific Minecraft security/modding experiment. The goal is not to claim impossible anti-cheat magic; the goal is to test stricter client/server checks, access-control ideas, and mod verification flows in a controlled environment.

## Current Scope

- Minecraft Fabric mod.
- Build system: Gradle / Fabric Loom.
- Main entry: `src/main/java/com/strict/Main.java`.
- Fabric descriptor: `src/main/resources/fabric.mod.json`.
- Target family: Minecraft 1.21.x, based on current repository metadata.

## What It Is For

- Testing strict server/client access-control ideas.
- Exploring mod verification and version-specific checks.
- Building a controlled test environment before any real-server deployment.

## What It Is Not

- Not a guarantee against bypasses.
- Not a production anti-cheat claim.
- Not something to install on a public server without testing and logs review.

## Build

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

## Deployment Notes

- Match the Minecraft/Fabric version exactly.
- Test with a local client and staging server first.
- Document expected false positives before using it with real players.
- Keep authentication tokens, server addresses, and private config out of the repository.

## Project Status

Experimental. Keep the claims narrow, keep the tests reproducible, and document what the checks actually prove.

## License

MIT.
