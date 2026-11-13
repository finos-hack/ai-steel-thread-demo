package org.finos.ai.demo.traderx;
import java.util.*;
/** Shared serialization for a reviewed batch; the concrete workflow chooses its item collection. */
public final class TraderXPlanVariables {
  private TraderXPlanVariables() {}
  public static Map<String,Object> of(Map<String,Object> plan,String itemsKey) {
    var json=new tools.jackson.databind.ObjectMapper();
    Map<String,Object> v=new LinkedHashMap<>();
    v.put("accountId",plan.get("accountId")); v.put("accountName",plan.get("accountName"));
    v.put("planJson",TraderXWorkflowService.storeJson(json.writeValueAsString(plan)));
    v.put("resultsJson","[]"); v.put("batchIndex",0); v.put("batchSize",((List<?>)plan.get(itemsKey)).size());
    return v;
  }
}
