/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.pipe;

import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.Instance;

public interface PipeOutputAccumulator
{
	// "subPipe" is either the iteratedPipe in IteratingPipe or one
	// of the parallel pipes in ParallelPipe.
	public void pipeOutputAccumulate (Instance carrier,	Pipe subPipe);

	// This must not simply raise UnsupportedOperationException!
	public PipeOutputAccumulator clonePipeOutputAccumulator ();
	
	// Include this method in the interface?
	//public Object get (int i);
}
