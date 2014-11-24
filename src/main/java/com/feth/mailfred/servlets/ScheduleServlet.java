package com.feth.mailfred.servlets;


import com.feth.mailfred.EntityConstants;
import com.feth.mailfred.EntityConstants.ScheduledMail.Property.ProcessingOptions;
import com.feth.mailfred.exceptions.*;
import com.feth.mailfred.scheduler.Scheduler;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.appengine.api.datastore.*;
import com.google.appengine.api.users.UserServiceFactory;
import org.json.JSONObject;

import javax.servlet.ServletException;
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

    public static final String ERROR_CODE_MESSAGE_ID_INVALID = "MessageIdInvalid";
    public static final String ERROR_CODE_NO_ACTION_SPECIFIED = "NoActionSpecified";
    public static final String ERROR_CODE_INVALID_SCHEDULE_TIME = "InvalidScheduleTime";
    public static final String ERROR_CODE_NO_SCHEDULE_TIME = "NoScheduleTime";
    public static final String ERROR_CODE_STORING_FAILED = "StoringFailed";
    public static final String ERROR_CODE_AUTH_MISSING = "authMissing";

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.addHeader("Access-Control-Allow-Origin", "*");
    }

    @Override
    public void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        schedule(req, resp);
    }

    @Override
    public void doPost(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException {
        schedule(req, resp);
    }

    private void schedule(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        log.entering(ScheduleServlet.class.getName(), "schedule");
        final Date now = new Date();
        final String userId = UserServiceFactory.getUserService().getCurrentUser().getUserId();
        final Scheduler scheduler = new Scheduler(userId);

        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.setContentType("application/json");
        final JSONObject response = new JSONObject();
        response.put("success", false);
        response.put("error", "Unknown error occurred");
        try {
            log.info("Getting schedule date from the request");
            final Date scheduleAt = getScheduledAtFromRequest(req, now);

            log.info("Getting processing options from the request");
            final List<String> processingOptions = getProcessingOptionsFromRequest(req);

            // this is more expensive, so do it last
            log.info("Getting mailId from the request");
            final String mailId = getMailIdFromRequest(req, scheduler);

            log.info(String.format("User %s told us to schedule mail with ID %s at %s with the following options: %s", userId, mailId, scheduleAt, processingOptions));

            scheduleMail(now, userId, scheduler, mailId, scheduleAt, processingOptions);

            response.put("success", true);
            response.put("error", false);
        } catch (final MessageIdInvalidException e) {
            final JSONObject error = new JSONObject();
            error.put("code", ERROR_CODE_MESSAGE_ID_INVALID);
            response.put("error", error);
        } catch (final MessageNotFoundException e) {
            final JSONObject error = new JSONObject();
            error.put("code", ERROR_CODE_MESSAGE_ID_INVALID);
            response.put("error", error);
        } catch (final NoActionSpecifiedException e) {
            final JSONObject error = new JSONObject();
            error.put("code", ERROR_CODE_NO_ACTION_SPECIFIED);
            response.put("error", error);
        } catch (InvalidScheduleTimeException e) {
            final JSONObject error = new JSONObject();
            error.put("code", ERROR_CODE_INVALID_SCHEDULE_TIME);
            response.put("error", error);
        } catch (NoScheduleTimeException e) {
            final JSONObject error = new JSONObject();
            error.put("code", ERROR_CODE_NO_SCHEDULE_TIME);
            response.put("error", error);
        } catch (final GoogleJsonResponseException e) {
            final GoogleJsonError details = e.getDetails();
            final GoogleJsonError.ErrorInfo errorInfo = details.getErrors().get(0);
            final String reason = errorInfo.getReason();
            if (details.getCode() == HttpServletResponse.SC_UNAUTHORIZED && errorInfo.getLocation().equals("Authorization") && (reason.equals("required") || reason.equals("authError"))) {
                final JSONObject error = new JSONObject();
                error.put("code", ERROR_CODE_AUTH_MISSING);
                response.put("error", error);
            }
        } catch (StoringFailedException e) {
            final JSONObject error = new JSONObject();
            error.put("code", ERROR_CODE_STORING_FAILED);
            response.put("error", error);
        } catch (final Throwable e) {
            log.severe(e.getMessage());
            e.printStackTrace();
        }
        response.write(resp.getWriter());
    }

    private List<String> getProcessingOptionsFromRequest(HttpServletRequest req) throws NoActionSpecifiedException {
        final List<String> processingOptions = getTheProcessingOptions(req);
        if (!processingOptions.contains(ProcessingOptions.MARK_UNREAD) &&
                !processingOptions.contains(ProcessingOptions.MOVE_TO_INBOX) &&
                !processingOptions.contains(ProcessingOptions.STAR)
                ) {
            throw new NoActionSpecifiedException("There must be at least one processing option enabled");
        }
        return processingOptions;
    }

    private Date getScheduledAtFromRequest(HttpServletRequest req, Date now) throws InvalidScheduleTimeException, NoScheduleTimeException {
        final Long when = getWhenTheMailShouldBeScheduled(req, now);
        if (when == null) {
            throw new InvalidScheduleTimeException("Schedule time must be given in a proper format");
        }
        return new Date(when);
    }

    private String getMailIdFromRequest(final HttpServletRequest req, final Scheduler scheduler) throws IOException, MessageNotFoundException, MessageIdInvalidException {
        final String mailId = req.getParameter(PARAMETER_MESSAGE_ID);
        if (!Scheduler.isValidMessageId(mailId)) {
            log.info(String.format("Given mailId '%s' is not well-formed", mailId));
            throw new MessageIdInvalidException();
        }
        try {
            scheduler.getMessageByMailId(mailId);
        } catch(GoogleJsonResponseException e) {
            if(e.getDetails().getCode() == HttpServletResponse.SC_NOT_FOUND) {
                throw new MessageNotFoundException();
            } else {
                throw e;
            }
        }
        return mailId;
    }

    private void scheduleMail(Date now, String userId, Scheduler scheduler, String mailId, Date scheduleAt, List<String> processingOptions) throws IOException, StoringFailedException {
        final DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        final boolean archive = processingOptions.contains(ProcessingOptions.ARCHIVE_AFTER_SCHEDULING);
        final List<Entity> unprocessedSameScheduledMails = getUnprocessedScheduledMailsFromSameUserWithSameMailId(userId, mailId, ds);
        final Entity scheduledMail = createNewScheduledMailEntity(userId, mailId, scheduleAt, processingOptions, now);

        final TransactionOptions options = TransactionOptions.Builder.withXG(true);
        final Transaction txn = ds.beginTransaction(options);

        try {
            markAllPreviouslyScheduledMailsAsCancelled(unprocessedSameScheduledMails, now);
            unprocessedSameScheduledMails.add(scheduledMail);
            ds.put(unprocessedSameScheduledMails);

            scheduler.schedule(mailId, archive);
            txn.commit();
        } catch (IOException e) {
            throw new StoringFailedException();
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
        for (final String key : ProcessingOptions.VALID_PROCESS_OPTION_KEYS) {
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
    private Long getWhenTheMailShouldBeScheduled(final HttpServletRequest req, final Date now) throws NoScheduleTimeException {
        String whenParam = req.getParameter(PARAMETER_WHEN);
        if (whenParam == null || whenParam.trim().equals("")) {
            throw new NoScheduleTimeException();
        }

        Long when = null;
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
        return when;
    }
}
