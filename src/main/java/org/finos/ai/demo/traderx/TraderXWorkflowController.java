package org.finos.ai.demo.traderx;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/demo/traderx")
public class TraderXWorkflowController {
  private final TraderXWorkflowService service;
  private final String publicUrl;
  public TraderXWorkflowController(TraderXWorkflowService service,
      @org.springframework.beans.factory.annotation.Value("${traderx.public-url:http://localhost:18080}") String publicUrl) {
    this.service=service; this.publicUrl=publicUrl;
  }
  @GetMapping("/environment") public Map<String,String> environment() { return Map.of("publicUrl",publicUrl); }
  @GetMapping("/options") public Map<String, Object> options() { return service.options(); }
  @GetMapping("/people") public JsonNode people(@RequestParam(defaultValue="user") String search) { return service.people(search); }
  @GetMapping("/quotes/{ticker}") public JsonNode quote(@PathVariable String ticker) { return service.quote(ticker); }
  @PostMapping("/workflows/{kind}") @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> start(@PathVariable String kind, @RequestBody JsonNode r) { return service.start(kind, r); }
  @GetMapping("/workflows/{kind}")
  public List<Map<String, Object>> list(@PathVariable String kind) { return service.list(kind); }
  @GetMapping("/instances/{id}")
  public Map<String, Object> detail(@PathVariable String id) { return service.detail(id); }
  @PostMapping("/instances/{id}/decision")
  public Map<String, Object> decide(@PathVariable String id, @RequestBody TraderXWorkflowService.Decision d) { return service.decide(id, d); }
  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  public Map<String, String> upstreamError(IllegalStateException ex) {
    return Map.of("detail", Objects.toString(ex.getMessage(), "TraderX is unavailable"));
  }
  @ExceptionHandler(ResponseStatusException.class)
  public org.springframework.http.ResponseEntity<Map<String,String>> badRequest(ResponseStatusException ex) {
    return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(Map.of("detail", Objects.toString(ex.getReason(), "Invalid request")));
  }
}
