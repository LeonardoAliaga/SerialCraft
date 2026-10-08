---
title: How to run SerialCraft tests with Gradle, JUnit and GameTest
description: Learn how to execute SerialCraft Java unit tests, Minecraft GameTests and GitHub Actions CI.
---
# Testing SerialCraft: JUnit, Minecraft GameTest and CI

Starting with **0.4.6-beta**, the `test` branch includes Java unit tests, Minecraft world tests and a GitHub Actions **Build and test** workflow.

## Prepare the environment

Use the `test` branch, a **Java 25 JDK**, and Gradle Wrapper from the repository. In a terminal run `java -version` and `git branch --show-current`.

| Goal | Windows | Linux / macOS |
| --- | --- | --- |
| Unit tests only | `.\gradlew.bat test` | `./gradlew test` |
| Full build and unit tests | `.\gradlew.bat build` | `./gradlew build` |
| Minecraft GameTests | `.\gradlew.bat -PioGameTests runGameTest` | `./gradlew -PioGameTests runGameTest` |

The optional `-PioGameTests` switch is a Gradle project property. `build.gradle` uses it to enable Fabric's test source set and GameTest environment.

## What does each test cover?

**JUnit** checks algorithms and code behavior without requiring a physical device: signal conversion, gates, bounded queues, telemetry, codec parsing, authentication, persistence and waveform helpers.

**GameTest** runs scenarios inside a temporary Minecraft testing world. The suite covers the five I/O faces, redstone input and output, gates, levers, buttons, comparators, repeaters, neighboring blocks, ownership rules, chunks and a functional scenario with 64 modules.

**Build** compiles and packages the mod in addition to configured unit tests. A passing build is not proof that every physical board or GUI layout has been checked.

## GitHub Actions

When you push to `test` or open a PR targeting `test`, [GitHub Actions](https://github.com/LeonardoAliaga/SerialCraft/actions) runs `./gradlew build --console=plain` followed by `./gradlew -PioGameTests runGameTest --console=plain`. Reports and logs are uploaded as workflow artifacts.

Green means the configured commands succeeded; red requires opening the logs. Unit test reports are normally located at `build/reports/tests/test/index.html`, with GameTest logs in `build/run/gameTest/logs`.

For a combined local run:

~~~powershell
.\gradlew.bat build -PioGameTests runGameTest
~~~

## Test limitations

GameTests do not substitute for an actual Arduino/ESP32, real USB disconnects, Wi-Fi hardware, UI inspection or long-running performance measurements. The [test matrix](/io-test-matrix) and [audit report](/audit-0.4.6) document what was automated and what remains a manual check.

If a run fails, identify the first actionable error, verify the JDK and distinguish compilation/test failures from unavailable dependency repositories.
