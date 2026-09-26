package probe.plugin;

import probe.shared.StudioProtocol;
import probe.shared.StudioVersion;

public final class PluginMain {
    public static String health() {
        return StudioVersion.current() + ":" + StudioProtocol.SCHEMA;
    }

    private PluginMain() {
    }
}
