package com.ujcms.generator.mybatis;

import org.mybatis.generator.api.GeneratedXmlFile;
import org.mybatis.generator.config.MergeConstants;
import org.mybatis.generator.exception.ShellException;
import org.mybatis.generator.internal.DomWriter;
import org.w3c.dom.*;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.mybatis.generator.internal.util.messages.Messages.getString;

public class XmlFileMerger {
    private XmlFileMerger() {
    }

    private static class NullEntityResolver implements EntityResolver {
        /**
         * returns an empty reader. This is done so that the parser doesn't
         * attempt to read a DTD. We don't need that support for the merge and
         * it can cause problems on systems that aren't Internet connected.
         */
        @Override
        public InputSource resolveEntity(String publicId, String systemId) {
            StringReader sr = new StringReader(""); //$NON-NLS-1$

            return new InputSource(sr);
        }
    }

    public static String getMergedSource(GeneratedXmlFile generatedXmlFile, File existingFile) throws ShellException {
        try {
            return getMergedSource(new InputSource(new StringReader(generatedXmlFile.getFormattedContent())),
                    new InputSource(new InputStreamReader(new FileInputStream(existingFile), StandardCharsets.UTF_8)),
                    existingFile.getName());
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new ShellException(getString("Warning.13", existingFile.getName()), e);
        }
    }

    public static String getMergedSource(InputSource newFile, InputSource existingFile, String existingFileName)
            throws IOException, SAXException, ParserConfigurationException, ShellException {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(new XmlFileMerger.NullEntityResolver());

        Document existingDocument = builder.parse(existingFile);
        Document newDocument = builder.parse(newFile);

        DocumentType newDocType = newDocument.getDoctype();
        DocumentType existingDocType = existingDocument.getDoctype();

        if (!newDocType.getName().equals(existingDocType.getName())) {
            throw new ShellException(getString("Warning.12", existingFileName));
        }

        Element existingRootElement = existingDocument.getDocumentElement();
        Element newRootElement = newDocument.getDocumentElement();

        // reconcile the root element attributes -
        // take all attributes from the new element and add to the existing
        // element

        // remove all attributes from the existing root element
        NamedNodeMap attributes = existingRootElement.getAttributes();
        int attributeCount = attributes.getLength();
        for (int i = attributeCount - 1; i >= 0; i--) {
            Node node = attributes.item(i);
            existingRootElement.removeAttribute(node.getNodeName());
        }

        // add attributes from the new root node to the old root node
        attributes = newRootElement.getAttributes();
        attributeCount = attributes.getLength();
        for (int i = 0; i < attributeCount; i++) {
            Node node = attributes.item(i);
            existingRootElement.setAttribute(node.getNodeName(), node.getNodeValue());
        }

        // remove the old generated elements and any
        // white space before the old nodes
        List<Node> nodesToDelete = new ArrayList<>();
        NodeList children = existingRootElement.getChildNodes();
        int length = children.getLength();
        for (int i = 0; i < length; i++) {
            Node node = children.item(i);
            if (isGeneratedNode(node)) {
                nodesToDelete.add(node);
            } else if (isWhiteSpace(node) && isGeneratedNode(children.item(i + 1))) {
                nodesToDelete.add(node);
            }
        }

        for (Node node : nodesToDelete) {
            existingRootElement.removeChild(node);
        }

        // add the new generated elements
        children = newRootElement.getChildNodes();
        length = children.getLength();
        Node firstChild = existingRootElement.getFirstChild();
        for (int i = 0; i < length; i++) {
            Node node = children.item(i);
            // 只合并 Column_List、insert、update
            if (!(node instanceof Element)) continue;
            Element element = (Element) node;
            String id = element.getAttribute("id");
            if (!"BaseResultMap".equals(id) && !"ResultMapWithBLOBs".equals(id) && !"Column_List".equals(id) && !"insert".equals(id) && !"update".equals(id))
                continue;
            // don't add the last node if it is only white space
            if (i == length - 1 && isWhiteSpace(node)) {
                break;
            }

            Node newNode = existingDocument.importNode(node, true);
            // BaseResultMap 放到第一位，其它放到最后。
            if (firstChild != null && ("BaseResultMap".equals(id) || "ResultMapWithBLOBs".equals(id))) {
                existingRootElement.insertBefore(newNode, firstChild);
            } else {
                existingRootElement.appendChild(newNode);
            }
        }

        // pretty print the result
        return prettyPrint(existingDocument);
    }

    private static String prettyPrint(Document document) throws ShellException {
        DomWriter dw = new DomWriter();
        return dw.toString(document);
    }

    private static boolean isGeneratedNode(Node node) {
        // 只合并 Column_List、select、insert、update
        if (!(node instanceof Element)) return false;
        Element element = (Element) node;
        String id = element.getAttribute("id");
        return "BaseResultMap".equals(id) || "ResultMapWithBLOBs".equals(id) || "Column_List".equals(id) || "insert".equals(id) || "update".equals(id);
        // return node != null
        //         && node.getNodeType() == Node.ELEMENT_NODE
        //         && (isOldFormatNode(node) || isNewFormatNode(node));
    }

    private static boolean isOldFormatNode(Node node) {
        Element element = (Element) node;
        String id = element.getAttribute("id"); //$NON-NLS-1$
        if (id != null) {
            return MergeConstants.idStartsWithPrefix(id);
        }

        return false;
    }

    private static boolean isNewFormatNode(Node node) {
        // check for new node format - if the first non-whitespace node
        // is an XML comment, and the comment includes
        // one of the old element tags,
        // then it is a generated node
        NodeList children = node.getChildNodes();
        int length = children.getLength();
        for (int i = 0; i < length; i++) {
            Node childNode = children.item(i);
            if (childNode != null && childNode.getNodeType() == Node.COMMENT_NODE) {
                String commentData = ((Comment) childNode).getData();
                return MergeConstants.commentContainsTag(commentData);
            }
        }

        return false;
    }

    private static boolean isWhiteSpace(Node node) {
        boolean rc = false;

        if (node != null && node.getNodeType() == Node.TEXT_NODE) {
            Text tn = (Text) node;
            if (tn.getData().trim().length() == 0) {
                rc = true;
            }
        }

        return rc;
    }
}
