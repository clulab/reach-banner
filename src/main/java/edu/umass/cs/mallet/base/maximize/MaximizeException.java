/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Jerod Weinman <a href="mailto:weinman@cs.umass.edu">weinman@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.maximize;

public class MaximizeException extends RuntimeException {

    public MaximizeException()
    {
	super();
    }

    public MaximizeException(String message)
    {
	super(message);
    }

    public MaximizeException(String message, Throwable cause)
    {
	super(message,cause);
    }

    public MaximizeException(Throwable cause)
    {
	super(cause);
    }
}
