package org.finos.ai.demo;

import java.util.Date;
import java.util.List;

public record LoanApprovalInstanceSummary(
    String processInstanceId,
    String businessKey,
    String processDefinitionKey,
    String state,
    Date startTime,
    Date endTime,
    Integer requestedAmount,
    String loanPurpose,
    String sampleDocument,
    String creditRiskAgentProvider,
    String creditRiskAgentModel,
    String creditRiskAgentCacheStrategy,
    String creditRiskAgentPromptCacheKey,
    String documentProcessingAgentProvider,
    String documentProcessingAgentModel,
    String documentProcessingAgentCacheStrategy,
    String documentProcessingAgentPromptCacheKey,
    List<String> currentActivityNames,
    /** PASS / WARN / FAIL / IN_PROGRESS from per-instance compliance evidence. */
    String complianceOverallStatus) {}
