package org.finos.ai.demo.observability;

import java.time.Duration;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.finos.fluxnova.bpm.client.task.ExternalTask;
import org.finos.fluxnova.bpm.client.task.ExternalTaskHandler;
import org.springframework.beans.factory.ObjectProvider;

import io.opentelemetry.context.Scope;

@Aspect
class ExternalTaskTracingAspect {

  private final ObjectProvider<OpenTelemetryTracingSupport> tracingSupport;
  private final TaskExecutionMetrics metrics;

  ExternalTaskTracingAspect(
      ObjectProvider<OpenTelemetryTracingSupport> tracingSupport, TaskExecutionMetrics metrics) {
    this.tracingSupport = tracingSupport;
    this.metrics = metrics;
  }

  @Around("execution(* org.finos.fluxnova.bpm.client.task.ExternalTaskHandler.execute(..))")
  public void traceExternalTask(ProceedingJoinPoint joinPoint) throws Throwable {
    ExternalTask externalTask = (ExternalTask) joinPoint.getArgs()[0];
    long startNanos = System.nanoTime();
    OpenTelemetryTracingSupport tracing = tracingSupport.getIfAvailable();
    var span = tracing == null
        ? null
        : tracing.startExternalTaskSpan(
            externalTask.getId(),
            externalTask.getTopicName(),
            externalTask.getProcessInstanceId(),
            externalTask.getProcessDefinitionId(),
            externalTask.getActivityId());
    Scope scope = span == null ? null : span.makeCurrent();
    try {
      joinPoint.proceed();
      metrics.recordSuccess(
          TaskExecutionMetrics.TYPE_EXTERNAL_TASK,
          externalTask.getTopicName(),
          Duration.ofNanos(System.nanoTime() - startNanos));
    } catch (Throwable throwable) {
      if (span != null) {
        span.recordException(throwable);
      }
      metrics.recordFailure(
          TaskExecutionMetrics.TYPE_EXTERNAL_TASK,
          externalTask.getTopicName(),
          Duration.ofNanos(System.nanoTime() - startNanos));
      throw throwable;
    } finally {
      if (scope != null) {
        scope.close();
      }
      if (span != null) {
        span.end();
      }
    }
  }

}
