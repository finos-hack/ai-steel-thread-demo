package org.finos.ai.demo.observability;

import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;

final class TaskExecutionMetrics {

  static final String TYPE_EXTERNAL_TASK = "external-task";
  static final String TYPE_ACTIVITY = "activity";
  static final String TYPE_USER_TASK = "user-task";
  static final String TYPE_PROCESS = "process";

  private static final String EXECUTIONS = "fluxnova.task.executions";
  private static final String DURATION = "fluxnova.task.duration";

  private final MeterRegistry registry;

  TaskExecutionMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  void recordSuccess(String taskType, String taskName, Duration duration) {
    record(taskType, taskName, duration, true);
  }

  void recordFailure(String taskType, String taskName, Duration duration) {
    record(taskType, taskName, duration, false);
  }

  private void record(String taskType, String taskName, Duration duration, boolean success) {
    String name = taskName == null || taskName.isBlank() ? "unknown" : taskName;
    registry.counter(
            EXECUTIONS,
            "task_type", taskType,
            "task_name", name,
            "status", success ? "success" : "failure")
        .increment();
    if (success) {
      registry.timer(DURATION, "task_type", taskType, "task_name", name).record(duration);
    }
  }

}
