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

public class CsvIterator extends AbstractPipeInputIterator
{
	LineNumberReader reader;
	Pattern lineRegex;
	int uriGroup, targetGroup, dataGroup;
	String currentLine;
	
	public CsvIterator (Reader input, Pattern lineRegex, int dataGroup, int targetGroup, int uriGroup)
	{
		this.reader = new LineNumberReader (input);
		this.lineRegex = lineRegex;
		this.targetGroup = targetGroup;
		this.dataGroup = dataGroup;
		this.uriGroup = uriGroup;
		if (dataGroup < 0)
			throw new IllegalStateException ("You must extract a data field.");
		try {
			this.currentLine = reader.readLine();
		} catch (IOException e) {
			throw new IllegalStateException ();
		}
	}

	public CsvIterator (Reader input, String lineRegex, int dataGroup, int targetGroup, int uriGroup)
	{
		this (input, Pattern.compile (lineRegex), dataGroup, targetGroup, uriGroup);
	}

	public CsvIterator (String filename, String lineRegex, int dataGroup, int targetGroup, int uriGroup)
		throws java.io.FileNotFoundException
	{
		this (new FileReader (new File(filename)),
					Pattern.compile (lineRegex), dataGroup, targetGroup, uriGroup);
	}
	
	// The PipeInputIterator interface

	public Instance nextInstance ()
	{
		String uriStr = null;
		String data = null;
		String target = null;
		Matcher matcher = lineRegex.matcher(currentLine);
		if (matcher.find()) {
			if (uriGroup > -1)
				uriStr = matcher.group(uriGroup);
			if (targetGroup > -1)
				target = matcher.group(targetGroup);
			if (dataGroup > -1)
				data = matcher.group(dataGroup);
		} else
			throw new IllegalStateException ("Line #"+reader.getLineNumber()+" does not match regex");

		String uri;
		if (uriStr == null) {
			uri = "csvline:"+reader.getLineNumber();
		} else {
			uri = uriStr;
		}
		assert (data != null);
		Instance carrier = new Instance (data, target, uri, null);
		try {
			this.currentLine = reader.readLine();
		} catch (IOException e) {
			throw new IllegalStateException ();
		}
		return carrier;
	}

	public boolean hasNext ()	{	return currentLine != null;	}
	
}
