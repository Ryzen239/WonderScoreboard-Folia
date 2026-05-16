package ryzen23;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ryzen23.fastboard.FastBoard;

public final class WonderScoreboard extends JavaPlugin implements Listener {

    private final Map<UUID, Board> boards = new ConcurrentHashMap<>();
    private boolean scoresHidden;
    private boolean placeholders;
    private AnimatedText title;
    private List<AnimatedText> lines;
    private List<String> scoreNumbers;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        placeholders = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        getServer().getPluginManager().registerEvents(this, this);
        for (Player player : getServer().getOnlinePlayers()) {
            player.getScheduler().run(this, task -> setup(player), null);
        }
    }

    @Override
    public void onDisable() {
        for (Board board : boards.values()) {
            if (!board.fb.isDeleted()) {
                try {
                    board.fb.delete();
                } catch (Throwable ignored) {
                }
            }
        }
        boards.clear();
    }

    private void loadSettings() {
        reloadConfig();
        scoresHidden = getConfig().getBoolean("scores.hidden", false);
        ConfigurationSection display = getConfig().getConfigurationSection("display");
        title = readText(display == null ? null : display.getConfigurationSection("title"));
        lines = new ArrayList<>();
        if (display != null) {
            int index = 1;
            while (display.isConfigurationSection("line-" + index)) {
                lines.add(readText(display.getConfigurationSection("line-" + index)));
                index++;
            }
        }
        scoreNumbers = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            scoreNumbers.add(ChatColor.RED.toString() + (lines.size() - i));
        }
    }

    private AnimatedText readText(ConfigurationSection section) {
        if (section == null) {
            return new AnimatedText(new String[] { "" }, 1000L);
        }
        List<String> raw = section.getStringList("frames");
        if (raw.isEmpty()) {
            raw = new ArrayList<>();
            raw.add("");
        }
        return new AnimatedText(raw.toArray(new String[0]), section.getLong("interval", 1000L));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        setup(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Board board = boards.remove(event.getPlayer().getUniqueId());
        if (board != null && !board.fb.isDeleted()) {
            try {
                board.fb.delete();
            } catch (Throwable ignored) {
            }
        }
    }

    private void setup(Player player) {
        UUID id = player.getUniqueId();
        if (boards.containsKey(id)) {
            return;
        }
        Board board = new Board(new FastBoard(player));
        boards.put(id, board);
        apply(board, System.currentTimeMillis());
        player.getScheduler().runAtFixedRate(this, task -> tick(id, task), null, 1L, 1L);
    }

    private void tick(UUID id, ScheduledTask task) {
        Board board = boards.get(id);
        if (board == null || board.fb.isDeleted()) {
            task.cancel();
            return;
        }
        apply(board, System.currentTimeMillis());
    }

    private void apply(Board board, long now) {
        Player player = board.fb.getPlayer();
        String newTitle = render(player, title.frame(now));
        if (!newTitle.equals(board.lastTitle)) {
            board.fb.updateTitle(newTitle);
            board.lastTitle = newTitle;
        }
        List<String> newLines = new ArrayList<>(lines.size());
        for (AnimatedText line : lines) {
            newLines.add(render(player, line.frame(now)));
        }
        if (!newLines.equals(board.lastLines)) {
            board.fb.updateLines(newLines);
            if (!scoresHidden && board.fb.customScoresSupported()) {
                board.fb.updateScores(scoreNumbers);
            }
            board.lastLines = newLines;
        }
    }

    private String render(Player player, String text) {
        if (placeholders) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static final class AnimatedText {

        private final String[] frames;
        private final long interval;

        private AnimatedText(String[] frames, long interval) {
            this.frames = frames;
            this.interval = interval <= 0L ? 1L : interval;
        }

        private String frame(long now) {
            if (frames.length == 1) {
                return frames[0];
            }
            return frames[(int) ((now / interval) % frames.length)];
        }
    }

    private static final class Board {

        private final FastBoard fb;
        private String lastTitle;
        private List<String> lastLines;

        private Board(FastBoard fb) {
            this.fb = fb;
        }
    }
}
