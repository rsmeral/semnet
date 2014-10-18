package xsmeral.semnet.crawler.util;

import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mozilla.universalchardet.UniversalDetector;
import xsmeral.semnet.crawler.HTMLCrawler;

/**
 * Provides method for detection of character set of HTML content.
 */
public class CharsetDetector {

    private static final String HTTP_CONTENT_TYPE = "Content-Type";
    private static final String HTTP_HEAD = "HEAD";
    private static final Pattern PATTERN_HEADER = Pattern.compile("charset=(.*)\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_HTML_META = Pattern.compile("<meta\\s+http-equiv\\s*=\\s*[\"']Content-Type[\"']\\s+content\\s*=\\s*[\"'][^;]+;\\s*charset=([^\"']+)[\"']\\s*/?>", Pattern.CASE_INSENSITIVE);
    /**
     * The META tags in HTML are usually found within the first 512 bytes
     */
    private static final int BUFFER_SIZE = 512;// bytes
    /**
     * An upper bound to where the META tag can be found
     */
    private static final int MAX_BYTES_READ_META = 8192;// bytes

    /**
     * Convenience method, calls {@link #detectCharset(java.net.URL) detectCharset(new URL(url))}.
     */
    public static String detectCharset(String url) throws MalformedURLException {
        return detectCharset(new URL(url));
    }

    /**
     * Tries to find the charset of the HTML content at the specified URL.
     * <br /><br />
     * It looks in the following places, returning the first found result<br /><br />
     * <ol>
     *  <li>The {@code Content-Type} HTTP header</li>
     *  <li>The HTML tag {@code <meta http-equiv="Content-Type" content="..." />},
     *      which should contain the same directive as the corresponding HTTP header</li>
     *  <li>If the two previous fail, the <a href="http://code.google.com/p/juniversalchardet/">juniversalchardet</a>
     *      is used to guess the character set used</li>
     * </ol>
     * @param url The URL to connect to
     * @return The first found result or null if the charset can't be found or guessed
     */
    public static String detectCharset(URL url) {
        try {
            // look in Content-Type HTTP header first
            HttpURLConnection conn = ConnectionManager.getConnection(url);
            conn.setRequestMethod(HTTP_HEAD);
            String contentType = conn.getHeaderField(HTTP_CONTENT_TYPE);
            if (contentType != null) {
                Matcher m = PATTERN_HEADER.matcher(contentType);
                if (m.find()) {
                    return validateCharset(m.group(1));
                }
            }

            // if not found, look for <meta http-equiv="Content-Type"... /> in HTML
            conn = ConnectionManager.getConnection(url);
            int read = 0;
            int totalRead = 0;
            byte[] buf = new byte[BUFFER_SIZE];
            boolean found = false;
            Matcher matcher = null;
            PushbackInputStream is = new PushbackInputStream(conn.getInputStream(), MAX_BYTES_READ_META);
            // read BUFFER_SIZE bytes until MAX_BYTES_READ_META is reached
            while (!found && totalRead < MAX_BYTES_READ_META && (read = is.read(buf, totalRead, Math.min(BUFFER_SIZE, MAX_BYTES_READ_META - totalRead))) > 0) {
                totalRead += read;
                String str = new String(buf);
                matcher = PATTERN_HTML_META.matcher(str);
                found = matcher.find();
                if (!found && totalRead < MAX_BYTES_READ_META) {
                    buf = Arrays.copyOf(buf, Math.min(totalRead + BUFFER_SIZE, MAX_BYTES_READ_META));
                }
            }
            if (found) {
                return validateCharset(matcher.group(1));
            }

            // if still not found, try guessing (using juniversalchardet)
            is.unread(buf);
            buf = new byte[4096];
            UniversalDetector detector = new UniversalDetector(null);
            read = 0;
            while ((read = is.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, read);
            }
            detector.dataEnd();
            String encoding = detector.getDetectedCharset();
            conn.disconnect();
            return validateCharset(encoding);
        } catch (SocketTimeoutException ex) {
        } catch (IOException ex) {
            Logger.getLogger(HTMLCrawler.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private static String validateCharset(String charset) {
        return Charset.isSupported(charset)?charset:null;
    }
}
