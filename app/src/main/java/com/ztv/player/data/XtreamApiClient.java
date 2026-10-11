package com.ztv.player.data;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLException;

public final class XtreamApiClient {
    private static final int MAX_GET_ATTEMPTS = 3;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 20000;
    // Public API calls run on `executor`; the two catalog HTTP requests of each
    // fetchLibrary fan out to `httpExecutor`. Keeping them on separate pools means a
    // burst of fetchLibrary calls (home hub warms LIVE+MOVIE+SERIES at once) can never
    // starve its own sub-requests, and all six requests really run in parallel.
    private final ExecutorService executor = Executors.newFixedThreadPool(6, backgroundThreads("ztv-api"));
    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(6, backgroundThreads("ztv-http"));
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HostProvider hostProvider;

    public XtreamApiClient() {
        this(null);
    }

    public XtreamApiClient(HostProvider hostProvider) {
        this.hostProvider = hostProvider;
    }

    private static XtreamUrlFactory urlFactory(XtreamCredentials credentials) {
        return new XtreamUrlFactory(credentials.host());
    }

    public void authenticate(XtreamCredentials credentials, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                postSuccess(callback, withHostFallback(credentials, candidate -> {
                    XtreamUrlFactory urlFactory = urlFactory(candidate);
                    String response = get(urlFactory.authUrl(candidate.username(), candidate.password()));
                    return XtreamAuthParser.isAuthenticated(response);
                }));
            } catch (Exception exception) {
                postError(callback, exception);
            }
        });
    }

    /** Signs in against exactly {@code credentials.host()} -- the address typed on the sign-in
     *  screen -- without consulting the saved host list. */
    public void authenticateAt(XtreamCredentials credentials, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                String response = get(urlFactory(credentials).authUrl(credentials.username(), credentials.password()));
                postSuccess(callback, XtreamAuthParser.isAuthenticated(response));
            } catch (Exception exception) {
                postError(callback, exception);
            }
        });
    }

    /** True when the failure means the server itself could not be reached (vs. a bad answer). */
    public static boolean isServerUnreachable(Exception exception) {
        return isHostUnavailable(exception);
    }

    public void fetchAccountInfo(XtreamCredentials credentials, Callback<XtreamAccountInfo> callback) {
        executor.execute(() -> {
            try {
                postSuccess(callback, withHostFallback(credentials, candidate -> {
                    XtreamUrlFactory urlFactory = urlFactory(candidate);
                    String response = get(urlFactory.authUrl(candidate.username(), candidate.password()));
                    return XtreamAuthParser.parseAccountInfo(response);
                }));
            } catch (Exception exception) {
                postError(callback, exception);
            }
        });
    }

    public void fetchLibrary(XtreamCredentials credentials, XtreamContentType type, Callback<List<XtreamMediaSection>> callback) {
        executor.execute(() -> {
            try {
                postSuccess(callback, withHostFallback(credentials,
                        candidate -> fetchLibraryFromHost(candidate, type)));
            } catch (Exception exception) {
                postError(callback, exception);
            }
        });
    }

    public void fetchMovieInfo(XtreamCredentials credentials, int vodId, Callback<XtreamMovieInfo> callback) {
        executor.execute(() -> {
            try {
                postSuccess(callback, withHostFallback(credentials, candidate -> {
                    XtreamUrlFactory urlFactory = urlFactory(candidate);
                    String response = get(urlFactory.vodInfoUrl(candidate.username(), candidate.password(), vodId));
                    return XtreamMediaParser.parseMovieInfo(response);
                }));
            } catch (Exception exception) {
                postError(callback, exception);
            }
        });
    }

    public void fetchShortEpg(XtreamCredentials credentials, int streamId, Callback<List<XtreamEpgProgram>> callback) {
        executor.execute(() -> {
            try {
                postSuccess(callback, withHostFallback(credentials, candidate -> {
                    XtreamUrlFactory urlFactory = urlFactory(candidate);
                    String response = get(urlFactory.shortEpgUrl(candidate.username(), candidate.password(), streamId, 4));
                    return XtreamEpgParser.parseShortEpg(response);
                }));
            } catch (Exception exception) {
                postError(callback, exception);
            }
        });
    }

    /** One `get_series_info` round trip -> series metadata + every episode. */
    public void fetchSeriesDetail(XtreamCredentials credentials, int seriesId, Callback<XtreamSeriesDetail> callback) {
        executor.execute(() -> {
            try {
                postSuccess(callback, withHostFallback(credentials, candidate -> {
                    XtreamUrlFactory urlFactory = urlFactory(candidate);
                    String response = get(urlFactory.seriesInfoUrl(candidate.username(), candidate.password(), seriesId));
                    return new XtreamSeriesDetail(
                            XtreamMediaParser.parseSeriesInfo(response),
                            XtreamMediaParser.parseSeriesEpisodes(response));
                }));
            } catch (Exception exception) {
                postError(callback, exception);
            }
        });
    }

    public void fetchSeriesEpisodes(XtreamCredentials credentials, int seriesId, Callback<List<XtreamMediaItem>> callback) {
        executor.execute(() -> {
            try {
                postSuccess(callback, withHostFallback(credentials, candidate -> {
                    XtreamUrlFactory urlFactory = urlFactory(candidate);
                    String response = get(urlFactory.seriesInfoUrl(candidate.username(), candidate.password(), seriesId));
                    return XtreamMediaParser.parseSeriesEpisodes(response);
                }));
            } catch (Exception exception) {
                postError(callback, exception);
            }
        });
    }

    private List<XtreamMediaSection> fetchLibraryFromHost(
            XtreamCredentials credentials,
            XtreamContentType type
    ) throws Exception {
        Future<List<XtreamCategory>> categoriesFuture = null;
        Future<List<XtreamMediaItem>> itemsFuture = null;
        try {
            XtreamUrlFactory urlFactory = urlFactory(credentials);
            categoriesFuture = httpExecutor.submit(() -> getParsed(
                    urlFactory.playerApiUrl(credentials.username(), credentials.password(), categoriesAction(type)),
                    XtreamMediaParser::parseCategories));
            itemsFuture = httpExecutor.submit(() -> getParsed(
                    urlFactory.playerApiUrl(credentials.username(), credentials.password(), itemsAction(type)),
                    reader -> parseItems(type, reader)));
            List<XtreamMediaItem> items = itemsFuture.get();
            List<XtreamCategory> categories = categoriesFuture.get();
            return XtreamMediaParser.buildSections(categories, items);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            throw cause instanceof Exception ? (Exception) cause : exception;
        } finally {
            cancel(categoriesFuture);
            cancel(itemsFuture);
        }
    }

    private <T> T withHostFallback(
            XtreamCredentials credentials,
            HostRequest<T> request
    ) throws Exception {
        List<String> hosts = hostProvider == null
                ? Collections.singletonList(credentials.host())
                : hostProvider.orderedHosts();
        if (hosts.isEmpty()) {
            throw new IOException("No owner-managed server is configured");
        }

        Exception lastFailure = null;
        for (String host : hosts) {
            try {
                T result = request.execute(credentials.withHost(host));
                if (hostProvider != null) {
                    hostProvider.markHealthy(host);
                }
                return result;
            } catch (Exception exception) {
                if (!isHostUnavailable(exception)) {
                    throw exception;
                }
                lastFailure = exception;
            }
        }
        throw lastFailure == null ? new IOException("All owner-managed servers failed") : lastFailure;
    }

    public void shutdown() {
        executor.shutdownNow();
        httpExecutor.shutdownNow();
    }

    private static void cancel(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    /** Catalog downloads parse megabytes on few-core TV boxes; keep them off the UI's way. */
    private static java.util.concurrent.ThreadFactory backgroundThreads(String name) {
        return runnable -> new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            runnable.run();
        }, name);
    }

    private static String get(String url) throws IOException {
        return getParsed(url, XtreamApiClient::readAll);
    }

    private static <T> T getParsed(String url, BodyParser<T> parser) throws IOException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_GET_ATTEMPTS; attempt++) {
            try {
                return getParsedOnce(url, parser);
            } catch (IOException exception) {
                lastException = exception;
                if (attempt == MAX_GET_ATTEMPTS || !isRetryable(exception)) {
                    throw exception;
                }
                sleepBeforeRetry(attempt);
            }
        }
        throw lastException == null ? new IOException("Request failed") : lastException;
    }

    private static <T> T getParsedOnce(String url, BodyParser<T> parser) throws IOException {
        long startedAt = android.os.SystemClock.elapsedRealtime();
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "ZTV/0.1 Android");
        // Explicit gzip shrinks the multi-megabyte catalog payloads ~85-90% on the wire.
        // Setting the header manually disables the platform's transparent decoding, so
        // the body is un-gzipped here based on the response's Content-Encoding.
        connection.setRequestProperty("Accept-Encoding", "gzip");
        int code = connection.getResponseCode();
        long firstByteAt = android.os.SystemClock.elapsedRealtime();
        if (code < 200 || code >= 300) {
            // Fully draining + closing the body returns the socket to the keep-alive
            // pool; disconnect() would kill it and force a new TCP handshake next call.
            drain(bodyStream(connection.getErrorStream(), connection));
            throw new HttpStatusException(code);
        }
        String encoding = connection.getContentEncoding();
        InputStream raw = connection.getInputStream();
        CountingInputStream wire = new CountingInputStream(new BufferedInputStream(raw, 64 * 1024));
        InputStream decoded = "gzip".equalsIgnoreCase(encoding)
                ? new GZIPInputStream(wire, 32 * 1024)
                : wire;
        CountingInputStream payload = new CountingInputStream(decoded);
        Reader body = new InputStreamReader(payload, StandardCharsets.UTF_8);
        try {
            T result = parser.parse(body);
            drainReader(body);
            logRequest(url, encoding, firstByteAt - startedAt,
                    android.os.SystemClock.elapsedRealtime() - firstByteAt, wire.count, payload.count);
            return result;
        } finally {
            closeQuietly(body);
        }
    }

    /** Debug-only timing; logs the action name, never the credential-bearing URL. */
    private static void logRequest(String url, String encoding, long headerMs, long bodyMs, long wireBytes, long payloadBytes) {
        if (!com.ztv.player.BuildConfig.DEBUG) {
            return;
        }
        int actionIndex = url.indexOf("action=");
        String action = actionIndex < 0 ? "auth" : url.substring(actionIndex + 7).split("&")[0];
        android.util.Log.d("ZTV", "perf http action=" + action
                + " encoding=" + (encoding == null ? "identity" : encoding)
                + " headerMs=" + headerMs + " bodyMs=" + bodyMs
                + " wireKB=" + (wireBytes / 1024) + " payloadKB=" + (payloadBytes / 1024));
    }

    private static final class CountingInputStream extends java.io.FilterInputStream {
        private long count;

        private CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }
    }

    private static InputStream bodyStream(InputStream stream, HttpURLConnection connection) throws IOException {
        if (stream == null) {
            return new java.io.ByteArrayInputStream(new byte[0]);
        }
        InputStream buffered = new BufferedInputStream(stream, 64 * 1024);
        if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
            return new GZIPInputStream(buffered, 32 * 1024);
        }
        return buffered;
    }

    private static void drain(InputStream stream) {
        byte[] buffer = new byte[4096];
        try {
            while (stream.read(buffer) != -1) {
                // discard
            }
        } catch (IOException ignored) {
            // Error bodies are best-effort; the socket just won't be reused.
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void drainReader(Reader reader) {
        char[] buffer = new char[2048];
        try {
            while (reader.read(buffer) != -1) {
                // consume trailing whitespace so the socket can be pooled
            }
        } catch (IOException ignored) {
        }
    }

    private static void closeQuietly(Reader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
        }
    }

    private static boolean isRetryable(IOException exception) {
        if (exception instanceof SocketTimeoutException) {
            return true;
        }
        // Truncated body (connection dropped mid-download) surfaces as EOFException
        // from the streaming parser; a fresh request usually succeeds.
        if (exception instanceof EOFException) {
            return true;
        }
        if (exception instanceof HttpStatusException) {
            return HostFailoverPolicy.shouldFailOverHttpStatus(((HttpStatusException) exception).code());
        }
        return false;
    }

    private static boolean isHostUnavailable(Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof HttpStatusException) {
                return HostFailoverPolicy.shouldFailOverHttpStatus(((HttpStatusException) cause).code());
            }
            if (cause instanceof SocketTimeoutException
                    || cause instanceof UnknownHostException
                    || cause instanceof ConnectException
                    || cause instanceof NoRouteToHostException
                    || cause instanceof SocketException
                    || cause instanceof SSLException
                    || cause instanceof EOFException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static void sleepBeforeRetry(int attempt) throws IOException {
        try {
            Thread.sleep(300L * attempt);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IOException("Request retry interrupted", interruptedException);
        }
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder builder = new StringBuilder(8 * 1024);
        char[] buffer = new char[8 * 1024];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            builder.append(buffer, 0, read);
        }
        return builder.toString();
    }

    private static String categoriesAction(XtreamContentType type) {
        if (type == XtreamContentType.MOVIE) {
            return "get_vod_categories";
        }
        if (type == XtreamContentType.SERIES) {
            return "get_series_categories";
        }
        return "get_live_categories";
    }

    private static String itemsAction(XtreamContentType type) {
        if (type == XtreamContentType.MOVIE) {
            return "get_vod_streams";
        }
        if (type == XtreamContentType.SERIES) {
            return "get_series";
        }
        return "get_live_streams";
    }

    private static List<XtreamMediaItem> parseItems(XtreamContentType type, Reader body) throws IOException {
        if (type == XtreamContentType.MOVIE) {
            return XtreamMediaParser.parseMovieItems(body);
        }
        if (type == XtreamContentType.SERIES) {
            return XtreamMediaParser.parseSeriesItems(body);
        }
        return XtreamMediaParser.parseLiveItems(body);
    }

    private <T> void postSuccess(Callback<T> callback, T result) {
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private void postError(Callback<?> callback, Exception exception) {
        mainHandler.post(() -> callback.onError(exception));
    }

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(Exception exception);
    }

    private interface BodyParser<T> {
        T parse(Reader body) throws IOException;
    }

    private interface HostRequest<T> {
        T execute(XtreamCredentials credentials) throws Exception;
    }

    private static final class HttpStatusException extends IOException {
        private final int code;

        private HttpStatusException(int code) {
            super("HTTP " + code);
            this.code = code;
        }

        private int code() {
            return code;
        }
    }
}
