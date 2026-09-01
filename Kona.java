import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Kona {

    static final Model MODEL = new Model();
    static final Scanner IN = new Scanner(System.in);

    public static void main(String[] args) {
        Conversation conversation = new Conversation(
                "You are a coding assistant. Answer in two sentences.");

        while (true) {
            System.out.print("\nYou: ");
            if (!IN.hasNextLine()) {
                return;
            }
            String question = IN.nextLine().trim();
            if (question.isEmpty()) {
                continue;
            }
            System.out.println("\nkona: " + respond(conversation, question));
        }
    }

    static String respond(Conversation conversation, String question) {
        conversation.add(Message.user(question));
        Message reply = MODEL.reply(conversation);
        conversation.add(reply);
        return reply.content();
    }
}

record Message(String role, String content, JsonNode wire) {

    static Message system(String content) {
        return new Message("system", content, null);
    }

    static Message user(String content) {
        return new Message("user", content, null);
    }
}

final class Conversation {

    private final List<Message> messages = new ArrayList<>();

    Conversation(String instructions) {
        messages.add(Message.system(instructions));
    }

    void add(Message message) {
        messages.add(message);
    }

    List<Message> messages() {
        return messages;
    }
}

final class Model {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String URL = System.getenv().getOrDefault(
            "KONA_URL", "http://localhost:11434/v1/chat/completions");
    private static final String NAME =
            System.getenv().getOrDefault("KONA_MODEL", "qwen3-coder:latest");

    Message reply(Conversation conversation) {
        try {
            ObjectNode body = JSON.createObjectNode();
            body.put("model", NAME);
            body.set("messages", messages(conversation));

            HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response =
                    HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = JSON.readTree(response.body());
            ObjectNode message = (ObjectNode) root.path("choices").path(0).path("message");

            return new Message(message.path("role").asText(),
                               message.path("content").asText(),
                               message);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ArrayNode messages(Conversation conversation) {
        ArrayNode array = JSON.createArrayNode();
        for (Message message : conversation.messages()) {
            if (message.wire() != null) {
                array.add(message.wire());
                continue;
            }
            ObjectNode node = JSON.createObjectNode();
            node.put("role", message.role());
            node.put("content", message.content());
            array.add(node);
        }
        return array;
    }
}
