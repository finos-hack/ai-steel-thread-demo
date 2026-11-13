package org.finos.ai.demo.traderx;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/demo/traderx/stale")
public class StaleController {
  private final StalePlanning planning;
  private final TraderXWorkflowService service;
  public StaleController(StalePlanning planning,TraderXWorkflowService service) { this.planning=planning; this.service=service; }
  @PostMapping("/preview") public Map<String,Object> preview(@RequestBody StalePlanning.StaleReview r) { return planning.stale(r,false); }
  @PostMapping("/start") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Map<String,Object> start(@RequestBody StalePlanning.StaleReview r) { return service.start("stale",r); }
}
