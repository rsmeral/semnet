package xsmeral.semnet.crawler.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * TODO: HTTP status checking
 */
/**
 * Provides consistent network settings across classes using HttpURLConnections.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class ConnectionManager {

    private static final String HTTP_REFERER = "Referer";
    private static final String HTTP_USER_AGENT = "User-Agent";
    private static final int TIMEOUT_WAIT = 5000;
    private static int readTimeout = 10000;
    private static int connTimeout = 10000;
    private static boolean followRedirects = true;
    private static String userAgent = "SemNetCrawler/1.0";

    /**
     * Corresponds to {@link HttpURLConnection#getConnectTimeout()}
     */
    public static int getConnTimeout() {
        return connTimeout;
    }

    /**
     * Corresponds to {@link HttpURLConnection#setConnectTimeout(int)}
     */
    public static void setConnTimeout(int connTimeout) {
        ConnectionManager.connTimeout = connTimeout;
    }

    /**
     * Corresponds to {@link HttpURLConnection#getReadTimeout()}
     */
    public static int getReadTimeout() {
        return readTimeout;
    }

    /**
     * Corresponds to {@link HttpURLConnection#setReadTimeout(int)}
     */
    public static void setReadTimeout(int readTimeout) {
        ConnectionManager.readTimeout = readTimeout;
    }

    /**
     * Returns the User-agent.
     * If it has not been set, returns the default value (not Java default).
     */
    public static String getUserAgent() {
        return userAgent;
    }

    /**
     * Same as {@link HttpURLConnection#setRequestProperty(java.lang.String, java.lang.String) HttpURLConnection.setRequestProperty("User-Agent", userAgent)}
     */
    public static void setUserAgent(String userAgent) {
        ConnectionManager.userAgent = userAgent;
    }

    /**
     * Corresponds to {@link HttpURLConnection#getFollowRedirects()}
     */
    public static boolean isFollowRedirects() {
        return followRedirects;
    }

    /**
     * Corresponds to {@link HttpURLConnection#setFollowRedirects(boolean)}
     */
    public static void setFollowRedirects(boolean followRedirects) {
        ConnectionManager.followRedirects = followRedirects;
    }

    /**
     * Returns a HttpUrlConnection set up with the defined settings.
     */
    public static HttpURLConnection getConnection(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(readTimeout);
        conn.setConnectTimeout(connTimeout);
        conn.setRequestProperty(HTTP_USER_AGENT, userAgent);
        HttpURLConnection.setFollowRedirects(followRedirects);
        return conn;
    }

    /**
     * Returns an InputStream to the given URL, possibly retrying the connection.
     * Sets the referer field to the given value.
     */
    public static InputStream getInputStream(URL url, int retries, String referer) throws IOException {
        InputStream is = null;
        int tried = 0;
        while (is == null) {
            try {
                HttpURLConnection httpConn = getConnection(url);
                if (referer != null) {
                    httpConn.setRequestProperty(HTTP_REFERER, referer);
                }
                is = httpConn.getInputStream();
            } catch (IOException ex) {
                if (tried++ == retries) {
                    throw ex;
                }
                try {
                    Thread.sleep(TIMEOUT_WAIT);
                } catch (InterruptedException ex1) {
                    Logger.getLogger(ConnectionManager.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }
        }
        return is;
    }

    /**
     * Returns an InputStream to the given URL, possibly retrying the connection.
     */
    public static InputStream getInputStream(URL url, int retries) throws IOException {
        return getInputStream(url, retries, null);
    }

    /**
     * Returns an InputStream to the given URL.
     */
    public static InputStream getInputStream(URL url) throws IOException {
        return getInputStream(url, 0, null);
    }
}
