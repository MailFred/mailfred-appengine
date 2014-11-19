package com.feth.mailfred;


public abstract class EntityDefintions {

    public static abstract class ScheduledMail {
        public static final String NAME = "ScheduledMail";

        public static abstract class Properties {
            public static final String USER_ID = "userId";
            public static final String MAIL_ID = "mailId";
            public static final String SCHEDULED_AT = "scheduledAt";
            public static final String SCHEDULED_FOR = "scheduledFor";
            public static final String ACTIONS = "actions";
            public static final String PROCESSED_AT = "processedAt";
            public static final String HAS_BEEN_PROCESSED = "hasBeenProcessed";

            public static final String ACTION_PROPERTY_VALUE_UNREAD = "unread";
        }
    }

}
