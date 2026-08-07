package org.finos.ai.demo.observability.business;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fluxnova.observability.business")
public class LoanApprovalBusinessMetricsProperties {

  private boolean enabled = true;
  private String workflowNamePrefix = "LoanApproval";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getWorkflowNamePrefix() {
    return workflowNamePrefix;
  }

  public void setWorkflowNamePrefix(String workflowNamePrefix) {
    this.workflowNamePrefix = workflowNamePrefix;
  }

}
