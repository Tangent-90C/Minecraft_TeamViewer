package fun.prof_chen.teamviewer.main_code.plugin;

import java.nio.file.Path;

/** Machine-readable result for plugin package filesystem operations. */
public record PluginFileOperationResult(Code code, Path path, String detail) {
    public enum Code {
        SUCCESS,
        NOT_FOUND,
        BUILTIN_READ_ONLY,
        INVALID_SOURCE,
        TARGET_EXISTS,
        INVALID_DISABLED_ENTRY,
        IO_ERROR
    }

    public PluginFileOperationResult {
        detail = detail == null ? "" : detail;
    }

    public boolean succeeded() {
        return code == Code.SUCCESS;
    }

    public static PluginFileOperationResult success(Path path) {
        return new PluginFileOperationResult(Code.SUCCESS, path, "");
    }

    public static PluginFileOperationResult failure(Code code, Path path, Throwable error) {
        return new PluginFileOperationResult(code, path,
                error == null ? "" : error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
    }
}
