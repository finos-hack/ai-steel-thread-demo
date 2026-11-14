package org.finos.ai.demo.traderx;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/demo/traderx/rebalance")
public class RebalanceController {
  private final RebalancePlanning planning;
  private final TraderXWorkflowService service;
  public RebalanceController(RebalancePlanning planning,TraderXWorkflowService service) { this.planning=planning; this.service=service; }
  @PostMapping("/preview") public Map<String,Object> preview(@RequestBody RebalancePlanning.Rebalance r) { return planning.rebalance(r,false); }
  @PostMapping("/start") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Map<String,Object> start(@RequestBody RebalancePlanning.Rebalance r) { return service.start("rebalance",r); }
}
