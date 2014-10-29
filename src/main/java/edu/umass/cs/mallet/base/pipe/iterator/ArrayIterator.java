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

import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.util.ArrayListUtils;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

public class ArrayIterator extends AbstractPipeInputIterator
{
	Iterator subIterator;
  Object target;
	int index;
	
	public ArrayIterator (List data, Object target)
	{
		this.subIterator = data.iterator ();
		this.target = target;
		this.index = 0;
	}

	public ArrayIterator (List data)
	{
		this (data, null);
	}
	
	public ArrayIterator (Object[] data, Object target)
	{
		this (ArrayListUtils.createArrayList (data), target);
	}

	public ArrayIterator (Object[] data)
	{
		this (data, null);
	}
	
	// The PipeInputIterator interface

	public Instance nextInstance ()
	{
		URI uri = null;
		try { uri = new URI ("array:" + index++); }
		catch (Exception e) { e.printStackTrace(); throw new IllegalStateException(); }
		return new Instance (subIterator.next(), target, uri, null);
	}

	public boolean hasNext ()	{	return subIterator.hasNext();	}
	
}

