package dev.ayran.steveai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

final class GeminiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15)).build();

    record Reply(String text, String action) {}

    static CompletableFuture<Reply> ask(String userMessage) {
        String prompt = """
            You are Steve, a friendly and capable Minecraft companion. The player speaks Turkish.
            Reply in natural, concise Turkish. Return ONLY a JSON object with exactly these keys:
            {"text":"your Turkish response","action":"NONE"}
            Allowed action values: NONE, FOLLOW, STOP, COME, STAY.
            Choose FOLLOW when asked to follow, STOP or STAY when asked to wait/stop,
            COME when asked to come here. For building, mining, crafting, combat, or any
            unsupported ability, be honest that this version cannot perform that task yet.
            Never invent that you completed an in-game action. Never output commands or code.
            Player request: %s
            """.formatted(userMessage.replace("\n", " "));
        JsonObject body = new JsonObject();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        com.google.gson.JsonArray parts = new com.google.gson.JsonArray();
        parts.add(part);
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        com.google.gson.JsonArray contents = new com.google.gson.JsonArray();
        contents.add(content);
        body.add("contents", contents);
        body.addProperty("generationConfig", "{\"responseMimeType\":\"application/json\"}");

        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
            + GeminiConfig.model() + ":generateContent?key=" + GeminiConfig.apiKey();
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(45))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("Gemini HTTP " + response.statusCode());
                }
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                String raw = root.getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content").getAsJsonArray("parts").get(0)
                    .getAsJsonObject().get("text").getAsString();
                JsonObject parsed = JsonParser.parseString(raw).getAsJsonObject();
                String text = parsed.has("text") ? parsed.get("text").getAsString() : "Buradayim.";
                String action = parsed.has("action") ? parsed.get("action").getAsString() : "NONE";
                if (!action.matches("FOLLOW|STOP|COME|STAY|NONE")) action = "NONE";
                return new Reply(text, action);
            });
    }
}
