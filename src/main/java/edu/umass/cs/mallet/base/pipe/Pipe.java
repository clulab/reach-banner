/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package edu.umass.cs.mallet.base.pipe;

import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.util.MalletLogger;
import edu.umass.cs.mallet.base.util.PropertyList;
import java.lang.reflect.Method;
import java.net.URI;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.*;
import java.util.HashMap;
import java.io.*;
import java.rmi.dgc.VMID;
/**
	The abstract superclass of all Pipes, which transform one data type to another.
	Pipes are most often used for feature extraction.
	<p>
	A pipe operates on an {@link edu.umass.cs.mallet.base.types.Instance}, which is a carrier of data.
	A pipe reads from and writes to fields in the Instance when it is requested
	to process the instance. It is up to the pipe which fields in the Instance it
	reads from and writes to, but usually a pipe will read its input from and write
	its output to the "data" field of an instance.
    <p>
    A pipe doesn't have any direct notion of input or output - it merely modifies instances
    that are handed to it.  A set of helper classes, subclasses of {@link edu.umass.cs.mallet.base.pipe.iterator.AbstractPipeInputIterator},
    iterate over commonly encountered input data structures and feed the elements of these
    data structures to a pipe as instances.
    <p>
    A pipe is frequently used in conjunction with an {@link edu.umass.cs.mallet.base.types.InstanceList}  As instances are added
	to the list, they are processed by the pipe associated with the instance list and
	the processed Instance is kept in the list.
    <p>
    In one common usage, a {@link edu.umass.cs.mallet.base.pipe.iterator.FileIterator} is given a list of directories to operate over.
	The FileIterator walks through each directory, creating an instance for each
	file and putting the data from the file in the data field of the instance.
	The directory of the file is stored in the target field of the instance.  The
    FileIterator feeds instances to an InstanceList, which processes the instances through
    its associated pipe and keeps the results.
	<p>
    Pipes can be hierachically composed. In a typical usage, a SerialPipe is created which
    holds instances of other pipes in an ordered list. Piping
	in instance through a SerialPipe means piping the instance through the child pipes
	in sequence.
    <p>
    A pipe holds onto two separate Alphabets: one for the symbols (feature names)
    encountered in the data fields of the instances processed through the pipe,
    and one for the symbols encountered in the target fields.
    <p>

 @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public abstract class Pipe implements Serializable
{
	private static Logger logger = MalletLogger.getLogger(Pipe.class.getName());

	Pipe parent = null;
	Alphabet dataDict = null;
	Alphabet targetDict = null;

	// If non-null, then get*Alphabet methods are guaranteed to return non-null,
	// and a member of this class
	Class dataDictClass = null;
	Class targetDictClass = null;

	boolean dataAlphabetResolved = false;
	boolean targetAlphabetResolved = false;
	boolean targetProcessing = true;

    VMID instanceId = new VMID();  //used in readResolve to distinguish persistent instances

	/** Construct pipe with type specifications for dictionaries.
         *  Pass non-null as class if you want the given dictionary created as an
         *  instance of the class
         *  @param dataDictClass Class that will be used to create a data dictionary.
         *  @param targetDictClass Class that will be used to create a data dictionary.
         *   WHEN WHEN WHEN WHEN
         */
	public Pipe (Class dataDictClass, Class targetDictClass)
	{
		assert (dataDictClass == null || Alphabet.class.isAssignableFrom (dataDictClass));
		assert (targetDictClass == null || Alphabet.class.isAssignableFrom (targetDictClass));
		this.dataDictClass = dataDictClass;
		this.targetDictClass = targetDictClass;
	}


       /** Construct a pipe with no data and target dictionaries
	*/
	public Pipe ()
	{
		this ((Class)null, (Class)null);
	}

	/**
	 * Construct pipe with data and target dictionaries.
     * Note that, since the default values of the dataDictClass and targetDictClass are null,
	 * that if you specify null for one of the arguments here, this pipe step will not
         * ever create 	any corresponding dictionary for the argument.
         *  @param dataDict  Alphabet that will be used as the data dictionary.
         *  @param targetDict Alphabet that will be used as the target dictionary.
         *   WHEN WHEN WHEN WHEN
         */
	public Pipe (Alphabet dataDict,   Alphabet targetDict)
	{
		this.dataDict = dataDict;
		this.targetDict = targetDict;
		// Is doesn't matter what the dataDictClass and targetDictClass
		// because they will never get used, now that we have already
		// allocated dictionaries in place.
	}

	/**
	 *  Process an Instance.  This method takes an input Instance,
	 *  destructively modifies it in some way, and returns it.
	 *  This is the method by which all pipes are eventually run.
     * <p>
	 *  One can create a new concrete subclass of Pipe simply by
	 *  implementing this method.
     * @param carrier Instance to be processed.
	 */
	public abstract Instance pipe (Instance carrier);


        /**
         * Create and process an Instance. An instance is created from
         * the given arguments and then the pipe is run on the instance.
         * @param data Object used to initialize data field of new instance.
         * @param target Object used to initialize target field of new instance.
         * @param name Object used to initialize name field of new instance.
         * @param source Object used to initialize source field of new instance.
         * @param parent  Unused
         * @param properties Unused
         */
	public Instance pipe (Object data, Object target, Object name, Object source,
                              Instance parent, PropertyList properties)
	{
		return pipe (new Instance (data, target, name, source));
	}

	/** Set whether input is taken from target field of instance during processing.
         *  If argument is false, don't expect to find input material for the target.
	 *  By default, this is true. */
	public void setTargetProcessing (boolean lookForAndProcessTarget)
	{
		targetProcessing = lookForAndProcessTarget;
	}

	/** Return true iff this pipe expects and processes information in
			the <tt>target</tt> slot. */
	public boolean isTargetProcessing ()
	{
		return targetProcessing;
	}

	// Note: This must be called *before* this Pipe has been added to
	// the parent's collection of pipes, otherwise in
	// DictionariedPipe.setParent() we will simply get back this Pipe's
	// Alphabet information.
	public void setParent (Pipe p)
	{
		// Force the user to explicitly set to null before making changes.
		if (p != null) {
			//logger.info ("vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv\n"
			//+ "setParent " + p.getClass().getName());
			if (parent != null)
				throw new IllegalStateException ("Parent already set.");
			// xxx ?? assert (!dataAlphabetResolved);
		} else {
			logger.info ("Setting parent to null.");
			Thread.currentThread().dumpStack();
		}
		parent = p;
	}

	public Pipe getParent () { return parent; }

	public Pipe getParentRoot () {
		if (parent == null)
			return this;
		Pipe p = parent;
		while (p.parent != null)
			p = p.parent;
		return p;
	}

	protected Alphabet resolveDataAlphabet ()
	{
		//Thread.dumpStack ();
		//assert (parent != null);
		// xxx This is the problem assert (parent.dataDict != null);
		if (dataAlphabetResolved)
			throw new IllegalStateException ("Data Alphabet already resolved.");
		Alphabet pfd = parent == null ? null : parent.dataDict;
		if (pfd == null) {
			if (dataDict == null && dataDictClass != null) {
				try {
					dataDict = (Alphabet) dataDictClass.newInstance();
				} catch (Exception e) {
					throw new IllegalStateException ("Cannot create new data dictionary of class "+dataDictClass.getName());
				}
				logger.fine ("Creating data  Alphabet.");
			}
		} else {
			if (dataDict == null) {
				// This relies on the fact that these methods are called in order by
				// parents, and even if the parent has a different "dataDict" in its final
				// output, right now "parent.dataDict" will be whatever dictionary is coming
				// to this pipe at (that potentially intermediate) stage of the pipeline.
				dataDict = pfd;
				logger.fine ("Assigning data Alphabet from above.");
			}	else if (!dataDict.equals (pfd))
				throw new IllegalStateException ("Parent and local data Alphabet do not match.");
			else
				logger.fine ("Data Alphabet already matches.");
		}
		//assert (dataDict != null);
		dataAlphabetResolved = true;
		return dataDict;
	}

	protected Alphabet resolveTargetAlphabet ()
	{
		if (targetAlphabetResolved)
			throw new IllegalStateException ("Target Alphabet already resolved.");
		Alphabet pld = parent == null ? null : parent.targetDict;
		if (pld == null) {
			if (targetDict == null && targetDictClass != null)
				try {
					targetDict = (Alphabet) targetDictClass.newInstance();
				} catch (Exception e) {
					throw new IllegalStateException ("Cannot create new target dictionary of class "+targetDictClass.getName());
				}
		} else {
			if (targetDict == null)
				// This relies on the fact that these methods are called in order by
				// parents, and even if the parent has a different "targetDict" in its final
				// output, right now "parent.targetDict" will be whatever dictionary is coming
				// to this pipe at (that potentially intermediate) stage of the pipeline.
				targetDict = pld;
			else if (!targetDict.equals (pld))
				throw new IllegalStateException ("Parent and local target Alphabet do not match.");
		}
		//assert (targetDict != null);
		targetAlphabetResolved = true;
		return targetDict;
	}

	// If this Pipe produces objects that use a Alphabet, this
	// method returns that dictionary.  Even if this particular Pipe
	// doesn't use a Alphabet it may return non-null if
	// objects passing through it use a dictionary.

	// This method should not be called until the dictionary is really
	// needed, because it may set off a chain of events that "resolve"
	// the dictionaries of an entire pipeline, and generally this
	// resolution should not take place until the pipeline is completely
	// in place, and pipe() is being called.
  // xxx Perhaps desire to wait until pipe() is being called is unrealistic
	// and unnecessary.

	public Alphabet getDataAlphabet ()
	{
		//Thread.dumpStack();
		if (!dataAlphabetResolved)
			getParentRoot().resolveDataAlphabet();
		assert (dataAlphabetResolved);
		return dataDict;
	}

	public Alphabet getTargetAlphabet ()
	{
		if (!targetAlphabetResolved)
			getParentRoot().resolveTargetAlphabet();
		assert (targetAlphabetResolved);
		return targetDict;
	}

	public void setDataAlphabet (Alphabet dDict)
	{
		if (dataDict != null && dataDict.size() > 0)
			throw new IllegalStateException
				("Can't set this Pipe's Data  Alphabet; it already has one.");
		dataDict = dDict;
	}

    public boolean isDataAlphabetSet() 
    {
	System.out.println("Data Alphabet: " + dataDict);
	if (dataDict != null && dataDict.size() > 0)
	    return true;

	return false;
    }

	public void setTargetAlphabet (Alphabet tDict)
	{
		if (targetDict != null)
			throw new IllegalStateException
				("Can't set this Pipe's Target Alphabet; it already has one.");
		targetDict = tDict;
	}


    public VMID getInstanceId() { return instanceId;} // for debugging

	// Serialization

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;

	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(parent);
		out.writeObject(dataDict);
		out.writeObject(targetDict);
		out.writeBoolean(dataAlphabetResolved);
		out.writeBoolean(targetAlphabetResolved);
		out.writeBoolean(targetProcessing);
		out.writeObject(dataDictClass);
		out.writeObject(targetDictClass);
        out.writeObject(instanceId);
//        System.out.println("Pipe WriteObject: instance id= " + instanceId);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		parent = (Pipe) in.readObject();
		dataDict = (Alphabet) in.readObject();
		targetDict = (Alphabet) in.readObject();
		dataAlphabetResolved = in.readBoolean();
		targetAlphabetResolved = in.readBoolean();
		targetProcessing = in.readBoolean();
		dataDictClass = (Class) in.readObject();
		targetDictClass = (Class) in.readObject();
        if (version >0) {   //added in version 1
           instanceId = (VMID) in.readObject();
        }
//        System.out.println("Pipe ReadObject: instance id= " + instanceId);
	}

    private transient static HashMap deserializedEntries = new HashMap();
    /**
    * This gets called after readObject; it lets the object decide whether
    * to return itself or return a previously read in version.
    * We use a hashMap of instanceIds to determine if we have already read
    * in this object.
    * @return
    * @throws ObjectStreamException
    */

    public Object readResolve() throws ObjectStreamException {
       //System.out.println(" *** Pipe ReadResolve: instance id= " + instanceId);
       Object previous = deserializedEntries.get(instanceId);
       if (previous != null){
           //System.out.println(" *** Pipe ReadResolve:Resolving to previous instance. instance id= " + instanceId);
           return previous;
       }
       if (instanceId != null){
           deserializedEntries.put(instanceId, this);
       }
       //System.out.println(" *** Pipe ReadResolve: new instance. instance id= " + instanceId);
       return this;
    }
}
