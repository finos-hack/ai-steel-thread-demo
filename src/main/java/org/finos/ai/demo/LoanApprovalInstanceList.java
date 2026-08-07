package org.finos.ai.demo;

import java.util.List;

public record LoanApprovalInstanceList(
    String processDefinitionKey,
    int activeCount,
    int totalCount,
    List<LoanApprovalInstanceSummary> instances) {}
