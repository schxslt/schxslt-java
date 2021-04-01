/*
 * Copyright (C) 2019-2021 by David Maus <dmaus@dmaus.name>
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package name.dmaus.schxslt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

import net.jcip.annotations.ThreadSafe;

/**
 * Compile validation stylesheet from a Schematron schema.
 */
@ThreadSafe
public final class Compiler
{
    private static final String[] XSLT10STEPS = {"/xslt/1.0/include.xsl", "/xslt/1.0/expand.xsl", "/xslt/1.0/compile-for-svrl.xsl"};
    private static final String[] XSLT20STEPS = {"/xslt/2.0/include.xsl", "/xslt/2.0/expand.xsl", "/xslt/2.0/compile-for-svrl.xsl"};

    private static final String QUERYBINDING_XSLT1 = "xslt";
    private static final String QUERYBINDING_XSLT2 = "xslt2";
    private static final String QUERYBINDING_XSLT3 = "xslt3";
    private static final String QUERYBINDING_DEFAULT = "";

    private final TransformerFactory transformerFactory;

    public Compiler ()
    {
        transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setURIResolver(new Resolver());
    }

    public Compiler (final TransformerFactory transformerFactory)
    {
        this.transformerFactory = transformerFactory;
    }

    public Document compile (final Source schema, final Map<String, Object> options) throws SchematronException
    {
        Document schematron = loadSchematron(schema);
        try {
            List<Transformer> pipeline;
            
            String queryBinding = schematron.getDocumentElement().getAttribute("queryBinding").toLowerCase();
            switch (queryBinding) {
            case QUERYBINDING_DEFAULT:
            case QUERYBINDING_XSLT1:
                pipeline = createPipeline(XSLT10STEPS, options);
                break;
            case QUERYBINDING_XSLT2:
            case QUERYBINDING_XSLT3:
                pipeline = createPipeline(XSLT20STEPS, options);
                break;
            default:
                throw new SchematronException("Unsupported query language: " + queryBinding);
            }

            String systemId = schematron.getDocumentURI();
            DOMSource schemaSource = new DOMSource(schematron, systemId);
            
            Document stylesheet = applyPipeline(pipeline, schemaSource);
            stylesheet.setDocumentURI(systemId);
            return stylesheet;
        } catch (TransformerException e) {
            throw new SchematronException("Error compiling Schematron to transformation stylesheet", e);
        }
    }

    private Document loadSchematron (final Source source) throws SchematronException
    {
        String systemId = source.getSystemId();

        try {
            Transformer identityTransformer = transformerFactory.newTransformer();
            DOMResult schema = new DOMResult();
            identityTransformer.transform(source, schema);

            Document schemaDocument = (Document)schema.getNode();
            schemaDocument.setDocumentURI(systemId);

            return schemaDocument;
        } catch (TransformerException e) {
            throw new SchematronException("Error creating the Schematron document", e);
        }
    }

    private List<Transformer> createPipeline (final String[] steps, final Map<String, Object> options) throws TransformerException
    {
        final Resolver resolver = new Resolver();
        final List<Transformer> templates = new ArrayList<Transformer>();

        for (String step : steps) {
            final Source source = resolver.resolve(step, null);
            final Transformer transformer = transformerFactory.newTransformer(source);
            if (options != null) {
                for (Map.Entry<String, Object> param : options.entrySet()) {
                    transformer.setParameter(param.getKey(), param.getValue());
                }
            }
            templates.add(transformer);
        }

        return templates;
    }

    private Document applyPipeline (final List<Transformer> steps, final Source document) throws TransformerException
    {
        DOMResult result = null;
        Source source = document;

        for (Transformer transformer : steps) {
            result = new DOMResult();
            transformer.transform(source, result);
            source = new DOMSource(result.getNode(), source.getSystemId());
        }

        return (Document)result.getNode();
    }

}
