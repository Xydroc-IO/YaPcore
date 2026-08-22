package com.yapcore.server;

import com.yapcore.config.ConfigHub;
import com.yapcore.config.ServerConfig;
import com.yapcore.paper.PaperPluginsLayout;
import com.yapcore.paper.PaperKernel;
import com.yapcore.folia.FoliaKernel;
import com.yapcore.kernel.GameKernel;
import com.yapcore.protocol.DualStackGateway;
import com.yapcore.ranks.YapRanks;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Function;

/**
 * Folia / Paper / Mojang authority startup.
 */
final class GameAuthorityBoot {

    private static final Logger LOG = Logger.getLogger("YaPcore.Server");

    private GameAuthorityBoot() {
    }

    static void ensureConfigHubs(Path rootDir, ServerConfig config) {
        if (config.isFoliaAuthority()) {
            try {
                PaperPluginsLayout.ensureUnified(rootDir, rootDir.resolve(config.getFoliaDir()));
                ConfigHub.ensure(rootDir, rootDir.resolve(config.getFoliaDir()));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Could not unify plugins/config hub for Folia", e);
            }
        } else if (config.isPaperAuthority()) {
            try {
                PaperPluginsLayout.ensureUnified(rootDir, rootDir.resolve(config.getPaperDir()));
                ConfigHub.ensure(rootDir, rootDir.resolve(config.getPaperDir()));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Could not unify plugins/config hub", e);
            }
        } else {
            try {
                ConfigHub.ensure(rootDir, rootDir.resolve(config.getPaperDir()));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Could not ensure config hub", e);
            }
        }
    }

    static void startAuthority(ServerConfig config,
                               FoliaKernel foliaKernel,
                               PaperKernel paperKernel,
                               GameKernel gameKernel,
                               DualStackGateway gateway) throws IOException, InterruptedException {
        switch (config.getGameAuthority()) {
            case FOLIA -> {
                foliaKernel.start();
                gateway.setProxyToGameKernel(config.isWrappedGameProxy());
                com.yapcore.game.command.GameCommandBridge.setProcessDispatch(foliaKernel::dispatchConsoleCommand);
            }
            case PAPER -> {
                paperKernel.start();
                gateway.setProxyToGameKernel(config.isWrappedGameProxy());
                com.yapcore.game.command.GameCommandBridge.setProcessDispatch(paperKernel::dispatchConsoleCommand);
            }
            case MOJANG -> {
                gameKernel.start();
                gateway.setProxyToGameKernel(true);
            }
            case NATIVE -> gateway.setProxyToGameKernel(false);
        }
    }

    static void maybeScheduleRanksAutoApply(YaPcoreServer server) {
        ServerConfig config = server.getConfig();
        if (!config.isYapRanksAutoApply()) {
            return;
        }
        if (!config.isPaperAuthority() || !server.paperKernel().isRunning()) {
            if (config.isFoliaAuthority() && server.foliaKernel().isRunning()) {
                scheduleRanks(server, server.foliaKernel()::dispatchConsoleCommand);
                return;
            }
            LOG.warning("yap-ranks-auto-apply ignored — Folia/Paper not running");
            return;
        }
        scheduleRanks(server, server.paperKernel()::dispatchConsoleCommand);
    }

    private static void scheduleRanks(YaPcoreServer server, Function<String, String> dispatch) {
        if (!YapRanks.yapPermsInstalled(server.getPluginManager().getPluginsDir())) {
            LOG.warning("yap-ranks-auto-apply set but yap-perms.jar not found in plugins/ — "
                    + "run: gradle installProductDefaults");
            return;
        }
        if (YapRanks.isApplied(server.getRootDir())) {
            LOG.info("YaP ranks pack already applied (config/yap-ranks-applied)");
            return;
        }
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(8_000L);
                if (!server.isRunning()) {
                    return;
                }
                var result = YapRanks.apply(server.getRootDir(), dispatch, false);
                LOG.info(result.summary());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "yap-ranks-auto-apply failed", e);
            }
        }, "yap-ranks-auto-apply");
        t.setDaemon(true);
        t.start();
        LOG.info("Scheduled YaP ranks auto-apply in ~8s (yap-perms.jar detected)");
    }
}
