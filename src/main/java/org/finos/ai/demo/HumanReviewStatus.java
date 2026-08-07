package org.finos.ai.demo;

import java.util.Date;
import java.util.List;

/** Open loan-officer review task for mi-11 human feedback. */
public record HumanReviewStatus(
    String processInstanceId,
    boolean taskOpen,
    String taskId,
    String taskName,
    String taskDefinitionKey,
    Date created,
    String approvalDecision,
    Integer creditScore,
    String creditRiskBand,
    Double aiRiskScore,
    String loanOfficerDecision,
    Boolean agreesWithAi,
    String feedbackComment,
    boolean mi11Enabled) {}
