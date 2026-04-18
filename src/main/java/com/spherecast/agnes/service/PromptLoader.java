package com.spherecast.agnes.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PromptLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptLoader.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Z_]+)}}");

    private final ResourceLoader resourceLoader;

    public PromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Cacheable("prompt-templates")
    public String load(String name) {
        String path = "classpath:prompts/" + name + ".txt";
        try {
            var resource = resourceLoader.getResource(path);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load prompt template: " + path, e);
        }
    }

    public String render(String name, Map<String, String> vars) {
        String template = load(name);
        StringBuffer sb = new StringBuffer();
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) {
            String key = m.group(1);
            String value = vars.get(key);
            if (value == null) {
                log.warn("Prompt template '{}' has placeholder {{{}}} with no value — replacing with empty string", name, key);
                value = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        String result = sb.toString();
        Matcher remaining = PLACEHOLDER.matcher(result);
        if (remaining.find()) {
            log.warn("Prompt template '{}' still contains unreplaced placeholders after render", name);
        }
        return result;
    }
}
