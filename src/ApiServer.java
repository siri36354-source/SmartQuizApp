import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;

public class ApiServer {
    private static final int DEFAULT_PORT = 8081;
    private static final Path ROOT = Path.of(".").toAbsolutePath().normalize();
    private static final Path WEB = ROOT.resolve("web");
    private static final String QUESTIONS_FILE = "questions.csv";
    private static final String STATS_FILE = "stats.csv";
    private static final String HISTORY_FILE = "history.csv";
    private static final QuestionBank bank = new QuestionBank();
    private static final ScoreTracker tracker = new ScoreTracker();
    private static final Map<String, QuizAttempt> activeQuizzes = new HashMap<>();

    public static void main(String[] args) throws Exception {
        bank.load(QUESTIONS_FILE);
        if (bank.getAll().isEmpty()) throw new IllegalStateException("No questions found in questions.csv");
        tracker.load(STATS_FILE);
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT)));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/", ApiServer::handleApi);
        server.createContext("/", ApiServer::handleStatic);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("QuizApp UI is running on port " + port);
        System.out.println("For local use, open http://localhost:" + port);
        System.out.println("Keep this window open while using the app. Press Ctrl+C to stop.");
    }

    private static void handleApi(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/api/dashboard") && ex.getRequestMethod().equals("GET")) {
                sendJson(ex, 200, dashboardJson());
            } else if (path.equals("/api/quiz") && ex.getRequestMethod().equals("GET")) {
                startQuiz(ex);
            } else if (path.equals("/api/submit") && ex.getRequestMethod().equals("POST")) {
                submitQuiz(ex);
            } else if (path.equals("/api/history") && ex.getRequestMethod().equals("GET")) {
                sendJson(ex, 200, historyJson());
            } else {
                sendJson(ex, 404, "{\"error\":\"API endpoint not found\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, "{\"error\":" + q("Server error: " + e.getMessage()) + "}");
        }
    }

    private static void startQuiz(HttpExchange ex) throws IOException {
        Map<String,String> query = parseForm(ex.getRequestURI().getRawQuery());
        String topic = query.getOrDefault("topic", "ALL");
        List<Question> selected = topic.equalsIgnoreCase("ALL") ? bank.getAll() : bank.getByTopic(topic);
        if (selected.isEmpty()) {
            sendJson(ex, 400, "{\"error\":\"No questions found for this category\"}"); return;
        }
        Collections.shuffle(selected);
        String id = UUID.randomUUID().toString();
        activeQuizzes.put(id, new QuizAttempt(topic, selected, System.currentTimeMillis()));
        StringBuilder out = new StringBuilder("{\"sessionId\":").append(q(id)).append(",\"topic\":").append(q(topic)).append(",\"secondsPerQuestion\":30,\"questions\":[");
        for (int i=0;i<selected.size();i++) {
            if (i>0) out.append(',');
            Question question=selected.get(i);
            out.append("{\"id\":").append(i).append(",\"topic\":").append(q(question.getTopic())).append(",\"text\":").append(q(question.getText())).append('}');
        }
        out.append("]}");
        sendJson(ex, 200, out.toString());
    }

    private static void submitQuiz(HttpExchange ex) throws IOException {
        Map<String,String> form = parseForm(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String id=form.get("sessionId");
        QuizAttempt attempt=activeQuizzes.remove(id);
        if (attempt==null) { sendJson(ex, 400, "{\"error\":\"Quiz session expired or already submitted. Start a new quiz.\"}"); return; }
        int score=0;
        Map<String,int[]> round = new TreeMap<>();
        StringBuilder items=new StringBuilder("[");
        for(int i=0;i<attempt.questions.size();i++) {
            Question question=attempt.questions.get(i);
            String answer=form.getOrDefault("answer"+i, "").trim();
            boolean correct=question.isCorrect(answer);
            if(correct) score++;
            tracker.record(question.getTopic(), correct);
            int[] counts=round.computeIfAbsent(question.getTopic(), k->new int[2]);
            counts[1]++; if(correct) counts[0]++;
            if(i>0) items.append(',');
            items.append("{\"id\":").append(i).append(",\"topic\":").append(q(question.getTopic())).append(",\"text\":").append(q(question.getText())).append(",\"userAnswer\":").append(q(answer)).append(",\"correctAnswer\":").append(q(question.getAnswer())).append(",\"correct\":").append(correct).append('}');
        }
        items.append(']');
        tracker.save(STATS_FILE);
        String weakTopic=""; double lowest=2;
        for(Map.Entry<String,int[]> e:round.entrySet()) { double accuracy=(double)e.getValue()[0]/e.getValue()[1]; if(accuracy<lowest){lowest=accuracy;weakTopic=e.getKey();} }
        long elapsed=Math.max(0,(System.currentTimeMillis()-attempt.startedAt)/1000);
        String date=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        appendHistory(date, attempt.topic, score, attempt.questions.size(), weakTopic);
        StringBuilder topicStats=new StringBuilder("["); int n=0;
        for(Map.Entry<String,int[]> e:round.entrySet()) { if(n++>0)topicStats.append(','); topicStats.append("{\"topic\":").append(q(e.getKey())).append(",\"correct\":").append(e.getValue()[0]).append(",\"total\":").append(e.getValue()[1]).append(",\"accuracy\":").append(Math.round((double)e.getValue()[0]*100/e.getValue()[1])).append('}'); }
        topicStats.append(']');
        sendJson(ex,200,"{\"score\":"+score+",\"total\":"+attempt.questions.size()+",\"percentage\":"+Math.round((double)score*100/attempt.questions.size())+",\"weakTopic\":"+q(weakTopic)+",\"elapsedSeconds\":"+elapsed+",\"items\":"+items+",\"topicStats\":"+topicStats+"}");
    }

    private static String dashboardJson() {
        StringBuilder topics=new StringBuilder("["); int n=0;
        for(String topic:bank.getTopics()) { if(n++>0)topics.append(','); topics.append(q(topic)); }
        topics.append(']');
        StringBuilder stats=new StringBuilder("["); n=0;
        for(Map.Entry<String,TopicStats> e:tracker.getStats().entrySet()) { if(n++>0)stats.append(','); TopicStats s=e.getValue(); stats.append("{\"topic\":").append(q(e.getKey())).append(",\"correct\":").append(s.getCorrect()).append(",\"attempts\":").append(s.getAttempts()).append(",\"accuracy\":").append(Math.round(s.getAccuracy()*100)).append('}'); }
        stats.append(']');
        int totalAttempts=0,totalCorrect=0; for(TopicStats s:tracker.getStats().values()){totalAttempts+=s.getAttempts();totalCorrect+=s.getCorrect();}
        return "{\"topics\":"+topics+",\"stats\":"+stats+",\"totalAttempts\":"+totalAttempts+",\"totalCorrect\":"+totalCorrect+",\"overallAccuracy\":"+(totalAttempts==0?0:Math.round((double)totalCorrect*100/totalAttempts))+"}";
    }

    private static String historyJson() throws IOException {
        Path p=ROOT.resolve(HISTORY_FILE); StringBuilder arr=new StringBuilder("[");
        if(Files.exists(p)) { List<String> lines=Files.readAllLines(p); int start=Math.max(1,lines.size()-51); boolean first=true;
            for(int i=lines.size()-1;i>=start;i--) { String[] a=lines.get(i).split(",",-1); if(a.length<5)continue; if(!first)arr.append(','); first=false;
                arr.append("{\"date\":").append(q(a[0])).append(",\"topic\":").append(q(a[1])).append(",\"score\":").append(number(a[2])).append(",\"total\":").append(number(a[3])).append(",\"weakTopic\":").append(q(a[4])).append('}'); }
        }
        return arr.append(']').toString();
    }
    private static void appendHistory(String date,String topic,int score,int total,String weak) throws IOException {
        Path p=ROOT.resolve(HISTORY_FILE); if(!Files.exists(p)) Files.writeString(p,"date,topic,score,total,weakTopic\n");
        String safeTopic=topic.replace(","," "); String safeWeak=weak.replace(","," ");
        Files.writeString(p,date+","+safeTopic+","+score+","+total+","+safeWeak+"\n",StandardOpenOption.APPEND);
    }
    private static int number(String s){try{return Integer.parseInt(s);}catch(Exception e){return 0;}}
    private static void handleStatic(HttpExchange ex) throws IOException {
        String path=URLDecoder.decode(ex.getRequestURI().getPath(),StandardCharsets.UTF_8);
        if(path.equals("/"))path="/index.html";
        Path file=WEB.resolve(path.substring(1)).normalize();
        if(!file.startsWith(WEB.normalize()) || !Files.isRegularFile(file)){ byte[] b="Not found".getBytes(StandardCharsets.UTF_8); ex.sendResponseHeaders(404,b.length); try(OutputStream os=ex.getResponseBody()){os.write(b);} return; }
        String type=file.toString().endsWith(".css")?"text/css; charset=utf-8":file.toString().endsWith(".js")?"application/javascript; charset=utf-8":"text/html; charset=utf-8";
        byte[] b=Files.readAllBytes(file); ex.getResponseHeaders().set("Content-Type",type); ex.sendResponseHeaders(200,b.length); try(OutputStream os=ex.getResponseBody()){os.write(b);}
    }
    private static Map<String,String> parseForm(String raw) {
        Map<String,String> map=new LinkedHashMap<>(); if(raw==null||raw.isEmpty())return map;
        for(String part:raw.split("&")){int i=part.indexOf('=');String k=i<0?part:part.substring(0,i),v=i<0?"":part.substring(i+1);map.put(URLDecoder.decode(k,StandardCharsets.UTF_8),URLDecoder.decode(v,StandardCharsets.UTF_8));} return map;
    }
    private static String q(String s) { if(s==null)return "\"\""; StringBuilder b=new StringBuilder("\""); for(char c:s.toCharArray()){switch(c){case '\\'->b.append("\\\\");case '"'->b.append("\\\"");case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");default->{if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}}return b.append('"').toString(); }
    private static void sendJson(HttpExchange ex,int status,String json)throws IOException{byte[] b=json.getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");ex.getResponseHeaders().set("Cache-Control","no-store");ex.sendResponseHeaders(status,b.length);try(OutputStream os=ex.getResponseBody()){os.write(b);}}
    private static class QuizAttempt { final String topic; final List<Question> questions; final long startedAt; QuizAttempt(String t,List<Question> q,long s){topic=t;questions=new ArrayList<>(q);startedAt=s;} }
}
