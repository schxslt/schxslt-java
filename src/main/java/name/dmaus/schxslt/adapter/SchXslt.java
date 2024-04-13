/*
 * Copyright (C) by David Maus <dmaus@dmaus.name>
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

package name.dmaus.schxslt.adapter;

import java.util.List;

import name.dmaus.schxslt.SchematronException;

/**
 * Adapter for SchXslt.
 */
public final class SchXslt implements Adapter
{
    private static final List<String> XSLT10STEPS = List.of("classpath:/xslt/1.0/include.xsl", "classpath:/xslt/1.0/expand.xsl", "classpath:/xslt/1.0/compile-for-svrl.xsl");
    private static final List<String> XSLT20STEPS = List.of("classpath:/xslt/2.0/include.xsl", "classpath:/xslt/2.0/expand.xsl", "classpath:/xslt/2.0/compile-for-svrl.xsl");

    public List<String> getTranspilerStylesheets (final String queryBinding) throws SchematronException
    {
        if ("".equals(queryBinding) || "xslt".equals(queryBinding)) {
            return XSLT10STEPS;
        } else if ("xslt2".equals(queryBinding) || "xslt3".equals(queryBinding)) {
            return XSLT20STEPS;
        } else {
            throw new SchematronException("Unsupported query language binding: " + queryBinding);
        }
    }
}
