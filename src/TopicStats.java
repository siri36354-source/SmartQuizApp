public class TopicStats {
    private int attempts;
    private int correct;

    public TopicStats(int attempts, int correct) {
        this.attempts = attempts;
        this.correct = correct;
    }

    public void record(boolean wasCorrect) {
        attempts++;
        if (wasCorrect) correct++;
    }

    public double getAccuracy() {
        return attempts == 0 ? 0 : (double) correct / attempts;
    }

    public int getAttempts() { return attempts; }
    public int getCorrect() { return correct; }
}
