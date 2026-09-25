package com.example.workflow.work;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.langchain4j.LangChain4j;
import dev.langchain4j.model.chat.ChatModel;

/** Owns the real ADK LlmAgent configuration and its model adapter. */
public final class ExecutionSubagent {
    private final ChatModel model;
    public ExecutionSubagent(ChatModel model) { this.model = model; }
    public LlmAgent create() {
        return LlmAgent.builder()
                .name("execution_subagent")
                .description("Generate a summary with Ollama through the ADK LangChain4j bridge")
                .model(LangChain4j.builder().chatModel(model).modelName("local-ollama").build())
                .instruction("Write a concise factual summary. Cover {planPrompt}. Include the topic name. "
                        + "Do not invent sources or statistics. Return plain text only.")
                .outputKey("draft")
                .build();
    }
}
