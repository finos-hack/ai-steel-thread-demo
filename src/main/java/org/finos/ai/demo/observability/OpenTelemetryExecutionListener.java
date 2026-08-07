package org.finos.ai.demo.observability;

import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.ExecutionListener;
import org.springframework.beans.factory.ObjectProvider;

import io.opentelemetry.api.trace.Span;

class OpenTelemetryExecutionListener implements ExecutionListener {

  private final ObjectProvider<OpenTelemetryTracingSupport> tracingSupport;
  private final TaskExecutionTracker tracker;
  private final TaskExecutionMetrics metrics;
  private final boolean processScope;

  OpenTelemetryExecutionListener(
      ObjectProvider<OpenTelemetryTracingSupport> tracingSupport,
      TaskExecutionTracker tracker,
      TaskExecutionMetrics metrics,
      boolean processScope) {
    this.tracingSupport = tracingSupport;
    this.tracker = tracker;
    this.metrics = metrics;
    this.processScope = processScope;
  }

  @Override
  public void notify(DelegateExecution execution) {
    String eventName = execution.getEventName();
    if (ExecutionListener.EVENTNAME_START.equals(eventName)) {
      onStart(execution);
      return;
    }
    if (ExecutionListener.EVENTNAME_END.equals(eventName)) {
      tracker.end(spanKey(execution), metrics);
    }
  }

  private void onStart(DelegateExecution execution) {
    OpenTelemetryTracingSupport tracing = tracingSupport.getIfAvailable();
    if (processScope) {
      Span span = tracing == null
          ? null
          : tracing.startProcessSpan(
              execution.getProcessInstanceId(),
              execution.getProcessDefinitionId(),
              execution.getBusinessKey());
      tracker.start(
          spanKey(execution),
          TaskExecutionMetrics.TYPE_PROCESS,
          execution.getProcessDefinitionId(),
          span);
      return;
    }

    Span span = tracing == null
        ? null
        : tracing.startActivitySpan(
            execution.getProcessInstanceId(),
            execution.getProcessDefinitionId(),
            execution.getCurrentActivityId(),
            execution.getActivityInstanceId());
    tracker.start(
        spanKey(execution),
        TaskExecutionMetrics.TYPE_ACTIVITY,
        execution.getCurrentActivityId(),
        span);
  }

  private String spanKey(DelegateExecution execution) {
    if (processScope) {
      return "process:" + execution.getProcessInstanceId();
    }
    return "activity:" + execution.getActivityInstanceId();
  }

}
