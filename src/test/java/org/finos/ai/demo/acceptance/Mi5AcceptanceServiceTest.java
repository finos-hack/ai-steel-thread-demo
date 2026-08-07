package org.finos.ai.demo.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import org.finos.ai.demo.dlp.Mi1DataLeakagePrevention;
import org.finos.ai.demo.dlp.Mi1DlpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class Mi5AcceptanceServiceTest {

  private Mi5AcceptanceService service;
  private Mi5AcceptanceProperties properties;

  @BeforeEach
  void setUp() {
    properties = new Mi5AcceptanceProperties();
    Mi1DlpProperties dlpProperties = new Mi1DlpProperties();
    Mi1DataLeakagePrevention dlp =
        new Mi1DataLeakagePrevention(dlpProperties, new SimpleMeterRegistry());
    service = new Mi5AcceptanceService(properties, dlp, dlpProperties);
  }

  @Test
  void runSuite_meetsPassRateThreshold() {
    Mi5AcceptanceReport report = service.runSuite();

    assertThat(report.total()).isGreaterThanOrEqualTo(10);
    assertThat(report.failed()).isZero();
    assertThat(report.passRate()).isEqualTo(1.0);
    assertThat(report.accepted()).isTrue();
    assertThat(service.lastReport()).isSameAs(report);
  }

  @Test
  void runSuite_whenDisabled_isNotAccepted() {
    properties.setEnabled(false);

    Mi5AcceptanceReport report = service.runSuite();

    assertThat(report.accepted()).isFalse();
    assertThat(report.cases()).hasSize(1);
    assertThat(report.cases().getFirst().id()).isEqualTo("mi5-disabled");
  }
}
