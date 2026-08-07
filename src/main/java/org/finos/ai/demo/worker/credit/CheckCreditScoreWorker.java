package org.finos.ai.demo.worker.credit;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.finos.fluxnova.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.finos.fluxnova.bpm.client.task.ExternalTask;
import org.finos.fluxnova.bpm.client.task.ExternalTaskHandler;
import org.finos.fluxnova.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ExternalTaskSubscription(topicName = CheckCreditScoreWorker.TOPIC)
public class CheckCreditScoreWorker implements ExternalTaskHandler {

  public static final String TOPIC = "check-credit-score";

  public static final String VAR_CREDIT_SCORE = "creditScore";
  public static final String VAR_CREDIT_RISK_BAND = "creditRiskBand";
  public static final String VAR_AI_RISK_SCORE = "aiRiskScore";

  private static final int MIN_CREDIT_SCORE = 620;
  private static final int MAX_CREDIT_SCORE = 800;
  private static final double MAX_AI_RISK_SCORE = 0.4;

  private static final Logger LOG = LoggerFactory.getLogger(CheckCreditScoreWorker.class);

  @Override
  public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
    int creditScore = ThreadLocalRandom.current().nextInt(MIN_CREDIT_SCORE, MAX_CREDIT_SCORE + 1);
    String creditRiskBand = toCreditRiskBand(creditScore);
    double aiRiskScore = ThreadLocalRandom.current().nextDouble(0, MAX_AI_RISK_SCORE);

    LOG.info(
        "Checked credit score for external task {} in process instance {}: creditScore={}, creditRiskBand={}, aiRiskScore={}",
        externalTask.getId(), externalTask.getProcessInstanceId(), creditScore, creditRiskBand, aiRiskScore);

    externalTaskService.complete(externalTask, Map.of(
        VAR_CREDIT_SCORE, creditScore,
        VAR_CREDIT_RISK_BAND, creditRiskBand,
        VAR_AI_RISK_SCORE, aiRiskScore));
  }

  public static String toCreditRiskBand(int creditScore) {
    if (creditScore >= 740) {
      return "LOW";
    }
    if (creditScore >= 670) {
      return "MEDIUM";
    }
    return "HIGH";
  }

}
