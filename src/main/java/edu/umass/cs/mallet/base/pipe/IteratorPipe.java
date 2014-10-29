/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package edu.umass.cs.mallet.base.pipe;

import edu.umass.cs.mallet.base.pipe.iterator.*;
import edu.umass.cs.mallet.base.types.Instance;
/**
 * Unimplemented.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class IteratorPipe extends AbstractPipeInputIterator
{
	PipeInputIterator[] iterators;
	Object next = null;

	public IteratorPipe (PipeInputIterator[] iterators)
	{
		this.iterators = iterators;
	}

	private boolean setNext()
	{
		return false;
	}

	public boolean hasNext ()
	{
		return next != null;
	}

	public Instance nextInstance ()
	{
		throw new UnsupportedOperationException ("Not yet implemented");
	}


}



