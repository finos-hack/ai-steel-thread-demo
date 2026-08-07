package org.finos.ai.demo.dlp;

/** Thrown when mi-1 DLP blocks disallowed LLM egress in strict mode. */
public class Mi1DataLeakageException extends RuntimeException {

  public Mi1DataLeakageException(String message) {
    super(message);
  }
}
