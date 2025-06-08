package com.g2806.PremiumAuth;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class PremiumAuthPlugin extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private final Map<String, Boolean> awaitingPin = new HashMap<>(); // Tracks players needing to enter PIN
    private final Map<String, BukkitRunnable> countdownTasks = new HashMap<>(); // Tracks countdown tasks

    @Override
    public void onEnable() {
        // Initialize config
        saveDefaultConfig();
        config = getConfig();
        if (!config.contains("premium-users")) {
            config.set("premium-users", new ArrayList<String>());
            saveConfig();
        }

        // Register commands
        getCommand("premium").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cThis command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;
            if (args.length != 1 || !args[0].matches("\\d{4}")) {
                player.sendMessage("§cUsage: /premium <4-digit-pin> (e.g., /premium 1234)");
                return true;
            }

            String playerName = player.getName();
            String pin = args[0];
            config.set("premium-users." + playerName, pin);
            saveConfig();
            player.sendMessage("§aPIN set to " + pin + ". You’ll need to enter /pin <pin> on login.");
            getLogger().info("Set PIN for " + playerName);
            return true;
        });

        getCommand("pin").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cThis command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;
            String playerName = player.getName();
            if (!awaitingPin.getOrDefault(playerName, false)) {
                player.sendMessage("§cYou don’t need to enter a PIN.");
                return true;
            }

            if (args.length != 1 || !args[0].matches("\\d{4}")) {
                player.sendMessage("§cUsage: /pin <4-digit-pin> (e.g., /pin 1234)");
                return true;
            }

            String inputPin = args[0];
            String storedPin = config.getString("premium-users." + playerName);
            if (storedPin != null && storedPin.equals(inputPin)) {
                awaitingPin.put(playerName, false);
                // Cancel countdown task
                BukkitRunnable countdown = countdownTasks.remove(playerName);
                if (countdown != null) {
                    countdown.cancel();
                }
                player.sendMessage("§aPIN verified successfully!");
                getLogger().info("PIN verified for " + playerName);
            } else {
                player.kickPlayer("§cIncorrect PIN. Please reconnect and try again.");
                getLogger().info("Incorrect PIN attempt for " + playerName);
            }
            return true;
        });

        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String playerName = event.getName();
        if (config.contains("premium-users." + playerName)) {
            getLogger().info("Player " + playerName + " has a registered PIN. Awaiting /pin on join.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        if (config.contains("premium-users." + playerName)) {
            awaitingPin.put(playerName, true);
            player.sendMessage("§eYou have 30 seconds to enter your PIN using /pin <4-digit-pin>.");
            player.sendMessage("§eMovement and commands are disabled until you verify your PIN.");
            getLogger().info("Awaiting PIN from " + playerName);

            // Start countdown timer
            BukkitRunnable countdown = new BukkitRunnable() {
                int secondsLeft = 30;

                @Override
                public void run() {
                    if (!awaitingPin.getOrDefault(playerName, false)) {
                        cancel(); // Stop if PIN is verified
                        return;
                    }
                    if (secondsLeft > 0) {
                        player.sendMessage("§cYou have " + secondsLeft + " seconds to enter your PIN.");
                        secondsLeft--;
                    } else {
                        player.kickPlayer("§cYou didn’t enter your PIN in time. Please reconnect and try again.");
                        awaitingPin.remove(playerName);
                        countdownTasks.remove(playerName);
                        getLogger().info("Kicked " + playerName + " for not entering PIN in time.");
                        cancel();
                    }
                }
            };
            countdown.runTaskTimer(this, 0L, 20L); // Run every second (20 ticks)
            countdownTasks.put(playerName, countdown);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        String playerName = event.getPlayer().getName();
        if (awaitingPin.getOrDefault(playerName, false)) {
            event.setCancelled(true); // Prevent all movement (including rotation)
            event.getPlayer().sendMessage("§cYou must enter your PIN with /pin <4-digit-pin> to move.");
            getLogger().info("Blocked movement for " + playerName + " awaiting PIN.");
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String playerName = event.getPlayer().getName();
        if (awaitingPin.getOrDefault(playerName, false)) {
            String command = event.getMessage().toLowerCase().split(" ")[0];
            // Allow only /pin command
            if (!command.equals("/pin")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou must enter your PIN with /pin <4-digit-pin> before using commands.");
                getLogger().info("Blocked command '" + event.getMessage() + "' for " + playerName + " awaiting PIN.");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        awaitingPin.remove(playerName);
        // Cancel countdown task
        BukkitRunnable countdown = countdownTasks.remove(playerName);
        if (countdown != null) {
            countdown.cancel();
        }
        getLogger().info("Cleared PIN await status for " + playerName);
    }

    private void addPremiumUser(String playerName, String pin) {
        config.set("premium-users." + playerName, pin);
        saveConfig();
        getLogger().info("Added PIN for " + playerName);
    }

    private boolean isPremiumUser(String playerName) {
        boolean hasPin = config.contains("premium-users." + playerName);
        getLogger().info("Checked PIN status for " + playerName + ": " + (hasPin ? "Has PIN" : "No PIN"));
        return hasPin;
    }
}