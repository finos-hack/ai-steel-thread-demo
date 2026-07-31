package org.finos.fluxnova.demo;

public record StartLoanApprovalResponse(
    String processInstanceId,
    String businessKey,
    String processDefinitionKey,
    String attachedFileVariable,
    String sampleDocument,
    int requestedAmount,
    String loanPurpose) {}
