package org.finos.ai.demo;

import java.util.List;

import org.finos.ai.demo.feedback.Mi11FeedbackVariables;

/** Static form schemas keyed by BPMN task definition id. */
final class HumanTaskSchemas {

  static final String LOAN_OFFICER = "UserTask_LoanOfficerReview";
  static final String COMPLIANCE_OFFICER = "UserTask_ComplianceReview";

  private HumanTaskSchemas() {}

  static HumanTaskFormSchema forTaskDefinitionKey(String taskDefinitionKey) {
    if (LOAN_OFFICER.equals(taskDefinitionKey)) {
      return loanOfficer();
    }
    if (COMPLIANCE_OFFICER.equals(taskDefinitionKey)) {
      return complianceOfficer();
    }
    return unknown(taskDefinitionKey);
  }

  private static HumanTaskFormSchema loanOfficer() {
    return new HumanTaskFormSchema(
        LOAN_OFFICER,
        "Loan officer review",
        "Record the underwriting decision for MANUAL_REVIEW. The credit-risk agent conversation "
            + "is shown unredacted here so you can judge the AI reasoning (mi-1 still redacts it in "
            + "the public execution log).",
        List.of(
            new HumanTaskFormField(
                Mi11FeedbackVariables.LOAN_OFFICER_DECISION,
                "Decision",
                "enum",
                true,
                List.of("APPROVE", "REJECT"),
                "Required by Gateway_6 (APPROVE continues; otherwise reject)."),
            new HumanTaskFormField(
                Mi11FeedbackVariables.AGREES_WITH_AI,
                "Agree with AI recommendation?",
                "boolean",
                false,
                null,
                "mi-11 structured feedback (optional)."),
            new HumanTaskFormField(
                Mi11FeedbackVariables.COMMENT,
                "Comment",
                "textarea",
                false,
                null,
                "Optional rationale (max " + Mi11FeedbackVariables.MAX_COMMENT_LENGTH + " chars).")));
  }

  private static HumanTaskFormSchema complianceOfficer() {
    return new HumanTaskFormSchema(
        COMPLIANCE_OFFICER,
        "Compliance officer review",
        "Clear or reject a fraud-flagged application. Completes UserTask_ComplianceReview.",
        List.of(
            new HumanTaskFormField(
                "complianceStatus",
                "Compliance status",
                "enum",
                true,
                List.of("CLEARED", "REJECTED"),
                "CLEARED matches Gateway_3; otherwise the default reject path is taken."),
            new HumanTaskFormField(
                "complianceComment",
                "Comment",
                "textarea",
                false,
                null,
                "Optional compliance notes.")));
  }

  private static HumanTaskFormSchema unknown(String taskDefinitionKey) {
    return new HumanTaskFormSchema(
        taskDefinitionKey == null ? "unknown" : taskDefinitionKey,
        "Human task",
        "No app form schema is registered for this task definition. Complete with free-form variables.",
        List.of());
  }
}
