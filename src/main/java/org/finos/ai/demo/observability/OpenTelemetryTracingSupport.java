package org.finos.ai.demo.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

final class OpenTelemetryTracingSupport {

  static final AttributeKey<String> PROCESS_INSTANCE_ID = AttributeKey.stringKey("fluxnova.process.instance.id");
  static final AttributeKey<String> PROCESS_DEFINITION_ID = AttributeKey.stringKey("fluxnova.process.definition.id");
  static final AttributeKey<String> BUSINESS_KEY = AttributeKey.stringKey("fluxnova.process.business.key");
  static final AttributeKey<String> ACTIVITY_ID = AttributeKey.stringKey("fluxnova.activity.id");
  static final AttributeKey<String> ACTIVITY_INSTANCE_ID = AttributeKey.stringKey("fluxnova.activity.instance.id");
  static final AttributeKey<String> TASK_ID = AttributeKey.stringKey("fluxnova.task.id");
  static final AttributeKey<String> TASK_NAME = AttributeKey.stringKey("fluxnova.task.name");
  static final AttributeKey<String> TASK_EVENT = AttributeKey.stringKey("fluxnova.task.event");
  static final AttributeKey<String> EXTERNAL_TASK_ID = AttributeKey.stringKey("fluxnova.external-task.id");
  static final AttributeKey<String> EXTERNAL_TASK_TOPIC = AttributeKey.stringKey("fluxnova.external-task.topic");

  private final Tracer tracer;

  OpenTelemetryTracingSupport(OpenTelemetry openTelemetry) {
    this.tracer = openTelemetry.getTracer("org.finos.ai.demo.observability");
  }

  Span startProcessSpan(String processInstanceId, String processDefinitionId, String businessKey) {
    var builder = tracer.spanBuilder("fluxnova.process")
        .setSpanKind(SpanKind.INTERNAL)
        .setAttribute(PROCESS_INSTANCE_ID, processInstanceId)
        .setAttribute(PROCESS_DEFINITION_ID, processDefinitionId);
    if (businessKey != null) {
      builder.setAttribute(BUSINESS_KEY, businessKey);
    }
    return builder.startSpan();
  }

  Span startActivitySpan(
      String processInstanceId,
      String processDefinitionId,
      String activityId,
      String activityInstanceId) {
    var builder = tracer.spanBuilder("fluxnova.activity")
        .setSpanKind(SpanKind.INTERNAL)
        .setAttribute(PROCESS_INSTANCE_ID, processInstanceId)
        .setAttribute(PROCESS_DEFINITION_ID, processDefinitionId)
        .setAttribute(ACTIVITY_INSTANCE_ID, activityInstanceId);
    if (activityId != null) {
      builder.setAttribute(ACTIVITY_ID, activityId);
    }
    return builder.startSpan();
  }

  Span startTaskSpan(String taskId, String taskName, String processInstanceId, String eventName) {
    var builder = tracer.spanBuilder("fluxnova.task")
        .setSpanKind(SpanKind.INTERNAL)
        .setAttribute(TASK_ID, taskId)
        .setAttribute(PROCESS_INSTANCE_ID, processInstanceId)
        .setAttribute(TASK_EVENT, eventName);
    if (taskName != null) {
      builder.setAttribute(TASK_NAME, taskName);
    }
    return builder.startSpan();
  }

  Span startExternalTaskSpan(
      String externalTaskId,
      String topic,
      String processInstanceId,
      String processDefinitionId,
      String activityId) {
    var builder = tracer.spanBuilder("fluxnova.external-task")
        .setSpanKind(SpanKind.INTERNAL)
        .setAttribute(EXTERNAL_TASK_ID, externalTaskId)
        .setAttribute(EXTERNAL_TASK_TOPIC, topic)
        .setAttribute(PROCESS_INSTANCE_ID, processInstanceId)
        .setAttribute(PROCESS_DEFINITION_ID, processDefinitionId);
    if (activityId != null) {
      builder.setAttribute(ACTIVITY_ID, activityId);
    }
    return builder.startSpan();
  }

}
