#!/usr/bin/env python3
"""
위젯 라이트 테마 리소스 생성기.

다크(기본) 위젯 레이아웃/드로어블/스타일에서 색을 바꿔 *_light 버전을 만든다.
다크 쪽을 고친 뒤 이 스크립트를 다시 돌리면 된다:  python3 tools/gen_light_widgets.py
"""
import re
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"

# 다크 → 라이트 색 (긴 것부터 바꿔야 #FFFFFF가 #99FFFFFF 안을 먼저 건드리지 않음)
COLORS = [
    ("#E61E1E20", "#F2F6F6F8"),  # 위젯 패널
    ("#D91C1C1E", "#D9F4F4F6"),  # Dock 패널
    ("#FFFFFFFF", "#FF1C1C1E"),  # 벡터 아이콘 흰 선
    ("#CCFFFFFF", "#CC1C1C1E"),
    ("#99FFFFFF", "#8E8E93"),    # 보조 글씨
    ("#80FFFFFF", "#8E8E93"),
    ("#66FFFFFF", "#AEAEB2"),
    ("#40FFFFFF", "#26000000"),  # Dock 테두리
    ("#33FFFFFF", "#1A000000"),  # 패널 테두리, 선택 표시
    ("#26FFFFFF", "#1A000000"),  # 구분선
    ("#14FFFFFF", "#0A000000"),  # 사이드바
    ("#FFFFFF", "#1C1C1E"),      # 기본 글씨
]

# 테마와 상관없는 위젯 (스티커 메모는 메모지 색 그대로)
SKIP_LAYOUTS = {"widget_sticky", "widget_photo"}

# 라이트 버전이 필요한 드로어블 (흰색/어두운 패널을 쓰는 것만)
DRAWABLES = [
    "widget_bg", "dock_bg", "finder_sidebar_bg", "finder_side_selected", "widget_divider",
    "ic_add_tile", "ic_media_play", "ic_media_pause", "ic_media_next", "ic_media_prev", "ic_bat_phone", "ic_bat_headphones", "ic_bat_watch", "ic_bat_device",
]

# 레이아웃에서 쓰는 위젯 스타일 → 라이트 스타일(이름.Light)
STYLES = ["CalCell", "CalEvent", "MusicButton", "StAppName", "StAppTime", "FinderSideHeader", "FinderSideItem", "StorageRow", "StorageLabel", "StorageValue"]

HEADER = "<!-- tools/gen_light_widgets.py 가 만든 파일. 직접 고치지 말 것 -->\n"


def recolor(text: str) -> str:
    for dark, light in COLORS:
        text = re.sub(re.escape(dark), light, text, flags=re.IGNORECASE)
    return text


def lighten_refs(text: str) -> str:
    for d in DRAWABLES:
        text = text.replace(f"@drawable/{d}\"", f"@drawable/{d}_light\"")
    for s in STYLES:
        text = text.replace(f"@style/{s}\"", f"@style/{s}.Light\"")
    return text


def with_header(text: str) -> str:
    # XML 선언 바로 다음 줄에 안내 주석
    if text.startswith("<?xml"):
        first, rest = text.split("\n", 1)
        return first + "\n" + HEADER + rest
    return HEADER + text


def main():
    layouts = sorted(
        p for p in (RES / "layout").glob("widget_*.xml")
        if not p.stem.endswith("_light") and p.stem not in SKIP_LAYOUTS
    )
    for p in layouts:
        out = p.with_name(p.stem + "_light.xml")
        out.write_text(with_header(lighten_refs(recolor(p.read_text()))))

    for d in DRAWABLES:
        src = RES / "drawable" / f"{d}.xml"
        (RES / "drawable" / f"{d}_light.xml").write_text(with_header(recolor(src.read_text())))

    # themes.xml에서 위젯 스타일을 찾아 .Light로 복사
    themes = (RES / "values/themes.xml").read_text()
    blocks = []
    for s in STYLES:
        m = re.search(rf'<style name="{re.escape(s)}">.*?</style>', themes, flags=re.S)
        assert m, s
        blocks.append(recolor(m.group(0)).replace(f'name="{s}"', f'name="{s}.Light"', 1))
    (RES / "values/widget_styles_light.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n' + HEADER + "<resources>\n    "
        + "\n\n    ".join(blocks) + "\n</resources>\n"
    )
    print(f"layouts {len(layouts)}, drawables {len(DRAWABLES)}, styles {len(STYLES)}")


if __name__ == "__main__":
    main()
