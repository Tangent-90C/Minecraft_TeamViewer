package fun.prof_chen.teamviewer.main_code.network.transport;

import fun.prof_chen.teamviewer.main_code.network.abstraction.SocketProcess;
import fun.prof_chen.teamviewer.main_code.network.abstraction.TransportProcess;
import fun.prof_chen.teamviewer.main_code.network.abstraction.TransportListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.net.Proxy;
import java.net.ProxySelector;

public final class OkHttpTransportProcess implements TransportProcess {
    @Override
    public SocketProcess connect(String uri, boolean useSystemProxy, TransportListener listener) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (useSystemProxy) {
            builder.proxySelector(ProxySelector.getDefault());
        } else {
            builder.proxy(Proxy.NO_PROXY);
        }

        OkHttpClient client = builder.build();
        Request request = new Request.Builder().url(uri).build();
        WebSocket webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                String negotiatedExtensions = response == null ? "" : response.header("Sec-WebSocket-Extensions", "");
                listener.onOpen(negotiatedExtensions == null ? "" : negotiatedExtensions);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                listener.onTextMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                if (bytes != null) {
                    listener.onBinaryMessage(bytes.toByteArray());
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                listener.onClosed(code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                listener.onFailure(t);
            }
        });

        return new SocketProcess() {
            @Override
            public void send(byte[] payload) {
                if (payload == null) {
                    return;
                }
                webSocket.send(ByteString.of(payload, 0, payload.length));
            }

            @Override
            public void close(int statusCode, String reason) {
                webSocket.close(statusCode, reason);
            }
        };
    }
}