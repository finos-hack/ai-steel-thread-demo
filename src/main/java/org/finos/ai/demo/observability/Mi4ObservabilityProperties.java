package org.finos.ai.demo.observability;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime settings for FINOS AIGF mi-4 (AI System Observability), aligned with CALM controls.
 */
@ConfigurationProperties(prefix = "fluxnova.observability.mi-4")
public class Mi4ObservabilityProperties {

  private boolean enabled = true;

  /** Emit sanitized structured LLM audit log lines (no raw PII / prompts). */
  private boolean sanitizedAuditLog = true;

  /** Persist last LLM call metadata on the process instance for the status API. */
  private boolean persistCallMetadata = true;

  /** Configured model version / pin label exposed on metrics and audit (e.g. gpt-5.4). */
  private String modelVersionLabel = "pinned-config";

  /** Grafana base URL for deep links from the Launch UI. */
  private String grafanaBaseUrl = "http://localhost:3000";

  private List<String> dashboardPaths =
      List.of(
          "/d/fluxnova-task-execution/fluxnova-task-execution",
          "/d/fluxnova-ai-observability/fluxnova-ai-observability");

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isSanitizedAuditLog() {
    return sanitizedAuditLog;
  }

  public void setSanitizedAuditLog(boolean sanitizedAuditLog) {
    this.sanitizedAuditLog = sanitizedAuditLog;
  }

  public boolean isPersistCallMetadata() {
    return persistCallMetadata;
  }

  public void setPersistCallMetadata(boolean persistCallMetadata) {
    this.persistCallMetadata = persistCallMetadata;
  }

  public String getModelVersionLabel() {
    return modelVersionLabel;
  }

  public void setModelVersionLabel(String modelVersionLabel) {
    this.modelVersionLabel = modelVersionLabel;
  }

  public String getGrafanaBaseUrl() {
    return grafanaBaseUrl;
  }

  public void setGrafanaBaseUrl(String grafanaBaseUrl) {
    this.grafanaBaseUrl = grafanaBaseUrl;
  }

  public List<String> getDashboardPaths() {
    return dashboardPaths;
  }

  public void setDashboardPaths(List<String> dashboardPaths) {
    this.dashboardPaths = dashboardPaths;
  }
}
