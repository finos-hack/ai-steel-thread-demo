package org.finos.ai.demo.worker.credit;

import java.util.Map;

import org.finos.fluxnova.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.finos.fluxnova.bpm.client.task.ExternalTask;
import org.finos.fluxnova.bpm.client.task.ExternalTaskHandler;
import org.finos.fluxnova.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ExternalTaskSubscription(topicName = EvaluateCreditRiskWorker.TOPIC)
public class EvaluateCreditRiskWorker implements ExternalTaskHandler {

  public static final String TOPIC = "evaluate-credit-risk";

  public static final String VAR_CREDIT_SCORE = "creditScore";
  public static final String VAR_AI_RISK_SCORE = "aiRiskScore";
  public static final String VAR_APPROVAL_DECISION = "approvalDecision";

  private static final Logger LOG = LoggerFactory.getLogger(EvaluateCreditRiskWorker.class);

  @Override
  public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
    int creditScore = toInt(externalTask.getVariable(VAR_CREDIT_SCORE));
    double aiRiskScore = toDouble(externalTask.getVariable(VAR_AI_RISK_SCORE));
    String approvalDecision = evaluateApprovalDecision(creditScore, aiRiskScore);

    LOG.info(
        "Evaluated credit risk for external task {} in process instance {}: creditScore={}, aiRiskScore={}, approvalDecision={}",
        externalTask.getId(), externalTask.getProcessInstanceId(), creditScore, aiRiskScore, approvalDecision);

    externalTaskService.complete(externalTask, Map.of(VAR_APPROVAL_DECISION, approvalDecision));
  }

  public static String evaluateApprovalDecision(int creditScore, double aiRiskScore) {
    if (creditScore >= 750 && aiRiskScore < 0.2) {
      return "MANUAL_REVIEW";
    }
    if (creditScore >= 750) {
      return "MANUAL_REVIEW";
    }
    if (creditScore >= 650 && aiRiskScore < 0.3) {
      return "MANUAL_REVIEW";
    }
    return "REJECT";
  }

  private static int toInt(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    throw new IllegalArgumentException("Missing or invalid " + VAR_CREDIT_SCORE + ": " + value);
  }

  private static double toDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    throw new IllegalArgumentException("Missing or invalid " + VAR_AI_RISK_SCORE + ": " + value);
  }

}
