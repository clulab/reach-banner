/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package edu.umass.cs.mallet.base.pipe;

import java.util.Iterator;
import java.util.ArrayList;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.util.UriUtils;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.pipe.PipeOutputAccumulator;
import edu.umass.cs.mallet.base.pipe.PipeOutputArrayList;
import edu.umass.cs.mallet.base.pipe.iterator.PipeInputIterator;
import java.io.*;
/**
 * Converts the iterator in the data field to a PipeOutputAccumulation of the values
 * spanned by the iterator.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class IteratingPipe extends Pipe implements Serializable
{
	Pipe iteratedPipe;
	PipeOutputAccumulator accumulator;

	public IteratingPipe (PipeOutputAccumulator accumulator, Pipe iteratedPipe)
	{
		assert (iteratedPipe.getParent() == null);
		this.iteratedPipe = iteratedPipe;
		this.accumulator = accumulator;
		iteratedPipe.setParent (this);
	}

	public IteratingPipe (Pipe iteratedPipe)
	{
		this(new PipeOutputArrayList(), iteratedPipe);
	}

	public void setTargetProcessing (boolean lookForAndProcessTarget)
	{
		super.setTargetProcessing (lookForAndProcessTarget);
		iteratedPipe.setTargetProcessing (lookForAndProcessTarget);
	}


	protected Alphabet resolveDataAlphabet ()
	{
		if (dataAlphabetResolved)
			throw new IllegalStateException ("Alphabet already resolved.");
		Alphabet fd = iteratedPipe.resolveDataAlphabet ();
		if (dataDict == null)
			dataDict = fd;
		else
			assert (dataDict.equals(fd));
		return dataDict;
	}
	
	protected Alphabet resolveTargetAlphabet ()
	{
		if (targetAlphabetResolved)
			throw new IllegalStateException ("Target Alphabet already resolved.");
		Alphabet ld = iteratedPipe.resolveTargetAlphabet ();
		if (targetDict == null)
			targetDict = ld;
		else
			assert (targetDict.equals(ld));
		return targetDict;
	}

	public Instance pipe (Instance carrier)
	{
		assert (carrier.getData() instanceof PipeInputIterator);
		carrier.setData(iteratePipe (iteratedPipe, accumulator.clonePipeOutputAccumulator(),	carrier));
		return carrier;
	}

	public static PipeOutputAccumulator iteratePipe (Pipe iteratedPipe,
																									 PipeOutputAccumulator accumulator,
																									 Instance carrier)
	{
		PipeInputIterator iter = (PipeInputIterator) carrier.getData();
		iter.setParentInstance (carrier);
		while (iter.hasNext()) {
      // Make sure that instance.pipe field gets set when piping instance.
      Instance subInstance = iter.nextInstance();
      Instance pipedInstance = new Instance (subInstance.getData (), subInstance.getTarget (),
                                             subInstance.getName (), subInstance.getSource (), iteratedPipe);
      accumulator.pipeOutputAccumulate (pipedInstance, iteratedPipe);
    }
		return accumulator;
	}
	
	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(iteratedPipe);
		out.writeObject(accumulator);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		iteratedPipe = (Pipe) in.readObject();
		accumulator = (PipeOutputAccumulator) in.readObject ();
	}

}
