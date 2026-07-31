# Agentic Subprocess Example

Standalone Fluxnova Spring Boot demo for AI agentic ad-hoc subprocesses (loan approval).

## Requirements

- Java 21+
- Maven 3.9+
- Locally installed agentic plugins from [finos/fluxnova-plugins](https://github.com/finos/fluxnova-plugins) (`1.0.0-SNAPSHOT`)
- `OPENAI_API_KEY` for document intelligence / agent LLM calls

## Build

```bash
mvn clean package
```

## Run

```bash
export OPENAI_API_KEY=...
mvn spring-boot:run
```

Then deploy `LoanApprovalProcess_AI` from Fluxnova Modeler (auto-deployment expects the BPMN on the engine), and start a demo instance:

```bash
curl -X POST 'http://localhost:8080/api/demo/loan-approval/start-with-sample-id'
```

## Observability

See `observability/README.md` for Prometheus / Grafana / Tempo via Docker Compose.
