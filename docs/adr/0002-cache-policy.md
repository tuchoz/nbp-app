# ADR-0002: Adaptive local cache, stale-if-error, single-flight

**Status:** Accepted

## Context

Table A is normally updated once on working days. Re-querying on every HTTP request wastes upstream traffic, yet assuming today's rate is immutable could miss corrections.

## Decision

The application uses an in-memory adaptive TTL cache.

The caching strategy follows the NBP Table A publication schedule.

Cache TTL policy:

| Condition | TTL |
|---|---|
| Before 11:45 | Up to 60 minutes |
| 11:45–12:30 | 5 minutes |
| After 12:30, today's rate available | 60 minutes |
| After 12:30, today's rate unavailable | 10 minutes |
| Weekends | 6 hours |

All publication times are interpreted in the Europe/Warsaw timezone.

### Publication window boundary

Before 11:45, cache expiration is capped at the beginning
of the NBP publication window.

For example:

- Rate fetched at 11:20 expires at 11:45.
- Rate fetched at 11:40 expires at 11:45.
- Rate fetched at 11:44 expires at 11:45.

This prevents a cache entry created before publication
from remaining valid throughout the publication window.

### Cache refresh

The application uses lazy cache refresh.

Expired entries are refreshed when the next HTTP request
arrives. No background polling is performed.

### Resilience

The cache also implements:

- Single-flight refresh using asyncio.Lock.
- Stale-if-error for temporary NBP API failures.
- Bounded retry with exponential backoff.
- Maximum stale age of 14 days.
- Periodic revalidation to detect possible corrections.

### Limitations

The cache is stored in process memory.

Each Kubernetes Pod maintains an independent cache.

Temporary differences between replicas are accepted
as an architectural trade-off.

No shared Redis cache is used.

No cache-specific metrics are collected.

## Consequences

There is no background polling. Subsequent user requests trigger refresh after TTL. A replica restart loses cache. Kubernetes replicas can temporarily diverge. This intentionally does not include cache metrics, per agreement. Historical holidays are not modeled.
