package com.feth.mailfred.servlets;

import com.feth.mailfred.entities.EntityHelper;
import com.feth.mailfred.exceptions.MessageNotFoundException;
import com.feth.mailfred.scheduler.Scheduler;
import com.feth.mailfred.scheduler.exceptions.ScheduledLabelWasRemovedException;
import com.feth.mailfred.scheduler.exceptions.WasAnsweredButNoAnswerOptionWasGivenException;
import com.feth.mailfred.util.Utils;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import static com.feth.mailfred.entities.EntityConstants.ScheduledMail.Property;

public class ProcessServlet extends HttpServlet {

    private static final Logger log = Logger.getLogger(ProcessServlet.class.getName());

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        log.entering(ProcessServlet.class.getName(), "doGet");

        final Date processingRunStart = new Date();
        final DatastoreService ds = DatastoreServiceFactory.getDatastoreService();

        final Iterable<Entity> toBeProcessedScheduledMails = EntityHelper.getToBeProcessedScheduledMails(ds, processingRunStart);
        int processed = 0;
        for (final Entity scheduledMail : toBeProcessedScheduledMails) {
            processed++;
            try {
                final String mailId = (String) scheduledMail.getProperty(Property.MAIL_ID);
                final String userId = (String) scheduledMail.getProperty(Property.USER_ID);
                @SuppressWarnings("unchecked")
                final List<String> processingOptions = (List<String>) scheduledMail.getProperty(Property.PROCESSING_OPTIONS);

                log.info(String.format(
                        "Starting processing mail with ID %s for user %s with options %s",
                        mailId,
                        userId,
                        processingOptions
                ));


                final Scheduler s = new Scheduler(userId);
                final Date now = new Date();
                try {
                    s.process(mailId, processingOptions);
                    scheduledMail.setProperty(Property.PROCESS_STATUS, Property.ProcessStatus.PROCESSED_CORRECTLY);
                } catch (WasAnsweredButNoAnswerOptionWasGivenException e) {
                    scheduledMail.setProperty(Property.PROCESS_STATUS, Property.ProcessStatus.ANSWERED);
                } catch (MessageNotFoundException e) {
                    scheduledMail.setProperty(Property.PROCESS_STATUS, Property.ProcessStatus.NOT_FOUND);
                } catch (ScheduledLabelWasRemovedException e) {
                    scheduledMail.setProperty(Property.PROCESS_STATUS, Property.ProcessStatus.OUTBOX_LABEL_REMOVED);
                } catch(Exception e) {
                    scheduledMail.setProperty(Property.PROCESS_STATUS, Property.ProcessStatus.ERROR);
                } finally {
                    scheduledMail.setProperty(Property.HAS_BEEN_PROCESSED, true);
                    scheduledMail.setProperty(Property.PROCESSED_AT, now);
                }
                ds.put(scheduledMail);
            } catch (final Exception e) {
                // if there is a problem with one mail, we don't want the others to be affected
                log.severe(e.getMessage());
                if (Utils.isDev()) {
                    e.printStackTrace();
                }
            }
        }
        log.info(String.format("Processed %d mails",processed));
        log.exiting(ProcessServlet.class.getName(), "doGet");
    }

}
