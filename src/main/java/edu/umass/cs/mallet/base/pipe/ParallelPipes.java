/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package edu.umass.cs.mallet.base.pipe;

import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.Instance;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Collection;
import java.io.*;

/**
 * Convert an instance to the PipeOutputAccumulator output produced by running
 * the original instance through each of the sub pipes contained in the parallel pipe.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class ParallelPipes extends Pipe implements Serializable
{
	ArrayList pipes;
	PipeOutputAccumulator accumulator;

	protected ParallelPipes (PipeOutputAccumulator accumulator)
	{
		this.pipes = new ArrayList ();
		this.accumulator = accumulator;
	}

	public ParallelPipes (PipeOutputAccumulator accumulator, Pipe[] pipes)
	{
		this (accumulator);
		for (int i = 0; i < pipes.length; i++) {
			this.add (pipes[i]);
		}
	}

	protected void add (Pipe pipe)
	{
		pipe.setParent (this);
		this.pipes.add (pipe);
	}

	public void setTargetProcessing (boolean lookForAndProcessTarget)
	{
		super.setTargetProcessing (lookForAndProcessTarget);
		for (int i = 0; i < pipes.size(); i++)
			((Pipe)pipes.get(i)).setTargetProcessing (lookForAndProcessTarget);
	}

	protected Alphabet resolveDataAlphabet ()
	{
		if (dataAlphabetResolved)
			throw new IllegalStateException ("Alphabet already resolved.");
		// Set this before we ask our children anything so they can use it
		if (parent != null)
			dataDict = parent.dataDict;
		// Start at the first of the parallel pipelines to allow first-added Pipe's
		// to create a dictionary first.
		// xxx The below implies that either all feature dictionaries
		// must be null, or none of them.  Is this right?
		for (int i = 0; i < pipes.size(); i++) {
			Alphabet fd = ((Pipe)pipes.get(i)).resolveDataAlphabet ();
			if (dataDict == null)
				dataDict = fd;
			else if (! dataDict.equals (fd))
				throw new IllegalArgumentException
					("ParallelPipes pipe " + pipes.get(i).getClass().getName()
					 + "does not have same output Alphabet as previous pipes.");
		}
		dataAlphabetResolved = true;
		return dataDict;
	}

	protected Alphabet resolveTargetAlphabet ()
	{
		if (targetAlphabetResolved)
			throw new IllegalStateException ("Target Alphabet already resolved.");
		// Set this before we ask our children anything so they can use it
		if (parent != null)
			targetDict = parent.targetDict;
		// Start at the first of the parallel pipelines to allow first-added Pipe's
		// to create a dictionary first.
		// xxx The below implies that either all feature dictionaries
		// must be null, or none of them.  Is this right?
		for (int i = 0; i < pipes.size(); i++) {
			Alphabet ld = ((Pipe)pipes.get(i)).resolveTargetAlphabet ();
			if (targetDict == null)
				targetDict = ld;
			else if (! targetDict.equals (ld))
				throw new IllegalArgumentException
					("ParallelPipes pipe " + pipes.get(i).getClass().getName()
					 + "does not have same target Alphabet as previous pipes.");
		}
		targetAlphabetResolved = true;
		return targetDict;
	}

	public Instance pipe (Instance carrier)
	{
		Object result;
		PipeOutputAccumulator localAccumulator = accumulator.clonePipeOutputAccumulator();

		// Pass the input object to each of the parallel pipes
		for (int i = 0; i < pipes.size(); i++) {
			Pipe p = (Pipe)pipes.get(i);
			localAccumulator.pipeOutputAccumulate (p.pipe (carrier), p);
		}
		carrier.setData(localAccumulator);
		return carrier;
	}
	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(pipes);
		out.writeObject(accumulator);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		pipes = (ArrayList) in.readObject();
		accumulator = (PipeOutputAccumulator) in.readObject();
	}


}
