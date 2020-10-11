/*
 * Copyright (C) 2019,2020 by David Maus <dmaus@dmaus.name>
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
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Logger;

import net.jcip.annotations.GuardedBy;

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

    @GuardedBy("this")
    private TransformerFactory transformerFactory;

    @GuardedBy("this")
    private List<String> pipelineSteps;

    @GuardedBy("this")
    private Templates validatesTemplates;

    public Schematron (final Source schematron)
    {
        this(schematron, null, null);
    }

    public Schematron (final Source schematron, final String phase)
    {
        this(schematron, phase, null);
    }

    public Schematron (final Source schematron, final String phase, final TransformerFactory transformerFactory)
    {
        this(schematron, phase, transformerFactory, null);
    }

    /**
     * Bottleneck constructor, Source may not be null.
     *
     * @param schematron May not be null
     * @param phase Validation phase
     * @param transformerFactory TransformerFactory to use, possibly with custom URIResolver
     * @param options Compiler options
     */
    public Schematron (final Source schematron, final String phase, final TransformerFactory transformerFactory,
                       final Map<String, Object> options)
    {
        if (schematron == null) {
            throw new IllegalArgumentException("Source may not be null");
        }
        if (transformerFactory == null) {
            this.transformerFactory = TransformerFactory.newInstance();
            this.transformerFactory.setURIResolver(new Resolver());
        } else {
            this.transformerFactory = transformerFactory;
            // resolver may be null
            if (transformerFactory.getURIResolver() == null) {
                transformerFactory.setURIResolver(new Resolver());
            }
        }
        if (options != null) {
            this.options.putAll(options);
        }
        if (phase != null) {
            this.options.put(PHASE, phase);
        }
        this.schematron = loadSchematron(schematron);
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

    public static Schematron newInstance (final Source schematron, final String phase, final TransformerFactory transformerFactory)
    {
        return new Schematron(schematron, phase, transformerFactory);
    }

    public static Schematron newInstance (final Source schematron, final String phase, final TransformerFactory transformerFactory, final Map<String, Object> options)
    {
        return new Schematron(schematron, phase, transformerFactory, options);
    }

    public synchronized void setPipelineSteps (final List<String> steps)
    {
        pipelineSteps = Collections.unmodifiableList(steps);
    }

    /**
     * Return a new instance with the specified compiler options.
     *
     * @param  opts Compiler options
     * @return Parametrized instance
     * @deprecated use constructors instead
     */
    @Deprecated public Schematron withOptions (final Map<String, Object> opts)
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
     * @deprecated use constructors instead
     */
    @Deprecated public Schematron withTransformerFactory (final TransformerFactory factory)
    {
        Schematron newSchematron = new Schematron(this);
        newSchematron.setTransformerFactory(factory);
        return newSchematron;
    }

    /**
     * Returns a new instance for the specified compilation pipeline .
     *
     * @param  steps Stylesheets used to create the validation stylesheet
     * @return Parametrized instance
     * @deprecated use constructors instead
     */
    @Deprecated public Schematron withPipelineSteps (final String[] steps)
    {
        if (steps.length == 0) {
            throw new IllegalArgumentException("A transformation pipeline must have a least one step");
        }
        Schematron newSchematron = new Schematron(this);
        newSchematron.setPipelineSteps(Arrays.asList(steps));
        return newSchematron;
    }

    /**
     * Returns a new instance for the specified validation phase.
     *
     * @param  phase Validation phase
     * @return Parametrized instance
     * @deprecated use constructors instead
     */
    @Deprecated public Schematron withPhase (final String phase)
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
        return createValidator().validate(document, parameters);
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

    public synchronized SchematronValidator createValidator () throws SchematronException
    {
        try {
            if (validatesTemplates == null) {
                validatesTemplates = transformerFactory.newTemplates(new DOMSource(getValidationStylesheet()));
            }
            return new SchematronValidator(validatesTemplates);
        } catch (TransformerException e) {
            throw new SchematronException("Error compiling validation stylesheet", e);
        }
    }

    private synchronized void setTransformerFactory (final TransformerFactory transformerFactory)
    {
        this.transformerFactory = transformerFactory;
    }

    private synchronized Document loadSchematron (final Source source)
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

            synchronized (this) {
                if (pipelineSteps == null) {

                    String queryBinding = schematron.getDocumentElement().getAttribute("queryBinding").toLowerCase();
                    switch (queryBinding) {
                    case QUERYBINDING_DEFAULT:
                    case QUERYBINDING_XSLT1:
                        pipelineSteps = Arrays.asList(XSLT10STEPS);
                        break;
                    case QUERYBINDING_XSLT2:
                    case QUERYBINDING_XSLT3:
                        pipelineSteps = Arrays.asList(XSLT20STEPS);
                        break;
                    default:
                        throw new SchematronException("Unsupported query language: " + queryBinding);
                    }
                    LOGGER.info(String.format("Query binding %s found, using %s", queryBinding, String.join(", ", pipelineSteps)));
                }
            }

            List<Transformer> pipeline = createPipeline();

            String systemId = schematron.getDocumentURI();
            DOMSource schemaSource = new DOMSource(schematron, systemId);

            Document stylesheet = applyPipeline(pipeline, schemaSource);
            stylesheet.setDocumentURI(systemId);

            return stylesheet;

        } catch (TransformerException e) {
            throw new SchematronException("Error compiling Schematron to transformation stylesheet", e);
        }
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

    private synchronized List<Transformer> createPipeline () throws TransformerException
    {
        final URIResolver resolver = transformerFactory.getURIResolver();
        final List<Transformer> templates = new ArrayList<Transformer>();

        for (String step : pipelineSteps) {
            final Source source = resolver.resolve(step, null);
            final Transformer transformer = transformerFactory.newTransformer(source);
            for (Map.Entry<String, Object> param : options.entrySet()) {
                transformer.setParameter(param.getKey(), param.getValue());
            }
            templates.add(transformer);
        }

        return templates;
    }
}
