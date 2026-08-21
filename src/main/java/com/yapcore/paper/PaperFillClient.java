package com.yapcore.paper;

import com.yapcore.fill.FillClient;

import java.io.IOException;

/** @deprecated use {@link FillClient} */
@Deprecated
public final class PaperFillClient {

    private PaperFillClient() {
    }

    public static String latestServerJarUrl(String minecraftVersion) throws IOException {
        return FillClient.latestServerJarUrl("paper", minecraftVersion);
    }
}
