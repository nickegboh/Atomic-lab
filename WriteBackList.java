import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

/*
 * WriteBackList.java
 *
 * List of commited transactions with pending writebacks.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */
public class WriteBackList{
	
	
	private LinkedList<Transaction> writeBackTransactions; 
	private SimpleLock WriteBackLock;
	private ADisk theDisk;
	
    // 
    // You can modify and add to the interfaces
    //

	//constructor
	 public WriteBackList(ADisk myDisk) {
		 WriteBackLock = new SimpleLock();
		 writeBackTransactions = new LinkedList<Transaction>();
		 theDisk = myDisk; 
	 }
    // Once a transaction is committed in the log,
    // move it from the ActiveTransactionList to 
    // the WriteBackList
    public void addCommitted(Transaction t) throws IllegalArgumentException, IOException{
	 	 try{
	 		WriteBackLock.lock();
			writeBackTransactions.add(t);
			if(!theDisk.writebackthread.isAlive())
				theDisk.writebackthread.run(theDisk);
		 } 
		 finally {
			 WriteBackLock.unlock();
	    }
    }

    //
    // A write-back thread should process
    // writebacks in FIFO order.
    //
    // NOTE: Don't remove the Transaction from
    // the list until the writeback is done
    // (reads need to see them)!
    //
    // NOTE: Service transactions in FIFO order
    // so that if there are multiple writes
    // to the same sector, the write that is
    // part of the last-committed transaction "wins".
    //
    // NOTE: you need to use log order for commit
    // order -- the transaction IDs are assigned
    // when transactions are created, so commit
    // order may not match transaction ID order.
    //    
    public Transaction getNextWriteback(){
	 	Transaction temp;  
    	try{
		 		WriteBackLock.lock();
				temp = writeBackTransactions.peek();
			 } 
	 	 finally {
				 WriteBackLock.unlock();
		 }
	 	 return temp;
    }

    //
    // Remove a transaction -- its writebacks
    // are now safely on disk.
    //
    public Transaction removeNextWriteback(){
	 	Transaction temp;  
    	try{
		 		WriteBackLock.lock();
				temp = writeBackTransactions.remove();
			 } 
	 	 finally {
				 WriteBackLock.unlock();
		 }
	 	 return temp;
    }

    //
    // Check to see if a sector has been written
    // by a committed transaction. If there
    // are multiple writes to the same sector,
    // be sure to return the last-committed write.
    //
    public boolean checkRead(int secNum, byte buffer[])
    throws IllegalArgumentException, 
           IndexOutOfBoundsException
    {
        ListIterator<Transaction> i = writeBackTransactions.listIterator();
        boolean updateFound = false;;
        while(i.hasNext())
        	if(i.next().checkRead(secNum, buffer))
        		updateFound = true; 
        return updateFound;
    }

    
    
}