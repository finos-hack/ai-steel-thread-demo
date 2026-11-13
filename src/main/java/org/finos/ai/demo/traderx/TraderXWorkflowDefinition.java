package org.finos.ai.demo.traderx;
import java.util.Map;
import tools.jackson.databind.JsonNode;
/** A workflow registers independently; the foundation deploys no business process. */
public interface TraderXWorkflowDefinition {
  String kind();
  String processKey();
  Map<String,Object> prepare(JsonNode request);
  String title(Map<String,Object> variables);
  default String batchItemsKey() { return null; }
}
