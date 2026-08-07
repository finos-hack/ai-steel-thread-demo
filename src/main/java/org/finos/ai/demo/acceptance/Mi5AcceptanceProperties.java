package org.finos.ai.demo.acceptance;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime settings for FINOS AIGF mi-5 (System Acceptance Testing).
 */
@ConfigurationProperties(prefix = "fluxnova.acceptance.mi-5")
public class Mi5AcceptanceProperties {

  private boolean enabled = true;

  /** Minimum fraction of cases that must pass for the suite to be accepted (0–1). */
  private double passRateThreshold = 0.9;

  /** Catalog / AIGF page for the mitigation. */
  private String aigfCatalogUrl =
      "https://air-governance-framework.finos.org/mitigations/mi-5_system-acceptance-testing.html";

  private List<String> criteriaCategories =
      new ArrayList<>(
          List.of(
              "credit-risk-bands",
              "approval-decision",
              "mi-1-dlp",
              "loan-application-bounds",
              "document-validity"));

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public double getPassRateThreshold() {
    return passRateThreshold;
  }

  public void setPassRateThreshold(double passRateThreshold) {
    this.passRateThreshold = passRateThreshold;
  }

  public String getAigfCatalogUrl() {
    return aigfCatalogUrl;
  }

  public void setAigfCatalogUrl(String aigfCatalogUrl) {
    this.aigfCatalogUrl = aigfCatalogUrl;
  }

  public List<String> getCriteriaCategories() {
    return criteriaCategories;
  }

  public void setCriteriaCategories(List<String> criteriaCategories) {
    this.criteriaCategories = criteriaCategories;
  }
}
