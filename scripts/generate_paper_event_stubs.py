#!/usr/bin/env python3
import os, re, pathlib, sys
root = pathlib.Path(sys.argv[1])
out = pathlib.Path(sys.argv[2])
TEMPLATE = """package {package};

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
{extra}
/** Paper/Bukkit event stub for YaPcore (generated). */
public class {classname} extends Event{impl} {{
    private static final HandlerList HANDLERS = new HandlerList();
{fields}
    public {classname}() {{ super({async}); }}
    public {classname}(Object... ctx) {{ super({async}); }}
{methods}
    @Override public HandlerList getHandlers() {{ return HANDLERS; }}
    public static HandlerList getHandlerList() {{ return HANDLERS; }}
}}
"""
preserve = {
    "org.bukkit.event.player.PlayerJoinEvent",
    "org.bukkit.event.player.PlayerQuitEvent",
    "org.bukkit.event.player.PlayerEvent",
    "org.bukkit.event.player.PlayerInteractEvent",
    "org.bukkit.event.player.AsyncPlayerChatEvent",
    "org.bukkit.event.inventory.InventoryClickEvent",
    "org.bukkit.event.inventory.InventoryCloseEvent",
    "org.bukkit.event.inventory.InventoryDragEvent",
    "org.bukkit.event.block.BlockBreakEvent",
    "org.bukkit.event.block.BlockPlaceEvent",
    "io.papermc.paper.event.player.AsyncChatEvent",
}
n = 0
for path in sorted(root.rglob("*Event.java")):
    text = path.read_text(encoding="utf-8", errors="ignore")
    pkg = re.search(r"package\s+([\w.]+)\s*;", text)
    cls = re.search(r"public\s+(?:final\s+|abstract\s+)?class\s+(\w+Event)\b", text)
    if not pkg or not cls:
        continue
    package, classname = pkg.group(1), cls.group(1)
    fqn = f"{package}.{classname}"
    dest = out.joinpath(*package.split("."), f"{classname}.java")
    if fqn in preserve and dest.exists():
        continue
    cancel = "Cancellable" in text
    asyncf = "true" if "Async" in classname else "false"
    extra = "import org.bukkit.event.Cancellable;\n" if cancel else ""
    impl = " implements Cancellable" if cancel else ""
    fields = "    private boolean cancelled;\n" if cancel else ""
    methods = """
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
""" if cancel else ""
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(TEMPLATE.format(
        package=package, classname=classname, extra=extra, impl=impl,
        fields=fields, methods=methods, async=asyncf))
    n += 1
print(f"regenerated {n} event stubs into {out}")
