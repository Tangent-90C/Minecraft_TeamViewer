package fun.prof_chen.teamviewer.neoforge.aio;

import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterBundle;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterFactory;

/** The only ServiceLoader provider visible to the shared client bootstrap. */
public final class NeoForgeAioFactory implements ClientAdapterFactory<Object, Object> {
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ClientAdapterBundle<Object, Object> create() {
        ClientAdapterFactory delegate = NeoForgeAioSelector.newFactory();
        return (ClientAdapterBundle<Object, Object>) delegate.create();
    }
}
