/*
 * TransId.java
 *
 * Interface to ADisk
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.io.FileNotFoundException;

public class TransID{

  //
  // Implement this class
  //
	 private static long counter = 0;
	 private SimpleLock TransIDLock = new SimpleLock();
	 private long tid; 
	 
	 // Constructor
	 public TransID () {
	 	 try{
			 TransIDLock.lock();
			 tid = counter++;
			 return this;
		 } 
		 finally {
			 TransIDLock.unlock();
	    }
	 }

	 public long getTidfromTransID(){
	 	 return this.tid; 
	 }

	 /*public static long next() {
		
	 }*/
	 // for use in recovery
	 public static void setCounter(long c) {
		 counter = c;
	 }
}
