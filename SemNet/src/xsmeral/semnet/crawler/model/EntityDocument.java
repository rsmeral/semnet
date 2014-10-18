package xsmeral.semnet.crawler.model;

import org.htmlcleaner.TagNode;
import xsmeral.semnet.crawler.HTMLCrawler;

/**
 * Container for documents retrieved by {@link HTMLCrawler}, passed to a scraper (wrapper).
 * <br />
 * Contains base URL for resolving links, absolute URL of this document,
 * a descriptor of the contained entity and a TagNode containing parsed
 * document tree.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 * @see <a href="http://htmlcleaner.sourceforge.net/">HtmlCleaner</a>
 */
public class EntityDocument {

    private String baseURL;
    private String url;
    private EntityDescriptor entDesc;
    private TagNode document;

    public EntityDocument() {
    }

    /**
     * Initializes all fields
     */
    public EntityDocument(String baseURL, String url, EntityDescriptor entDesc, TagNode document) {
        this.baseURL = baseURL;
        this.url = url;
        this.entDesc = entDesc;
        this.document = document;
    }

    /**
     * Returns the TagNode (<a href="http://htmlcleaner.sourceforge.net/">HtmlCleaner</a>)
     * containing the document tree.
     */
    public TagNode getDocument() {
        return document;
    }

    public void setDocument(TagNode document) {
        this.document = document;
    }

    /**
     * Returns the base URL of the host, where this document originated.
     */
    public String getBaseURL() {
        return baseURL;
    }

    public void setBaseURL(String baseURL) {
        this.baseURL = baseURL;
    }

    /**
     * Returns absolute URL of the document.
     */
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Returns the entity descriptor describing this document.
     */
    public EntityDescriptor getEntityDescriptor() {
        return entDesc;
    }

    public void setEntityDescriptor(EntityDescriptor entDesc) {
        this.entDesc = entDesc;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final EntityDocument other = (EntityDocument) obj;
        if ((this.url == null) ? (other.url != null) : !this.url.equals(other.url)) {
            return false;
        }
        if (this.document != other.document && (this.document == null || !this.document.equals(other.document))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + (this.url != null ? this.url.hashCode() : 0);
        hash = 23 * hash + (this.document != null ? this.document.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return "EntityDocument{" + "url=" + url + '}';
    }

}
