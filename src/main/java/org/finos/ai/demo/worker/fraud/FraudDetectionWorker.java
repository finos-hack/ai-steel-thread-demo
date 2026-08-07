package org.finos.ai.demo.worker.fraud;

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
@ExternalTaskSubscription(topicName = FraudDetectionWorker.TOPIC)
public class FraudDetectionWorker implements ExternalTaskHandler {

  public static final String TOPIC = "fraud-detection";

  public static final String VAR_FRAUD_RISK_SCORE = "fraudRiskScore";
  public static final String VAR_IS_FRAUDULENT = "isFraudulent";

  private static final Logger LOG = LoggerFactory.getLogger(FraudDetectionWorker.class);

  @Override
  public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
    int fraudRiskScore = ThreadLocalRandom.current().nextInt(0, 31);
    boolean isFraudulent = false;

    LOG.info(
        "Ran fraud detection for external task {} in process instance {}: fraudRiskScore={}",
        externalTask.getId(), externalTask.getProcessInstanceId(), fraudRiskScore);

    externalTaskService.complete(externalTask, Map.of(
            VAR_FRAUD_RISK_SCORE, fraudRiskScore,
            VAR_IS_FRAUDULENT, isFraudulent));
  }

}
