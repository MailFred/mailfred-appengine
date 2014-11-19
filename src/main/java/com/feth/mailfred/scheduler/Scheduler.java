package com.feth.mailfred.scheduler;

import com.feth.mailfred.scheduler.exceptions.MessageWasNotFoundException;
import com.feth.mailfred.scheduler.exceptions.ScheduledLabelWasRemovedException;
import com.feth.mailfred.scheduler.exceptions.WasAnsweredButNoAnswerOptionWasGivenException;
import com.feth.mailfred.util.Utils;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.ModifyMessageRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class Scheduler {

    private static final Logger log = Logger.getLogger(Scheduler.class.getName());

    private static final String BASE_LABEL = "MailFred";
    private static final String SCHEDULED_LABEL = "MailFred/Scheduled";
    public static final String LABEL_ID_UNREAD = "UNREAD";
    public static final String LABEL_ID_STARRED = "STARRED";
    public static final String LABEL_ID_INBOX = "INBOX";

    final private Gmail gmail;

    public Scheduler(final String userId) throws IOException {
        this.gmail = Utils.loadGmailClient(userId);
    }

    private Gmail gmail() {
        return this.gmail;
    }

    private String me() {
        return "me";
    }

    private List<Label> labelCache = null;

    private List<Label> getLabels() throws IOException {
        if (labelCache == null) {
            final ListLabelsResponse response = gmail().users().labels().list(me()).execute();
            labelCache = response.getLabels();
        }
        return labelCache;
    }

    public boolean addSchedulingLabels(final String mailId) throws IOException {
        final ModifyMessageRequest mmr = new ModifyMessageRequest().setAddLabelIds(
                Arrays.asList(
                        getBaseLabel().getId(),
                        getScheduledLabel().getId()
                )
        );

        try {
            final Message message = gmail().users().messages().modify(me(), mailId, mmr).execute();

        } catch (GoogleJsonResponseException e) {
            return false;
        }

        return true;
    }

    public Message getMessageByMailId(final String mailId) throws IOException {
        Message message = null;

        try {
            message = gmail().users().messages().get(me(), mailId).execute();
        } catch (final GoogleJsonResponseException e) {
            if (!(e.getDetails().getCode() == 400 && e.getDetails().getErrors().get(0).getReason().equals("invalidArgument"))) {
                // some other error, rethrow
                e.printStackTrace();
                return null;
            }
            // message ID not found, it's alright, we just didn't find it
        }
        return message;
    }

    /**
     * Whether a given message ID has the correct format
     *
     * @param mailId a GMail message ID (a hexadecimal number with 16 digits)
     * @return whether the given message ID is valid (in a sense of format) or not
     */
    public static boolean isValidMessageId(final String mailId) {
        if (mailId == null || mailId.length() != 16) {
            return false;
        }
        try {
            @SuppressWarnings("unused")
            final Long n = Long.parseLong(mailId, 16);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }


    private Label getBaseLabel() throws IOException {
        for (final Label l : getLabels()) {
            if (BASE_LABEL.equals(l.getName())) {
                return l;
            }
        }
        return createLabel(BASE_LABEL);
    }

    private Label getScheduledLabel() throws IOException {
        for (final Label l : getLabels()) {
            if (SCHEDULED_LABEL.equals(l.getName())) {
                return l;
            }
        }
        return createLabel(SCHEDULED_LABEL);
    }

    private Label createLabel(final String name) throws IOException {
        final Label newBaseLabel = new Label();
        newBaseLabel.setLabelListVisibility("labelHide");
        newBaseLabel.setMessageListVisibility("show");
        newBaseLabel.setName(name);
        final Label newLabel = gmail().users().labels().create(me(), newBaseLabel).execute();
        if (labelCache != null) {
            labelCache.add(0, newLabel);
        }
        return newLabel;
    }

    public void process(final String mailId, final List<String> options) throws
            IOException,
            WasAnsweredButNoAnswerOptionWasGivenException,
            MessageWasNotFoundException,
            ScheduledLabelWasRemovedException {

        final Message messageToBeProcessed = getMessageByMailId(mailId);
        if (messageToBeProcessed == null) {
            throw new MessageWasNotFoundException();
        }
        //final boolean wasAnswered = messageToBeProcessed.getThreadId().

        final ModifyMessageRequest mmr = new ModifyMessageRequest()
                .setAddLabelIds(Arrays.asList(
                        getBaseLabel().getId(),
                        LABEL_ID_UNREAD,
                        LABEL_ID_STARRED
                ))
                .setRemoveLabelIds(Collections.singletonList(getScheduledLabel().getId()));
        try {
            final Message message = gmail().users().messages().modify(me(), mailId, mmr).execute();

        } catch (GoogleJsonResponseException e) {
        // TODO: throw here
        }
    }
}
