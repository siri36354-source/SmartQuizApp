import java.util.*;

public class QuizSession {
    private final List<Question> questions;
    private final Scanner sc;
    private final ScoreTracker tracker;
    private int score = 0;
    private int streak = 0;
    private int bestStreak = 0;
    private final Map<String, TopicStats> roundStats = new TreeMap<>();

    public QuizSession(List<Question> questions, Scanner sc, ScoreTracker tracker) {
        this.questions = new ArrayList<>(questions);
        this.sc = sc;
        this.tracker = tracker;
        Collections.shuffle(this.questions);
    }

    public void run() {
        int number = 1;
        for (Question q : questions) {
            System.out.println("\nQ" + number++ + " [" + q.getTopic() + "] " + q.getText());
            long start = System.currentTimeMillis();
            String input = sc.nextLine();
            long seconds = (System.currentTimeMillis() - start) / 1000;

            boolean correct = q.isCorrect(input);
            tracker.record(q.getTopic(), correct);
            roundStats.computeIfAbsent(q.getTopic(), t -> new TopicStats(0, 0)).record(correct);

            if (correct) {
                score++;
                streak++;
                bestStreak = Math.max(bestStreak, streak);
                System.out.println("Correct! (" + seconds + "s)");
                if (streak >= 3) System.out.println("*** " + streak + " in a row! ***");
            } else {
                streak = 0;
                System.out.println("Wrong. Answer: " + q.getAnswer() + " (" + seconds + "s)");
            }
        }
        printSummary();
    }

    private void printSummary() {
        System.out.println("\n===== Round Summary =====");
        System.out.println("Score: " + score + "/" + questions.size());
        System.out.println("Best streak: " + bestStreak);

        String weakest = null;
        double lowest = 2;
        for (Map.Entry<String, TopicStats> e : roundStats.entrySet()) {
            double acc = e.getValue().getAccuracy();
            if (acc < lowest) { lowest = acc; weakest = e.getKey(); }
        }
        if (weakest != null) {
            System.out.printf("Weakest topic this round: %s (%.0f%%)%n", weakest, lowest * 100);
        }
    }
}
