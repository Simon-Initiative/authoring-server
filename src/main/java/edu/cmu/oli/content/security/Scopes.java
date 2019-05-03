package edu.cmu.oli.content.security;

public enum Scopes {

    // =======================================================================
    // Actions
    // =======================================================================

    VIEW_MATERIAL_ACTION {
        @Override
        public String toString() {
            return "urn:content-service:scopes:view_material";
        }
    },
    INSTRUCT_MATERIAL_ACTION {
        @Override
        public String toString() {
            return "urn:content-service:scopes:instruct_material";
        }
    },
    EDIT_MATERIAL_ACTION {
        @Override
        public String toString() {
            return "urn:content-service:scopes:edit_material";
        }
    },
    VIEW_RESPONSE_ACTION {
        @Override
        public String toString() {
            return "urn:content-service:scopes:view_responses";
        }
    },
    SCORE_RESPONSE_ACTION {
        @Override
        public String toString() {
            return "urn:content-service:scopes:grade";
        }
    },
    VIEW_REPORT_ACTION {
        @Override
        public String toString() {
            return "urn:content-service:scopes:view_report";
        }
    },
    ADMINISTER_ACTION {
        @Override
        public String toString() {
            return "urn:content-service:scopes:administer";
        }
    };

    public abstract String toString();

}
