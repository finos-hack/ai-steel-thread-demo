package org.finos.ai.demo.worker.credit;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-process alternative to {@link CheckCreditScoreWorker} for tools inside an agent ad-hoc
 * subprocess. Java delegates complete synchronously during tool dispatch, avoiding parallel
 * external-task completion races.
 */
@Component("checkCreditScoreDelegate")
public class CheckCreditScoreDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(CheckCreditScoreDelegate.class);

  private static final int MIN_CREDIT_SCORE = 620;
  private static final int MAX_CREDIT_SCORE = 800;
  private static final double MAX_AI_RISK_SCORE = 0.4;

  @Override
  public void execute(DelegateExecution execution) {
    int creditScore = ThreadLocalRandom.current().nextInt(MIN_CREDIT_SCORE, MAX_CREDIT_SCORE + 1);
    String creditRiskBand = CheckCreditScoreWorker.toCreditRiskBand(creditScore);
    double aiRiskScore = ThreadLocalRandom.current().nextDouble(0, MAX_AI_RISK_SCORE);

    LOG.info(
        "Checked credit score for execution {} in process instance {}: creditScore={}, creditRiskBand={}, aiRiskScore={}",
        execution.getId(), execution.getProcessInstanceId(), creditScore, creditRiskBand, aiRiskScore);

    execution.setVariables(Map.of(
        CheckCreditScoreWorker.VAR_CREDIT_SCORE, creditScore,
        CheckCreditScoreWorker.VAR_CREDIT_RISK_BAND, creditRiskBand,
        CheckCreditScoreWorker.VAR_AI_RISK_SCORE, aiRiskScore));
  }

}
