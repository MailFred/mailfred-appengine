package com.feth.mailfred;


public abstract class EntityConstants {

    public static abstract class ScheduledMail {

        public static final String NAME = "ScheduledMail";

        public static abstract class Property {

            public static final String USER_ID = "userId";
            public static final String MAIL_ID = "mailId";
            public static final String SCHEDULED_AT = "scheduledAt";
            public static final String SCHEDULED_FOR = "scheduledFor";
            public static final String PROCESSING_OPTIONS = "processingOptions";
            public static final String PROCESSED_AT = "processedAt";
            public static final String HAS_BEEN_PROCESSED = "hasBeenProcessed";
            public static final String PROCESS_STATUS = "processStatus";

            public static abstract class ProcessingOptions {

                public static final String PROCESS_OPTION_STAR = "starIt";
                public static final String PROCESS_OPTION_ARCHIVE_AFTER_SCHEDULING = "archiveAfterScheduling";
                public static final String PROCESS_OPTION_MARK_UNREAD = "markUnread";
                public static final String PROCESS_OPTION_MOVE_TO_INBOX = "moveToInbox";
                public static final String PROCESS_OPTION_ONLY_IF_NO_ANSWER = "onlyIfNoAnswer";
                public static final String[] VALID_PROCESS_OPTION_KEYS = {
                        PROCESS_OPTION_STAR,
                        PROCESS_OPTION_MARK_UNREAD,
                        PROCESS_OPTION_MOVE_TO_INBOX,
                        PROCESS_OPTION_ONLY_IF_NO_ANSWER,
                        PROCESS_OPTION_ARCHIVE_AFTER_SCHEDULING
                };
            }

            public static abstract class ProcessStatus {
                public static final String ANSWERED = "answered";
                public static final String NOT_FOUND = "notFound";
                public static final String CANCELED = "canceled";
                public static final String OUTBOX_LABEL_REMOVED = "labelRemoved";
                public static final String PROCESSED_CORRECTLY = "ok";
                public static final String ERRORED = "error";
            }
        }
    }

}
