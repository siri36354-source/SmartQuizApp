public class Question {
    private final String topic;
    private final String text;
    private final String answer;

    public Question(String topic, String text, String answer) {
        this.topic = topic;
        this.text = text;
        this.answer = answer;
    }

    public boolean isCorrect(String input) {
        return answer.equalsIgnoreCase(input.trim());
    }

    public String getTopic() { return topic; }
    public String getText() { return text; }
    public String getAnswer() { return answer; }
}
