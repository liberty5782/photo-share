import hashlib
import os

from fastapi import Depends, FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from sqlalchemy.orm import Session

import models
from database import engine, get_db

models.Base.metadata.create_all(bind=engine)

app = FastAPI(title="PhotoShare")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

UPLOAD_DIR = "uploads"
os.makedirs(UPLOAD_DIR, exist_ok=True)


# ── helpers ──────────────────────────────────────────────────────────────────

def require_device_id(request: Request) -> str:
    device_id = request.headers.get("X-Device-Id")
    if not device_id:
        raise HTTPException(status_code=400, detail="X-Device-Id header required")
    return device_id


def photo_dict(p: models.Photo) -> dict:
    return {
        "id": p.id,
        "caption": p.caption,
        "uploader_id": p.uploader_id,
        "uploader_name": p.uploader.name if p.uploader else None,
        "is_ephemeral": p.is_ephemeral,
        "file_hash": p.file_hash,
        "mime_type": p.mime_type,
        "created_at": p.created_at.isoformat() if p.created_at else None,
    }


# ── middleware: auto-register device ─────────────────────────────────────────

@app.middleware("http")
async def register_device_middleware(request: Request, call_next):
    device_id = request.headers.get("X-Device-Id")
    device_name = request.headers.get("X-Device-Name")
    if device_id and device_name:
        db = next(get_db())
        try:
            existing = db.get(models.Device, device_id)
            if existing:
                existing.name = device_name
            else:
                db.add(models.Device(id=device_id, name=device_name))
            db.commit()
        finally:
            db.close()
    return await call_next(request)


# ── routes ───────────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"ok": True}


@app.get("/photos")
def list_photos(request: Request, db: Session = Depends(get_db)):
    device_id = require_device_id(request)

    viewed_subq = (
        db.query(models.PhotoView.photo_id)
        .filter(models.PhotoView.device_id == device_id)
        .subquery()
    )

    photos = (
        db.query(models.Photo)
        .filter(
            ~(
                (models.Photo.is_ephemeral == True)
                & models.Photo.id.in_(viewed_subq)
            )
        )
        .order_by(models.Photo.created_at.desc())
        .all()
    )

    return [photo_dict(p) for p in photos]


@app.post("/photos", status_code=201)
async def upload_photo(
    request: Request,
    file: UploadFile = File(...),
    caption: str = Form(None),
    is_ephemeral: bool = Form(False),
    file_hash: str = Form(...),
    db: Session = Depends(get_db),
):
    device_id = require_device_id(request)

    # dedup: return existing photo if hash already known
    existing = db.query(models.Photo).filter(models.Photo.file_hash == file_hash).first()
    if existing:
        return JSONResponse(status_code=200, content=photo_dict(existing))

    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="File must be an image")

    content = await file.read()

    # verify the hash the client sent matches the actual file
    computed = hashlib.sha256(content).hexdigest()
    if computed != file_hash:
        raise HTTPException(status_code=400, detail="File hash mismatch")

    ext = file.content_type.split("/")[-1]
    if ext == "jpeg":
        ext = "jpg"

    file_path = os.path.join(UPLOAD_DIR, f"{file_hash}.{ext}")
    with open(file_path, "wb") as f:
        f.write(content)

    photo = models.Photo(
        file_hash=file_hash,
        file_path=file_path,
        mime_type=file.content_type,
        caption=caption,
        uploader_id=device_id,
        is_ephemeral=is_ephemeral,
    )
    db.add(photo)
    db.commit()
    db.refresh(photo)

    return JSONResponse(status_code=201, content=photo_dict(photo))


@app.get("/photos/{photo_id}/file")
def get_photo_file(photo_id: str, request: Request, db: Session = Depends(get_db)):
    device_id = require_device_id(request)

    photo = db.get(models.Photo, photo_id)
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")
    if not os.path.exists(photo.file_path):
        raise HTTPException(status_code=404, detail="File not found on disk")

    # record view (upsert)
    view = db.get(models.PhotoView, (photo_id, device_id))
    if not view:
        db.add(models.PhotoView(photo_id=photo_id, device_id=device_id))
        db.commit()

    return FileResponse(photo.file_path, media_type=photo.mime_type)


@app.post("/photos/{photo_id}/view")
def mark_viewed(photo_id: str, request: Request, db: Session = Depends(get_db)):
    device_id = require_device_id(request)

    photo = db.get(models.Photo, photo_id)
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")

    view = db.get(models.PhotoView, (photo_id, device_id))
    if not view:
        db.add(models.PhotoView(photo_id=photo_id, device_id=device_id))
        db.commit()

    return {"ok": True}


@app.delete("/photos/{photo_id}")
def delete_photo(photo_id: str, request: Request, db: Session = Depends(get_db)):
    device_id = require_device_id(request)

    photo = db.get(models.Photo, photo_id)
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")
    if photo.uploader_id != device_id:
        raise HTTPException(status_code=403, detail="Only the uploader can delete this photo")

    if os.path.exists(photo.file_path):
        os.remove(photo.file_path)

    db.delete(photo)
    db.commit()

    return {"ok": True}


@app.get("/devices")
def list_devices(db: Session = Depends(get_db)):
    devices = db.query(models.Device).all()
    return [
        {"id": d.id, "name": d.name, "last_seen": d.last_seen.isoformat() if d.last_seen else None}
        for d in devices
    ]
