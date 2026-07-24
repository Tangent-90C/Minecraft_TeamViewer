package fun.prof_chen.teamviewer.main_code.client.sdk;

import java.util.List;
import java.util.Objects;

/** Result of the runtime Adapter SDK technology compatibility kit. */
public record AdapterTckReport(
        String adapterVersion,
        boolean passed,
        List<String> issues,
        List<IntegrationCapability> integrations,
        boolean firstWorldRenderObserved,
        boolean firstHudRenderObserved) {
    public AdapterTckReport {
        adapterVersion = Objects.requireNonNull(adapterVersion, "adapterVersion");
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        integrations = List.copyOf(Objects.requireNonNull(integrations, "integrations"));
    }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"adapterVersion\":\"").append(escape(adapterVersion)).append("\",");
        json.append("\n  \"passed\":").append(passed).append(',');
        json.append("\n  \"firstWorldRenderObserved\":").append(firstWorldRenderObserved).append(',');
        json.append("\n  \"firstHudRenderObserved\":").append(firstHudRenderObserved).append(',');
        json.append("\n  \"issues\":[");
        appendStrings(json, issues);
        json.append("],\n  \"integrations\":[");
        for (int index = 0; index < integrations.size(); index++) {
            if (index > 0) json.append(',');
            IntegrationCapability capability = integrations.get(index);
            json.append("{\"id\":\"").append(escape(capability.id()))
                    .append("\",\"role\":\"").append(escape(capability.role()))
                    .append("\",\"status\":\"").append(capability.status())
                    .append("\",\"detail\":\"").append(escape(capability.detail())).append("\"}");
        }
        return json.append("]\n}\n").toString();
    }

    public AdapterTckReport withRenderObservations(boolean world, boolean hud) {
        return new AdapterTckReport(adapterVersion, passed, issues, integrations, world, hud);
    }

    private static void appendStrings(StringBuilder json, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) json.append(',');
            json.append('\"').append(escape(values.get(index))).append('\"');
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
