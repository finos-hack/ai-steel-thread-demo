package org.finos.ai.demo.feedback;

/** Process variable names written when completing the loan-officer review task. */
public final class Mi11FeedbackVariables {

  public static final String LOAN_OFFICER_DECISION = "loanOfficerDecision";
  public static final String AGREES_WITH_AI = "humanFeedbackAgreesWithAi";
  public static final String COMMENT = "humanFeedbackComment";
  public static final String CATEGORIES = "humanFeedbackCategories";
  public static final String SUBMITTED_AT = "humanFeedbackAt";

  public static final int MAX_COMMENT_LENGTH = 500;

  private Mi11FeedbackVariables() {}
}
