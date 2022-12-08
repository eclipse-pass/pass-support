package org.eclipse.pass.support.client;

import java.io.IOException;

import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Add an Authorization header for basic auth to all requests.
 */
public class OkHttpBasicAuthInterceptor implements Interceptor {
    private final String credentials;

    /**
     * Create new Interceptor which will add the given credentials.
     *
     * @param user for basic auth
     * @param password for basic auth
     */
    public OkHttpBasicAuthInterceptor(String user, String password) {
        this.credentials = Credentials.basic(user, password);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Request authenticatedRequest = request.newBuilder().header("Authorization", credentials).build();
        return chain.proceed(authenticatedRequest);
    }
}
