package edu.cmu.oli.content.resource.builders;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 * Methods for generating preference service XML.
 *
 * @author Raphael Gachuhi
 */
public final class OptionsWriter {

    private static final String _PUBLIC_ID = "-//Carnegie Mellon University//DTD Preferences 1.0//EN";
    private static final String _SYSTEM_ID = "http://oli.cmu.edu/dtd/oli_preferences_1_0.dtd";

    // =======================================================================
    // Private constructors
    // =======================================================================
    private OptionsWriter() {
    }

    // =======================================================================
    // Public static methods
    // =======================================================================

    /**
     * <p>
     * Converts the given preference set to an XML element in the specified
     * namespace. The resulting XML conforms to the OLI preferences DTD.
     * </p>
     *
     * @param prefSeti preference set
     * @param nsi      XML namespace
     * @return XML element representation of the preference set
     * @throws NullPointerException if either argument is <tt>null</tt>
     */
    public static Element preferenceSetToElement(JsonElement prefSeti, Namespace nsi) {
        if (prefSeti == null || prefSeti.isJsonNull() || prefSeti.isJsonPrimitive()
                || prefSeti.isJsonObject() && prefSeti.getAsJsonObject().size() == 0) {
            return null;
        }
        if (nsi == null) {
            nsi = Namespace.NO_NAMESPACE;
        }

        final Namespace ns = nsi;
        JsonObject prefSet = (JsonObject) prefSeti;
        // Preference set
        Element setElmnt = new Element("preferences", ns);

        // GUID
        if (prefSet.get("guid") != null) {
            setElmnt.setAttribute("guid", prefSet.get("@guid").getAsString());
        }

        // Preferences
        JsonArray preferences = prefSet.getAsJsonArray("preferences");
        if (preferences != null) {
            preferences.forEach((val) -> {
                Element element = preferenceToElement(val, ns);
                if (element != null) {
                    setElmnt.addContent(element);
                }
            });
        }

        return setElmnt;
    }

    /**
     * <p>
     * Converts the given preference to an XML element. The result XML conforms to
     * the OLI preferences DTD.
     * </p>
     *
     * @param pref preference
     * @return XML element representation of the preference
     * @throws NullPointerException if <tt>pref</tt> is <tt>null</tt>
     */
    public static Element preferenceToElement(JsonElement pref) {
        return preferenceToElement(pref, Namespace.NO_NAMESPACE);
    }

    /**
     * <p>
     * Converts the given preference to an XML element in the specified namespace.
     * The result XML conforms to the OLI preferences DTD.
     * </p>
     *
     * @param prefi preference
     * @param nsi   XML namespace
     * @return XML element representation of the preference
     * @throws NullPointerException if either argument is <tt>null</tt>
     */
    public static Element preferenceToElement(JsonElement prefi, Namespace nsi) {
        if (prefi == null || prefi instanceof JsonNull) {
            return null;
        }
        if (nsi == null) {
            nsi = Namespace.NO_NAMESPACE;
        }
        JsonObject pref = (JsonObject) prefi;
        final Namespace ns = nsi;

        // Preference
        Element prefElmnt = new Element("preference", ns);

        prefElmnt.setAttribute("name", pref.get("@name").getAsString());
        prefElmnt.setAttribute("type", pref.get("@type").getAsString());
        prefElmnt.setAttribute("default", pref.get("@default").getAsString());

        // Title
        Element titleElmnt = new Element("title", ns);
        titleElmnt.setText(pref.has("title") ? pref.get("title").getAsString() : null);
        prefElmnt.addContent(titleElmnt);

        // Description
        Element dscrElmnt = new Element("description", ns);
        dscrElmnt.setText(pref.has("description") ? pref.get("description").getAsString() : null);
        prefElmnt.addContent(dscrElmnt);

        // Possible values
        JsonArray values = pref.getAsJsonArray("values");
        values.forEach((val) -> {
            Element valueElmnt = new Element("value", ns);
            JsonObject value = (JsonObject) val;
            valueElmnt.setAttribute("value", value.get("@value").getAsString());
            valueElmnt.setText(value.get("label").getAsString());
            prefElmnt.addContent(valueElmnt);
        });
        return prefElmnt;
    }

}
