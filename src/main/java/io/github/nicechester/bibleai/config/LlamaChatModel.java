package io.github.nicechester.bibleai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.github.nicechester.bibleai.service.LlamaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
@RequiredArgsConstructor
public class LlamaChatModel implements ChatModel {
    
    private final LlamaService llamaService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Pattern to extract JSON function calls from response
    private static final Pattern JSON_FUNCTION_CALL_PATTERN = Pattern.compile(
        "(?i)(?:function_calls|tool_calls|functions)\\s*[:=]\\s*\\[([^\\]]+)\\]", 
        Pattern.DOTALL
    );
    
    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        if (!llamaService.isAvailable()) {
            throw new IllegalStateException("Llama model is not available");
        }
        
        List<ChatMessage> messages = chatRequest.messages();
        List<ToolSpecification> tools = chatRequest.toolSpecifications();
        
        log.debug("Processing chat request with {} messages and {} tools", 
                messages.size(), 
                tools != null ? tools.size() : 0);
        
        // Convert LangChain4j messages to a prompt string
        // Include tool information in the prompt since Llama doesn't support native function calling
        String prompt = buildPrompt(messages, tools);
        
        log.debug("Sending prompt to Llama model ({} messages)", messages.size());
        
        // Call your Llama service
        String response = llamaService.infer(prompt);
        
        log.debug("Received response from Llama model (length: {})", response.length());
        
        // Try to parse function calls from JSON format in the response
        List<ToolExecutionRequest> toolExecutionRequests = parseFunctionCallsFromResponse(response, tools);
        
        if (!toolExecutionRequests.isEmpty()) {
            // Model wants to call tools - return AiMessage with tool execution requests
            log.debug("Parsed {} function calls from response", toolExecutionRequests.size());
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(toolExecutionRequests))
                    .build();
        } else {
            // Regular text response
            return ChatResponse.builder()
                    .aiMessage(new AiMessage(response))
                    .build();
        }
    }
    
    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        // Delegate to doChat method
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();
        return doChat(chatRequest);
    }
    
    /**
     * Builds a prompt from chat messages and tools.
     * Formats messages for Mistral instruction format: [INST]...[/INST]
     * Includes tool descriptions in the system message since Llama doesn't support native function calling.
     */
    private String buildPrompt(List<ChatMessage> messages, List<ToolSpecification> tools) {
        // If tools are provided, we need to include them in the prompt
        // since Llama doesn't support native function calling like Gemini
        String toolDescriptions = "";
        if (tools != null && !tools.isEmpty()) {
            toolDescriptions = buildToolDescriptions(tools);
            log.debug("Including {} tool descriptions in prompt", tools.size());
        }
        
        return buildPrompt(messages, toolDescriptions);
    }
    
    /**
     * Builds tool descriptions as text to include in the prompt.
     * Instructs the model to return function calls in JSON format.
     */
    private String buildToolDescriptions(List<ToolSpecification> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nAvailable tools/functions you can use:\n");
        for (ToolSpecification tool : tools) {
            sb.append("\nFunction: ").append(tool.name()).append("\n");
            if (tool.description() != null && !tool.description().isEmpty()) {
                sb.append("Description: ").append(tool.description()).append("\n");
            }
            if (tool.parameters() != null) {
                sb.append("Parameters: ").append(tool.parameters().toString()).append("\n");
            }
        }
        sb.append("\n\nCRITICAL INSTRUCTIONS FOR FUNCTION CALLING:\n");
        sb.append("When you need to call a function, you MUST respond with ONLY a JSON object, nothing else.\n");
        sb.append("Do NOT include any explanatory text before or after the JSON.\n");
        sb.append("Do NOT describe what you're doing - just return the JSON.\n");
        sb.append("\nRequired JSON format:\n");
        sb.append("{\n");
        sb.append("  \"function_calls\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": \"function_name\",\n");
        sb.append("      \"arguments\": {\"param1\": \"value1\", \"param2\": \"value2\"}\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("\nExample: If you need to search for \"사랑\", return ONLY:\n");
        sb.append("{\"function_calls\": [{\"name\": \"searchVerses\", \"arguments\": {\"keyword\": \"사랑\"}}]}\n");
        sb.append("\nIf you don't need to call any functions, respond with normal text.\n");
        sb.append("You can call multiple functions by including multiple objects in the array.\n");
        sb.append("\nIMPORTANT: After calling a function, you will receive the function result. ");
        sb.append("You MUST include the actual data from the function result in your final response to the user. ");
        sb.append("Do not just acknowledge that you called the function - provide the actual answer with the data.\n");
        return sb.toString();
    }
    
    /**
     * Parses function calls from the model's JSON response.
     * Looks for JSON objects with "function_calls" array containing function call objects.
     */
    private List<ToolExecutionRequest> parseFunctionCallsFromResponse(String response, List<ToolSpecification> tools) {
        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();
        
        if (tools == null || tools.isEmpty()) {
            return toolExecutionRequests;
        }
        
        try {
            // Try to find JSON in the response
            // Look for JSON object that might be embedded in text
            String jsonStr = extractJsonFromResponse(response);
            
            if (jsonStr == null || jsonStr.isEmpty()) {
                return toolExecutionRequests;
            }
            
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode functionCalls = root.get("function_calls");
            
            if (functionCalls != null && functionCalls.isArray()) {
                for (JsonNode call : functionCalls) {
                    String functionName = call.get("name").asText();
                    JsonNode argumentsNode = call.get("arguments");
                    
                    // Convert arguments to JSON string
                    String arguments = argumentsNode != null ? objectMapper.writeValueAsString(argumentsNode) : "{}";
                    
                    // Verify the function exists in available tools
                    boolean toolExists = tools.stream()
                            .anyMatch(tool -> tool.name().equals(functionName));
                    
                    if (toolExists) {
                        toolExecutionRequests.add(ToolExecutionRequest.builder()
                                .name(functionName)
                                .arguments(arguments)
                                .build());
                        log.debug("Parsed function call: {} with arguments: {}", functionName, arguments);
                    } else {
                        log.warn("Function {} not found in available tools, ignoring", functionName);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse function calls from response (this is normal if response is plain text): {}", e.getMessage());
        }
        
        return toolExecutionRequests;
    }
    
    /**
     * Extracts JSON from the response, handling cases where JSON might be embedded in text.
     * Tries multiple strategies to find valid JSON.
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        
        // Strategy 0: Look for pattern like "[Function calls requested: { ... }]"
        // This handles cases where the model embeds JSON in explanatory text
        Pattern embeddedInBrackets = Pattern.compile(
            "(?i)\\[\\s*function\\s*calls?\\s*(?:requested|required)?\\s*[:=]\\s*(\\{[^\\}]+(?:\\{[^\\}]+\\}[^\\}]*)*\\})\\s*\\]",
            Pattern.DOTALL
        );
        Matcher matcher = embeddedInBrackets.matcher(response);
        if (matcher.find()) {
            String jsonCandidate = matcher.group(1);
            try {
                JsonNode node = objectMapper.readTree(jsonCandidate);
                if (node.has("function_calls")) {
                    return jsonCandidate;
                }
            } catch (Exception e) {
                log.debug("Failed to parse JSON from embedded brackets pattern: {}", e.getMessage());
            }
        }
        
        // Strategy 1: Check if entire response is JSON
        String trimmed = response.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                objectMapper.readTree(trimmed);
                return trimmed;
            } catch (Exception e) {
                // Not valid JSON, continue
            }
        }
        
        // Strategy 2: Look for JSON object with "function_calls" key
        // Match from first { to matching } - handle nested braces properly
        // Use a more robust approach to find complete JSON objects
        int startIdx = response.indexOf('{');
        if (startIdx != -1) {
            int braceCount = 0;
            int jsonStart = -1;
            for (int i = startIdx; i < response.length(); i++) {
                char c = response.charAt(i);
                if (c == '{') {
                    if (jsonStart == -1) jsonStart = i;
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0 && jsonStart != -1) {
                        String candidate = response.substring(jsonStart, i + 1);
                        if (candidate.contains("function_calls")) {
                            try {
                                JsonNode node = objectMapper.readTree(candidate);
                                if (node.has("function_calls")) {
                                    return candidate;
                                }
                            } catch (Exception e) {
                                // Not valid JSON, continue searching
                            }
                        }
                        jsonStart = -1;
                    }
                }
            }
        }
        
        // Fallback: Try regex pattern
        Pattern jsonPattern = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\"function_calls\"[^}]*\\}", Pattern.DOTALL);
        matcher = jsonPattern.matcher(response);
        
        if (matcher.find()) {
            String candidate = matcher.group();
            try {
                objectMapper.readTree(candidate);
                return candidate;
            } catch (Exception e) {
                // Not valid JSON, continue
            }
        }
        
        // Strategy 3: Look for JSON in code blocks or after "Function calls:" text
        // Pattern: [Function calls: [ {...}, {...} ] ] or similar patterns
        Pattern embeddedPattern = Pattern.compile(
            "(?i)(?:function\\s*calls?|tool\\s*calls?)\\s*[:=]\\s*\\[\\s*\\[([^\\]]+)\\]\\s*\\]", 
            Pattern.DOTALL
        );
        matcher = embeddedPattern.matcher(response);
        if (matcher.find()) {
            // Try to construct JSON from the array content
            String arrayContent = matcher.group(1);
            // Try to parse as JSON array items
            try {
                // Look for JSON objects in the array content (handle nested braces)
                List<String> objects = new ArrayList<>();
                int braceCount = 0;
                int start = -1;
                for (int i = 0; i < arrayContent.length(); i++) {
                    char c = arrayContent.charAt(i);
                    if (c == '{') {
                        if (start == -1) start = i;
                        braceCount++;
                    } else if (c == '}') {
                        braceCount--;
                        if (braceCount == 0 && start != -1) {
                            objects.add(arrayContent.substring(start, i + 1));
                            start = -1;
                        }
                    }
                }
                if (!objects.isEmpty()) {
                    // Construct proper JSON
                    StringBuilder json = new StringBuilder("{\"function_calls\":[");
                    for (int i = 0; i < objects.size(); i++) {
                        if (i > 0) json.append(",");
                        json.append(objects.get(i));
                    }
                    json.append("]}");
                    objectMapper.readTree(json.toString());
                    return json.toString();
                }
            } catch (Exception e) {
                log.debug("Failed to parse embedded function calls: {}", e.getMessage());
            }
        }
        
        // Strategy 3b: Look for simpler pattern: [Function calls: [ {...} ] ]
        Pattern simplePattern = Pattern.compile(
            "(?i)\\[\\s*function\\s*calls?\\s*[:=]\\s*\\[([^\\]]+)\\]\\s*\\]", 
            Pattern.DOTALL
        );
        matcher = simplePattern.matcher(response);
        if (matcher.find()) {
            String arrayContent = matcher.group(1);
            try {
                List<String> objects = new ArrayList<>();
                int braceCount = 0;
                int start = -1;
                for (int i = 0; i < arrayContent.length(); i++) {
                    char c = arrayContent.charAt(i);
                    if (c == '{') {
                        if (start == -1) start = i;
                        braceCount++;
                    } else if (c == '}') {
                        braceCount--;
                        if (braceCount == 0 && start != -1) {
                            objects.add(arrayContent.substring(start, i + 1));
                            start = -1;
                        }
                    }
                }
                if (!objects.isEmpty()) {
                    StringBuilder json = new StringBuilder("{\"function_calls\":[");
                    for (int i = 0; i < objects.size(); i++) {
                        if (i > 0) json.append(",");
                        json.append(objects.get(i));
                    }
                    json.append("]}");
                    objectMapper.readTree(json.toString());
                    return json.toString();
                }
            } catch (Exception e) {
                log.debug("Failed to parse simple function calls pattern: {}", e.getMessage());
            }
        }
        
        // Strategy 4: Try to find any JSON object that might contain function_calls
        // More permissive pattern
        Pattern loosePattern = Pattern.compile("\\{[^}]*\"function_calls\"[^}]*\\}", Pattern.DOTALL);
        matcher = loosePattern.matcher(response);
        int start = -1;
        int depth = 0;
        for (int i = 0; i < response.length(); i++) {
            if (response.charAt(i) == '{') {
                if (start == -1) start = i;
                depth++;
            } else if (response.charAt(i) == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    String candidate = response.substring(start, i + 1);
                    if (candidate.contains("function_calls")) {
                        try {
                            objectMapper.readTree(candidate);
                            return candidate;
                        } catch (Exception e) {
                            // Not valid, continue
                        }
                    }
                    start = -1;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Builds a prompt from chat messages.
     * Formats messages for Mistral instruction format: [INST]...[/INST]
     */
    private String buildPrompt(List<ChatMessage> messages) {
        return buildPrompt(messages, "");
    }
    
    /**
     * Builds a prompt from chat messages with optional tool descriptions.
     * Formats messages for Mistral instruction format: [INST]...[/INST]
     */
    private String buildPrompt(List<ChatMessage> messages, String toolDescriptions) {
        StringBuilder prompt = new StringBuilder();
        
        // Extract system message if present
        String systemMessage = null;
        
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage) {
                systemMessage = ((SystemMessage) message).text();
                break;
            }
        }
        
        // Append tool descriptions to system message if available
        if (!toolDescriptions.isEmpty()) {
            if (systemMessage == null) {
                systemMessage = toolDescriptions;
            } else {
                systemMessage = systemMessage + toolDescriptions;
            }
        }
        
        // Build conversation history
        // Similar to BaseGeminiChatModel's fromMessageToGContent, we need to handle:
        // - SystemMessage
        // - UserMessage  
        // - AiMessage (may contain tool execution requests or text)
        // - ToolExecutionResultMessage (tool results fed back to model)
        boolean firstUserMessage = true;
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage) {
                // System message will be included in the first [INST] tag
                continue;
            } else if (message instanceof UserMessage) {
                if (!firstUserMessage) {
                    prompt.append("</s>");
                }
                prompt.append("<s>[INST]");
                if (systemMessage != null && firstUserMessage) {
                    prompt.append(" ").append(systemMessage).append(" ");
                    systemMessage = null; // Only include system message once
                }
                prompt.append(" ").append(((UserMessage) message).singleText()).append(" [/INST]");
                firstUserMessage = false;
            } else if (message instanceof AiMessage) {
                AiMessage aiMessage = (AiMessage) message;
                // Check if this AiMessage contains tool execution requests
                if (aiMessage.toolExecutionRequests() != null && !aiMessage.toolExecutionRequests().isEmpty()) {
                    // Model wants to call tools - format this for Llama
                    // Since Llama doesn't support native function calling, we'll format it as text
                    prompt.append(" [Function calls requested: ");
                    aiMessage.toolExecutionRequests().forEach(req -> {
                        prompt.append(req.name()).append("(").append(req.arguments()).append(") ");
                    });
                    prompt.append("]");
                } else if (aiMessage.text() != null && !aiMessage.text().isEmpty()) {
                    // Regular text response
                    prompt.append(" ").append(aiMessage.text());
                }
            } else if (message instanceof ToolExecutionResultMessage) {
                // Tool execution results - feed these back to the model
                // Format clearly so the model understands it should use this information
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) message;
                prompt.append("</s>");
                prompt.append("<s>[INST] ");
                prompt.append("The function '").append(toolResult.toolName()).append("' returned the following result: ");
                prompt.append(toolResult.text());
                prompt.append(" Please provide a clear answer to the user's question based on this result. ");
                prompt.append("Include the actual data from the result in your response. [/INST]");
            }
        }
        
        // If no user message was found, just use the last message as prompt
        if (prompt.length() == 0 && !messages.isEmpty()) {
            ChatMessage lastMessage = messages.get(messages.size() - 1);
            if (lastMessage instanceof UserMessage) {
                prompt.append("<s>[INST]");
                if (systemMessage != null) {
                    prompt.append(" ").append(systemMessage).append(" ");
                }
                prompt.append(" ").append(((UserMessage) lastMessage).singleText()).append(" [/INST]");
            } else {
                // Fallback: simple format
                prompt.append(buildSimplePrompt(messages));
            }
        }
        
        return prompt.toString();
    }
    
    /**
     * Fallback method for simple prompt building
     */
    private String buildSimplePrompt(List<ChatMessage> messages) {
        StringBuilder prompt = new StringBuilder();
        
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage) {
                prompt.append("System: ").append(((SystemMessage) message).text()).append("\n");
            } else if (message instanceof UserMessage) {
                prompt.append("User: ").append(((UserMessage) message).singleText()).append("\n");
            } else if (message instanceof AiMessage) {
                prompt.append("Assistant: ").append(((AiMessage) message).text()).append("\n");
            }
        }
        
        prompt.append("Assistant: ");
        return prompt.toString();
    }
}

