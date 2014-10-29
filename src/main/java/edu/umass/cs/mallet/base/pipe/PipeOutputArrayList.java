/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */






package edu.umass.cs.mallet.base.pipe;

import java.util.ArrayList;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.pipe.PipeOutputAccumulator;
import edu.umass.cs.mallet.base.types.Instance;
import java.io.*;
/**
 * A PipeOutputAccumulator implemented as an ArrayList.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class PipeOutputArrayList extends ArrayList implements PipeOutputAccumulator, Serializable
{
	public PipeOutputArrayList ()
	{
		super ();
	}

	public PipeOutputAccumulator clonePipeOutputAccumulator ()
	{
		return (PipeOutputAccumulator) this.clone();
	}

	public void pipeOutputAccumulate (Instance carrier,	Pipe iteratedPipe)
	{
		super.add (carrier.getData());
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
	}

}
