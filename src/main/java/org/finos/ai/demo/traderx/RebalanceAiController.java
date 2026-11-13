package org.finos.ai.demo.traderx;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class RebalanceAiController {
  private final TraderXRebalanceAi ai;
  public RebalanceAiController(TraderXRebalanceAi ai) { this.ai=ai; }
  @PostMapping("/api/demo/traderx/rebalance/suggestions")
  public Map<String,Object> suggest(@RequestBody TraderXRebalanceAi.Request r) { return ai.suggest(r); }
}
