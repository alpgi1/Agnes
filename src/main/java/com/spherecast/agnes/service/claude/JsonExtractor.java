package com.spherecast.agnes.service.claude;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.regex.Pattern;

@Component
public class JsonExtractor {

    private static final Pattern LEADING_FENCE = Pattern.compile("^```(?:json)?\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_FENCE = Pattern.compile("\\s*```\\s*$");

    private final ObjectMapper objectMapper;

    public JsonExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode extractJson(String text) {
        if (text == null || text.isBlank()) {
            throw new JsonExtractionException("Cannot extract JSON from empty text");
        }
        String stripped = stripFences(text.trim());

        JsonNode node = tryParse(stripped);
        if (node != null) {
            return node;
        }

        String objectSubstring = between(stripped, '{', '}');
        if (objectSubstring != null) {
            node = tryParse(objectSubstring);
            if (node != null) {
                return node;
            }
        }

        String arraySubstring = between(stripped, '[', ']');
        if (arraySubstring != null) {
            node = tryParse(arraySubstring);
            if (node != null) {
                return node;
            }
        }

        throw new JsonExtractionException(
                "Could not extract JSON from response: " + truncate(text));
    }

    public <T> T extractJson(String text, Class<T> type) {
        JsonNode node = extractJson(text);
        try {
            return objectMapper.treeToValue(node, type);
        } catch (JacksonException e) {
            throw new JsonExtractionException(
                    "Failed to bind extracted JSON to " + type.getSimpleName() + ": " + truncate(text), e);
        }
    }

    private String stripFences(String s) {
        String out = LEADING_FENCE.matcher(s).replaceFirst("");
        out = TRAILING_FENCE.matcher(out).replaceFirst("");
        return out.trim();
    }

    private JsonNode tryParse(String candidate) {
        if (candidate == null || candidate.isBlank()) return null;
        try {
            return objectMapper.readTree(candidate);
        } catch (JacksonException e) {
            return null;
        }
    }

    private String between(String s, char open, char close) {
        int start = s.indexOf(open);
        int end = s.lastIndexOf(close);
        if (start < 0 || end <= start) return null;
        return s.substring(start, end + 1);
    }

    private String truncate(String s) {
        if (s.length() <= 500) return s;
        return s.substring(0, 500) + "...(truncated)";
    }
}
