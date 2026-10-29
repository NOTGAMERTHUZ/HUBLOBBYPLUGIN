package com.gamerthuz.ngthub;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class NGTHub extends Plugin implements Listener {
    private final Map<UUID, ScheduledTask> activeCountdowns = new ConcurrentHashMap<>();
    private final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();

    private String lobbyServer = "lobby";
    private int countdownSeconds = 3;
    private String countdownMessage = "&eSending you to lobby in &c%seconds%&e seconds...";
    private String connectingMessage = "&aConnecting to lobby...";
    private String alreadyThereMessage = "&eYou are already in the lobby.";
    private String alreadyCountingMessage = "&cLobby teleport is already in progress.";
    private String missingServerMessage = "&cLobby server '&f%server%&c' is not registered in the proxy config.";
    private String playerOnlyMessage = "&cOnly players can use this command.";
    private String connectionFailedMessage = "&cCould not connect to the lobby. Please try again.";

    @Override
    public void onEnable() {
        loadSettings();

        getProxy().getPluginManager().registerCommand(this, new LobbyCommand("hub"));
        getProxy().getPluginManager().registerCommand(this, new LobbyCommand("lobby"));
        getProxy().getPluginManager().registerListener(this, this);

        getLogger().info("NGTHub enabled: /hub and /lobby -> " + lobbyServer
                + " (" + countdownSeconds + "s countdown)");
    }

    @Override
    public void onDisable() {
        for (ScheduledTask task : activeCountdowns.values()) {
            task.cancel();
        }
        activeCountdowns.clear();
        inProgress.clear();
    }

    private void loadSettings() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin folder.");
        }

        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                getLogger().severe("Could not create config.yml: " + e.getMessage());
            }
        }

        try {
            Configuration c = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
            lobbyServer = c.getString("lobby-server", "lobby").trim();
            if (lobbyServer.isEmpty()) lobbyServer = "lobby";

            countdownSeconds = Math.max(0, Math.min(30, c.getInt("countdown-seconds", 3)));
            countdownMessage = c.getString("messages.countdown",
                    "&eSending you to lobby in &c%seconds%&e seconds...");
            connectingMessage = c.getString("messages.connecting", "&aConnecting to lobby...");
            alreadyThereMessage = c.getString("messages.already-there", "&eYou are already in the lobby.");
            alreadyCountingMessage = c.getString("messages.already-counting",
                    "&cLobby teleport is already in progress.");
            missingServerMessage = c.getString("messages.missing-server",
                    "&cLobby server '&f%server%&c' is not registered in the proxy config.");
            playerOnlyMessage = c.getString("messages.player-only", "&cOnly players can use this command.");
            connectionFailedMessage = c.getString("messages.connection-failed",
                    "&cCould not connect to the lobby. Please try again.");
        } catch (IOException e) {
            getLogger().severe("Failed to read config.yml: " + e.getMessage());
        }
    }

    private void startLobbyTeleport(ProxiedPlayer player) {
        UUID id = player.getUniqueId();

        ServerInfo target = ProxyServer.getInstance().getServerInfo(lobbyServer);
        if (target == null) {
            send(player, missingServerMessage.replace("%server%", lobbyServer));
            return;
        }

        if (player.getServer() != null
                && player.getServer().getInfo().getName().equalsIgnoreCase(lobbyServer)) {
            send(player, alreadyThereMessage);
            return;
        }

        // Set this first so repeated commands cannot start duplicate countdowns/connections.
        if (!inProgress.add(id)) {
            send(player, alreadyCountingMessage);
            return;
        }

        if (countdownSeconds == 0) {
            connectToLobby(player, target);
            return;
        }

        send(player, countdownMessage.replace("%seconds%", Integer.toString(countdownSeconds)));
        final int[] remaining = {countdownSeconds - 1};

        // First scheduled tick is one second later, avoiding a schedule/map registration race.
        ScheduledTask task = getProxy().getScheduler().schedule(this, () -> {
            if (!player.isConnected()) {
                cancelPlayerState(id);
                return;
            }

            if (remaining[0] > 0) {
                send(player, countdownMessage.replace("%seconds%", Integer.toString(remaining[0])));
                remaining[0]--;
                return;
            }

            activeCountdowns.remove(id);
            connectToLobby(player, target);
        }, 1, 1, TimeUnit.SECONDS);

        activeCountdowns.put(id, task);
    }

    private void connectToLobby(ProxiedPlayer player, ServerInfo target) {
        send(player, connectingMessage);
        player.connect(target, (success, error) -> {
            if (!success) {
                inProgress.remove(player.getUniqueId());
                send(player, connectionFailedMessage);
                if (error != null) {
                    getLogger().warning("Lobby connection failed for " + player.getName()
                            + ": " + error.getMessage());
                }
            }
            // On success, ServerConnectedEvent clears inProgress when the lobby connection completes.
        });
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        if (event.getServer().getInfo().getName().equalsIgnoreCase(lobbyServer)) {
            inProgress.remove(event.getPlayer().getUniqueId());
            ScheduledTask task = activeCountdowns.remove(event.getPlayer().getUniqueId());
            if (task != null) task.cancel();
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        cancelPlayerState(event.getPlayer().getUniqueId());
    }

    private void cancelPlayerState(UUID id) {
        inProgress.remove(id);
        ScheduledTask task = activeCountdowns.remove(id);
        if (task != null) task.cancel();
    }

    private static void send(ProxiedPlayer player, String message) {
        player.sendMessage(TextComponent.fromLegacyText(
                ChatColor.translateAlternateColorCodes('&', message)));
    }

    private final class LobbyCommand extends Command {
        LobbyCommand(String name) {
            super(name);
        }

        @Override
        public void execute(net.md_5.bungee.api.CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer player)) {
                sender.sendMessage(TextComponent.fromLegacyText(
                        ChatColor.translateAlternateColorCodes('&', playerOnlyMessage)));
                return;
            }
            startLobbyTeleport(player);
        }
    }
}
