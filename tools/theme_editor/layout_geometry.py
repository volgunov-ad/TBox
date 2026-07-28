"""Geometry helpers for HU dual-coordinate layout (physical + app VD)."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping

from hu_profiles import DisplayProfile


@dataclass(frozen=True)
class Rect:
    x: float
    y: float
    w: float
    h: float

    @property
    def right(self) -> float:
        return self.x + self.w

    @property
    def bottom(self) -> float:
        return self.y + self.h

    def clamped(self, max_w: float, max_h: float, *, min_size: float = 1.0) -> "Rect":
        w = max(min_size, min(self.w, max_w))
        h = max(min_size, min(self.h, max_h))
        x = max(0.0, min(self.x, max_w - w))
        y = max(0.0, min(self.y, max_h - h))
        return Rect(x, y, w, h)

    def contains(self, px: float, py: float) -> bool:
        return self.x <= px <= self.right and self.y <= py <= self.bottom


def _f(mapping: Mapping[str, Any] | None, key: str, default: float) -> float:
    if not isinstance(mapping, Mapping):
        return default
    try:
        return float(mapping.get(key, default))
    except (TypeError, ValueError):
        return default


def main_panel_physical_rect(panel: Mapping[str, Any], profile: DisplayProfile) -> Rect:
    """Map mainScreen panel relative position/size into physical panel pixels."""
    pos = panel.get("position") if isinstance(panel.get("position"), Mapping) else {}
    size = panel.get("size") if isinstance(panel.get("size"), Mapping) else {}
    rel_x = _f(pos, "x", 0.0)
    rel_y = _f(pos, "y", 0.0)
    rel_w = _f(size, "width", 0.4)
    rel_h = _f(size, "height", 0.4)
    return Rect(
        profile.app_vd_x + rel_x * profile.app_vd_width,
        profile.app_vd_y + rel_y * profile.app_vd_height,
        max(1.0, rel_w * profile.app_vd_width),
        max(1.0, rel_h * profile.app_vd_height),
    )


def floating_panel_physical_rect(panel: Mapping[str, Any]) -> Rect:
    """Floating panels are already in physical overlay pixels."""
    try:
        x = float(panel.get("startX", 50))
    except (TypeError, ValueError):
        x = 50.0
    try:
        y = float(panel.get("startY", 50))
    except (TypeError, ValueError):
        y = 50.0
    try:
        w = float(panel.get("width", 100))
    except (TypeError, ValueError):
        w = 100.0
    try:
        h = float(panel.get("height", 100))
    except (TypeError, ValueError):
        h = 100.0
    return Rect(x, y, max(1.0, w), max(1.0, h))


def physical_to_main_rel(
    rect: Rect,
    profile: DisplayProfile,
) -> tuple[float, float, float, float]:
    """Convert a physical rect into mainScreen relative x/y/width/height (clamped)."""
    vd_w = max(1, profile.app_vd_width)
    vd_h = max(1, profile.app_vd_height)
    # Clamp rect into VD first so relative coords stay valid on device.
    local = Rect(
        rect.x - profile.app_vd_x,
        rect.y - profile.app_vd_y,
        rect.w,
        rect.h,
    ).clamped(vd_w, vd_h, min_size=8.0)
    rel_x = local.x / vd_w
    rel_y = local.y / vd_h
    rel_w = local.w / vd_w
    rel_h = local.h / vd_h
    return (
        _clamp01(rel_x),
        _clamp01(rel_y),
        _clamp01(max(rel_w, 8.0 / vd_w)),
        _clamp01(max(rel_h, 8.0 / vd_h)),
    )


def apply_main_rel_to_panel(
    panel: dict[str, Any],
    rel_x: float,
    rel_y: float,
    rel_w: float,
    rel_h: float,
) -> None:
    pos = panel.get("position")
    if not isinstance(pos, dict):
        pos = {}
        panel["position"] = pos
    size = panel.get("size")
    if not isinstance(size, dict):
        size = {}
        panel["size"] = size
    pos["x"] = round(rel_x, 6)
    pos["y"] = round(rel_y, 6)
    size["width"] = round(rel_w, 6)
    size["height"] = round(rel_h, 6)
    panel["positionMode"] = "absolute"


def apply_floating_physical_to_panel(panel: dict[str, Any], rect: Rect, profile: DisplayProfile) -> None:
    clamped = rect.clamped(profile.physical_width, profile.physical_height, min_size=8.0)
    panel["startX"] = int(round(clamped.x))
    panel["startY"] = int(round(clamped.y))
    panel["width"] = int(round(clamped.w))
    panel["height"] = int(round(clamped.h))


def _clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))


def fit_scale(
    src_w: int,
    src_h: int,
    view_w: int,
    view_h: int,
    *,
    padding: int = 8,
) -> float:
    """Uniform scale so src fits in view with padding."""
    avail_w = max(1, view_w - 2 * padding)
    avail_h = max(1, view_h - 2 * padding)
    return min(avail_w / max(1, src_w), avail_h / max(1, src_h))
