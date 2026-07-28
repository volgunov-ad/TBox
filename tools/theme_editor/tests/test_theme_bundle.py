"""Unit tests for `.tboxtheme` bundle helpers (no GUI)."""

from __future__ import annotations

import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from theme_bundle import (
    ASSETS_ICONS_DIR,
    ASSETS_WALLPAPER_DARK_DIR,
    ASSETS_WALLPAPER_LIGHT_DIR,
    SECTION_APP_ICONS,
    SECTION_MAIN_SCREEN,
    ThemeBundle,
    normalize_hex_color,
    normalize_zip_entry_path,
    sanitize_theme_export_base_name,
    theme_json_zip_entry_priority,
)


class ThemeBundleTests(unittest.TestCase):
    def test_normalize_zip_entry_path(self) -> None:
        self.assertEqual(normalize_zip_entry_path("./theme.json"), "theme.json")
        self.assertEqual(normalize_zip_entry_path("/theme.json"), "theme.json")
        self.assertEqual(
            normalize_zip_entry_path(".\\my_theme\\theme.json"),
            "my_theme/theme.json",
        )

    def test_theme_json_priority(self) -> None:
        self.assertEqual(theme_json_zip_entry_priority("theme.json"), 0)
        self.assertEqual(
            theme_json_zip_entry_priority("exported/my.tboxtheme/theme.json"),
            len("exported/my.tboxtheme/theme.json"),
        )
        self.assertIsNone(theme_json_zip_entry_priority("readme.txt"))

    def test_new_empty_and_roundtrip(self) -> None:
        bundle = ThemeBundle.new_empty([SECTION_MAIN_SCREEN, SECTION_APP_ICONS])
        bundle.light_wallpapers["wall.jpg"] = b"fake-image"
        bundle.icons["com.example.app.png"] = b"png"
        main = bundle.theme[SECTION_MAIN_SCREEN]
        main["wallpaperSelectionByPage"] = {"light": {"1": "wall.jpg"}}

        data = bundle.to_bytes()
        loaded = ThemeBundle.load_bytes(data)
        self.assertEqual(loaded.theme["type"], "tbox_theme")
        self.assertEqual(loaded.theme["formatVersion"], 1)
        self.assertIn(SECTION_MAIN_SCREEN, loaded.theme["sections"])
        self.assertEqual(loaded.light_wallpapers["wall.jpg"], b"fake-image")
        self.assertEqual(loaded.icons["com.example.app.png"], b"png")
        self.assertIn("com.example.app", loaded.theme[SECTION_APP_ICONS]["packages"])

    def test_load_nested_theme_json(self) -> None:
        theme = {
            "formatVersion": 1,
            "type": "tbox_theme",
            "sections": ["mainScreen"],
            "mainScreen": {"pageCount": 2, "currentPage": 1},
        }
        raw = self._zip_bytes(
            {
                "bundle/theme.json": json.dumps(theme).encode("utf-8"),
                f"bundle/{ASSETS_WALLPAPER_LIGHT_DIR}a.png": b"img",
                f"bundle/{ASSETS_ICONS_DIR}com.app.png": b"icon",
            }
        )
        loaded = ThemeBundle.load_bytes(raw)
        self.assertEqual(loaded.theme["mainScreen"]["pageCount"], 2)
        self.assertEqual(loaded.light_wallpapers["a.png"], b"img")
        self.assertEqual(loaded.icons["com.app.png"], b"icon")

    def test_rejects_non_zip(self) -> None:
        with self.assertRaises(ValueError) as ctx:
            ThemeBundle.load_bytes(b"not-zip")
        self.assertEqual(str(ctx.exception), "not_a_zip_archive")

    def test_rejects_missing_theme_json(self) -> None:
        raw = self._zip_bytes({"readme.txt": b"hello"})
        with self.assertRaises(ValueError) as ctx:
            ThemeBundle.load_bytes(raw)
        self.assertEqual(str(ctx.exception), "theme.json not found")

    def test_validate_missing_wallpaper_reference(self) -> None:
        bundle = ThemeBundle.new_empty([SECTION_MAIN_SCREEN])
        bundle.theme[SECTION_MAIN_SCREEN]["wallpaperSelectionByPage"] = {
            "light": {"1": "missing.jpg"}
        }
        errors = bundle.validate()
        self.assertTrue(any("missing.jpg" in e for e in errors))

    def test_save_path_adds_extension(self) -> None:
        bundle = ThemeBundle.new_empty([SECTION_MAIN_SCREEN])
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "My Theme"
            saved = bundle.save_path(path)
            self.assertTrue(saved.name.endswith(".tboxtheme"))
            self.assertTrue(saved.is_file())
            reloaded = ThemeBundle.load_path(saved)
            self.assertEqual(reloaded.theme["type"], "tbox_theme")

    def test_sanitize_name(self) -> None:
        self.assertEqual(sanitize_theme_export_base_name("eco:theme?.tboxtheme"), "eco_theme_")
        self.assertIsNone(sanitize_theme_export_base_name("..."))

    def test_normalize_hex_color(self) -> None:
        self.assertEqual(normalize_hex_color("#abc"), "#FFAABBCC")
        self.assertEqual(normalize_hex_color("112233"), "#FF112233")
        self.assertEqual(normalize_hex_color("#80112233"), "#80112233")

    def test_dark_wallpapers_roundtrip(self) -> None:
        bundle = ThemeBundle.new_empty([SECTION_MAIN_SCREEN])
        bundle.dark_wallpapers["night.webp"] = b"dark"
        loaded = ThemeBundle.load_bytes(bundle.to_bytes())
        self.assertEqual(loaded.dark_wallpapers["night.webp"], b"dark")
        with zipfile.ZipFile(__import__("io").BytesIO(bundle.to_bytes())) as zf:
            names = zf.namelist()
        self.assertIn(f"{ASSETS_WALLPAPER_DARK_DIR}night.webp", names)
        self.assertIn("theme.json", names)

    @staticmethod
    def _zip_bytes(entries: dict[str, bytes]) -> bytes:
        import io

        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w") as zf:
            for name, data in entries.items():
                zf.writestr(name, data)
        return buf.getvalue()


if __name__ == "__main__":
    unittest.main()
