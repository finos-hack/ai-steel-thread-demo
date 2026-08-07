package org.finos.ai.demo;

public record StartLoanApprovalResponse(
    String processInstanceId,
    String businessKey,
    String processDefinitionKey,
    String attachedFileVariable,
    String sampleDocument,
    int requestedAmount,
    String loanPurpose,
    String creditRiskAgentProvider,
    String creditRiskAgentModel,
    String creditRiskAgentCacheStrategy,
    String creditRiskAgentPromptCacheKey,
    String documentProcessingAgentProvider,
    String documentProcessingAgentModel,
    String documentProcessingAgentCacheStrategy,
    String documentProcessingAgentPromptCacheKey) {}
