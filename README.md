# Virtual Craft

Virtual Craft is a Minecraft 1.21.1 NeoForge mod that adds a lightweight
virtual economy and electric-machine progression layer. Players can gather
electric resources, run energy machines, mine hash vouchers, and manage crypto
assets through an in-game holographic terminal.

## Features

- Electric ore, electric ingots, electric balls, and related crafting chains.
- Electric condenser, electric ball generator, PoW miner, and wireless power tower machines.
- Holographic terminal with wallet, exchange, shop, transfer, and mining settlement pages.
- Crypto-style wallet items and balances for BTC, ETH, USDT, SOL, TRX, and DOGE.
- Hash voucher mining flow: PoW miners consume energy and produce vouchers that can be settled in the terminal.
- Side configuration UI for supported machines.
- Virtual-world computer item and related component crafting chain.

## Requirements

- Minecraft `1.21.1`
- NeoForge `21.1.176` or newer in the `21.1.x` line
- Java `21`
- Modern UI `1.21.1-3.12.0.2` or compatible

## Building

Use the Gradle wrapper included in this repository. Do not use a system Gradle
installation.

```powershell
.\gradlew.bat build
```

The built mod jar is written to:

```text
build/libs/virtual_craft-0.1.jar
```

## Development

Run a development client:

```powershell
.\gradlew.bat runClient
```

Run a development server:

```powershell
.\gradlew.bat runServer
```

Run tests:

```powershell
.\gradlew.bat test
```

Compile only:

```powershell
.\gradlew.bat compileJava
```

## Repository Layout

```text
src/main/java/dev/tatami/virtualcraft/    Java source code
src/main/resources/                       Mod metadata, assets, data, recipes, loot tables
src/test/java/                            JUnit tests
gradle/                                   Gradle wrapper files
build.gradle                              NeoForge mod build script
gradle.properties                         Gradle JVM and cache settings
settings.gradle                           Gradle project settings
```

Local IDE settings, generated build output, dev-run saves, extracted reference
assets, and AI/tool scratch files are intentionally ignored and should not be
committed.

## License

This project is licensed under the Academic Free License v3.0. See
[LICENSE.txt](LICENSE.txt).

## Disclaimer

Virtual Craft is an unofficial Minecraft mod. It is not affiliated with or
endorsed by Mojang Studios, Microsoft, NeoForge, or Modern UI.
