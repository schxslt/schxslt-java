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

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;

import javax.xml.transform.Source;

import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

import java.util.Map;
import java.util.HashMap;

import java.util.List;
import java.util.ArrayList;

import java.util.logging.Logger;

/**
 * Main entry point for Schematron validation.
 *
 * <p>The class uses a functional interface to parametrize an instance. I.e. a call to a method starting with 'with'
 * creates a new parametrized instance.</p>
 */
public final class Schematron
{
    private static final Logger LOGGER = Logger.getLogger(Schematron.class.getName());
    private static final String[] XSLT10STEPS = {"/xslt/1.0/include.xsl", "/xslt/1.0/expand.xsl", "/xslt/1.0/compile-for-svrl.xsl"};
    private static final String[] XSLT20STEPS = {"/xslt/2.0/include.xsl", "/xslt/2.0/expand.xsl", "/xslt/2.0/compile-for-svrl.xsl"};

    private static final String QUERYBINDING_XSLT1 = "xslt";
    private static final String QUERYBINDING_XSLT2 = "xslt2";
    private static final String QUERYBINDING_XSLT3 = "xslt3";
    private static final String QUERYBINDING_DEFAULT = "";

    private static final String PHASE = "phase";

    private final Document schematron;

    private Map<String, Object> options = new HashMap<String, Object>();

    private TransformerFactory transformerFactory = TransformerFactory.newInstance();

    private String[] pipelineSteps;

    private Templates validatesTemplates;

    public Schematron (final Source schematron)
    {
        this(schematron, null);
    }

    public Schematron (final Source schematron, final String phase)
    {
        this.transformerFactory.setURIResolver(new Resolver());
        this.schematron = loadSchematron(schematron);

        if (phase != null) {
            options.put(PHASE, phase);
        }
    }

    private Schematron (final Schematron orig)
    {
        this.schematron = orig.schematron;
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
    public Schematron withOptions (final Map<String, Object> opts)
    {
        Schematron newSchematron = new Schematron(this);
        newSchematron.options.putAll(opts);
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
        newSchematron.options.put(PHASE, phase);
        return newSchematron;
    }

    public Result validate (final Source document) throws SchematronException
    {
        return validate(document, null);
    }

    /**
     * Performs the validation and returns the validation result.
     *
     * @param  document   The document to validate
     * @param  parameters Parameters for the validation stylesheet
     * @return The validation result
     *
     * @throws SchematronException A checked exception occured during validation
     */
    public Result validate (final Source document, final Map<String, Object> parameters) throws SchematronException
    {
        try {
            if (validatesTemplates == null) {
                synchronized (this) {
                    validatesTemplates = transformerFactory.newTemplates(new DOMSource(getValidationStylesheet()));
                }
            }
            Transformer validation = validatesTemplates.newTransformer();
            if (parameters != null) {
                for (Map.Entry<String, Object> param : parameters.entrySet()) {
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
     * @return The compiled validation stylesheet
     *
     * @throws SchematronException If compiling the validation stylesheet fails
     */
    public Document getValidationStylesheet () throws SchematronException
    {
        return compile();
    }

    private Document loadSchematron (final Source source)
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
            throw new RuntimeException("Error creating the Schematron document", e);
        }
    }

    private Document compile () throws SchematronException
    {
        try {
            Transformer[] pipeline;

            if (pipelineSteps == null) {

                String queryBinding = schematron.getDocumentElement().getAttribute("queryBinding").toLowerCase();
                switch (queryBinding) {
                case QUERYBINDING_DEFAULT:
                case QUERYBINDING_XSLT1:
                    pipelineSteps = XSLT10STEPS;
                    break;
                case QUERYBINDING_XSLT2:
                case QUERYBINDING_XSLT3:
                    pipelineSteps = XSLT20STEPS;
                    break;
                default:
                    throw new SchematronException("Unsupported query language: " + queryBinding);
                }
                LOGGER.info(String.format("Query binding %s found, using %s", queryBinding, String.join(", ", pipelineSteps)));
            }

            pipeline = createPipeline(pipelineSteps);

            String systemId = schematron.getDocumentURI();
            DOMSource schemaSource = new DOMSource(schematron, systemId);

            Document stylesheet = applyPipeline(pipeline, schemaSource);
            stylesheet.setDocumentURI(systemId);

            return stylesheet;

        } catch (TransformerException e) {
            throw new SchematronException("Error compiling Schematron to transformation stylesheet", e);
        }
    }

    private Document applyPipeline (final Transformer[] steps, final Source document) throws TransformerException
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

    private Transformer[] createPipeline (final String[] steps) throws TransformerException
    {
        final URIResolver resolver = transformerFactory.getURIResolver();
        final List<Transformer> templates = new ArrayList<Transformer>();

        for (int i = 0; i < steps.length; i++) {
            final Source source = resolver.resolve(steps[i], null);
            final Transformer transformer = transformerFactory.newTransformer(source);
            for (Map.Entry<String, Object> param : options.entrySet()) {
                transformer.setParameter(param.getKey(), param.getValue());
            }
            templates.add(transformer);
        }

        return templates.toArray(new Transformer[templates.size()]);
    }
}
