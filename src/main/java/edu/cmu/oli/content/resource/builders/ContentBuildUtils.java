package edu.cmu.oli.content.resource.builders;

/**
 * Utility methods for validating content.
 *
 * @author John A Rinderle
 * @author Bill Jerome
 */
public final class ContentBuildUtils {
    // =======================================================================
    // Private constructors
    // =======================================================================
    private ContentBuildUtils() {
    }

    // =======================================================================
    // Public static methods
    // =======================================================================

    /**
     * <p>
     * Returns <tt>true</tt> if the specified string is a valid identifier and
     * <tt>false</tt> otherwise. A valid identifier is between 2 and 150
     * characters in length. It must begin with a letter or underscore. The
     * remaining characters must be letters, underscores, digits, periods, or
     * hyphens. At least one character must be a letter or digit.</p>
     *
     * @param id identifier
     * @return <tt>true</tt> if valid identifier, <tt>false</tt> otherwise
     */
    public static boolean isValidIdentifier(String id) {
        if ((id == null) || (id.length() < 2) || (id.length() > 250)) {
            return false;
        }

        boolean nonsymbol = false;

        for (int i = 0; i < id.length(); i++) {
            boolean status = Character.isLetter(id.charAt(i));
            status = status || (id.charAt(i) == '_');

            if (i > 0) {
                status = status || Character.isDigit(id.charAt(i));
                status = status || (id.charAt(i) == '.');
                status = status || (id.charAt(i) == '-');
            }

            // Is current character a non-symbol character
            nonsymbol = Character.isLetterOrDigit(id.charAt(i));

            if (!status) {
                return false;
            }
        }

        return nonsymbol;
    }

    /**
     * <p>
     * Returns <tt>true</tt> if the specified string contains both upper and
     * lower case letters.</p>
     *
     * @param id identifier
     * @return <tt>true</tt> if mixed case, <tt>false</tt> otherwise
     * @throws NullPointerException if <tt>id</tt> is <tt>null</tt>
     */
    public static boolean isMixedCase(String id) {
        if (id == null) {
            throw (new NullPointerException("'id' cannot be nul;"));
        }

        boolean lower = false, upper = false;

        for (int i = 0; i < id.length(); i++) {
            // Check for mixed lower/upper case
            if (Character.isLowerCase(id.charAt(i))) {
                lower = true;
            } else if (Character.isUpperCase(id.charAt(i))) {
                upper = true;
            }
        }

        return (lower && upper);
    }

    /**
     * <p>
     * Returns <tt>true</tt> if the specified string is a valid version and
     * <tt>false</tt> otherwise. A valid version number is between 2 and 25
     * characters in length. It must begin with a letter or digit. The remaining
     * characters may be letters, digits, underscores, periods, or hyphens.</p>
     *
     * @param id identifier
     * @return <tt>true</tt> if valid identifier, <tt>false</tt> otherwise
     */
    public static boolean isValidVersion(String id) {
        if ((id == null) || (id.length() < 2) || (id.length() > 25)) {
            return false;
        }

        for (int i = 0; i < id.length(); i++) {
            boolean status = Character.isLetterOrDigit(id.charAt(i));

            if (i > 0) {
                status = status || (id.charAt(i) == '_');
                status = status || (id.charAt(i) == '.');
                status = status || (id.charAt(i) == '-');
            }

            if (!status) {
                return false;
            }
        }

        return true;
    }

}
