package fun.prof_chen.teamviewer.main_code.client.sdk;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Compile-time-visible identity and feature declaration for a Minecraft adapter. */
public record ClientAdapterDescriptor(String adapterVersion, Set<ClientFeature> features) {
    public ClientAdapterDescriptor {
        adapterVersion = Objects.requireNonNull(adapterVersion, "adapterVersion");
        features = Set.copyOf(Objects.requireNonNull(features, "features"));
        EnumSet<ClientFeature> missing = EnumSet.allOf(ClientFeature.class);
        missing.removeAll(features);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Incomplete Minecraft adapter " + adapterVersion + "; missing " + missing);
        }
    }

    public static ClientAdapterDescriptor complete(String adapterVersion) {
        return new ClientAdapterDescriptor(adapterVersion, EnumSet.allOf(ClientFeature.class));
    }
}
