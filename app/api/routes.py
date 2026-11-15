"""Page and health routes."""

from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.templating import Jinja2Templates

from app.config import APP_DIR
from app.services.nbp_client import NBPUnavailableError

router = APIRouter()
templates = Jinja2Templates(directory=str(APP_DIR / "templates"))


@router.get("/", response_class=HTMLResponse)
async def index(request: Request):
    try:
        result = await request.app.state.rate_service.get_usd()
    except NBPUnavailableError:
        return templates.TemplateResponse(
            request=request,
            name="index.html",
            context={"result": None, "error": True},
            status_code=503,
        )
    return templates.TemplateResponse(
        request=request,
        name="index.html",
        context={"result": result, "error": False},
    )


@router.get("/health/live", include_in_schema=False)
async def live():
    return {"status": "alive"}


@router.get("/health/ready", include_in_schema=False)
async def ready(request: Request):
    # NBP availability is NOT a prerequisite for application readiness.
    if not hasattr(request.app.state, "rate_service"):
        return JSONResponse(status_code=503, content={"status": "not_ready"})
    return {"status": "ready"}
