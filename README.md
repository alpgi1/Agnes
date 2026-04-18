# Agnes

AI Supply Chain decision-support backend.

## Quick start

Prerequisite: `db.sqlite` at the repo root (place it there before starting).

```bash
export ANTHROPIC_API_KEY=sk-ant-...
mvn spring-boot:run
```

Verify:

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/health/config
```

The app starts even without `ANTHROPIC_API_KEY` — `/api/health/config` will
just report `"apiKeyPresent": false`.
