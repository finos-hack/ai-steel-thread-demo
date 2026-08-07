package org.finos.ai.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.finos.ai.demo.acceptance.Mi5AcceptanceProperties;
import org.finos.ai.demo.acceptance.Mi5AcceptanceReport;
import org.finos.ai.demo.dlp.Mi1DlpProperties;
import org.finos.ai.demo.feedback.Mi11FeedbackProperties;
import org.finos.ai.demo.observability.Mi4ObservabilityProperties;
import org.junit.jupiter.api.Test;

class ComplianceControlSnapshotTest {

  @Test
  void capture_and_resolve_freezeLiveTogglesAndSat() {
    Mi1DlpProperties mi1 = new Mi1DlpProperties();
    mi1.setEnabled(true);
    mi1.setRedactStatusApi(true);
    Mi4ObservabilityProperties mi4 = new Mi4ObservabilityProperties();
    mi4.setEnabled(false);
    Mi5AcceptanceProperties mi5 = new Mi5AcceptanceProperties();
    mi5.setEnabled(true);
    Mi11FeedbackProperties mi11 = new Mi11FeedbackProperties();
    mi11.setEnabled(true);
    Mi5AcceptanceReport sat =
        new Mi5AcceptanceReport("2026-08-02T10:00:00Z", true, 1.0, 0.9, 16, 16, 0, java.util.List.of());

    Map<String, Object> captured = ComplianceControlSnapshot.capture(mi1, mi4, mi5, mi11, sat);

    mi1.setEnabled(false);
    mi4.setEnabled(true);
    mi5.setEnabled(false);
    mi11.setEnabled(false);

    LoanApprovalComplianceReport.ControlPosture posture =
        ComplianceControlSnapshot.resolvePosture(captured, mi1, mi4, mi5, mi11);
    LoanApprovalComplianceReport.SatSnapshot satSnapshot =
        ComplianceControlSnapshot.resolveSat(captured, posture.mi5Enabled(), null);

    assertThat(posture.instanceSnapshot()).isTrue();
    assertThat(posture.mi1Enabled()).isTrue();
    assertThat(posture.mi4Enabled()).isFalse();
    assertThat(posture.mi5Enabled()).isTrue();
    assertThat(posture.mi11Enabled()).isTrue();
    assertThat(posture.snapshottedAt()).isNotBlank();
    assertThat(satSnapshot.lastAccepted()).isTrue();
    assertThat(satSnapshot.lastPassRate()).isEqualTo(1.0);
    assertThat(satSnapshot.lastRanAt()).isEqualTo("2026-08-02T10:00:00Z");
  }

  @Test
  void resolvePosture_fallsBackToLiveWhenNoSnapshot() {
    Mi1DlpProperties mi1 = new Mi1DlpProperties();
    mi1.setEnabled(false);
    Mi4ObservabilityProperties mi4 = new Mi4ObservabilityProperties();
    Mi5AcceptanceProperties mi5 = new Mi5AcceptanceProperties();
    Mi11FeedbackProperties mi11 = new Mi11FeedbackProperties();
    mi11.setEnabled(false);

    LoanApprovalComplianceReport.ControlPosture posture =
        ComplianceControlSnapshot.resolvePosture(Map.of(), mi1, mi4, mi5, mi11);

    assertThat(posture.instanceSnapshot()).isFalse();
    assertThat(posture.mi1Enabled()).isFalse();
    assertThat(posture.mi4Enabled()).isTrue();
    assertThat(posture.mi11Enabled()).isFalse();
  }
}
