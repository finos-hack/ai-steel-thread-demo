package org.finos.ai.demo.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class TaskExecutionMetricsTest {

  @Test
  void recordsTaskExecutionMetrics() {
    var meterRegistry = new SimpleMeterRegistry();
    var metrics = new TaskExecutionMetrics(meterRegistry);

    metrics.recordSuccess(
        TaskExecutionMetrics.TYPE_EXTERNAL_TASK, "check-credit-score", Duration.ofMillis(50));

    assertThat(meterRegistry.find("fluxnova.task.executions").counter()).isNotNull();
    assertThat(meterRegistry.find("fluxnova.task.duration").timer()).isNotNull();
  }

}
