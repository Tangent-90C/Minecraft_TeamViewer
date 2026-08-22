package fun.prof_chen.teamviewer.main_code.config.ui;

import fun.prof_chen.teamviewer.main_code.client.bridge.ClientControlGateway;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationCapability;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationIds;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationImplementationSource;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.client.sdk.PluginRuntimeStatus;
import fun.prof_chen.teamviewer.main_code.plugin.DisabledPluginSnapshot;
import fun.prof_chen.teamviewer.main_code.plugin.PluginFileOperationResult;
import fun.prof_chen.teamviewer.main_code.plugin.PluginManifest;
import fun.prof_chen.teamviewer.main_code.plugin.PluginSnapshot;
import fun.prof_chen.teamviewer.main_code.plugin.PluginSettingState;
import fun.prof_chen.teamviewer.main_code.plugin.PluginRuntimeState;
import fun.prof_chen.teamviewer.main_code.plugin.PluginRuntimeAction;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

import static fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlId.*;
import static fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.*;

/** Common business state, layout and actions for the dense integration-plugin manager. */
public final class PluginManagerUiSession implements PluginManagerUiController {
    private static final int WIDE_THRESHOLD = 700;
    private static final int FRAME_MARGIN = 12;
    private static final int MAX_FRAME_WIDTH = 960;
    private static final int MAX_FRAME_HEIGHT = 560;
    private static final int HEADER_HEIGHT = 34;
    private static final int ROW_HEIGHT = 34;
    private static final int MESSAGE_HEIGHT = 22;

    private final ClientControlGateway control;
    private final LongSupplier wallClock;
    private final Map<ConfigControlId, String> textValues = new HashMap<>();
    private PluginManagerTab tab = PluginManagerTab.INSTALLED;
    private String selectedPluginId;
    private String selectedDisabledPluginId;
    private boolean compactDetail;
    private int installedListScroll;
    private int disabledListScroll;
    private int detailScroll;
    private int visibleRows = 1;
    private int detailScrollMaximum;
    private PluginFileOperationResult lastOperation;
    private DialogKind dialogKind;
    private String pendingDeleteStorageId;
    private String pendingRuntimePluginId;
    private String pendingRuntimeActionId;
    private Path copiedPluginPath;

    public PluginManagerUiSession(ClientControlGateway control) {
        this(control, System::currentTimeMillis);
    }

    PluginManagerUiSession(ClientControlGateway control, LongSupplier wallClock) {
        this.control = Objects.requireNonNull(control, "control");
        this.wallClock = Objects.requireNonNull(wallClock, "wallClock");
    }

    @Override
    public PluginManagerView view(int width, int height) {
        syncSelection();
        boolean compact = width < WIDE_THRESHOLD;
        int frameWidth = Math.max(300, Math.min(MAX_FRAME_WIDTH, width - FRAME_MARGIN * 2));
        int frameHeight = Math.max(220, Math.min(MAX_FRAME_HEIGHT, height - FRAME_MARGIN * 2));
        int frameX = (width - frameWidth) / 2;
        int frameY = (height - frameHeight) / 2;
        UiRect frame = new UiRect(frameX, frameY, frameWidth, frameHeight);
        UiRect header = new UiRect(frameX, frameY, frameWidth, HEADER_HEIGHT);

        int closeWidth = 50;
        int rescanWidth = 82;
        int tabWidth = compact ? 70 : 82;
        int tabX = frameX + (compact ? 92 : 132);
        ActionView installedTab = action(PLUGIN_TAB_INSTALLED,
                new UiRect(tabX, frameY + 7, tabWidth, 21),
                t("screen.mc_teamviewer.integration_plugin.manager.installed",
                        UiText.literal(String.valueOf(control.getIntegrationPlugins().size()))),
                null, true, tab == PluginManagerTab.INSTALLED, false);
        ActionView disabledTab = action(PLUGIN_TAB_DISABLED,
                new UiRect(tabX + tabWidth + 3, frameY + 7, tabWidth, 21),
                t("screen.mc_teamviewer.integration_plugin.manager.disabled",
                        UiText.literal(String.valueOf(control.getDisabledIntegrationPlugins().size()))),
                null, true, tab == PluginManagerTab.DISABLED, false);
        ActionView closeAction = action(BACK,
                new UiRect(frameX + frameWidth - closeWidth - 7, frameY + 7, closeWidth, 21),
                tr("screen.mc_teamviewer.integration_plugin.manager.close"), null, true, false, false);
        ActionView rescanAction = action(PLUGIN_RESCAN,
                new UiRect(closeAction.bounds().x() - rescanWidth - 4, frameY + 7, rescanWidth, 21),
                tr("screen.mc_teamviewer.integration_plugin.rescan"), null,
                tab == PluginManagerTab.INSTALLED, false, false);

        int messageHeight = lastOperation == null ? 0 : MESSAGE_HEIGHT;
        int bodyY = frameY + HEADER_HEIGHT;
        int bodyHeight = frameHeight - HEADER_HEIGHT - messageHeight;
        int listWidth = compact ? frameWidth : Math.max(240, Math.min(320, frameWidth * 34 / 100));
        UiRect listBounds = new UiRect(frameX, bodyY, listWidth, bodyHeight);
        UiRect detailBounds = compact
                ? new UiRect(frameX, bodyY, frameWidth, bodyHeight)
                : new UiRect(frameX + listWidth + 1, bodyY, frameWidth - listWidth - 1, bodyHeight);

        List<ListItemView> items = buildItems(listBounds);
        DetailView detail = buildDetail(detailBounds, compact);
        MessageView message = lastOperation == null ? null : new MessageView(
                new UiRect(frameX, frameY + frameHeight - MESSAGE_HEIGHT, frameWidth, MESSAGE_HEIGHT),
                operationResultText(lastOperation), operationDetail(lastOperation),
                lastOperation.succeeded() ? SUCCESS : ERROR);
        DialogView dialog = buildDialog(frame);
        return new PluginManagerView(frame, header,
                new UiRect(frameX + 12, frameY + 11, compact ? 76 : 112, 12),
                installedTab, disabledTab, rescanAction, closeAction,
                listBounds, detailBounds, compact, compact && compactDetail, tab,
                items, detail, message, dialog);
    }

    private List<ListItemView> buildItems(UiRect listBounds) {
        List<ListItemView> result = new ArrayList<>();
        int total = tab == PluginManagerTab.INSTALLED
                ? control.getIntegrationPlugins().size()
                : control.getDisabledIntegrationPlugins().size();
        visibleRows = Math.max(1, listBounds.height() / ROW_HEIGHT);
        int listScroll = clamp(currentListScroll(), 0, Math.max(0, total - visibleRows));
        setCurrentListScroll(listScroll);
        int end = Math.min(total, listScroll + visibleRows);
        for (int index = listScroll; index < end; index++) {
            int y = listBounds.y() + (index - listScroll) * ROW_HEIGHT;
            UiRect row = new UiRect(listBounds.x(), y, listBounds.width(), ROW_HEIGHT);
            if (tab == PluginManagerTab.INSTALLED) {
                PluginSnapshot plugin = control.getIntegrationPlugins().get(index);
                UiRect toggle = new UiRect(row.x() + row.width() - 38, row.y() + 10, 26, 14);
                result.add(new ListItemView(
                        ConfigControlId.plugin(plugin.id(), "manager-select"),
                        ConfigControlId.plugin(plugin.id(), "toggle"),
                        row, toggle, pluginName(plugin),
                        t("screen.mc_teamviewer.integration_plugin.manager.item_meta",
                                UiText.literal(plugin.version()), runtimeStatusText(plugin.runtimeStatus())),
                        runtimeStatusText(plugin.runtimeStatus()), pluginListTooltip(plugin),
                        statusColor(plugin.runtimeStatus()),
                        plugin.id().equals(selectedPluginId), plugin.enabled(), true,
                        pluginToggleActive(plugin)));
            } else {
                DisabledPluginSnapshot plugin = control.getDisabledIntegrationPlugins().get(index);
                result.add(new ListItemView(
                        ConfigControlId.plugin(plugin.storageId(), "manager-disabled-select"),
                        null, row, null, UiText.literal(plugin.name()),
                        t("screen.mc_teamviewer.integration_plugin.manager.item_disabled_meta",
                                UiText.literal(plugin.version()), UiText.literal(plugin.originalFileName())),
                        tr("screen.mc_teamviewer.integration_plugin.runtime.disabled"),
                        disabledPluginTooltip(plugin),
                        WARNING, plugin.storageId().equals(selectedDisabledPluginId),
                        false, false, false));
            }
        }
        return result;
    }

    private DetailView buildDetail(UiRect bounds, boolean compact) {
        if (tab == PluginManagerTab.DISABLED) {
            DisabledPluginSnapshot disabled = selectedDisabled();
            return disabled == null ? emptyDetail(bounds, compact,
                    tr("screen.mc_teamviewer.integration_plugin.disabled_none"))
                    : disabledDetail(bounds, compact, disabled);
        }
        PluginSnapshot plugin = selectedPlugin();
        return plugin == null ? emptyDetail(bounds, compact,
                tr("screen.mc_teamviewer.integration_plugin.none"))
                : installedDetail(bounds, compact, plugin);
    }

    private DetailView emptyDetail(UiRect bounds, boolean compact, UiText title) {
        detailScroll = 0;
        detailScrollMaximum = 0;
        return new DetailView(bounds, contentBounds(bounds),
                compactBack(bounds, compact), title, UiText.literal(""),
                UiText.literal(""), UiText.literal(""), null, MUTED,
                null, null, -1, null, List.of(), List.of(), List.of(),
                tab == PluginManagerTab.DISABLED, null);
    }

    private DetailView installedDetail(UiRect bounds, boolean compact, PluginSnapshot plugin) {
        for (PluginManifest.SettingDefinition definition : plugin.visibleSettingDefinitions()) {
            ConfigControlId id = ConfigControlId.setting(plugin.id(), definition.key());
            textValues.putIfAbsent(id, String.valueOf(plugin.settings().getOrDefault(definition.key(), "")));
        }
        UiRect content = contentBounds(bounds);
        UiText descriptionText = plugin.description().isBlank() ? null : pluginDescription(plugin);
        int descriptionHeight = descriptionText == null ? 0
                : estimatedWrappedHeight(plugin.description(), Math.max(40, content.width() - 20));
        int contentHeight = 26
                + descriptionHeight + (descriptionText == null ? 0 : 8)
                + (plugin.capabilities().isEmpty() ? 0 : 18)
                + plugin.capabilities().size() * 28
                + (plugin.capabilities().isEmpty() ? 0 : 8)
                + (plugin.runtimeState().isEmpty() ? 0 : 18)
                + plugin.runtimeState().size() * 28
                + (plugin.runtimeState().isEmpty() ? 0 : 8)
                + (plugin.visibleSettingDefinitions().isEmpty() ? 0 : 18)
                + plugin.visibleSettingDefinitions().stream().mapToInt(this::settingHeight).sum()
                + (plugin.visibleSettingDefinitions().isEmpty() ? 0 : 8)
                + plugin.runtimeActions().size() * 26
                + (plugin.runtimeActions().isEmpty() ? 0 : 8)
                + 30;
        detailScrollMaximum = Math.max(0, contentHeight - content.height());
        detailScroll = clamp(detailScroll, 0, detailScrollMaximum);
        int x = content.x() + 10;
        int width = content.width() - 20;
        int y = content.y() - detailScroll;
        TextBlockView description = null;
        if (descriptionText != null) {
            description = new TextBlockView(new UiRect(x, y, width, descriptionHeight),
                    descriptionText, MUTED);
            y += descriptionHeight + 8;
        }
        List<LineView> lines = new ArrayList<>();
        if (!plugin.capabilities().isEmpty()) y += 18;
        for (IntegrationCapability capability : plugin.capabilities()) {
            lines.add(new LineView(new UiRect(x, y, width, 27),
                    capabilityName(capability),
                    t("screen.mc_teamviewer.integration_plugin.manager.capability_meta",
                            roleText(capability.role()), sourceText(capability.implementationSource())),
                    supportStatusText(capability.status()), capabilityDiagnostic(capability),
                    capability.status() == IntegrationSupportStatus.AVAILABLE ? SUCCESS : WARNING));
            y += 28;
        }
        if (!lines.isEmpty()) y += 8;
        int runtimeLineStartIndex = lines.size();
        if (!plugin.runtimeState().isEmpty()) y += 18;
        for (PluginRuntimeState state : plugin.runtimeState()) {
            lines.add(new LineView(new UiRect(x, y, width, 27),
                    UiText.literal(state.label()), runtimeStateValue(state),
                    UiText.literal(""), UiText.literal(state.value()), SUCCESS));
            y += 28;
        }
        if (!plugin.runtimeState().isEmpty()) y += 8;
        List<SettingView> settings = new ArrayList<>();
        if (!plugin.visibleSettingDefinitions().isEmpty()) y += 18;
        for (PluginManifest.SettingDefinition definition : plugin.visibleSettingDefinitions()) {
            ConfigControlId id = ConfigControlId.setting(plugin.id(), definition.key());
            Object raw = plugin.settings().get(definition.key());
            SettingKind kind = switch (definition.type()) {
                case "boolean" -> SettingKind.BOOLEAN;
                case "enum" -> SettingKind.ENUM;
                default -> SettingKind.TEXT;
            };
            int height = settingHeight(definition);
            PluginSettingState state = plugin.settingState(definition.key());
            settings.add(new SettingView(id, new UiRect(x, y, width, height - 2),
                    settingName(plugin, definition),
                    kind == SettingKind.ENUM ? settingValue(plugin, definition, raw) : null,
                    settingTooltip(plugin, definition, state), kind,
                    textValues.getOrDefault(id, String.valueOf(raw == null ? "" : raw)),
                    256, Boolean.TRUE.equals(raw), state.enabled()));
            y += height;
        }
        if (!settings.isEmpty()) y += 8;
        List<ActionView> actions = new ArrayList<>();
        for (PluginRuntimeAction runtimeAction : plugin.runtimeActions()) {
            actions.add(action(ConfigControlId.pluginRuntimeAction(plugin.id(), runtimeAction.id()),
                    new UiRect(x, y, Math.min(220, width), 22),
                    UiText.literal(runtimeAction.label()),
                    runtimeAction.tooltip().isEmpty() ? null : UiText.literal(runtimeAction.tooltip()),
                    plugin.enabled() && runtimeAction.enabled(), false, runtimeAction.danger()));
            y += 26;
        }
        if (!plugin.runtimeActions().isEmpty()) y += 8;
        if (plugin.builtIn()) {
            actions.add(action(ConfigControlId.plugin(plugin.id(), "copy"),
                    new UiRect(x, y, Math.min(180, width), 22),
                    tr("screen.mc_teamviewer.integration_plugin.copy_custom"), null, true, false, false));
        } else {
            actions.add(action(ConfigControlId.plugin(plugin.id(), "uninstall"),
                    new UiRect(x, y, Math.min(180, width), 22),
                    tr(plugin.pendingRemoval()
                            ? "screen.mc_teamviewer.integration_plugin.uninstall_pending"
                            : "screen.mc_teamviewer.integration_plugin.uninstall"),
                    tr("screen.mc_teamviewer.integration_plugin.uninstall.tooltip"),
                    !plugin.pendingRemoval(), false, true));
        }
        long available = plugin.capabilities().stream()
                .filter(value -> value.status() == IntegrationSupportStatus.AVAILABLE).count();
        UiText summary = t("screen.mc_teamviewer.integration_plugin.manager.capability_summary",
                UiText.literal(String.valueOf(available)),
                UiText.literal(String.valueOf(plugin.capabilities().size())));
        return new DetailView(bounds, content, compactBack(bounds, compact),
                pluginName(plugin),
                t("screen.mc_teamviewer.integration_plugin.manager.subtitle",
                        UiText.literal(plugin.id()), UiText.literal(plugin.version())),
                runtimeStatusText(plugin.runtimeStatus()),
                summary,
                pluginDiagnostic(plugin), statusColor(plugin.runtimeStatus()),
                tr("screen.mc_teamviewer.integration_plugin.manager.capabilities"),
                tr("screen.mc_teamviewer.integration_plugin.manager.runtime_state"),
                plugin.runtimeState().isEmpty() ? -1 : runtimeLineStartIndex,
                tr("screen.mc_teamviewer.integration_plugin.manager.settings"),
                lines, settings, actions, false, description);
    }

    private DetailView disabledDetail(UiRect bounds, boolean compact, DisabledPluginSnapshot plugin) {
        detailScroll = 0;
        detailScrollMaximum = 0;
        UiRect content = contentBounds(bounds);
        int x = content.x() + 10;
        int lineY = content.y() + 18;
        int width = content.width() - 20;
        int gap = 5;
        int buttonWidth = Math.max(80, Math.min(140, (width - gap * 2) / 3));
        int actionY = lineY + 35;
        List<ActionView> actions = List.of(
                action(ConfigControlId.plugin(plugin.storageId(), "disabled-restore"),
                        new UiRect(x, actionY, buttonWidth, 22),
                        tr("screen.mc_teamviewer.integration_plugin.restore"), null, true, false, false),
                action(ConfigControlId.plugin(plugin.storageId(), "disabled-open-dir"),
                        new UiRect(x + buttonWidth + gap, actionY, buttonWidth, 22),
                        tr("screen.mc_teamviewer.integration_plugin.open_directory"), null, true, false, false),
                action(ConfigControlId.plugin(plugin.storageId(), "disabled-delete"),
                        new UiRect(x + (buttonWidth + gap) * 2, actionY, buttonWidth, 22),
                        tr("screen.mc_teamviewer.integration_plugin.delete"),
                        tr("screen.mc_teamviewer.integration_plugin.delete.tooltip"),
                        true, false, true));
        return new DetailView(bounds, content, compactBack(bounds, compact),
                UiText.literal(plugin.name()),
                t("screen.mc_teamviewer.integration_plugin.manager.disabled_subtitle",
                        UiText.literal(plugin.pluginId()), UiText.literal(plugin.version())),
                tr("screen.mc_teamviewer.integration_plugin.runtime.disabled"),
                tr("screen.mc_teamviewer.integration_plugin.manager.recoverable"),
                plugin.storagePath() == null ? null : UiText.literal(plugin.storagePath().toAbsolutePath().toString()),
                WARNING, tr("screen.mc_teamviewer.integration_plugin.manager.disabled_location"),
                null, -1, null,
                plugin.storagePath() == null ? List.of() : List.of(new LineView(
                        new UiRect(x, lineY, width, 27),
                        UiText.literal(plugin.storagePath().toAbsolutePath().toString()),
                        UiText.literal(plugin.originalFileName()), UiText.literal(""), null, MUTED)),
                List.of(), actions, true, null);
    }

    private DialogView buildDialog(UiRect frame) {
        if (dialogKind == null) return null;
        int width = Math.min(470, frame.width() - 34);
        int height = dialogKind == DialogKind.COPY_GUIDE ? 196 : 122;
        int x = frame.x() + (frame.width() - width) / 2;
        int y = frame.y() + (frame.height() - height) / 2;
        UiRect bounds = new UiRect(x, y, width, height);
        if (dialogKind == DialogKind.COPY_GUIDE) {
            List<UiText> lines = new ArrayList<>();
            lines.add(t("screen.mc_teamviewer.integration_plugin.copy_path",
                    UiText.literal(copiedPluginPath == null ? "-" : copiedPluginPath.toAbsolutePath().toString())));
            lines.add(tr("screen.mc_teamviewer.integration_plugin.copy_step_manifest"));
            lines.add(tr("screen.mc_teamviewer.integration_plugin.copy_step_lua"));
            lines.add(tr("screen.mc_teamviewer.integration_plugin.copy_step_provides"));
            lines.add(tr("screen.mc_teamviewer.integration_plugin.copy_step_restart"));
            lines.add(tr("screen.mc_teamviewer.integration_plugin.copy_step_readme"));
            return new DialogView(DialogKind.COPY_GUIDE, bounds,
                    tr("screen.mc_teamviewer.integration_plugin.copy_guide_title"), lines,
                    List.of(
                            action(PLUGIN_GUIDE_OPEN_DIRECTORY,
                                    new UiRect(x + 12, y + height - 32, (width - 29) / 2, 21),
                                    tr("screen.mc_teamviewer.integration_plugin.open_directory"),
                                    null, copiedPluginPath != null, false, false),
                            action(PLUGIN_DIALOG_CLOSE,
                                    new UiRect(x + 17 + (width - 29) / 2, y + height - 32,
                                            (width - 29) / 2, 21),
                                    tr("screen.mc_teamviewer.integration_plugin.return_manager"),
                                    null, true, false, false)));
        }
        if (dialogKind == DialogKind.RUNTIME_ACTION_CONFIRM) {
            PluginSnapshot plugin = pendingRuntimePluginId == null
                    ? null : control.getIntegrationPlugin(pendingRuntimePluginId);
            PluginRuntimeAction runtimeAction = plugin == null ? null : plugin.runtimeActions().stream()
                    .filter(value -> value.id().equals(pendingRuntimeActionId)).findFirst().orElse(null);
            UiText message = runtimeAction == null
                    ? tr("screen.mc_teamviewer.integration_plugin.runtime_action_unavailable")
                    : UiText.literal(runtimeAction.confirmation());
            List<ActionView> actions = new ArrayList<>();
            if (runtimeAction != null && runtimeAction.enabled()) {
                actions.add(action(PLUGIN_RUNTIME_CONFIRM,
                        new UiRect(x + 12, y + height - 32, (width - 29) / 2, 21),
                        tr("screen.mc_teamviewer.integration_plugin.runtime_action_confirm"),
                        null, true, false, runtimeAction.danger()));
            }
            actions.add(action(PLUGIN_DIALOG_CLOSE,
                    new UiRect(x + 17 + (width - 29) / 2, y + height - 32,
                            (width - 29) / 2, 21),
                    tr("screen.mc_teamviewer.config.cancel"), null, true, false, false));
            return new DialogView(DialogKind.RUNTIME_ACTION_CONFIRM, bounds,
                    tr("screen.mc_teamviewer.integration_plugin.runtime_action_confirm_title"),
                    List.of(message), actions);
        }
        DisabledPluginSnapshot disabled = pendingDeleteStorageId == null
                ? null : control.getDisabledIntegrationPlugin(pendingDeleteStorageId);
        UiText message = disabled == null
                ? tr("screen.mc_teamviewer.integration_plugin.disabled_unavailable")
                : t("screen.mc_teamviewer.integration_plugin.delete_confirm_message",
                        UiText.literal(disabled.name()));
        List<ActionView> actions = new ArrayList<>();
        if (disabled != null) {
            actions.add(action(ConfigControlId.plugin(disabled.storageId(), "disabled-delete-confirm"),
                    new UiRect(x + 12, y + height - 32, (width - 29) / 2, 21),
                    tr("screen.mc_teamviewer.integration_plugin.delete_confirm"),
                    null, true, false, true));
        }
        actions.add(action(PLUGIN_DIALOG_CLOSE,
                new UiRect(x + 17 + (width - 29) / 2, y + height - 32, (width - 29) / 2, 21),
                tr("screen.mc_teamviewer.config.cancel"), null, true, false, false));
        return new DialogView(DialogKind.DELETE_CONFIRM, bounds,
                tr("screen.mc_teamviewer.integration_plugin.delete_confirm_title"),
                List.of(message), actions);
    }

    @Override
    public ConfigUiAction activate(ConfigControlId id) {
        if (id == null) return ConfigUiAction.stay();
        if (BACK.equals(id)) {
            commitTextSettings();
            if (compactDetail) {
                compactDetail = false;
                return ConfigUiAction.stay();
            }
            return ConfigUiAction.closeToParent();
        }
        if (PLUGIN_COMPACT_BACK.equals(id)) {
            commitTextSettings();
            compactDetail = false;
            return ConfigUiAction.stay();
        }
        if (PLUGIN_TAB_INSTALLED.equals(id) || PLUGIN_TAB_DISABLED.equals(id)) {
            commitTextSettings();
            tab = PLUGIN_TAB_INSTALLED.equals(id)
                    ? PluginManagerTab.INSTALLED : PluginManagerTab.DISABLED;
            compactDetail = false;
            detailScroll = 0;
            dialogKind = null;
            syncSelection();
            return ConfigUiAction.stay();
        }
        if (PLUGIN_RESCAN.equals(id)) {
            commitTextSettings();
            control.rescanIntegrationPlugins();
            syncSelection();
            return ConfigUiAction.stay();
        }
        if (PLUGIN_DIALOG_CLOSE.equals(id)) {
            dialogKind = null;
            pendingDeleteStorageId = null;
            pendingRuntimePluginId = null;
            pendingRuntimeActionId = null;
            return ConfigUiAction.stay();
        }
        if (PLUGIN_RUNTIME_CONFIRM.equals(id)) {
            if (pendingRuntimePluginId != null && pendingRuntimeActionId != null) {
                control.invokeIntegrationPluginAction(pendingRuntimePluginId, pendingRuntimeActionId);
            }
            dialogKind = null;
            pendingRuntimePluginId = null;
            pendingRuntimeActionId = null;
            return ConfigUiAction.stay();
        }
        if (PLUGIN_GUIDE_OPEN_DIRECTORY.equals(id)) {
            if (copiedPluginPath == null || !control.openIntegrationPluginDirectory(copiedPluginPath)) {
                lastOperation = new PluginFileOperationResult(
                        PluginFileOperationResult.Code.IO_ERROR, copiedPluginPath, "Unable to open directory");
            }
            return ConfigUiAction.stay();
        }
        if (id.isPluginSetting()) return activateSetting(id);
        if (id.isPluginRuntimeAction()) return activateRuntimeAction(id);
        if (!id.isPluginAction()) return ConfigUiAction.stay();
        String action = id.pluginAction();
        if ("manager-select".equals(action)) {
            commitTextSettings();
            selectedPluginId = id.pluginId();
            compactDetail = true;
            detailScroll = 0;
            initializeTextValues(selectedPlugin());
            return ConfigUiAction.stay();
        }
        if ("manager-disabled-select".equals(action)) {
            commitTextSettings();
            selectedDisabledPluginId = id.pluginId();
            compactDetail = true;
            detailScroll = 0;
            return ConfigUiAction.stay();
        }
        if (action.startsWith("disabled-")) return activateDisabled(id, action);
        PluginSnapshot plugin = control.getIntegrationPlugin(id.pluginId());
        if (plugin == null) return ConfigUiAction.stay();
        if ("toggle".equals(action)) {
            control.setIntegrationPluginEnabled(plugin.id(), !plugin.enabled());
        } else if ("copy".equals(action)) {
            commitTextSettings();
            lastOperation = control.copyBuiltinIntegrationPluginResult(plugin.id());
            if (lastOperation.succeeded()) {
                copiedPluginPath = lastOperation.path();
                dialogKind = DialogKind.COPY_GUIDE;
            }
        } else if ("uninstall".equals(action)) {
            commitTextSettings();
            lastOperation = control.uninstallIntegrationPlugin(plugin.id());
            if (lastOperation.succeeded()) {
                selectedPluginId = null;
                compactDetail = false;
                syncSelection();
            }
        }
        return ConfigUiAction.stay();
    }

    private ConfigUiAction activateRuntimeAction(ConfigControlId id) {
        PluginSnapshot plugin = control.getIntegrationPlugin(id.pluginId());
        PluginRuntimeAction action = plugin == null ? null : plugin.runtimeActions().stream()
                .filter(value -> value.id().equals(id.pluginRuntimeActionId())).findFirst().orElse(null);
        if (action == null || !plugin.enabled() || !action.enabled()) return ConfigUiAction.stay();
        if (action.requiresConfirmation()) {
            pendingRuntimePluginId = plugin.id();
            pendingRuntimeActionId = action.id();
            dialogKind = DialogKind.RUNTIME_ACTION_CONFIRM;
        } else {
            control.invokeIntegrationPluginAction(plugin.id(), action.id());
        }
        return ConfigUiAction.stay();
    }

    private ConfigUiAction activateDisabled(ConfigControlId id, String action) {
        if ("disabled-restore".equals(action)) {
            lastOperation = control.restoreIntegrationPlugin(id.pluginId());
            if (lastOperation.succeeded()) {
                selectedDisabledPluginId = null;
                compactDetail = false;
                syncSelection();
            }
        } else if ("disabled-open-dir".equals(action)) {
            DisabledPluginSnapshot disabled = control.getDisabledIntegrationPlugin(id.pluginId());
            if (disabled == null || !control.openIntegrationPluginDirectory(disabled.storagePath())) {
                lastOperation = new PluginFileOperationResult(
                        PluginFileOperationResult.Code.IO_ERROR,
                        disabled == null ? null : disabled.storagePath(), "Unable to open directory");
            }
        } else if ("disabled-delete".equals(action)) {
            pendingDeleteStorageId = id.pluginId();
            dialogKind = DialogKind.DELETE_CONFIRM;
        } else if ("disabled-delete-confirm".equals(action)) {
            lastOperation = control.deleteDisabledIntegrationPlugin(id.pluginId());
            dialogKind = null;
            pendingDeleteStorageId = null;
            if (lastOperation.succeeded()) {
                selectedDisabledPluginId = null;
                compactDetail = false;
                syncSelection();
            }
        }
        return ConfigUiAction.stay();
    }

    private ConfigUiAction activateSetting(ConfigControlId id) {
        PluginSnapshot plugin = control.getIntegrationPlugin(id.pluginId());
        if (plugin == null) return ConfigUiAction.stay();
        PluginManifest.SettingDefinition definition = plugin.settingDefinitions().stream()
                .filter(value -> value.key().equals(id.settingKey())).findFirst().orElse(null);
        if (definition == null || !settingInteractive(plugin, definition.key())) return ConfigUiAction.stay();
        if ("boolean".equals(definition.type())) {
            boolean current = Boolean.TRUE.equals(plugin.settings().get(definition.key()));
            control.setIntegrationPluginSetting(plugin.id(), definition.key(), !current);
        } else if ("enum".equals(definition.type()) && !definition.options().isEmpty()) {
            String current = String.valueOf(plugin.settings().get(definition.key()));
            int index = definition.options().indexOf(current);
            String next = definition.options().get((index + 1 + definition.options().size())
                    % definition.options().size());
            control.setIntegrationPluginSetting(plugin.id(), definition.key(), next);
        }
        return ConfigUiAction.stay();
    }

    @Override
    public void setText(ConfigControlId id, String value) {
        if (id != null && id.isPluginSetting() && value != null) textValues.put(id, value);
    }

    @Override
    public void scrollList(int rows) {
        setCurrentListScroll(Math.max(0, currentListScroll() + rows));
    }

    @Override
    public void scrollDetail(int pixels) {
        detailScroll = clamp(detailScroll + pixels, 0, detailScrollMaximum);
    }

    @Override
    public void moveSelection(int rows) {
        if (rows == 0) return;
        commitTextSettings();
        if (tab == PluginManagerTab.INSTALLED) {
            List<PluginSnapshot> plugins = control.getIntegrationPlugins();
            if (plugins.isEmpty()) return;
            int index = indexOfPlugin(plugins, selectedPluginId);
            index = clamp(index + rows, 0, plugins.size() - 1);
            selectedPluginId = plugins.get(index).id();
            initializeTextValues(plugins.get(index));
            keepSelectionVisible(index);
        } else {
            List<DisabledPluginSnapshot> plugins = control.getDisabledIntegrationPlugins();
            if (plugins.isEmpty()) return;
            int index = indexOfDisabled(plugins, selectedDisabledPluginId);
            index = clamp(index + rows, 0, plugins.size() - 1);
            selectedDisabledPluginId = plugins.get(index).storageId();
            keepSelectionVisible(index);
        }
        detailScroll = 0;
    }

    @Override
    public void commitTextSettings() {
        PluginSnapshot plugin = selectedPlugin();
        if (plugin == null) return;
        for (PluginManifest.SettingDefinition definition : plugin.visibleSettingDefinitions()) {
            if ("boolean".equals(definition.type()) || "enum".equals(definition.type())) continue;
            if (!plugin.settingState(definition.key()).enabled()) continue;
            ConfigControlId id = ConfigControlId.setting(plugin.id(), definition.key());
            if (textValues.containsKey(id)) {
                control.setIntegrationPluginSetting(plugin.id(), definition.key(), textValues.get(id));
            }
        }
    }

    private void syncSelection() {
        List<PluginSnapshot> plugins = control.getIntegrationPlugins();
        if (indexOfPlugin(plugins, selectedPluginId) < 0) {
            String nextPluginId = plugins.isEmpty() ? null : plugins.get(0).id();
            if (!Objects.equals(selectedPluginId, nextPluginId)) {
                selectedPluginId = nextPluginId;
                installedListScroll = 0;
                if (tab == PluginManagerTab.INSTALLED) detailScroll = 0;
                initializeTextValues(selectedPlugin());
            }
        }
        List<DisabledPluginSnapshot> disabled = control.getDisabledIntegrationPlugins();
        if (indexOfDisabled(disabled, selectedDisabledPluginId) < 0) {
            String nextDisabledPluginId = disabled.isEmpty() ? null : disabled.get(0).storageId();
            if (!Objects.equals(selectedDisabledPluginId, nextDisabledPluginId)) {
                selectedDisabledPluginId = nextDisabledPluginId;
                disabledListScroll = 0;
                if (tab == PluginManagerTab.DISABLED) detailScroll = 0;
            }
        }
    }

    private void keepSelectionVisible(int index) {
        int listScroll = currentListScroll();
        if (index < listScroll) listScroll = index;
        else if (index >= listScroll + visibleRows) listScroll = index - visibleRows + 1;
        setCurrentListScroll(listScroll);
    }

    private int currentListScroll() {
        return tab == PluginManagerTab.INSTALLED ? installedListScroll : disabledListScroll;
    }

    private void setCurrentListScroll(int value) {
        if (tab == PluginManagerTab.INSTALLED) installedListScroll = value;
        else disabledListScroll = value;
    }

    private static boolean pluginToggleActive(PluginSnapshot plugin) {
        if (plugin.pendingRemoval()) return false;
        return switch (plugin.runtimeStatus()) {
            case ACTIVE, DISABLED -> true;
            case PENDING_RESTART, INCOMPATIBLE, LOAD_FAILED, SUSPENDED -> false;
        };
    }

    private static UiText pluginListTooltip(PluginSnapshot plugin) {
        UiText diagnostic = pluginDiagnostic(plugin);
        UiText reason = diagnostic;
        if (plugin.pendingRemoval()) {
            reason = tr("screen.mc_teamviewer.integration_plugin.uninstall_pending");
        } else if (reason == null && !pluginToggleActive(plugin)) {
            reason = runtimeStatusText(plugin.runtimeStatus());
        }
        return reason == null
                ? t("screen.mc_teamviewer.integration_plugin.manager.item_tooltip",
                        pluginName(plugin), UiText.literal(plugin.id()), UiText.literal(plugin.version()))
                : t("screen.mc_teamviewer.integration_plugin.manager.item_tooltip_diagnostic",
                        pluginName(plugin), UiText.literal(plugin.id()), UiText.literal(plugin.version()), reason);
    }

    private static UiText disabledPluginTooltip(DisabledPluginSnapshot plugin) {
        return t("screen.mc_teamviewer.integration_plugin.manager.disabled_item_tooltip",
                UiText.literal(plugin.name()), UiText.literal(plugin.version()),
                UiText.literal(plugin.originalFileName()),
                UiText.literal(plugin.storagePath() == null
                        ? "-" : plugin.storagePath().toAbsolutePath().toString()));
    }

    private PluginSnapshot selectedPlugin() {
        return selectedPluginId == null ? null : control.getIntegrationPlugin(selectedPluginId);
    }

    private DisabledPluginSnapshot selectedDisabled() {
        return selectedDisabledPluginId == null
                ? null : control.getDisabledIntegrationPlugin(selectedDisabledPluginId);
    }

    private void initializeTextValues(PluginSnapshot plugin) {
        if (plugin == null) return;
        for (PluginManifest.SettingDefinition definition : plugin.visibleSettingDefinitions()) {
            if ("boolean".equals(definition.type()) || "enum".equals(definition.type())) continue;
            ConfigControlId id = ConfigControlId.setting(plugin.id(), definition.key());
            textValues.put(id, String.valueOf(plugin.settings().getOrDefault(definition.key(), "")));
        }
    }

    private UiRect contentBounds(UiRect detail) {
        return new UiRect(detail.x(), detail.y() + 68, detail.width(),
                Math.max(0, detail.height() - 68));
    }

    private ActionView compactBack(UiRect detail, boolean compact) {
        return compact ? action(PLUGIN_COMPACT_BACK,
                new UiRect(detail.x() + 8, detail.y() + 8, 58, 20),
                tr("screen.mc_teamviewer.integration_plugin.manager.back_to_list"),
                null, true, false, false) : null;
    }

    private int settingHeight(PluginManifest.SettingDefinition definition) {
        return "boolean".equals(definition.type()) || "enum".equals(definition.type()) ? 28 : 34;
    }

    private static int estimatedWrappedHeight(String source, int width) {
        int codePoints = Math.max(1, source == null ? 0 : source.codePointCount(0, source.length()));
        int charactersPerLine = Math.max(8, width / 7);
        int lines = Math.max(1, Math.min(12,
                (codePoints + charactersPerLine - 1) / charactersPerLine));
        return lines * 11;
    }

    private static ActionView action(ConfigControlId id, UiRect bounds, UiText label, UiText tooltip,
                                     boolean active, boolean selected, boolean danger) {
        return new ActionView(id, bounds, label, tooltip, active, selected, danger);
    }

    private static int indexOfPlugin(List<PluginSnapshot> plugins, String id) {
        if (id == null) return -1;
        for (int i = 0; i < plugins.size(); i++) if (id.equals(plugins.get(i).id())) return i;
        return -1;
    }

    private static int indexOfDisabled(List<DisabledPluginSnapshot> plugins, String id) {
        if (id == null) return -1;
        for (int i = 0; i < plugins.size(); i++) if (id.equals(plugins.get(i).storageId())) return i;
        return -1;
    }

    private static int statusColor(PluginRuntimeStatus status) {
        return switch (status) {
            case ACTIVE -> SUCCESS;
            case DISABLED -> MUTED;
            case PENDING_RESTART -> WARNING;
            default -> ERROR;
        };
    }

    private static UiText pluginName(PluginSnapshot plugin) {
        return switch (plugin.id()) {
            case IntegrationIds.PLUGIN_NODEMC -> tr("screen.mc_teamviewer.integration_plugin.builtin.nodemc");
            case IntegrationIds.PLUGIN_SIMMC -> tr("screen.mc_teamviewer.integration_plugin.builtin.simmc");
            case IntegrationIds.PLUGIN_XAERO -> tr("screen.mc_teamviewer.integration_plugin.builtin.xaero");
            case IntegrationIds.PLUGIN_JOURNEYMAP -> tr("screen.mc_teamviewer.integration_plugin.builtin.journeymap");
            case IntegrationIds.PLUGIN_EXAMPLE -> tr("screen.mc_teamviewer.integration_plugin.builtin.example");
            case IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS -> tr("screen.mc_teamviewer.integration_plugin.builtin.tab_label_relations");
            default -> UiText.literal(plugin.name());
        };
    }

    private static UiText pluginDescription(PluginSnapshot plugin) {
        if (IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS.equals(plugin.id())) {
            return tr("screen.mc_teamviewer.integration_plugin.tab_label_relations.description");
        }
        return UiText.literal(plugin.description());
    }

    private static UiText capabilityName(IntegrationCapability capability) {
        return switch (capability.id()) {
            case IntegrationIds.JOURNEYMAP_PLAYERS -> tr("screen.mc_teamviewer.integration_plugin.capability.journeymap_players");
            case IntegrationIds.JOURNEYMAP_BEACONS -> tr("screen.mc_teamviewer.integration_plugin.capability.journeymap_beacons");
            case IntegrationIds.JOURNEYMAP_WAYPOINTS -> tr("screen.mc_teamviewer.integration_plugin.capability.journeymap_waypoints");
            case IntegrationIds.XAERO_WORLDMAP -> tr("screen.mc_teamviewer.integration_plugin.capability.xaero_worldmap");
            case IntegrationIds.XAERO_MINIMAP -> tr("screen.mc_teamviewer.integration_plugin.capability.xaero_minimap");
            case IntegrationIds.NODEMC_BATTLE_MAP -> tr("screen.mc_teamviewer.integration_plugin.capability.nodemc_battle_map");
            case IntegrationIds.SIMMC_BATTLE_MAP -> tr("screen.mc_teamviewer.integration_plugin.capability.simmc_battle_map");
            case IntegrationIds.EXAMPLE_REMOTE_PLAYER -> tr("screen.mc_teamviewer.integration_plugin.capability.example_remote_player");
            case IntegrationIds.EXAMPLE_SHARED_WAYPOINT -> tr("screen.mc_teamviewer.integration_plugin.capability.example_shared_waypoint");
            case IntegrationIds.EXAMPLE_BATTLE_MAP -> tr("screen.mc_teamviewer.integration_plugin.capability.example_battle_map");
            case IntegrationIds.TAB_LABEL_RELATIONS -> tr("screen.mc_teamviewer.integration_plugin.capability.tab_label_relations");
            default -> UiText.literal(capability.displayName());
        };
    }

    private static UiText settingName(PluginSnapshot plugin, PluginManifest.SettingDefinition setting) {
        if (IntegrationIds.PLUGIN_JOURNEYMAP.equals(plugin.id())) {
            return tr("screen.mc_teamviewer.integration_plugin.setting.journeymap_" + setting.key());
        }
        if (IntegrationIds.PLUGIN_XAERO.equals(plugin.id())) {
            return switch (setting.key()) {
                case "show_online_world_map", "show_offline_world_map", "show_offline_minimap" ->
                        tr("screen.mc_teamviewer.integration_plugin.setting.xaero_" + setting.key());
                default -> UiText.literal(setting.name());
            };
        }
        if (IntegrationIds.PLUGIN_EXAMPLE.equals(plugin.id())) {
            return tr("screen.mc_teamviewer.integration_plugin.setting.example_" + setting.key());
        }
        if (IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS.equals(plugin.id())) {
            return tr("screen.mc_teamviewer.integration_plugin.setting.tab_label_" + setting.key());
        }
        return UiText.literal(setting.name());
    }

    private static UiText settingValue(
            PluginSnapshot plugin, PluginManifest.SettingDefinition setting, Object value) {
        if (IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS.equals(plugin.id())
                && "relation_source_mode".equals(setting.key())) {
            return tr("screen.mc_teamviewer.integration_plugin.setting.tab_label_relation_source_mode."
                    + String.valueOf(value));
        }
        return UiText.literal(String.valueOf(value));
    }

    private static UiText runtimeStatusText(PluginRuntimeStatus status) {
        return tr("screen.mc_teamviewer.integration_plugin.runtime."
                + status.name().toLowerCase(Locale.ROOT));
    }

    private UiText runtimeStateValue(PluginRuntimeState state) {
        return PluginRuntimeStateText.value(state, wallClock.getAsLong());
    }

    private static boolean settingInteractive(PluginSnapshot plugin, String key) {
        if (plugin == null) return false;
        PluginSettingState state = plugin.settingState(key);
        return state.visible() && state.enabled();
    }

    private static UiText settingTooltip(
            PluginSnapshot plugin, PluginManifest.SettingDefinition setting, PluginSettingState state) {
        if (IntegrationIds.PLUGIN_JOURNEYMAP.equals(plugin.id())
                && ("show_remote_players".equals(setting.key()) || "show_last_seen_players".equals(setting.key()))) {
            return tr("screen.mc_teamviewer.integration_plugin.setting.journeymap_show_remote_players.tooltip");
        }
        return state.detail().isBlank() ? null : UiText.literal(state.detail());
    }

    private static UiText supportStatusText(IntegrationSupportStatus status) {
        return tr("screen.mc_teamviewer.integration_plugin.support."
                + status.name().toLowerCase(Locale.ROOT));
    }

    private static UiText sourceText(IntegrationImplementationSource source) {
        return tr("screen.mc_teamviewer.integration_plugin.source."
                + source.name().toLowerCase(Locale.ROOT));
    }

    private static UiText roleText(String role) {
        return tr("screen.mc_teamviewer.integration_plugin.role." + role.replace('-', '_'));
    }

    private static UiText pluginDiagnostic(PluginSnapshot plugin) {
        return plugin.detail().isBlank() ? null : t(
                "screen.mc_teamviewer.integration_plugin.technical_detail",
                runtimeStatusText(plugin.runtimeStatus()), UiText.literal(plugin.detail()));
    }

    private static UiText capabilityDiagnostic(IntegrationCapability capability) {
        return capability.detail().isBlank() ? null : t(
                "screen.mc_teamviewer.integration_plugin.technical_detail",
                supportStatusText(capability.status()), UiText.literal(capability.detail()));
    }

    private static UiText operationResultText(PluginFileOperationResult result) {
        String suffix = switch (result.code()) {
            case SUCCESS -> "success";
            case NOT_FOUND -> "not_found";
            case BUILTIN_READ_ONLY -> "builtin_read_only";
            case INVALID_SOURCE -> "invalid_source";
            case TARGET_EXISTS -> "target_exists";
            case INVALID_DISABLED_ENTRY -> "invalid_disabled_entry";
            case IO_ERROR -> "io_error";
        };
        return tr("screen.mc_teamviewer.integration_plugin.operation." + suffix);
    }

    private static UiText operationDetail(PluginFileOperationResult result) {
        if (result.detail().isBlank() && result.path() == null) return null;
        return t("screen.mc_teamviewer.integration_plugin.operation_detail",
                UiText.literal(result.path() == null ? "-" : result.path().toAbsolutePath().toString()),
                UiText.literal(result.detail().isBlank() ? "-" : result.detail()));
    }

    private static UiText tr(String key) {
        return UiText.translatable(key);
    }

    private static UiText t(String key, UiText... arguments) {
        return UiText.translatable(key, arguments);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
