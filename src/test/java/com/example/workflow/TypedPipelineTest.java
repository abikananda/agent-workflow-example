package com.example.workflow;

import com.example.workflow.agents.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TypedPipelineTest {
    @Test void orderedStagesPassTypedValues() {
        List<String> order = new ArrayList<>();
        TypedStageAgent<String, Integer> first = new TypedStageAgent<>("first", "Convert to length") {
            public Class<String> inputType() { return String.class; }
            public Class<Integer> outputType() { return Integer.class; }
            protected Integer execute(String value) { order.add("first"); return value.length(); }
        };
        TypedStageAgent<Integer, String> second = new TypedStageAgent<>("second", "Format length") {
            public Class<Integer> inputType() { return Integer.class; }
            public Class<String> outputType() { return String.class; }
            protected String execute(Integer value) { order.add("second"); return "length=" + value; }
        };
        var pipeline = TypedPipeline.builder("test_agent", String.class).then(first).then(second).build();
        assertEquals("length=4", new AdkExecutor<>(pipeline).execute("test-user", "test"));
        assertEquals(List.of("first", "second"), order);
    }
}
