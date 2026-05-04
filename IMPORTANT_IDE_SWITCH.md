# IMPORTANT: IDE Switch Guardrails

This project is used in both Android Studio and VS Code.
To avoid `jlink.exe does not exist` and Java path drift, follow these rules.

## Canonical JDK (use this everywhere)

Use only:

`D:\Program Files\Android Studio\jbr`

Do not use:

`C:\Users\Acer\.vscode\extensions\redhat.java-...\jre\...`

That VS Code extension runtime may not include the required `jlink.exe`.

## Project Files That Must Stay Stable

- `.idea/gradle.xml` must keep: `gradleJvm = jbr-21`
- `.idea/misc.xml` must keep: `project-jdk-name = jbr-21`
- `.vscode/settings.json` must keep Java/Gradle home pointing to `D:\Program Files\Android Studio\jbr`

## Before Switching IDE (every time)

1. Run: `./gradlew --stop`
2. Close the current IDE fully.
3. Open the other IDE.
4. Run: `./gradlew -version`
5. Confirm the JVM path shown is not from `.vscode\extensions\redhat.java...`

## Android Studio Check

1. `Settings > Build, Execution, Deployment > Build Tools > Gradle`
2. `Gradle JDK` = `jbr-21`
3. `File > Project Structure > SDK Location` uses Android Studio JBR/JDK 21 setup

## VS Code Check

This repo includes workspace settings in `.vscode/settings.json` to pin Java:

- `java.jdt.ls.java.home`
- `java.import.gradle.java.home`

Both should point to:

`D:\Program Files\Android Studio\jbr`

## AI Safety Prompt (copy/paste)

When asking any AI to edit this repo, prepend:

`Do not change Java/JDK paths. Keep .idea/gradle.xml gradleJvm=jbr-21, .idea/misc.xml project-jdk-name=jbr-21, and .vscode/settings.json pointing to D:\Program Files\Android Studio\jbr.`

## Quick Recovery If Error Returns

1. Re-check the three files above.
2. Run: `./gradlew --stop`
3. Reopen IDE and sync.
4. Run: `./gradlew :app:compileDebugJavaWithJavac --no-daemon`
