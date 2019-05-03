package edu.cmu.oli.content.resource.builders;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.Collection;
import java.util.Iterator;

/**
 * Methods for parsing preference service XML.
 *
 * @author Raphael Gachuhi
 */
public final class OptionsReader {

    // =======================================================================
    // Private constructor
    // =======================================================================
    private OptionsReader() {
    }

    // =======================================================================
    // Public static methods
    // =======================================================================

    /**
     * <p>
     * Parses the supplied element into a preference. This method assumes that
     * the supplied XML has been validated against the OLI preferences DTD.</p>
     *
     * @param prefElmnt
     * @return
     */
    public static JsonElement elementToPreference(Element prefElmnt) {
        return elementToPreference(prefElmnt, prefElmnt.getNamespace());
    }

    /**
     * <p>
     * Parses the supplied element into a preference. This method assumes that
     * the supplied XML has been validated against the OLI preferences DTD.</p>
     *
     * @param prefElmnt
     * @param ns
     * @return
     */
    public static JsonElement elementToPreference(Element prefElmnt, Namespace ns) {
        if (prefElmnt == null) {
            return JsonNull.INSTANCE;
        }
        if (!"preference".equals(prefElmnt.getName())) {
            final StringBuilder message = new StringBuilder();
            message.append("local name must be 'preference': ");
            message.append("name=").append(prefElmnt.getName());
            throw new IllegalArgumentException(message.toString());
        }

        if (ns == null) {
            ns = Namespace.NO_NAMESPACE;
        }

        String name = prefElmnt.getAttributeValue("name");
        String typeStr = prefElmnt.getAttributeValue("type");
        String title = prefElmnt.getChild("title", ns).getText();

        JsonObject prefBuilder = new JsonObject();
        prefBuilder.addProperty("@name", name);
        prefBuilder.addProperty("@type", typeStr);
        prefBuilder.addProperty("title", title);
        Element description = prefElmnt.getChild("description", ns);
        prefBuilder.addProperty("description", description == null ? null : description.getText());

        JsonArray valueArrayBuilder = new JsonArray();
        Collection valueElmnts = prefElmnt.getChildren("value", ns);
        for (Iterator i = valueElmnts.iterator(); i.hasNext(); ) {
            Element valueElmnt = (Element) i.next();
            String value = valueElmnt.getAttributeValue("value");
            String label = valueElmnt.getText();
            JsonObject val = new JsonObject();
            val.addProperty("@value", value);
            val.addProperty("label", label);
            valueArrayBuilder.add(val);
        }
        prefBuilder.add("values", valueArrayBuilder);
        String defaultStr = prefElmnt.getAttributeValue("default");
        prefBuilder.addProperty("@default", defaultStr);
        return prefBuilder;
    }

    /**
     * <p>
     * Parses the supplied element into a preference set. This method assumes
     * that the supplied XML has been validated against the OLI preferences
     * DTD.</p>
     *
     * @param setElmnt
     * @return
     */
    public static JsonElement elementToPreferenceSet(Element setElmnt) {
        if (setElmnt == null) {
            return JsonNull.INSTANCE;
        }

        return elementToPreferenceSet(setElmnt, setElmnt.getNamespace());
    }

    /**
     * <p>
     * Parses the supplied element into a preference set. This method assumes
     * that the supplied XML has been validated against the OLI preferences
     * DTD.</p>
     *
     * @param setElmnt
     * @param ns
     * @return JsonObject
     */
    public static JsonElement elementToPreferenceSet(Element setElmnt, Namespace ns) {
        if (setElmnt == null) {
            return JsonNull.INSTANCE;
        }

        if (!"preferences".equals(setElmnt.getName())) {
            final StringBuilder message = new StringBuilder();
            message.append("local name must be 'preferences': ");
            message.append("name=").append(setElmnt.getName());
            throw new IllegalArgumentException(message.toString());
        }

        if (ns == null) {
            ns = Namespace.NO_NAMESPACE;
        }

        String guid = setElmnt.getAttributeValue("guid");
        JsonObject prefSet = new JsonObject();
        prefSet.addProperty("@guid", guid);

        JsonArray prefsArrayBuilder = new JsonArray();
        Collection prefElmnts = setElmnt.getChildren("preference", ns);
        for (Iterator i = prefElmnts.iterator(); i.hasNext(); ) {
            Element prefElmnt = (Element) i.next();
            JsonElement pref = elementToPreference(prefElmnt, ns);
            prefsArrayBuilder.add(pref);
        }
        prefSet.add("preferences", prefsArrayBuilder);
        return prefSet;
    }

}
