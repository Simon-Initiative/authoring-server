package edu.cmu.oli.common;

import java.io.*;
import java.util.regex.Matcher;

/**
 * <p>
 * Models an email message. The message is constructed from a message template.
 * Tokens within the template can be replaced by calling the
 * <tt>replaceToken</tt> method.
 * </p>
 *
 */
public final class MessageTemplate {
    private final String from;
    private String subject;
    private String body;

    // =======================================================================
    // Public constructors
    // =======================================================================

    /**
     * <p>
     * Creates a new <tt>Message</tt> instance.
     * </p>
     *
     * @param from    email address of the message sender
     * @param subject subject of the email message
     * @param body    body of the email message
     * @throws NullPointerException if any of the arguments are <tt>null</tt>
     */
    public MessageTemplate(String from, String subject, String body) {
        if (from == null) {
            throw (new NullPointerException("'from' cannot be null"));
        } else if (subject == null) {
            throw (new NullPointerException("'subject' cannot be null"));
        } else if (body == null) {
            throw (new NullPointerException("'body' cannot be null"));
        }

        this.from = from;
        this.subject = subject;
        this.body = body;
    }

    /**
     * <p>
     * Creates a new <tt>Message</tt> instance from the specified template file.
     * </p>
     *
     * @param from     email address of the message sender
     * @param template file containing the message template
     * @throws NullPointerException  if either argument is <tt>null</tt>
     * @throws FileNotFoundException if <tt>template</tt> does not exist
     * @throws IOException           if an error occurs while reading the template
     */
    public MessageTemplate(String from, File template) throws IOException {
        if (from == null) {
            throw (new NullPointerException("'from' cannot be null"));
        } else if (template == null) {
            throw (new NullPointerException("'template' cannot be null"));
        } else if (!template.exists() || !template.isFile()) {
            throw (new FileNotFoundException(template.toString()));
        }

        // Set the from address
        this.from = from;

        // Load the message template
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(template));

            // Message subject is on first line of template
            this.subject = in.readLine();
            if (this.subject == null)
                this.subject = "";

            // Read in the body of the message from template
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = in.readLine()) != null) {
                sb.append(line).append("\n");
            }
            this.body = sb.toString();
        } finally {
            if (in != null)
                in.close();
        }
    }

    /**
     * <p>
     * Creates a new <tt>Message</tt> instance from the specified template file.
     * </p>
     *
     * @param from     email address of the message sender
     * @param template file containing the message template
     * @throws NullPointerException  if either argument is <tt>null</tt>
     * @throws FileNotFoundException if <tt>template</tt> does not exist
     * @throws IOException           if an error occurs while reading the template
     */
    public MessageTemplate(String from, InputStream template) throws IOException {
        if (from == null) {
            throw (new NullPointerException("'from' cannot be null"));
        } else if (template == null) {
            throw (new NullPointerException("'template' cannot be null"));
        }

        // Set the from address
        this.from = from;

        // Load the message template
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(template));

            // Message subject is on first line of template
            this.subject = in.readLine();
            if (this.subject == null)
                this.subject = "";

            // Read in the body of the message from template
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = in.readLine()) != null) {
                sb.append(line).append("\n");
            }
            this.body = sb.toString();
        } finally {
            if (in != null)
                in.close();
        }
    }

    /**
     * Copy constructor.
     */
    public MessageTemplate(MessageTemplate other) {
        if (other == null) {
            throw (new NullPointerException("'other' cannot be null"));
        }

        this.from = other.getFrom();
        this.subject = other.getSubject();
        this.body = other.getBody();
    }

    // =======================================================================
    // Public instance methods
    // =======================================================================

    /**
     * <p>
     * Replaces all instances of the token, having the specified name, contained
     * within the subject and/or body of the message with the given replacement
     * value.
     * </p>
     *
     * @param name  name of the token
     * @param value replacement value
     * @throws NullPointerException if either argument is <tt>null</tt>
     */
    public void replaceToken(String name, String value) {
        if (name == null) {
            throw (new NullPointerException("'name' cannot be null"));
        } else if (value == null) {
            throw (new NullPointerException("'value' cannot be null"));
        }

        String token = "@@" + name + "@@";
        if (this.subject != null) {
            this.subject = this.subject.replaceAll(token, Matcher.quoteReplacement(value));
        }
        if (this.body != null) {
            this.body = this.body.replaceAll(token, Matcher.quoteReplacement(value));
        }
    }

    /**
     * <p>
     * Gets the email address of the message sender.
     * </p>
     *
     * @return from address
     */
    public String getFrom() {
        return from;
    }

    /**
     * <p>
     * Gets the subject of the email message.
     * </p>
     *
     * @return message subject
     */
    public String getSubject() {
        return subject;
    }

    /**
     * <p>
     * Gets the body of the email message.
     * </p>
     *
     * @return message body
     */
    public String getBody() {
        return body;
    }
}
