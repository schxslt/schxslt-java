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

import java.util.logging.Logger;

import java.nio.file.Paths;

/**
 * Main entry point for Schematron validation.
 *
 * The class uses a functional interface to parametrize an instance. I.e. a call to a method starting with 'with'
 * creates a new parametrized instance.
 */
public final class Schematron
{
    static final Logger log = Logger.getLogger(Schematron.class.getName());
    static final String[] xslt10steps = {"/xslt/1.0/include.xsl", "/xslt/1.0/expand.xsl", "/xslt/1.0/compile-for-svrl.xsl"};
    static final String[] xslt20steps = {"/xslt/2.0/include.xsl", "/xslt/2.0/expand.xsl", "/xslt/2.0/compile-for-svrl.xsl"};

    final Document schematron;

    Resolver resolver = new Resolver();

    Map<String,Object> options = new HashMap<String,Object>();

    /**
     * Threadsafe as long as you don't reconfigure
     */
    private static final TransformerFactory TRANSFORMER_FACTORY = TransformerFactory.newInstance();

    TransformerFactory transformerFactory = TRANSFORMER_FACTORY;

    Document validationStylesheet;

    String[] pipelineSteps;

    public Schematron (final Source schematron)
    {
        this(schematron, null);
    }

    public Schematron (final Source schematron, final String phase)
    {
        this.schematron = loadSchematron(schematron);

        if (phase != null) {
            options.put("phase", phase);
        }

        transformerFactory.setURIResolver(resolver);
    }

    Schematron (final Schematron orig)
    {
        this.schematron = orig.schematron;
        this.resolver = orig.resolver;
        this.options = orig.options;
        this.transformerFactory = orig.transformerFactory;
    }

    public static Schematron newInstance (final Source schematron)
    {
        return new Schematron(schematron);
    }

    public static Schematron newInstance (final Source schematron, final String phase)
    {
        return new Schematron(schematron, phase);
    }

    /**
     * Return a new instance with the specified compiler options.
     *
     * @param  opts Compiler options
     * @return Parametrized instance
     */
    public Schematron withOptions (final Map<String,Object> opts)
    {
        Schematron newSchematron = new Schematron(this);
        newSchematron.options = opts;
        return newSchematron;
    }

    /**
     * Return a new instance with the specified transformer factory.
     *
     * @param  factory Transformer factory
     * @return Parametrized instance
     */
    public Schematron withTransformerFactory (final TransformerFactory factory)
    {
        Schematron newSchematron = new Schematron(this);
        newSchematron.transformerFactory = factory;
        return newSchematron;
    }

    /**
     * Returns a new instance for the specified compilation pipeline .
     *
     * @param  steps Stylesheets used to create the validation stylesheet
     * @return Parametrized instance
     */
    public Schematron withPipelineSteps (final String[] steps)
    {
        if (steps.length == 0) {
            throw new IllegalArgumentException("A transformation pipeline must have a least one step");
        }
        Schematron newSchematron = new Schematron(this);
        newSchematron.pipelineSteps = steps;
        return newSchematron;
    }

    /**
     * Returns a new instance for the specified validation phase.
     *
     * @param  phase Validation phase
     * @return Parametrized instance
     */
    public Schematron withPhase (final String phase)
    {
        Schematron newSchematron = new Schematron(this);
        newSchematron.options.put("phase", phase);
        return newSchematron;
    }

    public Result validate (final Source document) throws SchematronException
    {
        return validate(document, null);
    }

    /**
     * Performs the validation and returns the validation result.
     *
     * @throws SchematronException A checked exception occured during validation
     *
     * @param  document   The document to validate
     * @param  parameters Parameters for the validation stylesheet
     * @return The validation result
     */
    public Result validate (final Source document, final Map<String,Object> parameters) throws SchematronException
    {
        try {
            Transformer validation = transformerFactory.newTransformer(new DOMSource(getValidationStylesheet()));
            if (parameters != null) {
                for (Map.Entry<String,Object> param : parameters.entrySet()) {
                    validation.setParameter(param.getKey(), param.getValue());
                }
            }

            DOMResult result = new DOMResult();
            validation.transform(document, result);

            return new Result((Document)result.getNode());

        } catch (TransformerException e) {
            throw new SchematronException("Error running transformation stylesheet", e);
        }
    }

    /**
     * Compiles and returns the validation stylesheet.
     *
     * @throws SchematronException If compiling the validation stylesheet fails
     *
     * @return The compiled validation stylesheet
     */
    public Document getValidationStylesheet () throws SchematronException
    {
        if (validationStylesheet == null) {
            validationStylesheet = compile();
        }
        return validationStylesheet;
    }

    Document loadSchematron (final Source source)
    {
        String systemId = source.getSystemId();
        log.fine("Schematron base URI is " + systemId);

        try {
            Transformer identityTransformer = transformerFactory.newTransformer();
            DOMResult schema = new DOMResult();
            identityTransformer.transform(source, schema);

            Document schemaDocument = (Document)schema.getNode();
            schemaDocument.setDocumentURI(systemId);

            return schemaDocument;
        } catch (TransformerException e) {
            throw new RuntimeException("Error creating the Schematron document", e);
        }
    }

    Document compile () throws SchematronException
    {
        try {
            Transformer[] pipeline;

            if (pipelineSteps == null) {

                String queryBinding = schematron.getDocumentElement().getAttribute("queryBinding").toLowerCase();
                switch (queryBinding) {
                case "":
                case "xslt":
                    pipelineSteps = xslt10steps;
                    break;
                case "xslt2":
                case "xslt3":
                    pipelineSteps = xslt20steps;
                    break;
                default:
                    throw new SchematronException("Unsupported query language: " + queryBinding);
                }
            }

            pipeline = createPipeline(pipelineSteps);

            String systemId = schematron.getDocumentURI();
            DOMSource schemaSource = new DOMSource(schematron, systemId);
            log.fine("Schematron base URI is " + schemaSource.getSystemId());

            Document stylesheet = applyPipeline(pipeline, schemaSource);
            stylesheet.setDocumentURI(systemId);
            log.fine("Schematron base URI is " + stylesheet.getDocumentURI());

            return stylesheet;

        } catch (TransformerException e) {
            throw new SchematronException("Error compiling Schematron to transformation stylesheet", e);
        }
    }

    Document applyPipeline (final Transformer[] steps, final Source document) throws TransformerException
    {
        DOMResult result = null;
        Source source = document;

        for (int i = 0; i < steps.length; i++) {
            result = new DOMResult();
            steps[i].transform(source, result);
            source = new DOMSource(result.getNode(), source.getSystemId());
        }

        return (Document)result.getNode();
    }

    Transformer[] createPipeline (final String[] steps) throws TransformerException
    {
        final List<Transformer> templates = new ArrayList<Transformer>();

        for (int i = 0; i < steps.length; i++) {
            final Source source = resolver.resolve(steps[i], null);
            final Transformer transformer = transformerFactory.newTransformer(source);
            for (Map.Entry<String,Object> param : options.entrySet()) {
                transformer.setParameter(param.getKey(), param.getValue());
            }
            templates.add(transformer);
        }

        return templates.toArray(new Transformer[templates.size()]);
    }
}
