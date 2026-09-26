package com.example.workflow.work;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class WorkAgentConfig {
    @Bean ChatModel chatModel(@Value("${app.ollama.base-url}") String url,
                              @Value("${app.ollama.model}") String modelName,
                              @Value("${app.ollama.timeout}") Duration timeout) {
        return OllamaChatModel.builder().baseUrl(url).modelName(modelName).timeout(timeout).build();
    }
    @Bean WorkAgent workAgent(ChatModel model) { return new WorkAgent(model); }
}
