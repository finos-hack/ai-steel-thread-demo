package org.finos.ai.demo.observability;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.opentelemetry.api.trace.Span;

class TaskExecutionTracker {

  private record TrackedExecution(long startNanos, Span span, String taskType, String taskName) {
  }

  private final ConcurrentMap<String, TrackedExecution> active = new ConcurrentHashMap<>();

  void start(String key, String taskType, String taskName, Span span) {
    active.put(key, new TrackedExecution(System.nanoTime(), span, taskType, taskName));
  }

  void end(String key, TaskExecutionMetrics metrics) {
    TrackedExecution tracked = active.remove(key);
    if (tracked == null) {
      return;
    }
    if (tracked.span() != null) {
      tracked.span().end();
    }
    metrics.recordSuccess(
        tracked.taskType(),
        tracked.taskName(),
        Duration.ofNanos(System.nanoTime() - tracked.startNanos()));
  }

}
