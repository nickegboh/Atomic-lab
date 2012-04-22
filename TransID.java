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
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.concurrent.locks.Condition;
//import java.io.FileNotFoundException;

public class TransID{

  //
  // Implement this class
  //

	 private static int counter = 0;
	 private SimpleLock TransIDLock = new SimpleLock();
	 private int tid; 
	 
	 // Constructor
	 public TransID() {
	 	 try{
			 TransIDLock.lock();
			 tid = counter++;
		 } 
		 finally {
			 TransIDLock.unlock();
	    }
	 }
	 
	 public TransID(int tidGiven) {
	 	 try{
			 TransIDLock.lock();
			 tid = tidGiven;
			 setCounter(tidGiven+1);
		 } 
		 finally {
			 TransIDLock.unlock();
	    }
	 }

	 public int getTidfromTransID(){
	 	 return this.tid; 
	 }

	 // for use in recovery
	 public static void setCounter(int c) {
		 counter = c;
	 }
	
}