import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class QuestionBank {
    private final List<Question> questions = new ArrayList<>();

    public void load(String path) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(path));
        for (int i = 1; i < lines.size(); i++) {      // skip header
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",", 3);       // topic, question, answer
            if (parts.length < 3) continue;            // skip bad lines
            questions.add(new Question(parts[0].trim(), parts[1].trim(), parts[2].trim()));
        }
    }

    public List<Question> getAll() {
        return new ArrayList<>(questions);
    }

    public List<Question> getByTopic(String topic) {
        return questions.stream()
                .filter(q -> q.getTopic().equalsIgnoreCase(topic))
                .collect(Collectors.toList());
    }

    public Set<String> getTopics() {
        return questions.stream()
                .map(Question::getTopic)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
