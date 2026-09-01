import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Kona {

    static final Model MODEL = new Model();
    static final List<Tool> TOOLS = List.of(
            new Tool("read_file",
                     "Read the contents of a file at a relative path.",
                     List.of(new Parameter("path", "the relative path")),
                     arguments -> readFile(arguments.get("path"))),
            new Tool("list_files",
                     "List the files and the folders at a relative path.",
                     List.of(new Parameter("path", "the relative path, or . for the current folder")),
                     arguments -> listFiles(arguments.get("path"))));
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

        while (true) {
            Message reply = MODEL.reply(conversation, TOOLS);
            conversation.add(reply);

            if (reply.isAnswer()) {
                return reply.content();
            }

            for (ToolCall call : reply.calls()) {
                String result = run(call);
                conversation.add(Message.toolResult(call, result));
            }
        }
    }

    static String run(ToolCall call) {
        System.out.println("tool: " + Model.show(call));
        String result = TOOLS.stream()
                .filter(tool -> tool.name().equals(call.name()))
                .findFirst()
                .map(tool -> tool.body().run(call.arguments()))
                .orElse("error: unknown tool " + call.name());
        System.out.println("  -> " + summary(result));
        return result;
    }

    static String summary(String result) {
        String line = result.replace('\n', ' ');
        return line.length() <= 100 ? line : line.substring(0, 100) + " ...";
    }

    static String readFile(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    static String listFiles(String path) {
        try (var entries = Files.list(Path.of(path))) {
            return entries.map(entry -> entry.getFileName().toString())
                    .sorted()
                    .reduce("", (all, one) -> all.isEmpty() ? one : all + "\n" + one);
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }
}

record Parameter(String name, String description) {
}

interface Body {

    String run(Map<String, String> arguments);
}

record Tool(String name, String description, List<Parameter> parameters, Body body) {
}

record ToolCall(String id, String name, Map<String, String> arguments) {
}

record Message(String role, String content, List<ToolCall> calls, String callId, JsonNode wire) {

    static Message system(String content) {
        return new Message("system", content, List.of(), null, null);
    }

    static Message user(String content) {
        return new Message("user", content, List.of(), null, null);
    }

    static Message assistant(String content) {
        return new Message("assistant", content, List.of(), null, null);
    }

    static Message toolResult(ToolCall call, String result) {
        return new Message("tool", result, List.of(), call.id(), null);
    }

    boolean isAnswer() {
        return calls.isEmpty();
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

    Message reply(Conversation conversation, List<Tool> tools) {
        try {
            ObjectNode body = JSON.createObjectNode();
            body.put("model", NAME);
            body.set("messages", messages(conversation));
            body.set("tools", schema(tools));

            HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response =
                    HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = JSON.readTree(response.body());
            ObjectNode message = (ObjectNode) root.path("choices").path(0).path("message");

            List<ToolCall> calls = new ArrayList<>();
            for (JsonNode call : message.path("tool_calls")) {
                Map<String, String> arguments = new LinkedHashMap<>();
                JSON.readTree(call.path("function").path("arguments").asText())
                        .fields()
                        .forEachRemaining(field ->
                                arguments.put(field.getKey(), field.getValue().asText()));
                calls.add(new ToolCall(call.path("id").asText(),
                                       call.path("function").path("name").asText(),
                                       arguments));
            }

            return new Message(message.path("role").asText(),
                               message.path("content").asText(),
                               calls, null, message);
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
            if (message.callId() != null) {
                node.put("tool_call_id", message.callId());
            }
            array.add(node);
        }
        return array;
    }

    static String show(ToolCall call) {
        try {
            return call.name() + "(" + JSON.writeValueAsString(call.arguments()) + ")";
        } catch (Exception e) {
            return call.name() + "(?)";
        }
    }

    private ArrayNode schema(List<Tool> tools) {
        ArrayNode array = JSON.createArrayNode();
        for (Tool tool : tools) {
            ObjectNode properties = JSON.createObjectNode();
            ArrayNode required = JSON.createArrayNode();
            for (Parameter parameter : tool.parameters()) {
                ObjectNode property = JSON.createObjectNode();
                property.put("type", "string");
                property.put("description", parameter.description());
                properties.set(parameter.name(), property);
                required.add(parameter.name());
            }

            ObjectNode parameters = JSON.createObjectNode();
            parameters.put("type", "object");
            parameters.set("properties", properties);
            parameters.set("required", required);

            ObjectNode function = JSON.createObjectNode();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.set("parameters", parameters);

            ObjectNode entry = JSON.createObjectNode();
            entry.put("type", "function");
            entry.set("function", function);
            array.add(entry);
        }
        return array;
    }
}
