package org.finos.ai.demo.observability;

import org.finos.fluxnova.bpm.engine.delegate.DelegateTask;
import org.finos.fluxnova.bpm.engine.delegate.TaskListener;
import org.springframework.beans.factory.ObjectProvider;

import io.opentelemetry.api.trace.Span;

class OpenTelemetryTaskListener implements TaskListener {

  private final ObjectProvider<OpenTelemetryTracingSupport> tracingSupport;
  private final TaskExecutionTracker tracker;
  private final TaskExecutionMetrics metrics;

  OpenTelemetryTaskListener(
      ObjectProvider<OpenTelemetryTracingSupport> tracingSupport,
      TaskExecutionTracker tracker,
      TaskExecutionMetrics metrics) {
    this.tracingSupport = tracingSupport;
    this.tracker = tracker;
    this.metrics = metrics;
  }

  @Override
  public void notify(DelegateTask delegateTask) {
    String eventName = delegateTask.getEventName();
    if (TaskListener.EVENTNAME_CREATE.equals(eventName)) {
      OpenTelemetryTracingSupport tracing = tracingSupport.getIfAvailable();
      Span span = tracing == null
          ? null
          : tracing.startTaskSpan(
              delegateTask.getId(),
              delegateTask.getName(),
              delegateTask.getProcessInstanceId(),
              eventName);
      tracker.start(
          spanKey(delegateTask),
          TaskExecutionMetrics.TYPE_USER_TASK,
          delegateTask.getName(),
          span);
      return;
    }

    if (TaskListener.EVENTNAME_COMPLETE.equals(eventName) || TaskListener.EVENTNAME_DELETE.equals(eventName)) {
      tracker.end(spanKey(delegateTask), metrics);
    }
  }

  private String spanKey(DelegateTask delegateTask) {
    return "task:" + delegateTask.getId();
  }

}
