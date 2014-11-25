package com.feth.mailfred.servlets;

import com.feth.mailfred.entities.EntityConstants;
import com.feth.mailfred.entities.EntityHelper;
import com.feth.mailfred.scheduler.Scheduler;
import com.feth.mailfred.util.Utils;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeResponseUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.appengine.auth.oauth2.AbstractAppEngineAuthorizationCodeCallbackServlet;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.users.UserServiceFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OAuth2CallbackServlet extends AbstractAppEngineAuthorizationCodeCallbackServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void onSuccess(HttpServletRequest req, HttpServletResponse resp, Credential credential)
            throws ServletException, IOException {

        final String userId = UserServiceFactory.getUserService().getCurrentUser().getUserId();
        reboxUnscheduledMessagesForCurrentUserAfterAuth(userId);
        resp.sendRedirect("/");
    }

    private static void reboxUnscheduledMessagesForCurrentUserAfterAuth(String userId) throws IOException {

        final Scheduler scheduler = new Scheduler(userId);

        final DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        final Iterable<Entity> toBeProcessedScheduledMailsForCurrentUser = EntityHelper.getToBeProcessedScheduledMailsForUser(ds, userId);

        final List<String> scheduledMailIds = new ArrayList<String>();
        for (final Entity scheduledMail : toBeProcessedScheduledMailsForCurrentUser) {
            scheduledMailIds.add((String) scheduledMail.getProperty(EntityConstants.ScheduledMail.Property.MAIL_ID));
        }

        scheduler.reboxUnscheduledMessagesWithOutboxLabel(scheduledMailIds);
    }

    @Override
    protected void onError(
            HttpServletRequest req, HttpServletResponse resp, AuthorizationCodeResponseUrl errorResponse)
            throws ServletException, IOException {
        resp.sendRedirect("/unauthorized.html");
    }

    @Override
    protected String getRedirectUri(HttpServletRequest req) throws ServletException, IOException {
        return Utils.getRedirectUri(req);
    }

    @Override
    protected AuthorizationCodeFlow initializeFlow() throws IOException {
        final String userId = UserServiceFactory.getUserService().getCurrentUser().getUserId();
        return Utils.newFlow(userId);
    }
}
