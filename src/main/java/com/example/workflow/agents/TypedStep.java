package com.example.workflow.agents;

public interface TypedStep<I, O> {
    String name();
    Class<I> inputType();
    Class<O> outputType();
    O apply(I input);
}
