/* Copyright (C) 2003 University of Pennsylvania.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
   @author Fernando Pereira <a href="mailto:pereira@cis.upenn.edu">pereira@cis.upenn.edu</a>
 */

package edu.umass.cs.mallet.base.pipe.iterator;

import edu.umass.cs.mallet.base.pipe.*;
import edu.umass.cs.mallet.base.types.Instance;

/**
 * Provides a {@link PipeExtendedIterator} that applies a {@link Pipe} to
 * the {@link Instance}s returned by a given {@link PipeExtendedIterator},
 * It is intended to encapsulate preprocessing that should not belong to the
 * input {@link Pipe} of a {@link Classifier} or {@link Transducer}.
 *
 * @author <a href="mailto:pereira@cis.upenn.edu">Fernando Pereira</a>
 * @version 1.0
 */
public class PipeExtendedIterator extends AbstractPipeInputIterator
{
	private PipeInputIterator iterator;
  private Pipe pipe;

	/**
   * Creates a new <code>PipeExtendedIterator</code> instance.
   *
   * @param iterator the base <code>PipeExtendedIterator</code>
   * @param pipe The <code>Pipe</code> to postprocess the iterator output
   */
  public PipeExtendedIterator (PipeInputIterator iterator, Pipe pipe)
	{
		this.iterator = iterator;
    this.pipe = pipe;
	}

	public boolean hasNext ()
	{
		return iterator.hasNext();
	}

	public Instance nextInstance ()
	{
    return pipe.pipe(iterator.nextInstance());
	}


}



