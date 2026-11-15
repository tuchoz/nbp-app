# syntax=docker/dockerfile:1

# ==========================================
# Stage 1: Builder
# ==========================================

FROM python:3.13-slim AS builder

COPY --from=ghcr.io/astral-sh/uv:0.9.5 /uv /uvx /bin/

ENV UV_COMPILE_BYTECODE=0 \
    UV_LINK_MODE=copy \
    UV_PROJECT_ENVIRONMENT=/app/.venv

WORKDIR /app

COPY pyproject.toml uv.lock ./

RUN uv sync --locked --no-dev --no-install-project


# ==========================================
# Stage 2: Runtime
# ==========================================

FROM python:3.13-slim AS runtime
RUN apt-get update \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PATH="/app/.venv/bin:$PATH" \
    HOME=/tmp

WORKDIR /app

COPY --from=builder /app/.venv /app/.venv

COPY app/ /app/app/

RUN groupadd --gid 10001 appgroup && \
    useradd \
        --uid 10001 \
        --gid 10001 \
        --no-create-home \
        --shell /usr/sbin/nologin \
        appuser && \
    chown -R 10001:10001 /app

USER 10001:10001

EXPOSE 8080

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080", "--no-access-log"]