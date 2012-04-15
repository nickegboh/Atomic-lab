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
	 public static long counter = 0;
	 private static int last_tid = 0;
	 private SimpleLock TransIDLock = new SimpleLock();
	 
	 // Constructor
	 public TransID (int last_tid) {
		 last_tid = 0;
	 }

	 public int newTID(){
		 try{
			 TransIDLock.lock();
	         return last_tid++;
		 } 
		 finally {
			 TransIDLock.unlock();
	    }
	 }

	 public static long next() {
		 return counter++;
	 }
	 // for use in recovery
	 public static void setCounter(long c) {
		 counter = c;
	 }
}
