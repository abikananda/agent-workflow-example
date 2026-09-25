package com.example.workflow;

import com.example.workflow.agents.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TypedPipelineTest {
    @Test void orderedStagesPassTypedValues() {
        List<String> order = new ArrayList<>();
        TypedStep<String, Integer> first = new TypedStep<>() {
            public String name() { return "first"; }
            public Class<String> inputType() { return String.class; }
            public Class<Integer> outputType() { return Integer.class; }
            public Integer apply(String value) { order.add("first"); return value.length(); }
        };
        TypedStep<Integer, String> second = new TypedStep<>() {
            public String name() { return "second"; }
            public Class<Integer> inputType() { return Integer.class; }
            public Class<String> outputType() { return String.class; }
            public String apply(Integer value) { order.add("second"); return "length=" + value; }
        };
        var pipeline = TypedPipeline.builder("test_agent", String.class).then(first).then(second).build();
        assertEquals("length=4", new AdkExecutor<>(pipeline).execute("test-user", "test"));
        assertEquals(List.of("first", "second"), order);
    }
}
