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
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.Label;
import edu.umass.cs.mallet.base.types.Instance;
import java.util.Iterator;

public abstract class AbstractPipeInputIterator implements PipeInputIterator
{
	protected Instance parentInstance = null;

	// The PipeInputIterator interface
	public abstract Instance nextInstance ();
	public void setParentInstance (Instance carrier) { parentInstance = carrier; }

	// The Iterator interface
	public Object next () { return nextInstance(); }
	public abstract boolean hasNext ();
	public void remove ()	{	throw new UnsupportedOperationException ();	}
}
