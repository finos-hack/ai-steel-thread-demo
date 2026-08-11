#!/bin/sh
# One-shot job: wait for CALM Hub, ensure namespace + loan-approval architecture exist, then exit 0.
# It is expected to stop after success (Docker status "Exited (0)" is normal, not a failure).
set -eu

HUB_URL="${HUB_URL:-http://calmhub:8080}"
ARCH_FILE="${ARCH_FILE:-/data/loan-approval.calm.json}"

echo "Waiting for CALM Hub at ${HUB_URL}..."
i=0
# /q/health is not exposed in all Hub image variants; OpenAPI is.
until curl -fsS "${HUB_URL}/q/openapi" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 90 ]; then
    echo "CALM Hub did not become ready in time" >&2
    exit 1
  fi
  sleep 2
done
echo "CALM Hub is ready."

# Namespace create requires name + non-blank description. 409 = already exists.
HTTP_CODE=$(curl -sS -o /tmp/calm-ns-out.txt -w '%{http_code}' \
  -X POST "${HUB_URL}/api/calm/namespaces" \
  -H 'Content-Type: application/json' \
  -d '{"name":"finos","description":"AI Steel Thread Demo"}' || true)
case "$HTTP_CODE" in
  200|201) echo "Created namespace finos." ;;
  409) echo "Namespace finos already exists." ;;
  *)
    if curl -fsS "${HUB_URL}/api/calm/namespaces" | grep -q 'finos'; then
      echo "Namespace finos already present (create HTTP ${HTTP_CODE})."
    else
      echo "Failed to create namespace finos (HTTP ${HTTP_CODE}):" >&2
      cat /tmp/calm-ns-out.txt >&2 || true
      exit 1
    fi
    ;;
esac

ARCH_URL="${HUB_URL}/calm/namespaces/finos/architectures/loan-approval/versions/1.0.0"

HTTP_CODE=$(curl -sS -o /tmp/calm-seed-out.txt -w '%{http_code}' \
  -X POST "${ARCH_URL}" \
  -H 'Content-Type: application/json' \
  --data-binary @"${ARCH_FILE}" || true)

case "$HTTP_CODE" in
  200|201)
    echo "Seeded loan-approval architecture (HTTP ${HTTP_CODE})."
    echo "Seed complete — one-shot job exiting successfully."
    exit 0
    ;;
  409)
    echo "loan-approval architecture already present (HTTP 409)."
    echo "Seed complete — one-shot job exiting successfully."
    exit 0
    ;;
esac

# Older Hub builds may not return 409; confirm with GET.
if curl -fsS "${ARCH_URL}" >/dev/null 2>&1; then
  echo "loan-approval architecture already present (create HTTP ${HTTP_CODE})."
  echo "Seed complete — one-shot job exiting successfully."
  exit 0
fi

echo "Failed to seed architecture. Last response (HTTP ${HTTP_CODE}):" >&2
cat /tmp/calm-seed-out.txt >&2 || true
exit 1
