package jbuild.artifact.http;

import java.net.http.HttpClient;
import java.time.Duration;

public final class DefaultHttpClient {

    private enum Singleton {
        INSTANCE;
        public final HttpClient httpClient = create();
    }

    public static HttpClient get() {
        return Singleton.INSTANCE.httpClient;
    }

    private static HttpClient create() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
}
