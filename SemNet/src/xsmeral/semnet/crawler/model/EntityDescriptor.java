package xsmeral.semnet.crawler.model;

import xsmeral.semnet.xstream.EntityDescConverter;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import java.util.Collection;
import java.util.regex.Pattern;
import xsmeral.semnet.crawler.HTMLCrawler;
import xsmeral.semnet.manager.Configuration;

/**
 * Part of the configuration of {@link HTMLCrawler}, describes one entity type of a host.
 * <br />
 * Contains:
 * <br />
 * <ul>
 *  <li>URL pattern</li>
 *  <li>update frequency of the pages identified by the URL pattern</li>
 *  <li>list of scraper configurations - a class that extracts data from a web page</li>
 *  <li>&quot;weight&quot; of this entity - a measure of preference (defaults to 1)</li>
 * </ul>
 * In runtime, the reference to parent {@link HostDescriptor} is kept.
 * The hashCode is pre-computed for fast map lookup.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
@XStreamAlias("entity")
@XStreamConverter(EntityDescConverter.class)
public class EntityDescriptor {

    @XStreamAlias("pattern")
    private final Pattern urlPattern;
    private final int updateFreq;
    @XStreamAlias("scraper")
    private final Collection<Configuration> scrapers;
    private final int weight;
    private final transient int hashCode;
    private transient HostDescriptor hostDesc;

    /**
     * Initializes all fields
     */
    public EntityDescriptor(Pattern urlPattern, int updateFreq, Collection<Configuration> scrapers, int weight) {
        this.urlPattern = urlPattern;
        this.updateFreq = updateFreq;
        this.scrapers = scrapers;
        this.weight = weight;
        this.hashCode = hashCodeInternal();
    }

    /**
     * Returns the scraper classes that processes this entity type.
     */
    public Collection<Configuration> getScrapers() {
        return scrapers;
    }

    /**
     * Returns the URL pattern that identifies this entity type.
     */
    public Pattern getUrlPattern() {
        return urlPattern;
    }

    /**
     * Returns the update frequency for this entity type, in seconds.
     */
    public int getUpdateFreq() {
        return updateFreq;
    }

    /**
     * Returns the owning HostDescriptor.
     */
    public HostDescriptor getHostDesc() {
        return hostDesc;
    }

    /**
     * Sets the owning HostDescriptor.
     */
    public void setHostDesc(HostDescriptor hostDesc) {
        this.hostDesc = hostDesc;
    }

    /**
     * Returns the &quot;weight&quot; of this entity, a measue of preference.
     */
    public int getWeight() {
        return weight;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final EntityDescriptor other = (EntityDescriptor) obj;
        if (this.urlPattern != other.urlPattern && (this.urlPattern == null || !this.urlPattern.equals(other.urlPattern))) {
            return false;
        }
        if (this.scrapers != other.scrapers && (this.scrapers == null || !this.scrapers.equals(other.scrapers))) {
            return false;
        }
        return true;
    }

    private int hashCodeInternal() {
        int hash = 7;
        hash = 43 * hash + (this.urlPattern != null ? this.urlPattern.hashCode() : 0);
        hash = 43 * hash + (this.scrapers != null ? this.scrapers.hashCode() : 0);
        return hash;
    }

    /**
     * Returns (pre-computed) hash code.
     */
    @Override
    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public String toString() {
        return "EntityDescriptor{host=" + hostDesc.getBaseURL() + ", urlPattern=" + urlPattern + '}';
    }
}
