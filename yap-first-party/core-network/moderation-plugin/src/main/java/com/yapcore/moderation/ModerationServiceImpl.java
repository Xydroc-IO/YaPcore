package com.yapcore.moderation;

import com.yapcore.moderation.db.ModerationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ModerationServiceImpl implements ModerationService {

    private final ModerationRepository repository;

    public ModerationServiceImpl(ModerationRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isBanned(UUID uuid) {
        return activeBan(uuid).isPresent();
    }

    @Override
    public boolean isIpBanned(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        try {
            Optional<Punishment> ban = repository.findActiveIp(address);
            return ban.isPresent() && !ban.get().isExpired(System.currentTimeMillis());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isMuted(UUID uuid) {
        return activeMute(uuid).isPresent();
    }

    @Override
    public Optional<Punishment> activeBan(UUID uuid) {
        try {
            Optional<Punishment> ban = repository.findActive(uuid, PunishmentType.BAN);
            if (ban.isPresent() && ban.get().isExpired(System.currentTimeMillis())) {
                return Optional.empty();
            }
            return ban;
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Punishment> activeMute(UUID uuid) {
        try {
            Optional<Punishment> mute = repository.findActive(uuid, PunishmentType.MUTE);
            if (mute.isPresent() && mute.get().isExpired(System.currentTimeMillis())) {
                return Optional.empty();
            }
            return mute;
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Punishment> activeIpBan(String address) {
        try {
            Optional<Punishment> ban = repository.findActiveIp(address);
            if (ban.isPresent() && ban.get().isExpired(System.currentTimeMillis())) {
                return Optional.empty();
            }
            return ban;
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public CompletableFuture<List<Punishment>> history(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.history(uuid, limit);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Punishment> ban(UUID target, String targetName, UUID actor, String actorName,
                                             String reason, long expiresAtEpochMs, boolean ipBan) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.deactivateType(target, PunishmentType.BAN);
                Punishment ban = repository.insert(
                        PunishmentType.BAN, target, targetName, actor, actorName, reason, null, expiresAtEpochMs);
                return ban;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Punishment> banWithIp(UUID target, String targetName, UUID actor, String actorName,
                                                   String reason, long expiresAtEpochMs, String ip) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.deactivateType(target, PunishmentType.BAN);
                repository.deactivateIp(ip);
                return repository.insert(
                        PunishmentType.BAN, target, targetName, actor, actorName, reason, ip, expiresAtEpochMs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Punishment> mute(UUID target, String targetName, UUID actor, String actorName,
                                              String reason, long expiresAtEpochMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.deactivateType(target, PunishmentType.MUTE);
                return repository.insert(
                        PunishmentType.MUTE, target, targetName, actor, actorName, reason, null, expiresAtEpochMs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> unban(UUID target, UUID actor, String actorName, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.deactivateType(target, PunishmentType.BAN);
                return true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> unmute(UUID target, UUID actor, String actorName, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.deactivateType(target, PunishmentType.MUTE);
                return true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Punishment> warn(UUID target, String targetName, UUID actor, String actorName,
                                              String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.insert(
                        PunishmentType.WARN, target, targetName, actor, actorName, reason, null, 0L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void reload() {
        try {
            repository.expireStale();
        } catch (Exception ignored) {
        }
    }

    public ModerationRepository repository() {
        return repository;
    }
}
