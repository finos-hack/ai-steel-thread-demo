package org.finos.ai.demo.dlp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.ResolvedContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class Mi1DataLeakagePreventionTest {

  private Mi1DlpProperties properties;
  private Mi1DataLeakagePrevention dlp;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    properties = new Mi1DlpProperties();
    meterRegistry = new SimpleMeterRegistry();
    dlp = new Mi1DataLeakagePrevention(properties, meterRegistry);
  }

  @Test
  void filtersCreditRiskContextToAllowlist() {
    ResolvedContext filtered =
        dlp.filterCreditRiskContext(
            new ResolvedContext(
                Map.of(
                    "creditScore", 720,
                    "idNumber", "AB123",
                    "attachedFile", "secret",
                    "requestedAmount", 25000)));

    assertThat(filtered.variables())
        .containsOnlyKeys("creditScore", "requestedAmount")
        .doesNotContainKeys("idNumber", "attachedFile");
    assertThat(meterRegistry.counter("fluxnova.dlp.mi1.egress", "outcome", "blocked", "edge",
            Mi1DataLeakagePrevention.EDGE_AGENT_LLM, "category", "forbidden-or-unlisted-context")
            .count())
        .isEqualTo(1.0);
  }

  @Test
  void strictModeThrowsWhenForbiddenPresent() {
    properties.setStrict(true);
    dlp = new Mi1DataLeakagePrevention(properties, meterRegistry);

    assertThatThrownBy(
            () ->
                dlp.filterCreditRiskContext(
                    new ResolvedContext(Map.of("creditScore", 700, "idNumber", "X"))))
        .isInstanceOf(Mi1DataLeakageException.class)
        .hasMessageContaining("idNumber");
  }

  @Test
  void auditsDocumentIntelligenceEgress() {
    dlp.auditDocumentIntelligenceEgress(2048);
    assertThat(
            meterRegistry
                .counter(
                    "fluxnova.dlp.mi1.egress",
                    "outcome",
                    "allowed",
                    "edge",
                    Mi1DataLeakagePrevention.EDGE_DOCUMENT_INTELLIGENCE,
                    "category",
                    Mi1DataLeakagePrevention.CATEGORY_DOCUMENT_IMAGE)
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void redactsForbiddenNamesForStatusApi() {
    assertThat(dlp.redactForStatusApi("idNumber", "AB123")).isEqualTo("<redacted:mi-1>");
    assertThat(dlp.redactForStatusApi("creditScore", 720)).isEqualTo(720);
    assertThat(dlp.redactForStatusApi("agentConversationTrace", "[...]"))
        .isEqualTo("<redacted:mi-1>");
  }

  @Test
  void redactForStatusApi_respectsExplicitEnforceFlag() {
    properties.setEnabled(false);
    dlp = new Mi1DataLeakagePrevention(properties, meterRegistry);

    assertThat(dlp.redactForStatusApi("idNumber", "AB123")).isEqualTo("AB123");
    assertThat(dlp.redactForStatusApi("idNumber", "AB123", true)).isEqualTo("<redacted:mi-1>");
    assertThat(dlp.redactForStatusApi("idNumber", "AB123", false)).isEqualTo("AB123");
  }
}
