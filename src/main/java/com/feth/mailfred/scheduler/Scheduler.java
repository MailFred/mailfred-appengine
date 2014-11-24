package com.feth.mailfred.scheduler;

import com.feth.mailfred.EntityConstants.ScheduledMail.Property.ProcessingOptions;
import com.feth.mailfred.scheduler.exceptions.MessageWasNotFoundException;
import com.feth.mailfred.scheduler.exceptions.ScheduledLabelWasRemovedException;
import com.feth.mailfred.scheduler.exceptions.WasAnsweredButNoAnswerOptionWasGivenException;
import com.feth.mailfred.util.Utils;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.ModifyMessageRequest;

import java.io.IOException;
import java.util.ArrayList;
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
            final ListLabelsResponse response = gmail().users().labels().list(me())

                    .execute();
            labelCache = response.getLabels();
        }
        return labelCache;
    }

    public void schedule(final String mailId, boolean archive) throws IOException {
        final ModifyMessageRequest mmr = new ModifyMessageRequest().setAddLabelIds(
                Arrays.asList(
                        getBaseLabel().getId(),
                        getScheduledLabel().getId()
                )
        );
        if (archive) {
            mmr.setRemoveLabelIds(Collections.singletonList(LABEL_ID_INBOX));
        }

        gmail().users().messages().modify(me(), mailId, mmr).execute();
    }

    public Message getMessageByMailId(final String mailId) throws IOException {
        return gmail().users().messages().get(me(), mailId).execute();
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
        final Label newLabel = gmail().users().labels().create(me(), newBaseLabel)

                .execute();
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
        final boolean stillHasScheduledLabel = messageHasScheduledLabel(messageToBeProcessed);
        if (!stillHasScheduledLabel) {
            throw new ScheduledLabelWasRemovedException();
        }

        if (options.contains(ProcessingOptions.ONLY_IF_NO_ANSWER)) {
            final boolean isLastMessageInThread = isLastMessageInThread(messageToBeProcessed);
            if (!isLastMessageInThread) {
                throw new WasAnsweredButNoAnswerOptionWasGivenException();
            }
        }

        final List<String> addLabelIds = new ArrayList<String>(4);
        addLabelIds.add(getBaseLabel().getId());
        if (options.contains(ProcessingOptions.MARK_UNREAD)) {
            addLabelIds.add(LABEL_ID_UNREAD);
        }
        if (options.contains(ProcessingOptions.MOVE_TO_INBOX)) {
            addLabelIds.add(LABEL_ID_INBOX);
        }
        if (options.contains(ProcessingOptions.STAR)) {
            addLabelIds.add(LABEL_ID_STARRED);
        }

        final ModifyMessageRequest mmr = new ModifyMessageRequest()
                .setAddLabelIds(addLabelIds)
                .setRemoveLabelIds(Collections.singletonList(getScheduledLabel().getId()));

        gmail().users().messages().modify(me(), mailId, mmr).execute();
    }

    private boolean isLastMessageInThread(Message message) throws IOException {
        final com.google.api.services.gmail.model.Thread thread = gmail().users().threads().get(me(), message.getThreadId())

                .execute();
        final List<Message> threadMessages = thread.getMessages();
        return threadMessages.indexOf(message) == threadMessages.size() - 1;
    }

    private boolean messageHasScheduledLabel(Message message) throws IOException {
        return message.getLabelIds().contains(getScheduledLabel().getId());
    }
}
