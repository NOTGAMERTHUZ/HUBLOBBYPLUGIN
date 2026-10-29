package com.gamerthuz.ngthub;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class NGTHub extends Plugin {
    private final Map<UUID, ScheduledTask> activeCountdowns = new ConcurrentHashMap<>();
    private String lobbyServer = "lobby";
    private int countdownSeconds = 3;
    private String countdownMessage;
    private String connectingMessage;
    private String alreadyThereMessage;
    private String alreadyCountingMessage;
    private String missingServerMessage;
    private String playerOnlyMessage;

    @Override
    public void onEnable() {
        loadSettings();
        getProxy().getPluginManager().registerCommand(this, new LobbyCommand("hub"));
        getProxy().getPluginManager().registerCommand(this, new LobbyCommand("lobby"));
        getProxy().getPluginManager().registerListener(this, new net.md_5.bungee.api.plugin.Listener() {
            @net.md_5.bungee.event.EventHandler
            public void onDisconnect(net.md_5.bungee.api.event.PlayerDisconnectEvent event) {
                ScheduledTask task = activeCountdowns.remove(event.getPlayer().getUniqueId());
                if (task != null) task.cancel();
            }
        });
        getLogger().info("NGTHub enabled: /hub and /lobby -> " + lobbyServer + " (" + countdownSeconds + "s countdown)");
    }

    private void loadSettings() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) getLogger().warning("Could not create plugin folder.");
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                if (in != null) Files.copy(in, file.toPath());
            } catch (IOException e) { getLogger().severe("Could not create config.yml: " + e.getMessage()); }
        }
        try {
            Configuration c = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
            lobbyServer = c.getString("lobby-server", "lobby").trim();
            countdownSeconds = Math.max(0, Math.min(30, c.getInt("countdown-seconds", 3)));
            countdownMessage = c.getString("messages.countdown", "&eSending you to lobby in &c%seconds%&e seconds...");
            connectingMessage = c.getString("messages.connecting", "&aConnecting to lobby...");
            alreadyThereMessage = c.getString("messages.already-there", "&eYou are already in the lobby.");
            alreadyCountingMessage = c.getString("messages.already-counting", "&cLobby teleport is already in progress.");
            missingServerMessage = c.getString("messages.missing-server", "&cLobby server '&f%server%&c' is not registered in the proxy config.");
            playerOnlyMessage = c.getString("messages.player-only", "&cOnly players can use this command.");
        } catch (IOException e) {
            getLogger().severe("Failed to read config.yml: " + e.getMessage());
        }
    }

    private void startLobbyTeleport(ProxiedPlayer player) {
        ServerInfo target = ProxyServer.getInstance().getServerInfo(lobbyServer);
        if (target == null) {
            send(player, missingServerMessage.replace("%server%", lobbyServer));
            return;
        }
        if (player.getServer() != null && player.getServer().getInfo().getName().equalsIgnoreCase(lobbyServer)) {
            send(player, alreadyThereMessage);
            return;
        }
        if (activeCountdowns.containsKey(player.getUniqueId())) {
            send(player, alreadyCountingMessage);
            return;
        }
        if (countdownSeconds == 0) {
            send(player, connectingMessage);
            player.connect(target);
            return;
        }
        final int[] remaining = {countdownSeconds};
        ScheduledTask task = getProxy().getScheduler().schedule(this, () -> {
            if (!player.isConnected()) {
                ScheduledTask removed = activeCountdowns.remove(player.getUniqueId());
                if (removed != null) removed.cancel();
                return;
            }
            if (remaining[0] > 0) {
                send(player, countdownMessage.replace("%seconds%", Integer.toString(remaining[0])));
                remaining[0]--;
            } else {
                activeCountdowns.remove(player.getUniqueId());
                send(player, connectingMessage);
                player.connect(target);
            }
        }, 0, 1, TimeUnit.SECONDS);
        activeCountdowns.put(player.getUniqueId(), task);
    }

    private static void send(ProxiedPlayer player, String message) {
        player.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message)));
    }

    private final class LobbyCommand extends Command {
        LobbyCommand(String name) { super(name); }
        @Override
        public void execute(net.md_5.bungee.api.CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer player)) {
                sender.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', playerOnlyMessage)));
                return;
            }
            startLobbyTeleport(player);
        }
    }
}
