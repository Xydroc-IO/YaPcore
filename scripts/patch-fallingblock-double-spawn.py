#!/usr/bin/env python3
"""Prevent double-spawn of FallingBlockEntity under concurrent neighbor/block ticks."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FB = (
    ROOT
    / "vendor/paper/paper-server/src/minecraft/java/net/minecraft/world/entity/item/FallingBlockEntity.java"
)


def main() -> int:
    if not FB.is_file():
        print(f"SKIP: {FB} missing")
        return 0
    text = FB.read_text()
    if "YaPcore Phase 3 — do not double-spawn" in text:
        print("FallingBlockEntity.fall double-spawn guard already present")
        return 0

    old = """    public static FallingBlockEntity fall(final Level level, final BlockPos pos, final BlockState state) {
        FallingBlockEntity entity = new FallingBlockEntity(
            level,
            pos.getX() + 0.5,
            pos.getY(),
            pos.getZ() + 0.5,
            state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state
        );
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, state.getFluidState().createLegacyBlock())) return entity; // CraftBukkit
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        level.addFreshEntity(entity);
        return entity;
    }"""

    new = """    public static FallingBlockEntity fall(final Level level, final BlockPos pos, final BlockState state) {
        FallingBlockEntity entity = new FallingBlockEntity(
            level,
            pos.getX() + 0.5,
            pos.getY(),
            pos.getZ() + 0.5,
            state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state
        );
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, state.getFluidState().createLegacyBlock())) return entity; // CraftBukkit
        // YaPcore Phase 3 — do not double-spawn if another spatial tick already converted this block
        final BlockState now = level.getBlockState(pos);
        if (!now.is(state.getBlock())) {
            return entity; // not added to world
        }
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        level.addFreshEntity(entity);
        return entity;
    }"""

    if old not in text:
        raise SystemExit("FallingBlockEntity.fall pattern not found")
    FB.write_text(text.replace(old, new, 1))
    print("Patched FallingBlockEntity.fall against spatial double-spawn")
    return 0


if __name__ == "__main__":
    sys.exit(main())
