# Platform Scaffold Implementation Plan

1. Create the frontend workspace, strict TypeScript base config, Vite configs, application roots, routes, proxy settings, and build smoke tests.
2. Create the Maven parent, module POMs, Java 21 compiler settings, resource profiles, health endpoint, and worker startup probe.
3. Import Spring AI `2.0.0-M5`, add OpenAI-compatible configuration properties, and add the WireMock `ChatModel.call` integration test.
4. Generate Maven Wrapper using JDK 21; install frontend dependencies without committing package caches.
5. Run frontend builds, full Maven test suite under JDK 21, source/diff/credential checks, and verify no `bak/` path changed.

## Validation Commands

```powershell
pnpm --dir frontend/web build
pnpm --dir frontend/admin build
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; backend\mvnw.cmd test
git diff --check
```
