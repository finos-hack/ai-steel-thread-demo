package org.finos.ai.demo;

import java.util.List;

/** Payload to complete UserTask_LoanOfficerReview (mi-11). */
public record HumanFeedbackRequest(
    String loanOfficerDecision,
    Boolean agreesWithAi,
    String comment,
    List<String> categories) {}
