package com.example.workflow.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.SequentialAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Typed handoffs; ADK owns ordering, this scope keeps Java types rather than ADK string state keys. */
public final class TypedPipeline<I, O> {
    private final Class<I> inputType;
    private final Class<O> outputType;
    private final SequentialAgent root;
    private final Map<String, Object> invocations;

    private TypedPipeline(Class<I> inputType, Class<O> outputType, SequentialAgent root, Map<String, Object> invocations) {
        this.inputType = inputType; this.outputType = outputType; this.root = root; this.invocations = invocations;
    }
    public BaseAgent root() { return root; }
    public void start(String sessionId, I input) { invocations.put(sessionId, inputType.cast(input)); }
    public O finish(String sessionId) { return outputType.cast(invocations.remove(sessionId)); }
    public void discard(String sessionId) { invocations.remove(sessionId); }

    public static <I> Builder<I, I> builder(String name, Class<I> inputType) {
        return new Builder<>(name, inputType, inputType, new ArrayList<>());
    }
    public static final class Builder<I, C> {
        private final String name;
        private final Class<I> inputType;
        private final Class<C> currentType;
        private final List<TypedStageAgent<?, ?>> steps;
        private Builder(String name, Class<I> inputType, Class<C> currentType, List<TypedStageAgent<?, ?>> steps) {
            this.name = name; this.inputType = inputType; this.currentType = currentType; this.steps = steps;
        }
        public <N> Builder<I, N> then(TypedStageAgent<C, N> step) {
            if (!currentType.equals(step.inputType())) throw new IllegalArgumentException("Invalid stage input: " + step.name());
            List<TypedStageAgent<?, ?>> next = new ArrayList<>(steps); next.add(step);
            return new Builder<>(name, inputType, step.outputType(), next);
        }
        public TypedPipeline<I, C> build() {
            if (steps.isEmpty()) throw new IllegalStateException("Pipeline needs at least one subagent");
            Map<String, Object> scope = new ConcurrentHashMap<>();
            steps.forEach(step -> step.bind(scope));
            List<BaseAgent> agents = new ArrayList<>(steps);
            return new TypedPipeline<>(inputType, currentType,
                    SequentialAgent.builder().name(name).subAgents(agents).build(), scope);
        }
    }
}
