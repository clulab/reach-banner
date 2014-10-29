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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.io.*;

/**
 * Convert an instance through a sequence of pipes.
 * 
 * @author Andrew McCallum <a
 *         href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class SerialPipes extends Pipe implements Serializable
{
	private ArrayList<Pipe> pipes;

	public SerialPipes()
	{
		this.pipes = new ArrayList<Pipe>();
	}

	public SerialPipes(Object[] pipes)
	{
		this((Pipe[]) pipes);
	}

	public SerialPipes(Pipe[] pipes)
	{
		this.pipes = new ArrayList<Pipe>(pipes.length);
		// System.out.println ("SerialPipes init this = "+this);
		for (int i = 0; i < pipes.length; i++)
			this.add(pipes[i]);
	}

	public SerialPipes(List<Pipe> pipeList)
	{
		pipes = new ArrayList<Pipe>(pipeList.size());
		for (Iterator<Pipe> it = pipeList.iterator(); it.hasNext();)
		{
			Pipe pipe = (Pipe) it.next();
			add(pipe);
		}
	}

	public ArrayList<Pipe> getPipes()
	{
		return pipes;
	}

	// added by Fuchun
	public void setTargetProcessing(boolean lookForAndProcessTarget)
	{
		super.setTargetProcessing(lookForAndProcessTarget);
		for (int i = 0; i < pipes.size(); i++)
			((Pipe) pipes.get(i)).setTargetProcessing(lookForAndProcessTarget);
	}

	protected Alphabet resolveDataAlphabet()
	{
		if (dataAlphabetResolved)
			throw new IllegalStateException("Alphabet already resolved.");
		// Set this before we ask our children anything so they can use it.
		if (parent != null)
			dataDict = parent.dataDict;
		// Start at the beginning of the serial pipeline to allow earlier
		// Pipe's to create a dictionary first, and later pipes to
		// "inherit" that same dictionary.
		for (int i = 0; i < pipes.size(); i++)
		{
			Alphabet fd = ((Pipe) pipes.get(i)).resolveDataAlphabet();
			// // Only set the dictionaries if they are non-null, so that if one
			// of the
			// // pipes in the sequence does not insist on its own Alphabet, but
			// previous ones do,
			// // we preserve the original dictionaries.
			// xxx No! Non-vocabulary-insisting Pipe objects now carry a
			// dataDict. By removing the if()
			// statement below, we allow a Pipe in pipeline to actually *remove*
			// a dictionary
			// reflecting the fact that what it returns may no longer depend on
			// any dictionary.
			// if (fd != null)
			dataDict = fd;
		}
		dataAlphabetResolved = true;
		return dataDict;
	}

	protected Alphabet resolveTargetAlphabet()
	{
		if (targetAlphabetResolved)
			throw new IllegalStateException("Target Alphabet already resolved.");
		// Set this before we ask our children anything so they can use it
		if (parent != null)
			targetDict = parent.targetDict;
		// Start at the beginning of the serial pipeline to allow earlier Pipe's
		// to create a dictionary first.
		for (int i = 0; i < pipes.size(); i++)
		{
			Alphabet ld = ((Pipe) pipes.get(i)).resolveTargetAlphabet();
			// Only set the dictionaries if they are non-null, so that if one of
			// the
			// // pipes in the sequence does not insist on its own Alphabet, but
			// previous ones do,
			// // we preserve the original dictionaries.
			// xxx No! Non-vocabulary-insisting Pipe objects now carry a
			// dataDict. By removing the if()
			// statement below, we allow a Pipe in pipeline to actually *remove*
			// a dictionary
			// reflecting the fact that what it returns may no longer depend on
			// any dictionary.
			// if (ld != null)
			targetDict = ld;
		}
		targetAlphabetResolved = true;
		return targetDict;
	}

	protected void add(Pipe pipe)
	{
		// System.out.println ("SerialPipes add this = "+this);
		if (dataAlphabetResolved || targetAlphabetResolved)
			throw new IllegalStateException("Cannot add to SerialPipes after dictionaries are resolved.");
		// This pipe.setParent() must be called before we change our outputClass
		// to match pipe
		// so that PassThruPipe can work.
		// pipe.setInputClass (outputClass);
		pipe.setParent(this);
		pipes.add(pipe);
	}

	public Instance pipe(Instance carrier, int startingIndex)
	{
		// System.err.println(pipes.size());
		for (int i = startingIndex; i < pipes.size(); i++)
		{
			// System.err.println("Pipe: " + i);
			Pipe p = (Pipe) pipes.get(i);
			if (p == null)
			{
				System.err.println("Pipe is null");
			} else
			{
				carrier = p.pipe(carrier);
			}
		}
		return carrier;
	}

	// Call this version when you are not training and don't want conjunctions
	// to mess up the decoding.
	public Instance pipe(Instance carrier, int startingIndex, boolean growAlphabet)
	{
		// System.err.println(pipes.size());
		for (int i = startingIndex; i < pipes.size(); i++)
		{
			// System.err.println("Pipe: " + i);
			Pipe p = (Pipe) pipes.get(i);
			if (p == null)
			{
				System.err.println("Pipe is null");
			} else
			{
				try
				{
					// System.err.println("Pipe is not null");
					if (p instanceof TokenSequence2FeatureVectorSequence)
					{
						((TokenSequence2FeatureVectorSequence) p).setGrowAlphabet(false);
					}
					carrier = p.pipe(carrier);
				} catch (Exception e)
				{
					System.err.println("Exception on pipe " + i + ". " + e);
				}
			}
		}
		return carrier;
	}

	public void removePipe(int index)
	{
		pipes.remove(index);
	}

	// added by Fuchun Jan.30, 2004
	public void replacePipe(int index, Pipe p)
	{
		pipes.set(index, p);
	}

	public int size()
	{
		return pipes.size();
	}

	public Pipe getPipe(int index)
	{
		return pipes.get(index);
	}

	public Instance pipe(Instance carrier)
	{
		return pipe(carrier, 0);
	}

	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < pipes.size(); i++)
			sb.append(((Pipe) pipes.get(i)).toString() + ",");
		return sb.toString();
	}

	// Serialization

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;

	private void writeObject(ObjectOutputStream out) throws IOException
	{
		out.writeInt(CURRENT_SERIAL_VERSION);
		out.writeObject(pipes);
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
	{
		int version = in.readInt();
		pipes = (ArrayList) in.readObject();
	}

}
