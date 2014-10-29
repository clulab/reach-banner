/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package edu.umass.cs.mallet.base.types;

import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 *  A simple {@link Sequence} implementation where all of the
 *  elements must be Labels.   Provides a convenient type-safe accessor {@link #getLabels}.
 *  Instances of LabelsSequence are immutable.
 *
 *  @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class LabelsSequence implements Sequence, Serializable
{
	Labels[] seq;
	
  /**
	 *  Create a LabelsSequence from an array.  The array is shallow-copied.
	 */
	public LabelsSequence (Labels[] seq)
	{
		this.seq = new Labels[seq.length];
		System.arraycopy (seq, 0, this.seq, 0, seq.length);
	}

	public int size () { return seq.length; }

	public Object get (int i) { return seq[i]; }

	public Labels getLabels (int i) { return seq[i]; }

	public String toString ()
	{
		String ret = "LabelsSequence:\n";
		for (int i = 0; i < seq.length; i++) {
			ret += i+": ";
			ret += seq[i].toString();
			ret += "\n";
		}
		return ret;
	}

  // Serialization

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 0;

  private void writeObject (ObjectOutputStream out) throws IOException {
    out.writeInt(CURRENT_SERIAL_VERSION);
    out.defaultWriteObject ();
  }

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
    int version = in.readInt ();
    in.defaultReadObject ();
  }


}
