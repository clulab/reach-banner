/* Copyright (C) 2003 University of Pennsylvania
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Fernando Pereira <a href="mailto:pereira@cis.upenn.edu">pereira@cis.upenn.edu</a>
 */

package edu.umass.cs.mallet.base.pipe.iterator;

import java.util.Iterator;
import java.util.ArrayList;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.Label;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.util.ArrayListUtils;
import edu.umass.cs.mallet.base.pipe.Pipe;

public class ArrayDataAndTargetIterator extends AbstractPipeInputIterator
{
	Iterator subIterator;
  Iterator targetIterator;
	int index;
	
	public ArrayDataAndTargetIterator (ArrayList data, ArrayList targets)
	{
		this.subIterator = data.iterator ();
		this.targetIterator = targets.iterator ();
		this.index = 0;
	}

	public ArrayDataAndTargetIterator (Object[] data, Object target[])
	{
		this (ArrayListUtils.createArrayList (data),
          ArrayListUtils.createArrayList (target));
	}

	// The PipeInputIterator interface

	public Instance nextInstance ()
	{
		URI uri = null;
		try { uri = new URI ("array:" + index++); }
		catch (Exception e) { e.printStackTrace(); throw new IllegalStateException(); }
		return new Instance (subIterator.next(), targetIterator.next(), uri, null);
	}

	public boolean hasNext ()	{	return subIterator.hasNext();	}
	
}

