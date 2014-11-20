package com.feth.mailfred.util;

import com.google.api.client.auth.oauth2.DataStoreCredentialRefreshListener;
import com.google.api.client.extensions.appengine.datastore.AppEngineDataStoreFactory;
import com.google.api.client.extensions.appengine.http.UrlFetchTransport;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.appengine.api.utils.SystemProperty;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;

public class Utils {

    private static final AppEngineDataStoreFactory DATA_STORE_FACTORY =
            AppEngineDataStoreFactory.getDefaultInstance();
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final UrlFetchTransport HTTP_TRANSPORT = new UrlFetchTransport();
    private static final String APP_NAME = "MailFred";

    public static String getRedirectUri(HttpServletRequest req) {
        GenericUrl url = new GenericUrl(req.getRequestURL().toString());
        url.setRawPath("/oauth2callback");
        return url.build();
    }

    private static GoogleClientSecrets getClientCredential() throws IOException {
        return GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(Utils.class.getResourceAsStream("/client_secret.json")));
    }

    public static GoogleAuthorizationCodeFlow newFlow(final String userId) throws IOException {
        return new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
                getClientCredential(), Collections.singleton(GmailScopes.GMAIL_MODIFY))
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .addRefreshListener(
                        new DataStoreCredentialRefreshListener(userId, DATA_STORE_FACTORY))
                .build();
    }

    public static boolean isDev() {
        return SystemProperty.environment.value() == SystemProperty.Environment.Value.Development;
    }

    public static Gmail loadGmailClient(final String userId) throws IOException {
        final HttpRequestInitializer credential;
        if (isDev() || true) {
            credential = newFlow(userId).loadCredential(userId);
        } else {
            credential =
                    new AppIdentityCredential(Collections.singletonList(GmailScopes.GMAIL_MODIFY));
        }
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APP_NAME)
                .build();
    }
}
