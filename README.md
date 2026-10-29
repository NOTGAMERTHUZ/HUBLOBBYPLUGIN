# NGTHub

A lightweight BungeeCord proxy plugin by **GAMERTHUZ** that provides `/hub` and `/lobby` commands to connect players to the configured lobby server.

## Features

- `/hub` command
- `/lobby` command
- Configurable lobby server name
- Simple chat feedback

## Requirements

- A BungeeCord-compatible proxy
- A lobby server registered in the proxy's `config.yml`

## Installation

1. Build the project with Maven.
2. Copy `target/NGTHub-1.0.0.jar` into the **proxy's** `plugins` directory.
3. Restart the proxy.
4. Edit `plugins/NGTHub/config.yml` if your lobby server name is not `lobby`.
5. Run `/hub` or `/lobby` while connected through the proxy.

> This is a proxy plugin, not a Paper plugin. Do not put the JAR only in the Lobby or Main Paper server's `plugins` folder.

## Configuration

```yaml
lobby-server: "lobby"

messages:
  connecting: "&aConnecting you to the lobby..."
  already-there: "&eYou are already in the lobby."
  server-not-found: "&cThe lobby server '&f%server%&c' is not registered in BungeeCord config.yml."
  player-only: "&cThis command can only be used by a player."
```

The value of `lobby-server` must exactly match the lobby server's registered name in the proxy `config.yml`.

## Build

```bash
mvn -U clean package
```

The output JAR should be at `target/NGTHub-1.0.0.jar`.

## License

Released under the MIT License. See [LICENSE](LICENSE).
