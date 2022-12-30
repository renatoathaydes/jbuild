package jbuild.api;

@JbTaskInfo(
        name = "my-custom-task",
        inputs = {"*.txt", "*.json"},
        phase = @CustomTaskPhase( index = 42, name = "my-custom-phase"))
public class ExampleAnnotated {
}
