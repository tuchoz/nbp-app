"""Minimal structured JSON logging to stdout for Cloud Logging."""

import json
import logging
import sys
from datetime import UTC, datetime


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        message: dict[str, object] = {
            "timestamp": datetime.fromtimestamp(record.created, tz=UTC).isoformat(),
            "severity": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        for field in (
            "error_type",
            "nbp_status",
            "attempt",
            "method",
            "path",
            "status",
            "duration_ms",
        ):
            if hasattr(record, field):
                message[field] = getattr(record, field)
        return json.dumps(message, ensure_ascii=False)


def configure_logging(level: str) -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    logging.basicConfig(level=level.upper(), handlers=[handler], force=True)
