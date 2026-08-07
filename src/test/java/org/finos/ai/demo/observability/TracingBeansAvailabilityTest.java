package org.finos.ai.demo.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import io.opentelemetry.api.OpenTelemetry;

@SpringBootTest
class TracingBeansAvailabilityTest {

  @Autowired ApplicationContext context;

  @Test
  void openTelemetryAndTracingSupportAreAvailable() {
    var openTelemetryBeans = context.getBeansOfType(OpenTelemetry.class);
    assertThat(openTelemetryBeans)
        .as("OpenTelemetry SDK bean required for custom spans")
        .isNotEmpty();
    assertThat(context.getBeansOfType(OpenTelemetryTracingSupport.class))
        .as(
            "OpenTelemetryTracingSupport required for external task traces"
                + " (OpenTelemetry beans: "
                + openTelemetryBeans.keySet()
                + ")")
        .isNotEmpty();
  }

}
