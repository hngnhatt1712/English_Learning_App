"""Geometry/paint-order regression checks; run: python -m unittest discover -s icon."""
import unittest
import xml.etree.ElementTree as ET

from generate_owla_vectors import ANDROID, HEAD_IDS, ROOT, SOURCE, SVG


class OwlaVectorTest(unittest.TestCase):
    def test_original_geometry_colours_fill_and_order_survive_import(self):
        original = list(ET.parse(SOURCE).iter(f"{{{SVG}}}path"))
        vector = ET.parse(ROOT / "app/src/main/res/drawable/owla_mascot.xml")
        imported = [p for p in vector.iter("path") if not p.get(f"{{{ANDROID}}}name").startswith("lid_")]
        self.assertEqual(len(original), 30)
        self.assertEqual(len(imported), 30)
        for svg, android in zip(original, imported):
            self.assertEqual(svg.get("id"), android.get(f"{{{ANDROID}}}name"))
            self.assertEqual(svg.get("d"), android.get(f"{{{ANDROID}}}pathData"))
            self.assertEqual(svg.get("fill"), android.get(f"{{{ANDROID}}}fillColor"))
            self.assertEqual(android.get(f"{{{ANDROID}}}fillType"), "evenOdd")

    def test_lids_are_invisible_by_default_and_follow_their_eye(self):
        root = ET.parse(ROOT / "app/src/main/res/drawable/owla_mascot.xml").getroot()
        names = [p.get(f"{{{ANDROID}}}name") for p in root.iter("path")]
        clip_names = {p.get(f"{{{ANDROID}}}name") for p in root.iter("clip-path")}
        for side in ("left", "right"):
            for lid in ("top", "bottom", "crease"):
                name = f"lid_{side}_{lid}_fill"
                path = next(p for p in root.iter("path") if p.get(f"{{{ANDROID}}}name") == name)
                self.assertEqual(path.get(f"{{{ANDROID}}}fillAlpha"), "0")
                self.assertGreater(names.index(name), names.index(f"pupil_{side}"))
            for lid in ("top", "bottom"):
                self.assertIn(f"lid_{side}_{lid}_clip", clip_names)

    def test_head_has_only_head_layers_and_matching_viewports(self):
        head = ET.parse(ROOT / "icon/owla_head.svg").getroot()
        self.assertEqual(head.get("viewBox"), "0 0 1197 760")
        self.assertEqual({p.get("id") for p in head}, HEAD_IDS)
        vector = ET.parse(ROOT / "app/src/main/res/drawable/owla_head.xml").getroot()
        self.assertEqual(vector.get(f"{{{ANDROID}}}viewportWidth"), "1197")
        self.assertEqual(vector.get(f"{{{ANDROID}}}viewportHeight"), "760")
        self.assertEqual(
            [p.get("d") for p in head.iter(f"{{{SVG}}}path")],
            [p.get(f"{{{ANDROID}}}pathData") for p in vector.iter("path")],
        )


if __name__ == "__main__":
    unittest.main()
