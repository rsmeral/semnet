package xsmeral.doc;

import com.sun.javadoc.Tag;
import com.sun.tools.doclets.Taglet;
import java.util.Map;

public class ObjectProcessorTaglet implements Taglet {

    private static final String HEADER_FORMAT = "<br /><DT><B>%s</B></DT>";
    private static final String PARAM_FORMAT = "<dd><code>%s</code> - %s</dd>";
    private String name;
    private String headerText;

    public ObjectProcessorTaglet() {
    }

    public ObjectProcessorTaglet(String name, String headerText) {
        this.name = name;
        this.headerText = headerText;
    }

    public String header(String title) {
        return String.format(HEADER_FORMAT, title);
    }

    private String tagToString(Tag tag) {
        String tagText = tag.text();
        String paramName = tagText.substring(0, tagText.indexOf(" "));
        String paramText = tagText.substring(tagText.indexOf(" ") + 1);
        return String.format(PARAM_FORMAT, paramName, paramText);
    }

    public String toString(Tag tag) {
        return header(headerText) + tagToString(tag);
    }

    public String toString(Tag[] tags) {
        if (tags.length > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(header(headerText));
            for (Tag t : tags) {
                sb.append(tagToString(t));
            }
            return sb.toString();
        } else {
            return "";
        }
    }

    public boolean inField() {
        return false;
    }

    public boolean inConstructor() {
        return false;
    }

    public boolean inMethod() {
        return false;
    }

    public boolean inOverview() {
        return false;
    }

    public boolean inPackage() {
        return false;
    }

    public boolean inType() {
        return true;
    }

    public boolean isInlineTag() {
        return false;
    }

    public static void registerInternal(ObjectProcessorTaglet tag, Map tagletMap) {
        Taglet t = (Taglet) tagletMap.get(tag.getName());
        if (t != null) {
            tagletMap.remove(tag.getName());
        }
        tagletMap.put(tag.getName(), tag);
    }

    public String getName() {
        return name;
    }
}
