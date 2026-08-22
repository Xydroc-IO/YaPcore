package com.yapcore.mmo;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class SkillFeedbackServices {

    private SkillFeedbackServices() {
    }

    public static Optional<SkillFeedbackBridge> find() {
        var reg = Bukkit.getServicesManager().getRegistration(SkillFeedbackBridge.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}
