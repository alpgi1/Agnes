package com.spherecast.agnes.service;

import com.spherecast.agnes.dto.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HistoryFormatter {

    private static final int MAX_CONTENT_LENGTH = 500;

    public String format(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "(no prior turns)";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : history) {
            String content = msg.content();
            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH) + "...";
            }
            sb.append(msg.role()).append(": ").append(content).append("\n");
        }
        return sb.toString().stripTrailing();
    }
}
