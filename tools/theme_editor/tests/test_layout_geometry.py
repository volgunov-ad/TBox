"""Unit tests for HU layout geometry (no GUI)."""

from __future__ import annotations

import unittest

from hu_profiles import JETOUR_DASHING
from layout_geometry import (
    Rect,
    apply_floating_physical_to_panel,
    apply_main_rel_to_panel,
    fit_scale,
    floating_panel_physical_rect,
    main_panel_physical_rect,
    physical_to_main_rel,
)


class LayoutGeometryTests(unittest.TestCase):
    def test_jetour_profile_matches_adb(self) -> None:
        p = JETOUR_DASHING
        self.assertEqual(p.physical_width, 1920)
        self.assertEqual(p.physical_height, 1080)
        self.assertEqual(p.app_vd_width, 1320)
        self.assertEqual(p.app_vd_height, 856)
        self.assertEqual(p.app_vd_x, 570)
        self.assertEqual(p.app_vd_y, 100)

    def test_main_panel_full_vd(self) -> None:
        panel = {
            "id": "p1",
            "position": {"x": 0.0, "y": 0.0},
            "size": {"width": 1.0, "height": 1.0},
        }
        rect = main_panel_physical_rect(panel, JETOUR_DASHING)
        self.assertEqual(rect.x, 570)
        self.assertEqual(rect.y, 100)
        self.assertEqual(rect.w, 1320)
        self.assertEqual(rect.h, 856)

    def test_main_roundtrip_rel(self) -> None:
        panel: dict = {"id": "p1"}
        apply_main_rel_to_panel(panel, 0.1, 0.2, 0.3, 0.4)
        rect = main_panel_physical_rect(panel, JETOUR_DASHING)
        rel = physical_to_main_rel(rect, JETOUR_DASHING)
        self.assertAlmostEqual(rel[0], 0.1, places=5)
        self.assertAlmostEqual(rel[1], 0.2, places=5)
        self.assertAlmostEqual(rel[2], 0.3, places=5)
        self.assertAlmostEqual(rel[3], 0.4, places=5)

    def test_main_drag_clamped_into_vd(self) -> None:
        # Rect mostly outside VD should clamp into VD when converting to rel.
        outside = Rect(0, 0, 200, 200)
        rel_x, rel_y, rel_w, rel_h = physical_to_main_rel(outside, JETOUR_DASHING)
        self.assertGreaterEqual(rel_x, 0.0)
        self.assertGreaterEqual(rel_y, 0.0)
        self.assertLessEqual(rel_x + rel_w, 1.0 + 1e-6)
        self.assertLessEqual(rel_y + rel_h, 1.0 + 1e-6)

    def test_floating_physical(self) -> None:
        panel = {"startX": 50, "startY": 80, "width": 300, "height": 200}
        rect = floating_panel_physical_rect(panel)
        self.assertEqual(rect, Rect(50, 80, 300, 200))
        apply_floating_physical_to_panel(panel, Rect(10.4, 20.6, 100.2, 50.8), JETOUR_DASHING)
        self.assertEqual(panel["startX"], 10)
        self.assertEqual(panel["startY"], 21)
        self.assertEqual(panel["width"], 100)
        self.assertEqual(panel["height"], 51)

    def test_fit_scale(self) -> None:
        scale = fit_scale(1920, 1080, 960, 540, padding=0)
        self.assertAlmostEqual(scale, 0.5)


    def test_default_panels(self) -> None:
        from theme_bundle import default_floating_panel, default_main_screen_panel

        main = default_main_screen_panel(name="A", page_number=2)
        self.assertTrue(str(main["id"]).startswith("main-screen-"))
        self.assertEqual(main["pageNumber"], 2)
        self.assertEqual(main["size"]["width"], 0.4)
        floating = default_floating_panel(name="B", existing_ids={main["id"]})
        self.assertTrue(str(floating["id"]).startswith("floating-"))
        self.assertTrue(floating["enabled"])
        self.assertEqual(floating["width"], 100)


if __name__ == "__main__":
    unittest.main()
