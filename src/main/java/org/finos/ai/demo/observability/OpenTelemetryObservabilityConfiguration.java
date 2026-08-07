package org.finos.ai.demo.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.micrometer.core.instrument.MeterRegistry;
import org.finos.fluxnova.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigureAfter({OpenTelemetryTracingAutoConfiguration.class, OpenTelemetrySdkAutoConfiguration.class})
@EnableConfigurationProperties(OpenTelemetryObservabilityProperties.class)
@ConditionalOnProperty(prefix = "fluxnova.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenTelemetryObservabilityConfiguration {

  @Bean
  TaskExecutionMetrics taskExecutionMetrics(MeterRegistry meterRegistry) {
    return new TaskExecutionMetrics(meterRegistry);
  }

  @Bean
  LlmTokenUsageMetrics llmTokenUsageMetrics(
      MeterRegistry meterRegistry,
      ObjectProvider<ProcessScopedLlmTokenUsageWriter> processScopedWriter,
      ObjectProvider<Mi4ObservabilityProperties> mi4Properties,
      ObjectProvider<org.finos.ai.demo.tokenomics.TokenomicsService> tokenomicsService) {
    return new LlmTokenUsageMetrics(
        meterRegistry, processScopedWriter, mi4Properties, tokenomicsService);
  }

  @Bean
  TaskExecutionTracker taskExecutionTracker() {
    return new TaskExecutionTracker();
  }

  @Bean
  OpenTelemetryTracingSupport openTelemetryTracingSupport(OpenTelemetry openTelemetry) {
    return new OpenTelemetryTracingSupport(openTelemetry);
  }

  @Bean
  OpenTelemetryBpmnParseListener openTelemetryBpmnParseListener(
      ObjectProvider<OpenTelemetryTracingSupport> tracingSupport,
      TaskExecutionTracker tracker,
      TaskExecutionMetrics metrics) {
    var processExecutionListener =
        new OpenTelemetryExecutionListener(tracingSupport, tracker, metrics, true);
    var activityExecutionListener =
        new OpenTelemetryExecutionListener(tracingSupport, tracker, metrics, false);
    var taskListener = new OpenTelemetryTaskListener(tracingSupport, tracker, metrics);
    return new OpenTelemetryBpmnParseListener(processExecutionListener, activityExecutionListener, taskListener);
  }

  @Bean
  ProcessEnginePlugin openTelemetryProcessEnginePlugin(OpenTelemetryBpmnParseListener parseListener) {
    return new OpenTelemetryProcessEnginePlugin(parseListener);
  }

  @Bean
  ExternalTaskTracingAspect externalTaskTracingAspect(
      ObjectProvider<OpenTelemetryTracingSupport> tracingSupport, TaskExecutionMetrics metrics) {
    return new ExternalTaskTracingAspect(tracingSupport, metrics);
  }

}
