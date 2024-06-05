/*
 * Copyright (C) 2020 by David Maus <dmaus@dmaus.name>
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

import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXSource;

import org.w3c.dom.Document;

import org.xml.sax.XMLReader;

import net.jcip.annotations.ThreadSafe;

/**
 * Validates a document with a compiled Schematron.
 */
@ThreadSafe
final class Validator
{
    private final Templates schema;
    private final XMLReader reader;

    Validator (final XMLReader reader, final Templates schema)
    {
        this.reader = reader;
        this.schema = schema;
    }

    public Result validate (final Source document, final Map<String, Object> parameters) throws SchematronException
    {
        try {
            Transformer transformer = schema.newTransformer();
            if (parameters != null) {
                for (Map.Entry<String, Object> param : parameters.entrySet()) {
                    transformer.setParameter(param.getKey(), param.getValue());
                }
            }

            DOMResult result = new DOMResult();
            SAXSource source = new SAXSource(reader, SAXSource.sourceToInputSource(document));
            transformer.transform(source, result);

            return new Result((Document)result.getNode());

        } catch (TransformerException e) {
            throw new SchematronException("Error running transformation stylesheet", e);
        }
    }
}
