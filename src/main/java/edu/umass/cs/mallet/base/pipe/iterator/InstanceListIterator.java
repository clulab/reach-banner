/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package edu.umass.cs.mallet.base.pipe.iterator;

import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.InstanceList;

/**
 * This method feeds a pipeline from another InstanceList.
 *  The new instances will have their pipe set to the pipe this
 *  iterates into, and will carry no record of their previous origin.
 *  New instance objects will be created; the originals are not modified.
 * <P>
 * This must be used with caution, for it can result in accidentally
 *  piping the same instances through the same pipe twice, something that
 *  MALLET otherwise tries to protect you from.
 * <P>
 * To save memory, in the future we might add a flag that (if set) would
 *  cause this to remove the original instances from the InstanceList.
 * <P>
 * Created: Oct 31, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: InstanceListIterator.java,v 1.1 2011/07/29 09:11:46 bleaman Exp $
 */
public class InstanceListIterator extends AbstractPipeInputIterator {

  private InstanceList.Iterator iter;


  public InstanceListIterator (InstanceList source)
  {
    iter = source.iterator();
  }

  // The PipeInputIterator interface
  public Instance nextInstance ()
  {
    final Instance instance = iter.nextInstance ();
    Instance ret = new Instance (instance.getData(), instance.getTarget(), instance.getName(), instance.getSource());
    ret.setPropertyList (instance.getPropertyList ());
    return ret;
  }


  public boolean hasNext ()
  {
    return iter.hasNext();
  }
  
}
