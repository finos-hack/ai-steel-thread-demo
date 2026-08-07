package org.finos.ai.demo;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.finos.ai.demo.acceptance.Mi5AcceptanceReport;
import org.finos.ai.demo.acceptance.Mi5AcceptanceProperties;
import org.finos.ai.demo.dlp.Mi1DlpProperties;
import org.finos.ai.demo.feedback.Mi11FeedbackProperties;
import org.finos.ai.demo.observability.Mi4ObservabilityProperties;

/**
 * Process variables that freeze mi-1 / mi-4 / mi-5 / mi-11 enforcement (+ last SAT summary) at
 * instance start so compliance evidence reflects the posture that applied to that run.
 */
final class ComplianceControlSnapshot {

  static final String MI1_ENABLED = "complianceMi1Enabled";
  static final String MI1_REDACT_STATUS_API = "complianceMi1RedactStatusApi";
  static final String MI4_ENABLED = "complianceMi4Enabled";
  static final String MI5_ENABLED = "complianceMi5Enabled";
  static final String MI11_ENABLED = "complianceMi11Enabled";
  static final String SNAPSHOTTED_AT = "complianceControlsSnapshottedAt";
  static final String SAT_LAST_ACCEPTED = "complianceSatLastAccepted";
  static final String SAT_LAST_PASS_RATE = "complianceSatLastPassRate";
  static final String SAT_LAST_RAN_AT = "complianceSatLastRanAt";
  static final String SAT_NOTE = "complianceSatNote";

  private ComplianceControlSnapshot() {}

  static Map<String, Object> capture(
      Mi1DlpProperties mi1,
      Mi4ObservabilityProperties mi4,
      Mi5AcceptanceProperties mi5,
      Mi11FeedbackProperties mi11,
      Mi5AcceptanceReport lastSat) {
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put(MI1_ENABLED, mi1.isEnabled());
    variables.put(MI1_REDACT_STATUS_API, mi1.isRedactStatusApi());
    variables.put(MI4_ENABLED, mi4.isEnabled());
    variables.put(MI5_ENABLED, mi5.isEnabled());
    variables.put(MI11_ENABLED, mi11.isEnabled());
    variables.put(SNAPSHOTTED_AT, Instant.now().toString());
    if (lastSat != null) {
      variables.put(SAT_LAST_ACCEPTED, lastSat.accepted());
      variables.put(SAT_LAST_PASS_RATE, lastSat.passRate());
      variables.put(SAT_LAST_RAN_AT, lastSat.ranAt());
      variables.put(
          SAT_NOTE,
          lastSat.accepted()
              ? "Last system acceptance run met the pass-rate threshold (snapshotted at start)"
              : "Last system acceptance run did not meet the pass-rate threshold (snapshotted at start)");
    } else {
      variables.put(
          SAT_NOTE,
          mi5.isEnabled()
              ? "Suite enabled at start; no SAT run had been captured yet in this JVM session"
              : "Suite disabled at start");
    }
    return variables;
  }

  static boolean hasSnapshot(Map<String, Object> variables) {
    return variables != null && variables.containsKey(MI1_ENABLED);
  }

  static LoanApprovalComplianceReport.ControlPosture resolvePosture(
      Map<String, Object> variables,
      Mi1DlpProperties mi1,
      Mi4ObservabilityProperties mi4,
      Mi5AcceptanceProperties mi5,
      Mi11FeedbackProperties mi11) {
    if (hasSnapshot(variables)) {
      return new LoanApprovalComplianceReport.ControlPosture(
          asBoolean(variables.get(MI1_ENABLED), mi1.isEnabled()),
          asBoolean(variables.get(MI1_REDACT_STATUS_API), mi1.isRedactStatusApi()),
          asBoolean(variables.get(MI4_ENABLED), mi4.isEnabled()),
          asBoolean(variables.get(MI5_ENABLED), mi5.isEnabled()),
          asBoolean(variables.get(MI11_ENABLED), mi11.isEnabled()),
          asString(variables.get(SNAPSHOTTED_AT)),
          true);
    }
    return new LoanApprovalComplianceReport.ControlPosture(
        mi1.isEnabled(),
        mi1.isRedactStatusApi(),
        mi4.isEnabled(),
        mi5.isEnabled(),
        mi11.isEnabled(),
        null,
        false);
  }

  static LoanApprovalComplianceReport.SatSnapshot resolveSat(
      Map<String, Object> variables,
      boolean mi5Enabled,
      Mi5AcceptanceReport liveLastSat) {
    if (hasSnapshot(variables)) {
      Boolean accepted = asNullableBoolean(variables.get(SAT_LAST_ACCEPTED));
      Double passRate = asNullableDouble(variables.get(SAT_LAST_PASS_RATE));
      String ranAt = asString(variables.get(SAT_LAST_RAN_AT));
      String note = asString(variables.get(SAT_NOTE));
      if (note == null || note.isBlank()) {
        note =
            accepted == null
                ? (mi5Enabled
                    ? "Suite enabled at start; no SAT run had been captured yet"
                    : "Suite disabled at start")
                : accepted
                    ? "Last system acceptance run met the pass-rate threshold (snapshotted at start)"
                    : "Last system acceptance run did not meet the pass-rate threshold (snapshotted at start)";
      }
      return new LoanApprovalComplianceReport.SatSnapshot(
          mi5Enabled, accepted, passRate, ranAt, note);
    }
    if (liveLastSat == null) {
      return new LoanApprovalComplianceReport.SatSnapshot(
          mi5Enabled,
          null,
          null,
          null,
          mi5Enabled
              ? "Suite enabled; no run captured yet for this JVM session"
              : "Suite disabled");
    }
    return new LoanApprovalComplianceReport.SatSnapshot(
        mi5Enabled,
        liveLastSat.accepted(),
        liveLastSat.passRate(),
        liveLastSat.ranAt(),
        liveLastSat.accepted()
            ? "Last system acceptance run met the pass-rate threshold"
            : "Last system acceptance run did not meet the pass-rate threshold");
  }

  /** Reconstruct a lightweight report for checklist logic when a snapshot exists. */
  static Mi5AcceptanceReport toAcceptanceReportForChecks(
      LoanApprovalComplianceReport.SatSnapshot sat) {
    if (sat.lastAccepted() == null) {
      return null;
    }
    double passRate = sat.lastPassRate() == null ? 0 : sat.lastPassRate();
    return new Mi5AcceptanceReport(
        sat.lastRanAt() == null ? Instant.now().toString() : sat.lastRanAt(),
        sat.lastAccepted(),
        passRate,
        0.9,
        0,
        0,
        0,
        java.util.List.of());
  }

  private static boolean asBoolean(Object value, boolean fallback) {
    Boolean parsed = asNullableBoolean(value);
    return parsed == null ? fallback : parsed;
  }

  private static Boolean asNullableBoolean(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof String text && !text.isBlank()) {
      return Boolean.parseBoolean(text.trim());
    }
    return null;
  }

  private static Double asNullableDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Double.parseDouble(text.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
