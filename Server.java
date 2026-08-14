import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

public class Server {
    private static final SkipListLeaderboard leaderboard = new SkipListLeaderboard();

    public static void main(String[] args) throws IOException {
        // Pre-populate data across tiers (Bronze < 1500, Silver < 3000, Gold >= 3000)
        leaderboard.zadd("ApexPredator", 3400); // Gold
        leaderboard.zadd("CyberNinja", 3150);   // Gold
        leaderboard.zadd("PixelGamer", 2200);   // Silver
        leaderboard.zadd("ShadowKnight", 1800); // Silver
        leaderboard.zadd("NoviceNoob", 950);     // Bronze

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/leaderboard", new LeaderboardHandler());
        server.setExecutor(null);
        
        System.out.println("Advanced Tournament Leaderboard Engine started on http://localhost:8080/leaderboard");
        server.start();
    }

    static class LeaderboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String response = "";
            String query = exchange.getRequestURI().getQuery();

            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                if (query != null && query.contains("id=")) {
                    String playerId = "";
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair[0].equals("id") && pair.length > 1) playerId = pair[1];
                    }
                    Map<String, Object> info = leaderboard.getPlayerInfo(playerId);
                    if (info != null) {
                        response = String.format("{\"playerId\":\"%s\",\"tier\":\"%s\",\"score\":%d,\"rank\":%d,\"percentile\":%d}", 
                            info.get("playerId"), info.get("tier"), info.get("score"), info.get("rank"), info.get("percentile"));
                    } else {
                        response = "{\"error\":\"Player not found\"}";
                    }
                } else {
                    // Default GET: returns all tiers
                    Map<String, List<Map<String, Object>>> allTiers = leaderboard.getAllTiersTop(10);
                    response = formatTiersJson(allTiers);
                }
                exchange.sendResponseHeaders(200, response.getBytes().length);

            } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                if (query != null && query.contains("id=") && query.contains("score=")) {
                    String id = "";
                    int score = 0;
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair[0].equals("id")) id = pair[1];
                        if (pair[0].equals("score") && pair.length > 1) score = Integer.parseInt(pair[1]);
                    }
                    leaderboard.zadd(id, score);
                    response = "{\"status\":\"success\",\"message\":\"Updated " + id + " with score " + score + "\"}";
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                } else {
                    response = "{\"error\":\"Invalid parameters. Use ?id=Name&score=Value\"}";
                    exchange.sendResponseHeaders(400, response.getBytes().length);
                }
            }

            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private String formatTiersJson(Map<String, List<Map<String, Object>>> tiersMap) {
            StringBuilder sb = new StringBuilder("{");
            int tIdx = 0;
            for (Map.Entry<String, List<Map<String, Object>>> entry : tiersMap.entrySet()) {
                sb.append("\"").append(entry.getKey()).append("\":[");
                List<Map<String, Object>> list = entry.getValue();
                for (int i = 0; i < list.size(); i++) {
                    Map<String, Object> m = list.get(i);
                    sb.append(String.format("{\"rank\":%d,\"playerId\":\"%s\",\"score\":%d}", 
                        m.get("rank"), m.get("playerId"), m.get("score")));
                    if (i < list.size() - 1) sb.append(",");
                }
                sb.append("]");
                if (tIdx < tiersMap.size() - 1) sb.append(",");
                tIdx++;
            }
            sb.append("}");
            return sb.toString();
        }
    }
}