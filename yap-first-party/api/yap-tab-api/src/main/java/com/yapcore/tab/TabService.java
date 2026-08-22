package com.yapcore.tab;

import org.bukkit.entity.Player;

import java.util.List;

/** TAB list, header/footer, sidebar, and nametag refresh. */
public interface TabService {

    void refresh(Player player);

    void refreshAll();

    /** Override header/footer for this server until reload or clear. */
    void setHeaderFooter(List<String> header, List<String> footer);

    /** Override sidebar lines for this server until reload or clear. */
    void setSidebarLines(List<String> lines);

    /** Drop runtime overrides and network overlay. */
    void clearOverrides();
}
