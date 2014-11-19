package com.feth.mailfred.servlets;


import com.feth.mailfred.EntityDefintions;
import com.feth.mailfred.Scheduler;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.users.UserServiceFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Logger;


public class ScheduleServlet extends HttpServlet {

    private static final Logger log = Logger.getLogger(ScheduleServlet.class.getName());

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        final String userId = UserServiceFactory.getUserService().getCurrentUser().getUserId();
        final Scheduler scheduler = new Scheduler(userId);

        //final String mailId = "149bf5fb84c69c32";
        final String mailId = req.getParameter("msgId");
        if (scheduler.getMessageByMailId(mailId) == null) {
            throw new IllegalArgumentException("Given mailId could not be found");
        }

        Long when = getWhenTheMailShouldBeScheduled(req);
        if (when == null) {
            throw new IllegalArgumentException("Schedule time must be given");
        }

        final DatastoreService ds = DatastoreServiceFactory.getDatastoreService();

        final Entity scheduledMail = new Entity(EntityDefintions.ScheduledMail.NAME);

        scheduledMail.setProperty(EntityDefintions.ScheduledMail.Properties.USER_ID, userId);
        scheduledMail.setProperty(EntityDefintions.ScheduledMail.Properties.MAIL_ID, mailId);
        scheduledMail.setProperty(EntityDefintions.ScheduledMail.Properties.SCHEDULED_AT, new Date());
        scheduledMail.setProperty(EntityDefintions.ScheduledMail.Properties.SCHEDULED_FOR, new Date(when));
        scheduledMail.setProperty(EntityDefintions.ScheduledMail.Properties.PROCESSED_AT, null);
        scheduledMail.setProperty(EntityDefintions.ScheduledMail.Properties.HAS_BEEN_PROCESSED, false);
        scheduledMail.setUnindexedProperty(EntityDefintions.ScheduledMail.Properties.ACTIONS,
                Arrays.asList(
                        EntityDefintions.ScheduledMail.Properties.ACTION_PROPERTY_VALUE_UNREAD
                )
        );
        ds.put(scheduledMail);


        scheduler.schedule(mailId);

        // Retrieve a page of Threads; max of 100 by default.
        //ListThreadsResponse threadsResponse = service.users().threads().list("me").execute();
        //List<Thread> threads = threadsResponse.getThreads();

        // Print ID of each Thread.
        //for (Thread thread : threads) {
        //    System.out.println("Thread ID: " + thread.getId());
        //}

        resp.setContentType("text/plain");
        resp.getWriter().println("{ \"name\": \"World33\" }");
    }

    private Long getWhenTheMailShouldBeScheduled(HttpServletRequest req) {
        Long when = null;
        String whenParam = req.getParameter("when");
        if (whenParam != null && !whenParam.trim().equals("")) {
            final boolean isDelta = whenParam.startsWith("delta:");
            if (isDelta) {
                whenParam = whenParam.substring("delta:".length());
            }
            try {
                when = Long.parseLong(whenParam);
                if (isDelta) {
                    when += new Date().getTime();
                }
            } catch (final NumberFormatException nfe) {
                // we couldn't parse the given delta
            }
        }
        return when;
    }
}
