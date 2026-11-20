package com.shiyuan.chat.springai;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * A JDK {@link HttpClient} that remembers the status of the last response to a POST: every MCP
 * message is a POST, while the SDK's GET stream and closing DELETE are housekeeping whose replies
 * (often 405) must not hide the status that mattered.
 *
 * <p>MCP Java SDK 0.18.3 reports a refused request as "Failed to send message: DummyEvent[...]"
 * without the HTTP status, so a 401 (the member must sign in again) can't be told apart from a
 * server fault. The transport accepts a custom {@link HttpClient.Builder}; this client is what
 * that builder builds. One instance per question, so "last" is unambiguous.
 */
final class StatusRecordingHttpClient extends HttpClient {

    private final HttpClient delegate;
    private final AtomicInteger lastStatus = new AtomicInteger();

    StatusRecordingHttpClient(HttpClient delegate) {
        this.delegate = delegate;
    }

    /** Forget the last status, so the next read reflects only the request made after this. */
    void reset() {
        lastStatus.set(0);
    }

    /** The last POST response status, or 0 before any (or since {@link #reset()}). */
    int lastStatus() {
        return lastStatus.get();
    }

    private <T> HttpResponse.BodyHandler<T> recording(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        if (!"POST".equals(request.method())) {
            return handler;
        }
        return info -> {
            lastStatus.set(info.statusCode());
            return handler.apply(info);
        };
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        return delegate.send(request, recording(request, handler));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        return delegate.sendAsync(request, recording(request, handler));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                                            HttpResponse.PushPromiseHandler<T> push) {
        return delegate.sendAsync(request, recording(request, handler), push);
    }

    @Override public Optional<CookieHandler> cookieHandler() { return delegate.cookieHandler(); }
    @Override public Optional<Duration> connectTimeout() { return delegate.connectTimeout(); }
    @Override public Redirect followRedirects() { return delegate.followRedirects(); }
    @Override public Optional<ProxySelector> proxy() { return delegate.proxy(); }
    @Override public SSLContext sslContext() { return delegate.sslContext(); }
    @Override public SSLParameters sslParameters() { return delegate.sslParameters(); }
    @Override public Optional<Authenticator> authenticator() { return delegate.authenticator(); }
    @Override public Version version() { return delegate.version(); }
    @Override public Optional<Executor> executor() { return delegate.executor(); }

    /** The builder handed to the SDK transport: builds a JDK client, wrapped. */
    static final class Builder implements HttpClient.Builder {
        private final HttpClient.Builder real = HttpClient.newBuilder();
        private StatusRecordingHttpClient built;

        StatusRecordingHttpClient built() {
            return built;
        }

        @Override public Builder cookieHandler(CookieHandler h) { real.cookieHandler(h); return this; }
        @Override public Builder connectTimeout(Duration d) { real.connectTimeout(d); return this; }
        @Override public Builder sslContext(SSLContext c) { real.sslContext(c); return this; }
        @Override public Builder sslParameters(SSLParameters p) { real.sslParameters(p); return this; }
        @Override public Builder executor(Executor e) { real.executor(e); return this; }
        @Override public Builder followRedirects(Redirect r) { real.followRedirects(r); return this; }
        @Override public Builder version(Version v) { real.version(v); return this; }
        @Override public Builder priority(int p) { real.priority(p); return this; }
        @Override public Builder proxy(ProxySelector p) { real.proxy(p); return this; }
        @Override public Builder authenticator(Authenticator a) { real.authenticator(a); return this; }

        @Override
        public synchronized HttpClient build() {
            built = new StatusRecordingHttpClient(real.build());
            return built;
        }
    }
}
