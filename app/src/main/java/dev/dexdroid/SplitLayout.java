package dev.dexdroid;

/** Logical workspace geometry; display IDs and Android views never enter this model. */
final class SplitLayout {
    boolean enabled, vertical, swapped;
    int selected, maximized = -1;
    float ratio = .5f;

    int paneAtPosition(int position) {
        return swapped ? 1 - position : position;
    }

    String openLabel(int position) {
        if (!enabled) return "Open app";
        return vertical
                ? (position == 0 ? "Open up" : "Open down")
                : (position == 0 ? "Open left" : "Open right");
    }

    SplitLayout copy() {
        SplitLayout value = new SplitLayout();
        value.enabled = enabled;
        value.vertical = vertical;
        value.swapped = swapped;
        value.selected = selected;
        value.maximized = maximized;
        value.ratio = ratio;
        return value;
    }

    static final class Box {
        final int x, y, width, height;

        Box(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        boolean contains(float px, float py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }

    Box normal(int pane, int width, int height, int gap) {
        if (!enabled && pane == 0) return new Box(0, 0, width, height);
        int span = vertical ? height : width;
        int divider = Math.min(Math.max(1, gap), Math.max(1, span / 8));
        int first =
                Math.max(1, Math.round((span - divider) * Math.max(.25f, Math.min(.75f, ratio))));
        int slot = swapped ? 1 - pane : pane;
        int start = slot == 0 ? 0 : first + divider;
        int size = slot == 0 ? first : Math.max(1, span - start);
        return vertical ? new Box(0, start, width, size) : new Box(start, 0, size, height);
    }

    Box pane(int pane, int width, int height, int gap) {
        if (maximized >= 0)
            return pane == maximized ? new Box(0, 0, width, height) : new Box(0, 0, 0, 0);
        return normal(pane, width, height, gap);
    }

    Box display(int pane, int width, int height, int gap) {
        Box visible = pane(pane, width, height, gap);
        return visible.width == 0 ? normal(pane, width, height, gap) : visible;
    }

    int hit(float x, float y, int width, int height, int gap) {
        for (int i = 0; i < 2; i++) if (pane(i, width, height, gap).contains(x, y)) return i;
        return -1;
    }

    Box divider(int width, int height, int gap) {
        if (maximized >= 0 || !enabled) return new Box(0, 0, 0, 0);
        gap = Math.min(Math.max(1, gap), Math.max(1, (vertical ? height : width) / 8));
        Box first = normal(swapped ? 1 : 0, width, height, gap);
        return vertical
                ? new Box(0, first.height, width, gap)
                : new Box(first.width, 0, gap, height);
    }

    Box dividerGrab(int width, int height, int gap, int minimum) {
        Box line = divider(width, height, gap);
        if (line.width == 0 || line.height == 0) return line;
        if (vertical) {
            int size = Math.min(height, Math.max(line.height, minimum));
            int top = Math.max(0, Math.min(height - size, line.y + (line.height - size) / 2));
            return new Box(0, top, width, size);
        }
        int size = Math.min(width, Math.max(line.width, minimum));
        int left = Math.max(0, Math.min(width - size, line.x + (line.width - size) / 2));
        return new Box(left, 0, size, height);
    }
}
