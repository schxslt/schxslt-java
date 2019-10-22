/*
 * Copyright 2019 by David Maus <dmaus@dmaus.name>
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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import javax.xml.transform.stream.StreamSource;

import java.util.HashMap;

public class SchematronTest
{
    final String simpleSchema10 = "/simple-schema-10.sch";
    final String simpleSchema20 = "/simple-schema-20.sch";
    final String simpleSchema20catalog = "/simple-schema-20-catalog.sch";

    @BeforeAll
    public static void init ()
    {
        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
        System.setProperty("xml.catalog.files", SchematronTest.class.getResource("/catalog.xml").toString());
    }

    @Test
    public void newSchematronForXSLT10 () throws Exception
    {
        Schematron schematron = new Schematron(getResourceAsStream(simpleSchema10), "always-valid");
        Result result = schematron.validate(getResourceAsStream(simpleSchema10));
        assertTrue(result.isValid());
    }

    @Test
    public void extParamForXSLT10 () throws Exception
    {
        Schematron schematron = new Schematron(getResourceAsStream(simpleSchema10), "external-param");

        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("external-param", new Integer(1));

        Result result = schematron.validate(getResourceAsStream(simpleSchema10), map);
        assertTrue(result.isValid());
    }

    @Test
    public void extParamForXSLT20 () throws Exception
    {
        Schematron schematron = new Schematron(getResourceAsStream(simpleSchema10), "external-param");

        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("external-param", new Integer(1));

        Result result = schematron.validate(getResourceAsStream(simpleSchema10), map);
        assertTrue(result.isValid());
    }


    @Test
    public void newSchematronForXSLT20 () throws Exception
    {
        Schematron schematron = new Schematron(getResourceAsStream(simpleSchema20), "always-valid");
        Result result = schematron.validate(getResourceAsStream(simpleSchema10));
        assertTrue(result.isValid());
    }

    @Test
    public void catalogResolver () throws Exception
    {
        Schematron schematron = new Schematron(getResourceAsStream(simpleSchema20catalog));
        Result result = schematron.validate(getResourceAsStream(simpleSchema10));
        assertTrue(result.isValid());
    }

    StreamSource getResourceAsStream (String resource)
    {
        return new StreamSource(getClass().getResourceAsStream(resource));
    }
}
