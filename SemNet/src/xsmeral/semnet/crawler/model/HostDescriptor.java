package xsmeral.semnet.crawler.model;

import xsmeral.semnet.xstream.HostDescConverter;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import xsmeral.semnet.crawler.HTMLCrawler;

/**
 * The main configuration element of {@link HTMLCrawler}.
 * Contains:
 * <br />
 * <ul>
 *  <li>base URL</li>
 *  <li>arbitrary user-assigned name</li>
 *  <li>charset used</li>
 *  <li>crawl delay</li>
 *  <li>source URL preference</li>
 *  <li>patterns of source URLs, which are pages that themselves are not entity
 *      types, but contain links to entities</li>
 *  <li>update frequencies of the source URLs</li>
 *  <li>collection of {@link EntityDescriptor}s</li>
 * </ul>
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
@XStreamAlias("host")
@XStreamConverter(HostDescConverter.class)
public class HostDescriptor {

    private String baseURL;
    private String name;
    private String charset;
    private Integer crawlDelay;
    private boolean sourceFirst;
    @XStreamAlias("source")
    private Map<Pattern, Integer> sourceURLPatterns;
    @XStreamAlias("entities")
    private Collection<EntityDescriptor> entityDescriptors;

    /**
     * Creates empty source URL map and entity descriptor collection
     */
    public HostDescriptor() {
        this.sourceURLPatterns = new HashMap<Pattern, Integer>();
        this.entityDescriptors = new ArrayList<EntityDescriptor>();
    }

    /**
     * Initializes all fields
     */
    public HostDescriptor(String baseURL, String name, String charset, Integer crawlDelay, Boolean sourceFirst, Map<Pattern, Integer> sourceURLPatterns, Collection<EntityDescriptor> entityDescriptors) {
        this.baseURL = baseURL;
        this.name = name;
        this.charset = charset;
        this.crawlDelay = crawlDelay;
        this.sourceFirst = sourceFirst;
        this.sourceURLPatterns = sourceURLPatterns;
        this.entityDescriptors = entityDescriptors;
    }

    /**
     * Returns base URL of this host - the root level for crawling.
     */
    public String getBaseURL() {
        return baseURL;
    }

    public void setBaseURL(String baseURL) {
        this.baseURL = baseURL;
    }

    /**
     * Returns EntityDescriptors which represent entities in this host 
     * (pages that will be scraped)
     */
    public Collection<EntityDescriptor> getEntityDescriptors() {
        return entityDescriptors;
    }

    public void setEntityDescriptors(Collection<EntityDescriptor> entityDescriptors) {
        this.entityDescriptors = entityDescriptors;
        for(EntityDescriptor ed : entityDescriptors) {
            ed.setHostDesc(this);
        }
    }

    public void addEntityDescriptor(EntityDescriptor entityDescriptor) {
        this.entityDescriptors.add(entityDescriptor);
        entityDescriptor.setHostDesc(this);
    }

    /**
     * Returns (arbitrary, user-assigned) name of this host.
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns patterns of source URLs mapped to corresponding update frequencies.
     * Source URLs represent pages that are only crawled (searched for links), not scraped.
     */
    public Map<Pattern, Integer> getSourceURLPatterns() {
        return sourceURLPatterns;
    }

    public void setSourceURLPatterns(Map<Pattern, Integer> sourceURLPatterns) {
        this.sourceURLPatterns = sourceURLPatterns;
    }

    public void addSourceURLPattern(Pattern pattern, int updateFreq) {
        this.sourceURLPatterns.put(pattern, updateFreq);
    }

    /**
     * Returns the (user-defined) charset used by this host.
     * If the charset is not specified, the crawler tries to guess it from the content.
     */
    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    /**
     * Returns the crawl delay. Might be set to override the one found in the Robots Policy.
     */
    public Integer getCrawlDelay() {
        return crawlDelay;
    }

    public void setCrawlDelay(Integer crawlDelay) {
        this.crawlDelay = crawlDelay;
    }

    /**
     * Indicates whether source URLs should be crawled first
     */
    public boolean isSourceFirst() {
        return sourceFirst;
    }

    public void setSourceFirst(boolean sourceFirst) {
        this.sourceFirst = sourceFirst;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HostDescriptor other = (HostDescriptor) obj;
        if ((this.baseURL == null) ? (other.baseURL != null) : !this.baseURL.equals(other.baseURL)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 29 * hash + (this.baseURL != null ? this.baseURL.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return "HostDescriptor{" + name + ": " + baseURL + '}';
    }
}
