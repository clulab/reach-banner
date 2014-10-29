/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.pipe.iterator;

import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.*;
import java.io.*;
import java.util.regex.*;

/** Iterates over matching regular expresions. E.g. 
 *   regexp = Pattern.compile ("<p>(.+?)</p>")  will 
 *  extract <p> elements from:
 *
 * <p> This block is an element </p> this is not <p> but this is </p>
 *
*/

public class PatternMatchIterator extends AbstractPipeInputIterator
{
  Pattern regexp;
  Matcher matcher;
  String nextElement;
  int elementIndex;
  
  public PatternMatchIterator (CharSequence input, Pattern regexp)
  {
    this.elementIndex = 0;
    this.regexp = regexp;
    this.matcher = regexp.matcher (input);
    this.nextElement = getNextElement();
  }
  
  public String getNextElement ()
  {
    if (matcher.find())
      return matcher.group(1);
    else return null;
  }
  
  // The PipeInputIterator interface
  
  public Instance nextInstance ()
  {
    assert (nextElement != null);
    Instance carrier = new Instance (nextElement, null, "element"+elementIndex++,
                                     null);
    nextElement = getNextElement ();
    return carrier;
  }
  
  public boolean hasNext () { return nextElement != null; }
  
}
