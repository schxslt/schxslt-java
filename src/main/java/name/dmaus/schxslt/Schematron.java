/*
 * Copyright (C) 2019 by David Maus <dmaus@dmaus.name>
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

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;

import javax.xml.transform.Source;

import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

import java.util.Map;
import java.util.HashMap;

import java.util.List;
import java.util.ArrayList;

class Schematron
{

    final Source schematron;
    final String phase;

    final String[] xslt10steps = {"/xslt/1.0/include.xsl", "/xslt/1.0/expand.xsl", "/xslt/1.0/compile-for-svrl.xsl"};
    final String[] xslt20steps = {"/xslt/2.0/include.xsl", "/xslt/2.0/expand.xsl", "/xslt/2.0/compile-for-svrl.xsl"};

    final TransformerFactory transformerFactory = TransformerFactory.newInstance();
    final Resolver resolver = new Resolver();

    Schematron (final Source schematron)
    {
        this(schematron, null);
    }

    Schematron (final Source schematron, final String phase)
    {
        this.schematron = schematron;
        this.phase = phase;

        transformerFactory.setURIResolver(resolver);
    }

    public Result validate (final Source document)
    {
        return validate(document, null);
    }

    public Result validate (final Source document, final Map<String,Object> parameters)
    {
        try {
            Transformer validation = compile();
            if (parameters != null) {
                for (Map.Entry<String,Object> param : parameters.entrySet()) {
                    validation.setParameter(param.getKey(), param.getValue());
                }
            }

            DOMResult result = new DOMResult();
            validation.transform(document, result);

            return new Result((Document)result.getNode());

        } catch (TransformerException e) {
            throw new RuntimeException("Error running transformation stylesheet", e);
        }
    }

    Transformer compile ()
    {
        try {
            Transformer[] pipeline;

            Map<String,Object> parameters = new HashMap<String,Object>();
            if (phase != null) {
                parameters.put("phase", phase);
            }

            Transformer identityTransformer = transformerFactory.newTransformer();
            DOMResult schema = new DOMResult();
            identityTransformer.transform(schematron, schema);

            Document schemaDocument = (Document)schema.getNode();
            String queryBinding = schemaDocument.getDocumentElement().getAttribute("queryBinding").toLowerCase();
            switch (queryBinding) {
            case "":
            case "xslt":
                pipeline = createPipeline(xslt10steps, parameters);
                break;
            case "xslt2":
            case "xslt3":
                pipeline = createPipeline(xslt20steps, parameters);
                break;
            default:
                throw new RuntimeException("Unsupported query language: " + queryBinding);
            }

            Document validationStylesheet = applyPipeline(pipeline, new DOMSource(schemaDocument));
            return transformerFactory.newTransformer(new DOMSource(validationStylesheet));

        } catch (TransformerException e) {
            throw new RuntimeException("Error compiling Schematron to transformation stylesheet", e);
        }
    }

    Document applyPipeline (final Transformer[] steps, final Source document) throws TransformerException
    {
        DOMResult result = null;
        Source source = document;

        for (int i = 0; i < steps.length; i++) {
            result = new DOMResult();
            steps[i].transform(source, result);
            source = new DOMSource(result.getNode(), result.getSystemId());
        }

        return (Document)result.getNode();
    }

    Transformer[] createPipeline (final String[] steps, final Map<String, Object> parameters) throws TransformerException
    {
        final List<Transformer> templates = new ArrayList<Transformer>();

        for (int i = 0; i < steps.length; i++) {
            final Source source = resolver.resolve(steps[i], null);
            final Transformer transformer = transformerFactory.newTransformer(source);
            for (Map.Entry<String,Object> param : parameters.entrySet()) {
                transformer.setParameter(param.getKey(), param.getValue());
            }
            templates.add(transformer);
        }

        return templates.toArray(new Transformer[templates.size()]);
    }
}
