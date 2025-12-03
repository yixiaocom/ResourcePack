package resourcepack.resourcepack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.logging.Level;

public class ResourcePack extends JavaPlugin implements Listener {
    private volatile String resourcePackUrl;
    private volatile String resourcePackHash; // hex string or empty
    private volatile boolean forceOnJoin;
    private volatile int validationTimeoutMs;

    // messages
    private volatile String msgPrefix;
    private volatile String msgInstallPrompt;
    private volatile String msgThankYou;
    private volatile String msgKickOnRefuse;

    private volatile boolean kickIfNoPack;
    private volatile boolean resendOnWorldChange;
    private volatile int resendDelayTicks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!loadConfigValues()) {
            getLogger().severe("Failed to load configuration. Disabling plugin to avoid instability.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("---------------------------------");
        getLogger().info("|   [ResourcePack] is running   |");
        getLogger().info("---------------------------------");
        getLogger().info("[ResourcePack] If you have any questions or feedback about this plugin, please feel free to submit an issue on GitHub.");
        getLogger().info("[ResourcePack] 如果你对如插件的使用有任何的疑问，欢迎在qq上联系我 https://qm.qq.com/q/8IkS7PoSBO");
        getServer().getPluginManager().registerEvents(this, this);

        validateResourcePackAsync(resourcePackUrl);

        getLogger().info("ForceResourcePack enabled. URL: " + resourcePackUrl);
    }

    @Override
    public void onDisable() {
        getLogger().info("ForceResourcePack disabled.");
    }

    private boolean loadConfigValues() {
        try {
            FileConfiguration cfg = getConfig();
            resourcePackUrl = cfg.getString("resourcepack.url", "").trim();
            resourcePackHash = cfg.getString("resourcepack.hash", "").trim().toLowerCase(Locale.ROOT);
            forceOnJoin = cfg.getBoolean("resourcepack.force-on-join", true);
            validationTimeoutMs = cfg.getInt("resourcepack.validation-timeout-ms", 5000);

            msgPrefix = translateColors(cfg.getString("messages.prefix", "&c[DataPack] &r"));
            msgInstallPrompt = translateColors(cfg.getString("messages.install-prompt", "&eThe server requires a resource pack. Click to download."));
            msgThankYou = translateColors(cfg.getString("messages.thank-you", "&aThanks for using the server resource pack!"));
            msgKickOnRefuse = translateColors(cfg.getString("messages.kick-on-refuse", "&cYou must accept the server resource pack to play on this server."));

            kickIfNoPack = cfg.getBoolean("behavior.kick-if-no-pack", true);
            resendOnWorldChange = cfg.getBoolean("behavior.resend-if-not_matching_on_world_change", true);
            resendDelayTicks = cfg.getInt("behavior.resend-delay-ticks", 20);

            if (resourcePackUrl.isEmpty()) {
                getLogger().severe("resourcepack.url is empty in config.yml");
                return false;
            }
            if (!resourcePackUrl.startsWith("http://") && !resourcePackUrl.startsWith("https://")) {
                getLogger().severe("resourcepack.url must start with http:// or https://");
                return false;
            }
            if (!resourcePackHash.isEmpty()) {
                if (!resourcePackHash.matches("^[0-9a-f]{40}$")) {
                    getLogger().warning("resourcepack.hash does not look like a valid SHA-1 hex string. Ignoring hash.");
                    resourcePackHash = "";
                }
            }
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Exception while reading config: ", e);
            return false;
        }
    }

    private String translateColors(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    private void validateResourcePackAsync(String urlStr) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(validationTimeoutMs);
                conn.setReadTimeout(validationTimeoutMs);
                conn.setInstanceFollowRedirects(true);
                int code = conn.getResponseCode();
                if (code < 200 || code >= 400) {
                    getLogger().warning("Resource pack URL returned HTTP " + code + " - it may be unreachable or invalid.");
                } else {
                    long len = conn.getContentLengthLong();
                    getLogger().info("Resource pack reachable. Content-Length=" + len);
                    if (resourcePackHash.isEmpty() && len > 0 && len <= 20 * 1024 * 1024) {
                        computeRemoteSha1(url);
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Exception while validating resource pack URL: ", e);
            }
        });
    }

    private void computeRemoteSha1(URL url) {
        try {
            getLogger().info("Attempting to compute SHA-1 for resource pack (file <=20MB). This downloads the file.");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(validationTimeoutMs);
            conn.setReadTimeout(validationTimeoutMs);
            try (InputStream in = conn.getInputStream()) {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) md.update(buf, 0, r);
                String hex = bytesToHex(md.digest());
                resourcePackHash = hex;
                getLogger().info("Computed resource pack SHA-1: " + hex + " — saving to config.");
                Bukkit.getScheduler().runTask(this, () -> {
                    getConfig().set("resourcepack.hash", hex);
                    saveConfig();
                });
            } finally { conn.disconnect(); }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to compute SHA-1 for remote resource pack: ", e);
        }
    }

    private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (forceOnJoin) sendResourcePackToPlayer(player, true);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!resendOnWorldChange) return;
        Player player = event.getPlayer();
        // Delay slightly to let client settle
        Bukkit.getScheduler().runTaskLater(this, () -> sendResourcePackToPlayer(player, true), resendDelayTicks);
    }

    /**
     * Handles resource pack status responses from client.
     * Kick or thank depending on result.
     */
    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Status status = event.getStatus();
        Player player = event.getPlayer();
        try {
            switch (status) {
                case ACCEPTED:
                    // Client has accepted and started download — wait for SUCCESSFULLY_LOADED
                    player.sendMessage(msgPrefix + msgInstallPrompt);
                    break;
                case SUCCESSFULLY_LOADED:
                    // Validate: if hash configured, ensure client has same pack by trusting status — server cannot directly inspect client packs
                    player.sendMessage(msgPrefix + msgThankYou);
                    getLogger().info("Player " + player.getName() + " successfully loaded resource pack.");
                    break;
                case DECLINED:
                    getLogger().info("Player " + player.getName() + " declined resource pack.");
                    if (kickIfNoPack) {
                        player.kickPlayer(msgPrefix + msgKickOnRefuse);
                    } else {
                        player.sendMessage(msgPrefix + msgKickOnRefuse);
                    }
                    break;
                case FAILED_DOWNLOAD:
                    getLogger().warning("Player " + player.getName() + " failed to download resource pack.");
                    if (kickIfNoPack) {
                        player.kickPlayer(msgPrefix + msgKickOnRefuse);
                    } else {
                        player.sendMessage(msgPrefix + "Failed to download resource pack. " + msgKickOnRefuse);
                    }
                    break;
                default:
                    // Unknown or new status
                    getLogger().fine("ResourcePackStatus " + status + " for player " + player.getName());
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Exception handling ResourcePackStatus for " + player.getName() + ": ", e);
        }
    }

    private void sendResourcePackToPlayer(Player player, boolean force) {
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                // Always attempt to call with hash if available (byte[])
                if (resourcePackHash != null && !resourcePackHash.isEmpty()) {
                    try {
                        player.setResourcePack(resourcePackUrl, hexStringToByteArray(resourcePackHash));
                        getLogger().info("Sent resource pack with hash to player " + player.getName());
                        return;
                    } catch (Throwable t) {
                        // Some API versions may not have that overload accessible; fallthrough
                        getLogger().fine("setResourcePack(url, hash) failed reflectively — falling back. " + t.getMessage());
                    }
                }
                // Fallback
                try {
                    player.setResourcePack(resourcePackUrl);
                    getLogger().info("Sent resource pack to player " + player.getName());
                } catch (Throwable t) {
                    getLogger().warning("Failed to send resource pack to player " + player.getName() + ": " + t.getMessage());
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Unexpected exception when sending resource pack to player " + player.getName() + ": ", e);
            }
        });
    }

    private static byte[] hexStringToByteArray(String s) {
        if (s == null) return new byte[0];
        s = s.replaceAll("[^0-9a-fA-F]", "");
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("forcerp")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("forcerp.reload")) {
                    sender.sendMessage(msgPrefix + ChatColor.RED + "You don't have permission to run this command.");
                    return true;
                }
                sender.sendMessage(msgPrefix + ChatColor.GREEN + "Reloading ForceResourcePack configuration...");
                boolean ok = reloadAndValidate(sender);
                if (ok) sender.sendMessage(msgPrefix + ChatColor.GREEN + "Reload complete."); else sender.sendMessage(msgPrefix + ChatColor.RED + "Reload encountered issues. Check server logs.");
                return true;
            }
            sender.sendMessage(msgPrefix + ChatColor.YELLOW + "Usage: /forcerp reload");
            return true;
        }
        return false;
    }

    private boolean reloadAndValidate(CommandSender sender) {
        try {
            reloadConfig();
            if (!loadConfigValues()) {
                sender.sendMessage(msgPrefix + ChatColor.RED + "Failed to load configuration. See server logs.");
                return false;
            }
            sender.sendMessage(msgPrefix + ChatColor.GREEN + "Config loaded. Validating resource pack URL asynchronously...");
            validateResourcePackAsync(resourcePackUrl);
            if (forceOnJoin) {
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        sendResourcePackToPlayer(p, true);
                    }
                });
            }
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Exception during reload: ", e);
            return false;
        }
    }
}