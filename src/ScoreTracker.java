import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ScoreTracker {
    private final Map<String, TopicStats> stats = new TreeMap<>();

    public void record(String topic, boolean correct) {
        stats.computeIfAbsent(topic, t -> new TopicStats(0, 0)).record(correct);
    }

    public Map<String, TopicStats> getStats() {
        return stats;
    }

    public void printStats() {
        if (stats.isEmpty()) {
            System.out.println("No stats yet. Take a quiz first!");
            return;
        }
        System.out.println("\n--- Your Stats ---");
        for (Map.Entry<String, TopicStats> e : stats.entrySet()) {
            TopicStats s = e.getValue();
            System.out.printf("%-12s %d/%d (%.0f%%)%n",
                    e.getKey(), s.getCorrect(), s.getAttempts(), s.getAccuracy() * 100);
        }
    }

    /** Topics sorted weakest-first. Never-attempted topics come first. */
    public List<String> getWeakestTopics(Set<String> allTopics) {
        List<String> topics = new ArrayList<>(allTopics);
        topics.sort(Comparator.comparingDouble(t -> {
            TopicStats s = stats.get(t);
            return s == null ? -1 : s.getAccuracy();
        }));
        return topics;
    }

    public void save(String path) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, TopicStats> e : stats.entrySet()) {
            TopicStats s = e.getValue();
            lines.add(e.getKey() + "," + s.getAttempts() + "," + s.getCorrect());
        }
        try {
            Files.write(Path.of(path), lines);
        } catch (IOException ex) {
            System.out.println("Could not save stats: " + ex.getMessage());
        }
    }

    public void load(String path) {
        Path p = Path.of(path);
        if (!Files.exists(p)) return;   // first run
        try {
            for (String line : Files.readAllLines(p)) {
                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                stats.put(parts[0], new TopicStats(
                        Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())));
            }
        } catch (IOException | NumberFormatException ex) {
            System.out.println("Could not load stats: " + ex.getMessage());
        }
    }
}
