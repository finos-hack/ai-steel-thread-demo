package org.finos.ai.demo.worker.rate;

import org.finos.fluxnova.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.finos.fluxnova.bpm.client.task.ExternalTask;
import org.finos.fluxnova.bpm.client.task.ExternalTaskHandler;
import org.finos.fluxnova.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ExternalTaskSubscription(topicName = AdjustInterestRateWorker.TOPIC)
public class AdjustInterestRateWorker implements ExternalTaskHandler {

  public static final String TOPIC = "adjust-interest-rate";

  private static final Logger LOG = LoggerFactory.getLogger(AdjustInterestRateWorker.class);

  @Override
  public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
    LOG.debug("Received external task {} for topic {}", externalTask.getId(), TOPIC);

    externalTaskService.complete(externalTask, null);
  }

}
