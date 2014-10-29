/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.pipe.iterator;

import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.*;
import java.io.*;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.regex.*;
import java.net.URI;
import java.net.URISyntaxException;

public class SimpleFileIterator extends AbstractPipeInputIterator
{
	BufferedReader reader = null;
  int index = -1;
  String currentLine = null;
  boolean hasNextUsed = false;
	
	public SimpleFileIterator (String filename)
	{
    try {
      this.reader = new BufferedReader (new FileReader(filename));
      this.index = 0;
		} catch (IOException e) {
			throw new RuntimeException (e);
		}
	}

	public Instance nextInstance ()
	{
		URI uri = null;
		try { uri = new URI ("array:" + index++); }
		catch (Exception e) { throw new RuntimeException (e); }
    if (!hasNextUsed) {
      try {
        currentLine = reader.readLine();
      }
      catch (IOException e) {
        throw new RuntimeException (e);
      }
    }
    else
      hasNextUsed = false;
		return new Instance (currentLine, null, uri, null);
	}

	public boolean hasNext ()	{	
    hasNextUsed = true; 
    try {
      currentLine = reader.readLine(); 
    }
    catch (IOException e) {
      throw new RuntimeException (e);
    }
    return (currentLine != null);	
  }
	
}
