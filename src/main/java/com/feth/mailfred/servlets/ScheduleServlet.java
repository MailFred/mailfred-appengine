package com.feth.mailfred.servlets;


import com.feth.mailfred.EntityConstants;
import com.feth.mailfred.scheduler.Scheduler;
import com.feth.mailfred.util.Utils;
import com.google.appengine.api.datastore.*;
import com.google.appengine.api.users.UserServiceFactory;
import org.json.JSONObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import static com.feth.mailfred.EntityConstants.ScheduledMail.Property;


public class ScheduleServlet extends HttpServlet {

    private static final Logger log = Logger.getLogger(ScheduleServlet.class.getName());

    public static final String PARAMETER_WHEN = "when";
    public static final String PARAMETER_WHEN_VALUE_DELTA_PREFIX = "delta:";
    public static final String PARAMETER_MESSAGE_ID = "msgId";


    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        final Date now = new Date();
        final String userId = UserServiceFactory.getUserService().getCurrentUser().getUserId();
        final Scheduler scheduler = new Scheduler(userId);

        resp.setContentType("application/json");
        final JSONObject response = new JSONObject();
        try {
            final String mailId = getMailIdFromRequest(req, scheduler);
            final Date scheduleAt = getScheduledAtFromRequest(req, now);
            final List<String> processingOptions = getProcessingOptionsFromRequest(req);

            log.info(String.format("User %s told us to schedule mail with ID %s at %s with the following options: %s", userId, mailId, scheduleAt, processingOptions));

            scheduleMail(now, userId, scheduler, mailId, scheduleAt, processingOptions);

            response.put("success", true);
            response.put("error", false);
        } catch (final Exception e) {
            response.put("success", false);
            log.severe(e.getMessage());
            if (Utils.isDev()) {
                e.printStackTrace();
            }
            response.put("error", "Something went wrong");
        }
        response.write(resp.getWriter());
    }

    private List<String> getProcessingOptionsFromRequest(HttpServletRequest req) {
        final List<String> processingOptions = getTheProcessingOptions(req);
        if (processingOptions.size() == 0 || (processingOptions.size() == 1 && processingOptions.contains(Property.ProcessingOptions.PROCESS_OPTION_ONLY_IF_NO_ANSWER))) {
            throw new IllegalArgumentException("There must be at least one processing option enabled");
        }
        return processingOptions;
    }

    private Date getScheduledAtFromRequest(HttpServletRequest req, Date now) {
        final Long when = getWhenTheMailShouldBeScheduled(req, now);
        if (when == null) {
            throw new IllegalArgumentException("Schedule time must be given in a proper format");
        }
        return new Date(when);
    }

    private String getMailIdFromRequest(final HttpServletRequest req, final Scheduler scheduler) throws IOException {
        final String mailId = req.getParameter(PARAMETER_MESSAGE_ID);
        if (!Scheduler.isValidMessageId(mailId)) {
            throw new IllegalArgumentException(String.format("Given mailId '%s' is not well-formed", mailId));
        }
        if (scheduler.getMessageByMailId(mailId) == null) {
            throw new IllegalArgumentException("Given mailId could not be found");
        }
        return mailId;
    }

    private void scheduleMail(Date now, String userId, Scheduler scheduler, String mailId, Date scheduleAt, List<String> processingOptions) throws IOException {
        final DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        final List<Entity> unprocessedSameScheduledMails = getUnprocessedScheduledMailsFromSameUserWithSameMailId(userId, mailId, ds);
        final Entity scheduledMail = createNewScheduledMailEntity(userId, mailId, scheduleAt, processingOptions, now);

        final Transaction txn = ds.beginTransaction();
        try {
            markAllPreviouslyScheduledMailsAsCancelled(unprocessedSameScheduledMails, now);
            unprocessedSameScheduledMails.add(scheduledMail);
            ds.put(unprocessedSameScheduledMails);

            scheduler.addSchedulingLabels(mailId);
            txn.commit();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

    private Entity createNewScheduledMailEntity(String userId, String mailId, Date scheduledFor, List<String> processingOptions, Date scheduledAt) {
        final Entity scheduledMail = new Entity(EntityConstants.ScheduledMail.NAME);
        scheduledMail.setProperty(Property.USER_ID, userId);
        scheduledMail.setProperty(Property.MAIL_ID, mailId);
        scheduledMail.setProperty(Property.SCHEDULED_AT, scheduledAt);
        scheduledMail.setProperty(Property.SCHEDULED_FOR, scheduledFor);
        scheduledMail.setProperty(Property.PROCESSED_AT, null);
        scheduledMail.setProperty(Property.HAS_BEEN_PROCESSED, false);
        scheduledMail.setProperty(Property.PROCESS_STATUS, null);
        scheduledMail.setUnindexedProperty(Property.PROCESSING_OPTIONS, processingOptions);
        return scheduledMail;
    }

    private void markAllPreviouslyScheduledMailsAsCancelled(final List<Entity> unprocessedSameScheduledMails, final Date now) {
        for (final Entity sameScheduledMail : unprocessedSameScheduledMails) {
            sameScheduledMail.setProperty(Property.HAS_BEEN_PROCESSED, true);
            sameScheduledMail.setProperty(Property.PROCESSED_AT, now);
            sameScheduledMail.setProperty(Property.PROCESS_STATUS, Property.ProcessStatus.CANCELED);
        }
    }

    private List<Entity> getUnprocessedScheduledMailsFromSameUserWithSameMailId(final String userId, final String mailId, final DatastoreService ds) {
        final Query.Filter mailIdFilter = new Query.FilterPredicate(
                Property.MAIL_ID,
                Query.FilterOperator.EQUAL,
                mailId
        );

        final Query.Filter userIdFilter = new Query.FilterPredicate(
                Property.USER_ID,
                Query.FilterOperator.EQUAL,
                userId
        );

        final Query.Filter currentUserSameEmailButUnprocessedFilter =
                Query.CompositeFilterOperator.and(
                        userIdFilter,
                        mailIdFilter,
                        ProcessServlet.UNPROCESSED_SCHEDULED_MAIL_FILTER
                );

        final Query q = new Query(EntityConstants.ScheduledMail.NAME)
                .setFilter(currentUserSameEmailButUnprocessedFilter);

        final PreparedQuery pq = ds.prepare(q);
        return pq.asList(FetchOptions.Builder.withDefaults());
    }

    private List<String> getTheProcessingOptions(final HttpServletRequest req) {
        final List<String> options = new ArrayList<String>(2);
        for (final String key : Property.ProcessingOptions.PROCESS_OPTION_KEYS) {
            if ("true".equals(req.getParameter(key))) {
                options.add(key);
            }
        }
        return options;
    }

    /**
     * @param req the request to read the parameter from
     * @param now the current date - we need this in case we got a delta request
     * @return a unix timestamp
     */
    private Long getWhenTheMailShouldBeScheduled(final HttpServletRequest req, final Date now) {
        Long when = null;
        String whenParam = req.getParameter(PARAMETER_WHEN);
        if (whenParam != null && !whenParam.trim().equals("")) {
            final boolean isDelta = whenParam.startsWith(PARAMETER_WHEN_VALUE_DELTA_PREFIX);
            if (isDelta) {
                whenParam = whenParam.substring(PARAMETER_WHEN_VALUE_DELTA_PREFIX.length());
            }
            try {
                when = Long.parseLong(whenParam);
                if (isDelta) {
                    when += now.getTime();
                }
            } catch (final NumberFormatException nfe) {
                // we couldn't parse the given delta
            }
        }
        return when;
    }
}
