.PHONY: install lock run lint test coverage docker-build docker-run

install:
	uv sync --locked

lock:
	uv lock

run:
	uv run --locked uvicorn app.main:app --reload --host 127.0.0.1 --port 8080

lint:
	uv run --locked ruff check .
	uv run --locked ruff format --check .

test:
	uv run --locked pytest -q

coverage:
	uv run --locked pytest --cov=app --cov-branch --cov-report=term-missing --cov-report=xml:coverage.xml

docker-build:
	docker build -f docker/Dockerfile -t nbp-usd:local .

docker-run:
	docker run --rm -p 8080:8080 --read-only --tmpfs /tmp:rw,noexec,nosuid,size=16m --cap-drop=ALL --security-opt=no-new-privileges nbp-usd:local
