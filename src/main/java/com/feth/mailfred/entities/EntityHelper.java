package com.feth.mailfred.entities;

import com.feth.mailfred.exceptions.StoringFailedException;
import com.feth.mailfred.scheduler.Scheduler;
import com.google.appengine.api.datastore.*;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class EntityHelper {
    private static final Query.Filter UNPROCESSED_SCHEDULED_MAIL_FILTER = new Query.FilterPredicate(
            EntityConstants.ScheduledMail.Property.HAS_BEEN_PROCESSED,
            Query.FilterOperator.EQUAL,
            false
    );

    public static Iterable<Entity> getToBeProcessedScheduledMailsForUser(final DatastoreService ds, final String userId) {
        final Query.Filter userIdFilter = getUserIdFilter(userId);

        final Query.Filter currentUserButUnprocessedFilter =
                Query.CompositeFilterOperator.and(
                        userIdFilter,
                        UNPROCESSED_SCHEDULED_MAIL_FILTER
                );

        final Query q = new Query(EntityConstants.ScheduledMail.NAME)
                .setFilter(currentUserButUnprocessedFilter);

        final PreparedQuery pq = ds.prepare(q);
        return pq.asList(FetchOptions.Builder.withDefaults());
    }

    public static Iterable<Entity> getToBeProcessedScheduledMails(DatastoreService ds, Date processingRunStart) {
        final Query.Filter scheduledForNowOrThePastFilter = new Query.FilterPredicate(
                EntityConstants.ScheduledMail.Property.SCHEDULED_FOR,
                Query.FilterOperator.LESS_THAN_OR_EQUAL,
                processingRunStart
        );

        final Query.Filter scheduledForNowOrThePastAndUnprocessedFilter = Query.CompositeFilterOperator.and(
                scheduledForNowOrThePastFilter,
                UNPROCESSED_SCHEDULED_MAIL_FILTER
        );

        final Query q = new Query(EntityConstants.ScheduledMail.NAME).setFilter(scheduledForNowOrThePastAndUnprocessedFilter);
        final PreparedQuery pq = ds.prepare(q);
        return pq.asIterable();
    }

    public static void scheduleMail(Date now, String userId, Scheduler scheduler, String mailId, Date scheduleAt, List<String> processingOptions) throws IOException, StoringFailedException {
        final DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        final boolean archive = processingOptions.contains(EntityConstants.ScheduledMail.Property.ProcessingOptions.ARCHIVE_AFTER_SCHEDULING);
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

    private static Entity createNewScheduledMailEntity(String userId, String mailId, Date scheduledFor, List<String> processingOptions, Date scheduledAt) {
        final Entity scheduledMail = new Entity(EntityConstants.ScheduledMail.NAME);
        scheduledMail.setProperty(EntityConstants.ScheduledMail.Property.USER_ID, userId);
        scheduledMail.setProperty(EntityConstants.ScheduledMail.Property.MAIL_ID, mailId);
        scheduledMail.setProperty(EntityConstants.ScheduledMail.Property.SCHEDULED_AT, scheduledAt);
        scheduledMail.setProperty(EntityConstants.ScheduledMail.Property.SCHEDULED_FOR, scheduledFor);
        scheduledMail.setProperty(EntityConstants.ScheduledMail.Property.PROCESSED_AT, null);
        scheduledMail.setProperty(EntityConstants.ScheduledMail.Property.HAS_BEEN_PROCESSED, false);
        scheduledMail.setProperty(EntityConstants.ScheduledMail.Property.PROCESS_STATUS, null);
        scheduledMail.setUnindexedProperty(EntityConstants.ScheduledMail.Property.PROCESSING_OPTIONS, processingOptions);
        return scheduledMail;
    }

    private static void markAllPreviouslyScheduledMailsAsCancelled(final List<Entity> unprocessedSameScheduledMails, final Date now) {
        for (final Entity sameScheduledMail : unprocessedSameScheduledMails) {
            sameScheduledMail.setProperty(EntityConstants.ScheduledMail.Property.HAS_BEEN_PROCESSED, true);
            sameScheduledMail.setProperty(EntityConstants.ScheduledMail.Property.PROCESSED_AT, now);
            sameScheduledMail.setProperty(EntityConstants.ScheduledMail.Property.PROCESS_STATUS, EntityConstants.ScheduledMail.Property.ProcessStatus.CANCELED);
        }
    }

    private static List<Entity> getUnprocessedScheduledMailsFromSameUserWithSameMailId(final String userId, final String mailId, final DatastoreService ds) {
        final Query.Filter mailIdFilter = new Query.FilterPredicate(
                EntityConstants.ScheduledMail.Property.MAIL_ID,
                Query.FilterOperator.EQUAL,
                mailId
        );

        final Query.Filter userIdFilter = getUserIdFilter(userId);

        final Query.Filter currentUserSameEmailButUnprocessedFilter =
                Query.CompositeFilterOperator.and(
                        userIdFilter,
                        mailIdFilter,
                        UNPROCESSED_SCHEDULED_MAIL_FILTER
                );

        final Query q = new Query(EntityConstants.ScheduledMail.NAME)
                .setFilter(currentUserSameEmailButUnprocessedFilter);

        final PreparedQuery pq = ds.prepare(q);
        return pq.asList(FetchOptions.Builder.withDefaults());
    }

    private static Query.Filter getUserIdFilter(String userId) {
        return new Query.FilterPredicate(
                EntityConstants.ScheduledMail.Property.USER_ID,
                Query.FilterOperator.EQUAL,
                userId
        );
    }
}
