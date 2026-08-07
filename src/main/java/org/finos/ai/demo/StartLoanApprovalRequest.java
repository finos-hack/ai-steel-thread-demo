package org.finos.ai.demo;

public record StartLoanApprovalRequest(
    String businessKey,
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
    String documentProcessingAgentPromptCacheKey) {}
