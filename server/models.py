from sqlalchemy import Boolean, Column, DateTime, ForeignKey, String
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from uuid import uuid4

from database import Base


class Device(Base):
    __tablename__ = "devices"

    id = Column(String, primary_key=True)
    name = Column(String, nullable=False)
    last_seen = Column(DateTime, default=func.now(), onupdate=func.now())

    photos = relationship("Photo", back_populates="uploader")
    views = relationship("PhotoView", back_populates="device")


class Photo(Base):
    __tablename__ = "photos"

    id = Column(String, primary_key=True, default=lambda: str(uuid4()))
    file_hash = Column(String, unique=True, nullable=False)
    file_path = Column(String, nullable=False)
    mime_type = Column(String, nullable=False)
    caption = Column(String, nullable=True)
    uploader_id = Column(String, ForeignKey("devices.id"))
    is_ephemeral = Column(Boolean, default=False)
    created_at = Column(DateTime, default=func.now())

    uploader = relationship("Device", back_populates="photos")
    views = relationship("PhotoView", back_populates="photo", cascade="all, delete-orphan")


class PhotoView(Base):
    __tablename__ = "photo_views"

    photo_id = Column(String, ForeignKey("photos.id", ondelete="CASCADE"), primary_key=True)
    device_id = Column(String, ForeignKey("devices.id"), primary_key=True)
    viewed_at = Column(DateTime, default=func.now())

    photo = relationship("Photo", back_populates="views")
    device = relationship("Device", back_populates="views")
