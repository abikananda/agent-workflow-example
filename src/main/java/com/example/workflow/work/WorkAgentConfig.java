package com.example.workflow.work;

import com.example.workflow.agents.*;
import com.example.workflow.model.Contracts.*;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class WorkAgentConfig {
    @Bean ChatModel chatModel(@Value("${app.ollama.base-url}") String url,
                              @Value("${app.ollama.model}") String modelName) {
        return OllamaChatModel.builder().baseUrl(url).modelName(modelName).build();
    }
    @Bean AdkExecutor<WorkRequest, Reviewed> workAgent(ChatModel model) {
        TypedStep<WorkRequest, WorkPlan> analysis = new TypedStep<>() {
            public String name() { return "analysis_subagent"; }
            public Class<WorkRequest> inputType() { return WorkRequest.class; }
            public Class<WorkPlan> outputType() { return WorkPlan.class; }
            public WorkPlan apply(WorkRequest input) {
                return new WorkPlan(input.topic().trim(), List.of("Overview", "Key points", "Practical implications"));
            }
        };
        TypedStep<WorkPlan, Draft> execution = new TypedStep<>() {
            public String name() { return "execution_subagent"; }
            public Class<WorkPlan> inputType() { return WorkPlan.class; }
            public Class<Draft> outputType() { return Draft.class; }
            public Draft apply(WorkPlan plan) {
                String prompt = "Write a concise, factual summary about: " + plan.topic()
                        + ". Cover: " + String.join(", ", plan.sections())
                        + ". No invented sources or statistics. Return plain text only.";
                return new Draft(plan, model.chat(prompt));
            }
        };
        TypedStep<Draft, Reviewed> review = new TypedStep<>() {
            public String name() { return "review_subagent"; }
            public Class<Draft> inputType() { return Draft.class; }
            public Class<Reviewed> outputType() { return Reviewed.class; }
            public Reviewed apply(Draft draft) {
                String summary = draft.summary() == null ? "" : draft.summary().trim();
                if (summary.length() < 40 || summary.length() > 10000)
                    throw new IllegalArgumentException("Generated summary length is outside accepted range");
                if (!summary.toLowerCase().contains(draft.plan().topic().toLowerCase()))
                    throw new IllegalArgumentException("Summary omits requested topic");
                return new Reviewed(draft.plan().topic(), summary, draft.plan().sections());
            }
        };
        return new AdkExecutor<>(TypedPipeline.builder("work_agent", WorkRequest.class)
                .then(analysis).then(execution).then(review).build());
    }
}
