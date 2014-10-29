/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




package edu.umass.cs.mallet.base.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import edu.umass.cs.mallet.base.types.Labeling;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.pipe.PipeOutputAccumulator;
import edu.umass.cs.mallet.base.pipe.SerialPipes;
import edu.umass.cs.mallet.base.pipe.TokenSequence2FeatureSequence;
import edu.umass.cs.mallet.base.pipe.FeatureSequence2FeatureVector;
import edu.umass.cs.mallet.base.pipe.Target2Label;
import edu.umass.cs.mallet.base.pipe.iterator.PipeInputIterator;
import edu.umass.cs.mallet.base.pipe.iterator.RandomTokenSequenceIterator;
import edu.umass.cs.mallet.base.util.MalletLogger;
import edu.umass.cs.mallet.base.util.PropertyList;
import edu.umass.cs.mallet.base.util.Random;
import edu.umass.cs.mallet.base.util.DoubleList;
import java.util.logging.*;
import java.io.*;

/**
	 A machine learning "example" to be used in training, testing or
	 performance of various machine learning algorithms.

	 <p>An instance contains four generic fields of predefined name:
     "data", "target", "name", and "source".   "Data" holds the data represented
    `by the instance, "target" is often a label associated with the instance,
     "name" is a short identifying name for the instance (such as a filename),
     and "source" is human-readable sourceinformation, (such as the original text).

     <p> Each field has no predefined type, and may change type as the instance
     is processed. For example, the data field may start off being a string that
     represents a file name and then be processed by a {@link edu.umass.cs.mallet.base.pipe.Pipe} into a CharSequence
     representing the contents of the file, and eventually to a feature vector
     holding indices into an {@link edu.umass.cs.mallet.base.types.Alphabet} holding words found in the file.
     It is up to each pipe which fields in the Instance it modifies; the most common
     case is that the pipe modifies the data field.

	 <p>Generally speaking, there are two modes of operation for
	 Instances.  (1) An instance gets created and passed through a
	 Pipe, and the resulting data/target/name/source fields are used.
	 This is generally done for training instances.  (2) An instance
	 gets created with raw values in its slots, then different users
	 of the instance call newPipedCopy() with their respective
	 different pipes.  This might be done for test instances at
	 "performance" time.

     <p>Instances can be made immutable if locked.
	 Although unlocked Instances are mutable, typically the only code that
	 changes the values in the four slots is inside Pipes.

     <p> Note that constructing an instance with a pipe argument means
     "Construct the instance and then run it through the pipe".
     {@link edu.umass.cs.mallet.base.types.InstanceList} uses this method
     when adding instances through a pipeInputIterator.

   @see Pipe
   @see Alphabet
   @see InstanceList

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class Instance implements Serializable
{
	private static Logger logger = MalletLogger.getLogger(Instance.class.getName());

	Object data;									// The input data in digested form, e.g. a FeatureVector
	Object target;								// The output data in digested form, e.g. a Label
	Object name;									// A readable name of the source, e.g. for ML error analysis
	Object source;								/* The input in a reproducable form, e.g. enabling re-print of
																	 string w/ POS tags, usually without target information,
																	 e.g. an un-annotated RegionList. */
	Pipe pipe = null;							// The Pipe through which this instance had its fields set
	PropertyList properties = null;
	boolean locked = false;


	public Instance (Object data, Object target, Object name, Object source)
	{
		this.data = data;
		this.target = target;
		this.name = name;
		this.source = source;
	}

	/** Initialize the slots with the given four values, then put the
			Instance through the given pipe, then lock the instance. */
	public Instance (Object data, Object target, Object name, Object source,
									 Pipe p)
	{
		this (data, target, name, source);
		if (p != null) {
			p.pipe (this);
			locked = true;
		}
		pipe = p;
	}

	public Object getData () { return data; }
	public Object getTarget () { return target; }
	public Object getName () { return name; }
	public Object getSource () { return source; }
	public Pipe getPipe () { return pipe; }

	public PropertyList getPropertyList() {return properties;}// added by Fuchun

	/** This is a left-over convenience method that may be removed. */
	public Object getData (Pipe p) {
		if (p != pipe)
			throw new IllegalArgumentException ("Pipe doesn't match.");
		return data;
	}

	public boolean isLocked () { return locked; }
	public void setLock() { locked = true; }
	public void unLock() { locked = false; }

	public Instance getPipedCopy (Pipe p) {
		if (pipe != null)
			throw new IllegalStateException
				("This method can only be called on Instances that have not yet already been piped");
		Instance ret = p.pipe (this.shallowCopy());
		ret.pipe = p;
		return ret;
	}

	public Labeling getLabeling ()
	{
		if (target == null || target instanceof Labeling)
			return (Labeling)target;
		throw new IllegalStateException ("Target is not a Labeling; it is a "+target.getClass().getName());
	}

	protected void setPipe (Pipe p) {
		if (!locked) pipe = p;
		else throw new IllegalStateException ("Instance is locked.");
	}

	public void setData (Object d) {
		if (!locked) data = d;
		else throw new IllegalStateException ("Instance is locked.");
	}
	public void setTarget (Object t) {
		if (!locked) target = t;
		else throw new IllegalStateException ("Instance is locked.");
	}
	public void setLabeling (Labeling l) {
		// This test isn't strictly necessary, but might catch some typos.
		assert (target == null || target instanceof Labeling);
		if (!locked) target = l;
		else throw new IllegalStateException ("Instance is locked.");
	}
	public void setName (Object n) {
		if (!locked) name = n;
		else throw new IllegalStateException ("Instance is locked.");
	}
	public void setSource (Object s) {
		if (!locked) source = s;
		else throw new IllegalStateException ("Instance is locked.");
	}
	public void clearSource () {
		source = null;
	}

	public void setPropertyList (PropertyList p){//added by Fuchun
		if (!locked) properties = p;
		else throw new IllegalStateException ("Instance is locked.");
	}

	public Instance shallowCopy ()
	{
		Instance ret = new Instance (data, target, name, source);
		ret.pipe = pipe;
		ret.locked = locked;
		ret.properties = properties;
		return ret;
	}


	public interface Iterator extends java.util.Iterator
	{
		// xxx Change this to just return "Instance"?  No.
		public Instance nextInstance ();
		public double getInstanceWeight ();
	}

	// Setting and getting properties
	
	public void setProperty (String key, Object value)
	{
		properties = PropertyList.add (key, value, properties);
	}

	public void setNumericProperty (String key, double value)
	{
		properties = PropertyList.add (key, value, properties);
	}

	public PropertyList getProperties ()
	{
		return properties;
	}

	public Object getProperty (String key)
	{
		return properties == null ? null : properties.lookupObject (key);
	}

	public double getNumericProperty (String key)
	{
		return (properties == null ? 0.0 : properties.lookupNumber (key));
	}

	public boolean hasProperty (String key)
	{
		return (properties == null ? false : properties.hasProperty (key));
	}


	// Serialization of Instance

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(data);
		out.writeObject(target);
		out.writeObject(name);
		out.writeObject(source);
		out.writeObject(properties);
		out.writeObject(pipe);
		out.writeBoolean(locked);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		data = in.readObject();
		target = in.readObject();
		name = in.readObject();
		source = in.readObject();
		properties = (PropertyList) in.readObject();
		if (version > 0) {
			pipe = (Pipe) in.readObject();
			locked = in.readBoolean();
		}
	}

}
