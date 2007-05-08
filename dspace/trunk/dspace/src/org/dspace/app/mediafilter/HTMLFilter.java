/*
 * HTMLFilter.java
 *
 * $Id$
 *
 * Version: $Revision$
 *
 * Date: $Date$
 *
 * Copyright (c) 2002, Hewlett-Packard Company and Massachusetts
 * Institute of Technology.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Neither the name of the Hewlett-Packard Company nor the name of the
 * Massachusetts Institute of Technology nor the names of their
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 */
 
package org.dspace.app.mediafilter;

import org.textmining.text.extraction.WordExtractor;

import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.core.Context;

/*

to do:
    helpful error messages
        - can't find mediafilter.cfg
        - can't instantiate filter
        - bitstream format doesn't exist

 */


public class HTMLFilter extends MediaFilter
{
    /**
     * @param filename string filename
     *
     * @return string filtered filename
     */
    public String getFilteredName(String oldFilename)
    {
        return oldFilename + ".txt";
    }


    /**
     * @return String bundle name
     *
     */
    public String getBundleName()
    {
        return "TEXT";
    }
    

    /**
     * @return String bitstreamformat
     */
    public String getFormatString()
    {
        return "Text";
    }

        
    /**
     * @param source source input stream
     *
     * @return InputStream the resulting input stream
     */
    
    public InputStream getDestinationStream(InputStream source)
        throws Exception
    {
        // get input stream from bitstream
        // pass to filter, get string back
 
        HTMLEditorKit kit = new HTMLEditorKit();
        Document doc = kit.createDefaultDocument();
        kit.read(source, doc, 0);
 
        String extractedText = doc.getText(0, doc.getLength());

        // generate an input stream with the extracted text
        byte[] textBytes = extractedText.getBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(textBytes);

        return bais;  // will this work? or will the byte array be out of scope?
    }
}
