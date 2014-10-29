/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package edu.umass.cs.mallet.base.maximize;

/**
 * Callback interface that allows optimizer clients to perform some operation after every iteration.
 * 
 * Created: Sep 28, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: OptimizerEvaluator.java,v 1.1 2011/07/29 09:11:46 bleaman Exp $
 */
public interface OptimizerEvaluator {

  /**
   * Performs some operation at the end of each iteration of a maximizer.
   * @param maxable Function that's being optimized.
   * @param iter Number of just-finished iteration.
   * @return true if optimization should continue.
   */
  boolean evaluate (Maximizable maxable, int iter);
}
