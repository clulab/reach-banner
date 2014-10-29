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

/**
 *  Interface for classes that generate instances.
 *
 *  Typically, these instances will be unprocessed (e.g., they
 *  may come from a corpus data file), and are passed through a pipe
 *  as they are added to an InstanceList.
 *
 *  @see Pipe
 *  @see edu.umass.cs.mallet.base.types.InstanceList
 *
 *  @version $Id: PipeInputIterator.java,v 1.1 2011/07/29 09:11:46 bleaman Exp $
 */
public interface PipeInputIterator extends Iterator
{
	/** To be called once before iterator starts.
			However, Instance object do not currently store this parent information. */
	public void setParentInstance (Instance parent);

	public Instance nextInstance ();
}
