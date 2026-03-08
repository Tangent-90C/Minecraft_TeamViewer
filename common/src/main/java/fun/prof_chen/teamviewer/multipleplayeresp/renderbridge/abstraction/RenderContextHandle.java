package fun.prof_chen.teamviewer.multipleplayeresp.renderbridge.abstraction;

import java.util.Objects;

public record RenderContextHandle(Object nativeContext) {
	public static RenderContextHandle of(Object nativeContext) {
		return new RenderContextHandle(Objects.requireNonNull(nativeContext, "nativeContext"));
	}

	public <T> T requireNativeContext(Class<T> contextType) {
		Objects.requireNonNull(contextType, "contextType");
		if (!contextType.isInstance(nativeContext)) {
			throw new IllegalStateException("Expected render context of type " + contextType.getName() + " but got " + nativeContext.getClass().getName());
		}
		return contextType.cast(nativeContext);
	}
}