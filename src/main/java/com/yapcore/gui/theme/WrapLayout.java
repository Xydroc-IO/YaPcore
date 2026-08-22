package com.yapcore.gui.theme;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * FlowLayout that wraps to the next line when the parent is too narrow —
 * so button rows don't force the window wider than the screen.
 */
public final class WrapLayout extends FlowLayout {

    public WrapLayout() {
        super();
    }

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension min = layoutSize(target, false);
        min.width -= (getHgap() + 1);
        return min;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getWidth();
            Container parent = target.getParent();
            if (targetWidth == 0 && parent != null) {
                targetWidth = parent.getWidth();
            }
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int maxWidth = targetWidth - (insets.left + insets.right + hgap * 2);

            int x = 0;
            int y = insets.top + vgap;
            int rowHeight = 0;
            int reqWidth = 0;

            for (Component m : target.getComponents()) {
                if (!m.isVisible()) {
                    continue;
                }
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (x == 0 || x + d.width <= maxWidth) {
                    if (x > 0) {
                        x += hgap;
                    }
                    x += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                } else {
                    y += vgap + rowHeight;
                    x = d.width;
                    rowHeight = d.height;
                }
                reqWidth = Math.max(reqWidth, x);
            }
            y += rowHeight + vgap + insets.bottom;
            return new Dimension(reqWidth + insets.left + insets.right, y);
        }
    }
}
