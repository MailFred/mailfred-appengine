package com.feth.mailfred.servlets;

import com.feth.mailfred.Entity;
import com.feth.mailfred.Scheduler;
import com.google.appengine.api.datastore.*;
import com.google.appengine.api.datastore.Query.Filter;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import static com.feth.mailfred.Entity.ScheduledMail.Property;

public class ProcessServlet extends HttpServlet {

    private static final Logger log = Logger.getLogger(ProcessServlet.class.getName());

    private static final Filter unprocessedFilter = new Query.FilterPredicate(
            Property.HAS_BEEN_PROCESSED,
            Query.FilterOperator.EQUAL,
            false
    );

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        log.info("Processing");

        final Filter beforeNowFilter = new Query.FilterPredicate(
                Property.SCHEDULED_FOR,
                Query.FilterOperator.LESS_THAN_OR_EQUAL,
                new Date()
        );

        final Filter beforeNowAndUnprocessedFilter =
                Query.CompositeFilterOperator.and(beforeNowFilter, unprocessedFilter);

        final Query q = new Query(Entity.ScheduledMail.NAME)
                .setFilter(beforeNowAndUnprocessedFilter);

        // Use PreparedQuery interface to retrieve results
        final DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        final PreparedQuery pq = ds.prepare(q);
        for (final com.google.appengine.api.datastore.Entity scheduledMail : pq.asIterable()) {
            final String mailId = (String) scheduledMail.getProperty(Property.MAIL_ID);
            final String userId = (String) scheduledMail.getProperty(Property.USER_ID);
            log.info(String.format(
                    "Processing mail with ID %s for user %s",
                    mailId,
                    userId
            ));

            @SuppressWarnings("unchecked")
            final List<String> actions = (List<String>) scheduledMail.getProperty(Property.ACTIONS);
            final Scheduler s = new Scheduler(userId);
            if (s.process(mailId, actions)) {
                scheduledMail.setProperty(Property.HAS_BEEN_PROCESSED, true);
                scheduledMail.setProperty(Property.PROCESSED_AT, new Date());
                ds.put(scheduledMail);
            }
        }
    }
}
