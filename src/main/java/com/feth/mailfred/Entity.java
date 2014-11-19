package com.feth.mailfred;


public abstract class Entity {

    public static abstract class ScheduledMail {

        public static final String NAME = "ScheduledMail";

        public static abstract class Property {

            public static final String USER_ID = "userId";
            public static final String MAIL_ID = "mailId";
            public static final String SCHEDULED_AT = "scheduledAt";
            public static final String SCHEDULED_FOR = "scheduledFor";
            public static final String ACTIONS = "actions";
            public static final String PROCESSED_AT = "processedAt";
            public static final String HAS_BEEN_PROCESSED = "hasBeenProcessed";

            public static abstract class Actions {

                public static final String UNREAD = "unread";
            }
        }
    }

}
