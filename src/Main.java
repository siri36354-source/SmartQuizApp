import java.io.IOException;
import java.util.*;

public class Main {
    private static final String QUESTIONS_FILE = "questions.csv";
    private static final String STATS_FILE = "stats.csv";

    public static void main(String[] args) {
        QuestionBank bank = new QuestionBank();
        try {
            bank.load(QUESTIONS_FILE);
        } catch (IOException e) {
            System.out.println("Could not load " + QUESTIONS_FILE + ": " + e.getMessage());
            System.out.println("Run the app from the QuizApp folder (where questions.csv is).");
            return;
        }
        if (bank.getAll().isEmpty()) {
            System.out.println("No questions found in " + QUESTIONS_FILE);
            return;
        }

        ScoreTracker tracker = new ScoreTracker();
        tracker.load(STATS_FILE);
        Scanner sc = new Scanner(System.in);

        System.out.println("=== Quiz & Flashcard App ===");
        boolean running = true;
        while (running) {
            System.out.println("\n1) Start quiz  2) View stats  3) Review weak topics  4) Quit");
            System.out.print("> ");
            if (!sc.hasNextLine()) break;
            String option = sc.nextLine().trim();

            switch (option) {
                case "1" -> startQuiz(bank, tracker, sc);
                case "2" -> tracker.printStats();
                case "3" -> reviewWeak(bank, tracker, sc);
                case "4" -> running = false;
                default -> System.out.println("Invalid option. Enter 1-4.");
            }
        }
        tracker.save(STATS_FILE);
        System.out.println("Progress saved. Goodbye!");
    }

    private static void startQuiz(QuestionBank bank, ScoreTracker tracker, Scanner sc) {
        System.out.println("Topics: " + bank.getTopics());
        System.out.print("Pick a topic (or type ALL): ");
        String choice = sc.nextLine().trim();

        List<Question> selected = choice.equalsIgnoreCase("ALL")
                ? bank.getAll()
                : bank.getByTopic(choice);

        if (selected.isEmpty()) {
            System.out.println("No questions found for that topic.");
            return;
        }
        new QuizSession(selected, sc, tracker).run();
    }

    private static void reviewWeak(QuestionBank bank, ScoreTracker tracker, Scanner sc) {
        List<String> weakest = tracker.getWeakestTopics(bank.getTopics());
        List<Question> pool = new ArrayList<>();
        for (int i = 0; i < Math.min(2, weakest.size()); i++) {
            pool.addAll(bank.getByTopic(weakest.get(i)));
        }
        if (pool.isEmpty()) {
            System.out.println("Nothing to review yet.");
            return;
        }
        Collections.shuffle(pool);
        List<Question> review = pool.subList(0, Math.min(10, pool.size()));
        System.out.println("Reviewing your weakest topics: "
                + weakest.subList(0, Math.min(2, weakest.size())));
        new QuizSession(review, sc, tracker).run();
    }
}
