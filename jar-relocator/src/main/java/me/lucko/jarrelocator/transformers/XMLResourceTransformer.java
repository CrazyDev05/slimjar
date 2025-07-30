/*
 * Copyright Apache Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.lucko.jarrelocator.transformers;

import me.lucko.jarrelocator.Relocation;
import me.lucko.jarrelocator.ResourceTransformer;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class XMLResourceTransformer implements ResourceTransformer {
    private final Transformer transformer;
    private final DocumentBuilder builder;
    private final Map<String, File> xmlFiles = new LinkedHashMap<>();

    public XMLResourceTransformer() {
        try {
            this.transformer = TransformerFactory.newInstance().newTransformer();
            this.builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (final ParserConfigurationException | TransformerConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean shouldTransformResource(String resource) {
        return resource.endsWith(".xml");
    }

    @Override
    public void processResource(String resource, InputStream inputStream, Collection<Relocation> rules) throws IOException {
        try {
            Document document = builder.parse(inputStream);
            processNode(document, rules);

            File tempFile = File.createTempFile("xml-transformer", ".xml");
            tempFile.deleteOnExit();
            
            try (final var outputStream = new FileOutputStream(tempFile)) {
                transformer.transform(new DOMSource(document), new StreamResult(outputStream));

                xmlFiles.put(resource, tempFile);
            } catch (TransformerException e) {
                throw new IOException("Failed to transform XML document", e);
            }
        } catch (final SAXException e) {
            throw new IOException(e);
        }
    }
    
    private void processNode(final Node node, final Collection<Relocation> rules) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            final Text textNode = (Text) node;
            final String content = textNode.getNodeValue();
            final String relocatedContent = relocateIfPossible(content, rules);

            if (!content.equals(relocatedContent)) {
                textNode.setNodeValue(relocatedContent);
            }
        } else if (node.getNodeType() == Node.ELEMENT_NODE) {
            final NamedNodeMap attributes = node.getAttributes();
            if (attributes != null) {
                for (int i = 0; i < attributes.getLength(); i++) {
                    final Node attr = attributes.item(i);
                    final String value = attr.getNodeValue();
                    final String relocatedValue = relocateIfPossible(value, rules);

                    if (!value.equals(relocatedValue)) {
                        attr.setNodeValue(relocatedValue);
                    }
                }
            }
        }

        final NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            processNode(children.item(i), rules);
        }
    }
    
    private String relocateIfPossible(final String value, final Collection<Relocation> rules) {
        for (Relocation rule : rules) {
            if (rule.canRelocateClass(value)) {
                return rule.relocateClass(value);
            }
        }
        return value;
    }

    @Override
    public void writeOutput(final JarOutputStream jarOutputStream) throws IOException {
        for (final var entry : this.xmlFiles.entrySet()) {
            jarOutputStream.putNextEntry(new JarEntry(entry.getKey()));
            try (final var inputStream = new FileInputStream(entry.getValue())) {
                inputStream.transferTo(jarOutputStream);
            }
            jarOutputStream.closeEntry();
        }
    }
}
