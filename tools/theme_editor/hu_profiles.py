"""Head-unit display profiles for the PC theme layout canvas.

Coordinates mirror the Jetour multi-display layout seen via ADB:
physical panel hosts floating overlays; apps (MainActivity) run on an inset
virtual display whose origin is offset on the physical panel.
"""

from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path


def package_root() -> Path:
    """Directory that contains ``assets/`` (source tree or PyInstaller bundle)."""
    if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
        return Path(sys._MEIPASS)  # type: ignore[attr-defined]
    return Path(__file__).resolve().parent


@dataclass(frozen=True)
class DisplayProfile:
    """Physical panel + inset app virtual display (VD)."""

    id: str
    name: str
    physical_width: int
    physical_height: int
    app_vd_x: int
    app_vd_y: int
    app_vd_width: int
    app_vd_height: int
    underlay_filename: str

    @property
    def underlay_path(self) -> Path:
        return package_root() / "assets" / self.underlay_filename


# Measured from ADB screencap of Jetour Dashing (display 0 = 1920×1080,
# app VD display 5 = 1320×856 nested at ~570,100).
JETOUR_DASHING = DisplayProfile(
    id="jetour_dashing",
    name="Jetour Dashing (1920×1080)",
    physical_width=1920,
    physical_height=1080,
    app_vd_x=570,
    app_vd_y=100,
    app_vd_width=1320,
    app_vd_height=856,
    underlay_filename="jetour_dashing_underlay.png",
)

PROFILES: dict[str, DisplayProfile] = {
    JETOUR_DASHING.id: JETOUR_DASHING,
}

DEFAULT_PROFILE_ID = JETOUR_DASHING.id


def get_profile(profile_id: str | None) -> DisplayProfile:
    if profile_id and profile_id in PROFILES:
        return PROFILES[profile_id]
    return JETOUR_DASHING
