package org.eclipse.pass.support.client;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Add CSRF token as a header and cookie to requests.
 * The token can have any value.
 */
public class OkHttpCsrfInterceptor implements Interceptor {
    private static String CSRF_TOKEN = "anyvalue";

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request().newBuilder().header("X-XSRF-TOKEN", CSRF_TOKEN)
                .header("Cookie", "XSRF-TOKEN=" + CSRF_TOKEN).build();

        return chain.proceed(request);
    }
}
