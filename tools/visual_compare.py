"""Compare approved Android captures; produce a review report, never approve it."""

import argparse
import html
import json
import shutil
from pathlib import Path


def image_path(root, relative):
    path = (root / relative).resolve()
    if not path.is_relative_to(root.resolve()) or path.suffix.lower() != ".png":
        raise ValueError(f"Expected an in-directory PNG: {relative}")
    return path


def compare_image(before, after, spec, diff_path):
    from PIL import Image, ImageChops, ImageDraw

    tolerance = spec.get("pixel_tolerance", 0)
    ratio_limit = spec.get("max_changed_ratio", 0)
    if not isinstance(tolerance, int) or not 0 <= tolerance <= 255:
        raise ValueError("pixel_tolerance must be an integer in [0,255]")
    if not 0 <= ratio_limit < 1:
        raise ValueError("max_changed_ratio must be in [0,1)")
    with Image.open(before) as original, Image.open(after) as actual:
        if original.size != actual.size:
            raise ValueError(f"Image size differs: {original.size} vs {actual.size}")

        # Composite alpha instead of discarding it (transparent icon previews).
        def flattened(image):
            base = Image.new("RGBA", image.size, "black")
            return Image.alpha_composite(base, image.convert("RGBA")).convert("RGB")

        delta = ImageChops.difference(flattened(original), flattened(actual))
        r, g, b = delta.split()
        maximum = ImageChops.lighter(ImageChops.lighter(r, g), b)
        changed = maximum.point(lambda value: 255 if value > tolerance else 0)
        valid = Image.new("L", original.size, 255)
        draw = ImageDraw.Draw(valid)
        for mask in spec.get("masks", []):
            if not mask.get("reason", "").strip():
                raise ValueError("Every mask needs a reason")
            left, top, right, bottom = mask["rect"]
            if not (0 <= left < right <= original.width and 0 <= top < bottom <= original.height):
                raise ValueError("Mask outside image bounds")
            draw.rectangle((left, top, right - 1, bottom - 1), fill=0)
        considered = valid.histogram()[255]
        if considered == 0:
            raise ValueError("Masks hide the entire image")
        changed = ImageChops.multiply(changed, valid)
        count = changed.histogram()[255]
        ratio = count / considered
        delta.save(diff_path)
        return {
            "changed_pixels": count,
            "considered_pixels": considered,
            "changed_ratio": ratio,
            "result": "passed" if ratio <= ratio_limit else "failed",
        }


def compare(baseline_path, actual_dir, output_dir):
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    for key in ["approved_by", "approved_utc", "source_commit"]:
        if not baseline.get(key, "").strip():
            raise ValueError(f"Missing baseline approval metadata: {key}")
    actual = json.loads((actual_dir / "capture.json").read_text(encoding="utf-8"))
    configuration = baseline.get("configuration")
    required = {
        "device",
        "os_build",
        "resolution",
        "dpi",
        "rotation",
        "font_scale",
        "locale",
        "ime",
        "fixture",
    }
    if not isinstance(configuration, dict) or not required <= configuration.keys():
        raise ValueError("Baseline configuration is incomplete")
    if configuration != actual.get("configuration"):
        raise ValueError("Capture configuration differs from approved baseline")
    images = baseline.get("images", [])
    if not images:
        raise ValueError("No approved baseline images")
    output_dir.mkdir(parents=True, exist_ok=True)
    results, cards, seen = [], [], set()
    for index, spec in enumerate(images):
        relative = spec["path"]
        if relative in seen:
            raise ValueError(f"Duplicate baseline image: {relative}")
        seen.add(relative)
        row = {"path": relative, "result": "failed"}
        before_name, after_name, diff_name = [
            f"{index}-{kind}.png" for kind in ["before", "after", "diff"]
        ]
        try:
            before = image_path(baseline_path.parent, relative)
            after = image_path(actual_dir, relative)
            row.update(compare_image(before, after, spec, output_dir / diff_name))
            shutil.copyfile(before, output_dir / before_name)
            shutil.copyfile(after, output_dir / after_name)
            pictures = "".join(
                f'<figure><figcaption>{kind}</figcaption><img src="{name}"></figure>'
                for kind, name in [
                    ("Approved", before_name),
                    ("Actual", after_name),
                    ("Difference", diff_name),
                ]
            )
        except (OSError, ValueError) as error:
            row["error"] = str(error)
            pictures = html.escape(str(error))
        results.append(row)
        cards.append(
            f"<section><h2>{html.escape(relative)}: {row['result']}</h2>{pictures}</section>"
        )
    passed = all(row["result"] == "passed" for row in results)
    report = {
        "result": "passed" if passed else "failed",
        "visual_review": "unreviewed",
        "configuration": configuration,
        "images": results,
    }
    (output_dir / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    document = (
        '<!doctype html><meta charset="utf-8"><title>DexDroid visual comparison</title>'
        "<style>body{background:#121212;color:white;font:16px sans-serif;padding:24px}"
        "figure{display:inline-block;vertical-align:top;margin:8px;width:30%}"
        "img{max-width:100%;border:1px solid #555}section{border-top:1px solid #555}</style>"
        "<h1>DexDroid visual comparison</h1><p>Pixel result: "
        + report["result"]
        + ". Human visual review remains unreviewed.</p>"
        + "".join(cards)
    )
    (output_dir / "index.html").write_text(document, encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--actual", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        report = compare(args.baseline.resolve(), args.actual.resolve(), args.output.resolve())
    except (ImportError, OSError, ValueError, KeyError) as error:
        print(f"FAIL {error}")
        return 1
    print(f"{report['result'].upper()}: {args.output / 'index.html'} (visual review: unreviewed)")
    return int(report["result"] != "passed")


if __name__ == "__main__":
    raise SystemExit(main())
