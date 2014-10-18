package xsmeral.semnet.crawler.util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents site crawling policy defined in
 * <a href="http://www.robotstxt.org/orig.html">Robots Exclusion Protocol</a>
 * for one host.
 * Provides methods for checking URI against the policy.<br />
 * This implementation allows non-standard, however, widely used extensions
 * {@code Allow}, {@code Crawl-delay} and wildcards in URIs.<br />
 * The parser is lenient, ignoring non-matching lines and unknown fields.<br />
 * A more specific rule overrides a less specific rule (if a rule exists for
 * one specific user agent, it overrides the rule for *).
 *
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class RobotsPolicy {
    /* robots.txt format:
     *  case insensitive
     *  <field>:<optionalspace><value><optionalspace> # comment
     *  # comment
     */

    // file format constants
    private static final String ROBOTS_TXT = "/robots.txt";
    private static final String DISALLOW = "Disallow";
    private static final String ALLOW = "Allow";
    private static final String CRAWL_DELAY = "Crawl-delay";
    private static final String USER_AGENT = "User-agent";
    private static final String WILDCARD = "*";
    private static final String OR = "|";
    private static final String COLON = ":";
    // regex parts
    private static final String OPT_SPACE = "\\s*";
    private static final String OPT_COMMENT = "(#.*)?";
    private static final String FIELD = "(" + USER_AGENT + OR + DISALLOW + OR + ALLOW + OR + CRAWL_DELAY + ")";
    private static final String VALUE = "([^#]*?)";//value can be empty in Disallow
    // regex match groups
    private static final int FIELD_GROUP = 2;
    private static final int VALUE_GROUP = 3;
    // line pattern
    private static final Pattern ROBOTS_LINE = Pattern.compile(
            "^(" + FIELD + COLON + OPT_SPACE + VALUE + ")?" + OPT_SPACE + OPT_COMMENT + "$",
            Pattern.CASE_INSENSITIVE);
    // vars
    private String userAgent;
    private URL host;
    private boolean allowAll;
    private float crawlDelay;
    private List<String> allows;
    private List<String> disallows;

    /**
     * Calls {@link #load(java.net.URL) load} for the specified host and user agent.
     *
     * @param host The host to get the policy for
     * @param userAgent User agent, rules for which are searched
     */
    public RobotsPolicy(URL host, String userAgent) {
        this.userAgent = userAgent;
        load(host);
    }

    /**
     * Tries to load robots.txt at the specified host.
     * <br />
     * If the file doesn't exist, is empty or otherwise malformed,
     * the policy is considered to allow all user agents to all URLs
     *
     * @param host The host to load the policy from
     */
    public final void load(URL host) {
        this.host = host;
        allows = new ArrayList<String>();
        disallows = new ArrayList<String>();
        allowAll = false;
        crawlDelay = 0;
        BufferedReader br = null;
        try {
            URL robotsURL = host.toURI().resolve(ROBOTS_TXT).toURL();
            InputStream is = ConnectionManager.getInputStream(robotsURL, 2);
            br = new BufferedReader(new InputStreamReader(is));
            boolean userAgentMatch = false, specific = false, general = false, foundSpecific = false;
            while (br.ready()) {
                String line = br.readLine();
                Matcher m = ROBOTS_LINE.matcher(line);
                String currentField;
                String currentValue;
                if (m.matches()) {
                    currentField = m.group(FIELD_GROUP);
                    currentValue = m.group(VALUE_GROUP);
                    if (currentField != null) {
                        if (currentField.equalsIgnoreCase(USER_AGENT)) {
                            specific = currentValue.equals(userAgent);
                            general = currentValue.equals(WILDCARD);
                            if (specific && !foundSpecific) {
                                disallows.clear();
                                allows.clear();
                                crawlDelay = 0;
                                foundSpecific = specific;
                            }
                            userAgentMatch = currentValue != null && (specific || general);
                        } else if (userAgentMatch && (!general || !foundSpecific) && currentField.equalsIgnoreCase(DISALLOW)) {
                            if (currentValue != null && !currentValue.isEmpty()) {
                                disallows.add(currentValue);
                            }
                        } else if (userAgentMatch && currentField.equalsIgnoreCase(ALLOW)) {
                            if (currentValue != null && !currentValue.isEmpty()) {
                                allows.add(currentValue);
                            }
                        } else if (userAgentMatch && currentField.equalsIgnoreCase(CRAWL_DELAY)) {
                            try {
                                crawlDelay = Float.parseFloat(currentValue);
                            } catch (NumberFormatException ex) {
                                //ignore error, interpret the number as 0
                            }
                        }
                    }
                }
            }
            allowAll = disallows.isEmpty();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(RobotsPolicy.class.getName()).log(Level.INFO, "Robots policy file not found");
            allowAll = true;
        } catch (URISyntaxException ex) {
            Logger.getLogger(RobotsPolicy.class.getName()).log(Level.INFO, "Can't read Robots policy file");
            allowAll = true;
        } catch (MalformedURLException ex) {
            Logger.getLogger(RobotsPolicy.class.getName()).log(Level.INFO, "Can't read Robots policy file");
            allowAll = true;
        } catch (IOException ex) {
            Logger.getLogger(RobotsPolicy.class.getName()).log(Level.INFO, "Can't read Robots policy file");
            allowAll = true;
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(RobotsPolicy.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Checks whether this relative URI is allowed in this host's robots policy
     *
     * @param relativeUri An URI relative to the host
     * @return True, if the uri is allowed in this host's robots policy
     */
    public boolean allows(String relativeUri) {
        return !disallows(relativeUri);
    }

    /**
     * Complementary to {@link #allows(java.lang.String) allows}
     *
     * @param relativeUri An URI relative to the host
     * @return True, if the uri is NOT allowed in this host's robots policy
     */
    public boolean disallows(String relativeUri) {
        if (allowAll) {
            return false;
        }
        boolean disallowed = false;
        if (relativeUri.charAt(0) != '/') {
            relativeUri = "/" + relativeUri;
        }
        Iterator<String> it = disallows.iterator();
        while (it.hasNext() && !disallowed) {
            String disallowedURI = it.next();
            if (relativeUri.startsWith(disallowedURI)) {
                disallowed = true;
            } else if (disallowedURI.contains("*")) {
                disallowed = Pattern.matches("^" + disallowedURI.replaceAll("\\*", "[^/]+") + ".*$", relativeUri);
            }
        }
        if (disallowed) {
            it = allows.iterator();
            while (it.hasNext() && disallowed) {
                String allowedURI = it.next();
                if (relativeUri.startsWith(allowedURI)) {
                    disallowed = false;
                } else if (allowedURI.contains("*")) {
                    disallowed = !Pattern.matches("^" + allowedURI.replaceAll("\\*", "[^/]+") + ".*$", relativeUri);
                }
            }
        }
        return disallowed;
    }

    /**
     * Checks whether this policy allows all URLs for this user-agent
     *
     * @return True, if all URLs are allowed for this user-agent
     */
    public boolean allowsAll() {
        return allowAll;
    }

    /**
     * Returns the crawl delay in seconds.<br />
     * Crawl delay is the minimal amount of time a crawler should wait before
     * any two consequent requests to the same host.
     * 
     * @return The crawl delay in seconds
     */
    public float getCrawlDelay() {
        return crawlDelay;
    }

    /**
     * Returns the crawl delay in milliseconds
     */
    public int getCrawlDelayMillis() {
        return (int) (crawlDelay * 1000);
    }
}
