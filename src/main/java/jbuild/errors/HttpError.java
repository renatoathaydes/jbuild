package jbuild.errors;

import java.net.http.HttpResponse;

public class HttpError<B> {
    public final HttpResponse<B> httpResponse;

    public HttpError(HttpResponse<B> httpResponse) {
        this.httpResponse = httpResponse;
    }
}
