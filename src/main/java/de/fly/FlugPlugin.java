package de.fly;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlugPlugin extends JavaPlugin implements Listener, TabExecutor {

    // Stufe 0 = normale Creative-Geschwindigkeit (0.1), Stufe 10 = Maximum (1.0)
    private static final double BASE_SPEED = 0.1;
    private static final double SPEED_PER_LEVEL = 0.09;

    private NamespacedKey enabledKey;
    private final Map<UUID, Long> fallProtection = new HashMap<>();

    @Override
    public void onEnable() {
        enabledKey = new NamespacedKey(this, "fly_enabled");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("fly").setExecutor(this);
        getCommand("flyspeed").setExecutor(this);
    }

    // ---------- Befehle ----------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl geht nur im Spiel.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("fly")) {
            boolean on = !isEnabled(player);
            setEnabled(player, on);
            player.sendMessage(Component.text(on ? "Fliegen: AN" : "Fliegen: AUS",
                    on ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (on) {
                player.sendMessage(Component.text(
                        "Leertaste doppelt tippen zum Fliegen. Tempo: /flyspeed <0-10>",
                        NamedTextColor.GRAY));
            }
            return true;
        }

        // /flyspeed
        if (args.length == 0) {
            double level = (player.getFlySpeed() - BASE_SPEED) / SPEED_PER_LEVEL;
            player.sendMessage(Component.text(
                    "Flug-Geschwindigkeit: " + Math.round(level * 10) / 10.0 + " / 10",
                    NamedTextColor.AQUA));
            return true;
        }

        double level;
        try {
            level = Double.parseDouble(args[0].replace(',', '.'));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Bitte eine Zahl von 0 bis 10 angeben.", NamedTextColor.RED));
            return true;
        }
        if (level < 0 || level > 10) {
            player.sendMessage(Component.text("Erlaubt sind Werte von 0 bis 10.", NamedTextColor.RED));
            return true;
        }
        player.setFlySpeed((float) Math.min(1.0, BASE_SPEED + level * SPEED_PER_LEVEL));
        player.sendMessage(Component.text("Flug-Geschwindigkeit: " + level + " / 10", NamedTextColor.AQUA));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("flyspeed") && args.length == 1) {
            for (int i = 0; i <= 10; i++) {
                out.add(String.valueOf(i));
            }
        }
        return out;
    }

    // ---------- Flug-Logik ----------

    private boolean isEnabled(Player p) {
        Byte b = p.getPersistentDataContainer().get(enabledKey, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    private void setEnabled(Player p, boolean on) {
        p.getPersistentDataContainer().set(enabledKey, PersistentDataType.BYTE, (byte) (on ? 1 : 0));
        applyFlight(p, on);
    }

    private void applyFlight(Player p, boolean on) {
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            return; // dort fliegt man ohnehin
        }
        boolean inAir = !p.isOnGround();
        p.setAllowFlight(on);
        if (!on) {
            if (p.isFlying()) {
                p.setFlying(false);
            }
            if (inAir) {
                fallProtection.put(p.getUniqueId(), System.currentTimeMillis() + 10_000L);
            }
        }
    }

    // ---------- Events ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (isEnabled(e.getPlayer())) {
            applyFlight(e.getPlayer(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        fallProtection.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        if (!isEnabled(p)) {
            return;
        }
        // Nach dem Wechsel (z.B. zurück ins Survival) Flug wieder erlauben
        getServer().getScheduler().runTask(this, () -> {
            if (p.isOnline() && isEnabled(p)) {
                applyFlight(p, true);
            }
        });
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (isEnabled(p)) {
            e.setCancelled(true);
            return;
        }
        Long until = fallProtection.get(p.getUniqueId());
        if (until != null) {
            fallProtection.remove(p.getUniqueId());
            if (System.currentTimeMillis() < until) {
                e.setCancelled(true);
            }
        }
    }
}
