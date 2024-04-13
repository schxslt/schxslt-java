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

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.Source;

import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

import org.xmlresolver.XMLResolver;

import java.util.Map;
import java.util.HashMap;

import net.jcip.annotations.ThreadSafe;

/**
 * Main entry point for Schematron validation.
 */
@ThreadSafe
public final class Schematron
{
    private Validator validator;

    public Schematron (final Source schematron) throws SchematronException {
        this(schematron, null, null);
    }

    public Schematron (final Source schematron, final String phase) throws SchematronException {
        this(schematron, phase, null);
    }

    public Schematron (final Source schematron, final String phase, final TransformerFactory transformerFactory) throws SchematronException {
        this(schematron, phase, transformerFactory, null);
    }

    /**
     * Bottleneck constructor, Source may not be null.
     *
     * @param schematron May not be null
     * @param phase Validation phase
     * @param transformerFactory TransformerFactory to use, possibly with custom URIResolver
     * @param options Compiler options
     * @throws SchematronException If compiling the validation stylesheet fails
     */
    public Schematron (final Source schematron, final String phase, final TransformerFactory transformerFactory, final Map<String, Object> options) throws SchematronException {
        if (schematron == null) {
            throw new IllegalArgumentException("Source may not be null");
        }

        TransformerFactory factory = transformerFactory;
        if (factory == null) {
            XMLResolver resolver = new XMLResolver();
            factory = TransformerFactory.newInstance();
            factory.setURIResolver(resolver.getURIResolver());
        }

        Document stylesheet = compile(factory, schematron, phase, options);
        try {
            Templates templates = factory.newTemplates(new DOMSource(stylesheet, stylesheet.getDocumentURI()));
            validator = new Validator(templates);
        } catch (TransformerException e) {
            throw new SchematronException("Unable to create Validator instance", e);
        }
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
        return validator.validate(document, parameters);
    }

    private Document compile (final TransformerFactory transformerFactory, final Source schema, final String phase, final Map<String, Object> options) throws SchematronException
    {
        Compiler compiler = new Compiler(transformerFactory);
        Map<String, Object> compilerOptions = new HashMap<String, Object>();
        if (options != null) {
            compilerOptions.putAll(options);
        }
        if (phase != null) {
            compilerOptions.put("phase", phase);
        }
        return compiler.compile(schema, compilerOptions);
    }

}
