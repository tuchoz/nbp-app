# ADR-0001: Modular monolith with server-rendered HTML

**Status:** Accepted

## Context

One simple read-only page presents the USD/PLN average rate published in NBP Table A.

## Decision

Use Python 3.13, FastAPI, Jinja2, Pydantic and httpx in one ASGI service. No separate TypeScript frontend, Redis, database or Kubernetes client is required.

## Consequences

A single Docker image and Deployment minimizes operational overhead. The in-memory cache is per-process/per-pod and will temporarily differ between replicas. Treat this as an intentional consistency tradeoff and document it.
