package org.finos.ai.demo.traderx;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice(basePackages="org.finos.ai.demo.traderx")
public class TraderXErrors {
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
