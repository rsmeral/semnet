package xsmeral.semnet.util;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringEscapeUtils;

/**
 * Provides methods for URL normalization and host equality comparison.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class URLUtil {

    private static final String COLON = ":";
    private static final String SEMICOLON = ";";
    private static final String AMPERSAND = "&";
    private static final String EQUALS_SIGN = "=";
    private static final String PERCENT = "%";
    private static final String QUESTION_MARK = "?";
    private static final String FWD_SLASH = "/";
    private static final String DOT = ".";
    //
    private static String URL_CHARSET = "UTF-8";
    private static final String URLENCODED_SYMBOL_PATTERN = "%(\\p{XDigit}{2})";
    private static final Pattern urlEncSymbolPattern = Pattern.compile(URLENCODED_SYMBOL_PATTERN);
    // common parameters, usually of no significance to the content (session ids, analytics, etc.)
    private static final String[] unwantedStrings = {"^utm_", "session"};
    private static final List<Pattern> unwantedPatterns = new ArrayList<Pattern>(unwantedStrings.length);

    static {
        // pre-compile unwanted parameter patterns
        for (String unwantedStr : unwantedStrings) {
            unwantedPatterns.add(Pattern.compile(unwantedStr));
        }
    }

    /**
     * Returns scheme and authority part of URL with trailing slash.
     */
    public static String fullHost(URL url) {
        return "http://" + url.getAuthority() + "/";
    }

    public static boolean equalHosts(URL host1, URL host2, boolean full) {
        return full ? host1.equals(host2) : host1.getHost().equals(host2.getHost());
    }

    /**
     * Convenience method, calls {@link #normalize(java.net.URL) normalize}{@code (new URL(url))}.
     * Tries to add {@code http://} if it's missing
     * @param url String containing the url to be normalized
     * @return Normalized URL, or the supplied string unchanged in case of failure
     */
    public static URL normalize(String url) throws MalformedURLException {
        URL normalized = null;
        try {
            normalized = normalize(new URL(url));
        } catch (MalformedURLException ex) {
            // try adding http://, may help
            normalized = !url.startsWith("http://") ? normalize("http://" + url) : null;
        }
        return normalized;
    }

    /**
     * Important part of every crawler - a URL normalizer. Ensures equivalence
     * of different representations of the same URL.<br />
     * Adheres mostly to RFC 3986 and http://dblab.ssu.ac.kr/publication/LeKi05a.pdf<br />
     * Performs these steps:<br />
     * <ul>
     * <li>case normalization</li>
     * <li>removes document fragment</li>
     * <li>removes standard port number</li>
     * <li>decodes unreserved characters in path</li>
     * <li>parameter sorting (allows &amp; and ; delimiters) and filtering, allows
     * empty-valued and multi-valued params</li>
     * <li>capitalizes percent-encoded octets</li>
     * <li>normalizes path (resolves dot-segments and adds trailing slash to empty path)</li>
     * <li>tries to add http:// if the input url doesn't have protocol</li>
     * </ul>
     *
     * @author Ron Šmeral (xsmeral@fi.muni.cz)
     */
    public static URL normalize(URL url) {
        int port = url.getPort();
        String path = url.getPath();
        String query = sortAndCleanParams(StringEscapeUtils.unescapeHtml(url.getQuery()));
        if (path == null || path.isEmpty()) {
            path = FWD_SLASH;
        }
        try {
            StringBuilder outUrlStr = new StringBuilder(url.toString().length())//
                    .append(url.getProtocol())//
                    .append("://")//
                    .append(url.getHost().toLowerCase())//
                    .append((port == 80 || port == -1) ? "" : COLON + port)//
                    .append(decodeUnreserved(path)).append(!query.isEmpty() ? QUESTION_MARK : "")//
                    .append(query);
            return new URI(outUrlStr.toString()).normalize().toURL();
        } catch (MalformedURLException ex) {
            Logger.getLogger(URLUtil.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        } catch (URISyntaxException ex) {
            Logger.getLogger(URLUtil.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    /**
     * Used for parameter sorting and filtering
     * 
     * @param query The query string part of URL
     * @return Query string with sorted and filtered params
     */
    private static String sortAndCleanParams(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }

        // determine param delimiter, can be ; or &
        String delimiter = query.contains(AMPERSAND) ? AMPERSAND : query.contains(SEMICOLON) ? SEMICOLON : null;

        // if there is no delimiter and the query string doesn't contain equals sign, leave the query as is
        if (delimiter == null && !query.contains(EQUALS_SIGN)) {
            return URLEncode(URLDecode(query));
        }

        // sort and filter the params
        String[] params = (delimiter != null) ? query.split(delimiter) : new String[]{query};
        SortedSet<Param> paramSet = new TreeSet<Param>();

        for (int i = 0; i < params.length; i++) {
            String paramStr = params[i];
            if (!paramStr.isEmpty()) {
                boolean hasEquals = paramStr.contains(EQUALS_SIGN);
                int equalsPos = hasEquals ? paramStr.indexOf(EQUALS_SIGN) : -1;
                int paramLen = paramStr.length();
                String field = URLEncode(URLDecode(hasEquals ? paramStr.substring(0, equalsPos) : paramStr));
                String value = ((hasEquals && (equalsPos != paramLen - 1)) ? URLEncode(URLDecode(paramStr.substring(equalsPos + 1, paramLen))) : "");
                boolean isUnwanted = false;
                for (int j = 0; j < unwantedStrings.length && !isUnwanted; j++) {
                    isUnwanted = isUnwanted || unwantedPatterns.get(j).matcher(field).find();
                }
                if (!isUnwanted) {
                    paramSet.add(new Param(field, value, i));
                }
            }
        }

        // put the query string back together
        switch (paramSet.size()) {
            case 0:
                return "";
            case 1:
                return paramSet.first().toString();
            default:
                Iterator<Param> it = paramSet.iterator();
                StringBuilder queryString = new StringBuilder(query.length() + 10);
                queryString.append(it.next().toString());
                while (it.hasNext()) {
                    queryString.append(delimiter).append(it.next());
                }
                return queryString.toString();
        }
    }

    /**
     * Replaces characters not reserved in URL, according to RFC 3986,
     * which are ALPHA (%41-%5A and %61-%7A), DIGIT (%30-%39), hyphen (%2D), period (%2E),
     * underscore (%5F), or tilde (%7E)
     *
     * @param str Input string
     * @return String with percent-sequences only for reserved characters
     */
    private static String decodeUnreserved(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        if (!str.contains(PERCENT)) {
            return str;
        }
        Matcher m = urlEncSymbolPattern.matcher(str);
        StringBuffer buf = new StringBuffer();
        String rep;
        while (m.find()) {
            int v = Integer.parseInt(m.group(1), 16);
            if ((v >= 'a' && v <= 'z') || (v >= 'A' && v <= 'Z') || (v >= '0' && v <= '9')
                    || v == '-' || v == '.' || v == '_' || v == '~') {
                rep = String.valueOf((char) v);
            } else {
                rep = m.group().toUpperCase();
            }
            m.appendReplacement(buf, rep);
        }
        m.appendTail(buf);
        return buf.toString();
    }

    /**
     * Wrapper, calls {@link URLEncoder URLEncoder}.{@link URLEncoder#encode(java.lang.String, java.lang.String) encode}{@code (str, "UTF-8")}
     * @param str String to URL-encode, should be fully decoded
     * @return URL-encoded string
     */
    private static String URLEncode(String str) {
        try {
            // if the string contains at least one url-encoded symbol, it is very likely it's already url-encoded
            return str.contains(PERCENT) ? str : URLEncoder.encode(str, URL_CHARSET);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(URLUtil.class.getName()).log(Level.SEVERE, null, ex);
            return str;
        }
    }

    /**
     * Wrapper, calls {@link URLDecoder URLDecoder}.{@link URLDecoder#decode(java.lang.String, java.lang.String) decode}{@code (str, "UTF-8")}
     * @param str String to URL-decode
     * @return URL-decoded string
     */
    private static String URLDecode(String str) {
        try {
            return URLDecoder.decode(str, URL_CHARSET);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(URLUtil.class.getName()).log(Level.SEVERE, null, ex);
            return str;
        }
    }

    /**
     * Represents one query string param. Implements Comparable,
     * the sorting keys are field, value and original position in query string
     */
    private static final class Param implements Comparable {

        private int origPosition;
        private String field;
        private String value;

        public Param() {
        }

        public Param(String field, String value, int origPosition) {
            this.field = field;
            this.value = value;
        }

        public Param(String field, String value) {
            this(field, value, 0);
        }

        public int getOrigPosition() {
            return origPosition;
        }

        public void setOrigPosition(int origPosition) {
            this.origPosition = origPosition;
        }

        public String getField() {
            return field;
        }

        public void setField(final String field) {
            this.field = field;
        }

        public String getValue() {
            return value;
        }

        public void setValue(final String value) {
            this.value = value;
        }

        /**
         * Compares by field, value and original position in query string
         * @param obj Other object
         * @return Result of comparison
         */
        @Override
        public int compareTo(Object obj) {
            Param o = (Param) obj;
            int fieldDiff = this.field.compareTo(o.getField());
            if (fieldDiff != 0) {
                return fieldDiff;
            } else {
                int valDiff = this.value.compareTo(o.getValue());
                if (valDiff != 0) {
                    return valDiff;
                } else {
                    return this.origPosition - o.getOrigPosition();
                }
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Param other = (Param) obj;
            if (this.origPosition != other.origPosition) {
                return false;
            }
            if ((this.field == null) ? (other.field != null) : !this.field.equals(other.field)) {
                return false;
            }
            if ((this.value == null) ? (other.value != null) : !this.value.equals(other.value)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 59 * hash + this.origPosition;
            hash = 59 * hash + (this.field != null ? this.field.hashCode() : 0);
            hash = 59 * hash + (this.value != null ? this.value.hashCode() : 0);
            return hash;
        }

        @Override
        public String toString() {
            return new StringBuilder(field.length() + value.length() + 1).append(this.field).append(EQUALS_SIGN).append(this.value).toString();
        }
    }

}
