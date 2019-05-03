package edu.cmu.oli.content.security;

/**
 * @author Raphael Gachuhi
 */
public enum Roles {

    USER {
        @Override
        public String toString() {
            return "user";
        }
    },
    STUDENT {
        @Override
        public String toString() {
            return "student";
        }
    },
    TEACHING_ASSISTANT {
        @Override
        public String toString() {
            return "teaching_assistant";
        }
    },
    INSTRUCTOR {
        @Override
        public String toString() {
            return "instructor";
        }
    },
    ADMIN {
        @Override
        public String toString() {
            return "admin";
        }
    },
    CONTENT_DEVELOPER {
        @Override
        public String toString() {
            return "content_developer";
        }

    };

    public abstract String toString();

}
