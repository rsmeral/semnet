package xsmeral.semnet.util;

import java.util.ArrayList;
import java.util.List;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import xsmeral.semnet.scraper.AbstractScraper;

/**
 * Utility class for scrapers, simplifies XPath querying.
 * It is dependent on the HtmlCleaner API.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 * @see AbstractScraper
 */
public class XPathUtil {

    /**
     * Returns text content of a node. The node can be a String, a TagNode or a ContentNode.
     * In case of a TagNode, the first child of the node is returned. The result is trimmed.
     * @param node The node (a String, TagNode or ContentNode)
     * @return Text content of the node trimmed, or null
     * @see String#trim() 
     */
    public static String getText(Object node) {
        if (node instanceof String) {
            return (String) node;
        } else if (node instanceof TagNode) {
            return String.valueOf(((TagNode) node).getChildren().get(0)).trim();
        } else if (node instanceof ContentNode) {
            return ((ContentNode) node).toString().trim();
        }
        return null;
    }

    /**
     * Queries the supplied node with the supplied XPath expression and returns the text content.
     * @param node The node to query
     * @param xpath The query
     * @return Text content of the matched node, or null
     * @throws XPatherException
     */
    public static String queryText(TagNode node, String xpath) throws XPatherException {
        Object[] nodes = node.evaluateXPath(xpath);
        if (nodes.length > 0) {
            return getText(nodes[0]);
        }
        return null;
    }

    /**
     * Queries the supplied node with the supplied XPath expression
     * and returns list of text contents of the matched nodes.
     * @param node The node to query
     * @param xpath The query
     * @return List of text contents of the matched nodes, or an empty list
     * @throws XPatherException
     */
    public static List<String> queryTextNodes(TagNode node, String xpath) throws XPatherException {
        final Object[] objects = node.evaluateXPath(xpath);
        List<String> nodes = new ArrayList<String>(objects.length);
        for (Object obj : objects) {
            nodes.add(obj.toString());
        }
        return nodes;
    }

    /**
     * Queries the supplied node with the supplied XPath expression
     * and returns list of matching nodes
     * @param node The node to query
     * @param xpath The query
     * @return List of matching nodes
     * @throws XPatherException
     */
    public static List<TagNode> queryNodes(TagNode node, String xpath) throws XPatherException {
        final Object[] objects = node.evaluateXPath(xpath);
        List<TagNode> nodes = new ArrayList<TagNode>(objects.length);
        for (Object obj : objects) {
            if (obj instanceof TagNode) {
                nodes.add((TagNode) obj);
            }
        }
        return nodes;
    }
}
