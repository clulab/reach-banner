/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.types;

import edu.umass.cs.mallet.base.types.Sequence;
import java.util.ArrayList;

public class ArraySequence implements Sequence
{
	Object[] data;

	public ArraySequence (ArrayList a)
	{
		data = new Object[a.size()];
		for (int i = 0; i < a.size(); i++)
			data[i] = a.get(i);
	}

	public ArraySequence (Object[] a, boolean copy)
	{
		if (copy) {
			data = new Object[a.length];
			System.arraycopy (a, 0, data, 0, a.length);
		} else
			data = a;
	}

	public ArraySequence (Object[] a)
	{
		this (a, true);
	}

	protected ArraySequence (Sequence s, boolean copy)
	{
		if (s instanceof ArraySequence) {
			if (copy) {
				data = new Object[s.size()];
				System.arraycopy (((ArraySequence)s).data, 0, data, 0, data.length);
			} else
				data = ((ArraySequence)s).data;
		} else {
			data = new Object[s.size()];
			for (int i = 0; i < s.size(); i++)
				data[i] = s.get(i);
		}
	}
	
	public Object get (int index)
	{
		return data[index];
	}

	public int size ()
	{
		return data.length;
	}

  public String toString() {
    String toret = "";
    for (int i = 0; i < data.length; i++) {
      toret += " " + data[i];
    }

    return toret;
  }
}
