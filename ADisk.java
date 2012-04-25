/*
 * ADisk.java
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007, 2010 Mike Dahlin
 *
 * Michael Janusa mjjanusa@yahoo.com          5_slip days
 * Chinedu Egboh  tobe_egboh@mail.utexas.edu  5_slip days
 *
 */
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.io.FileNotFoundException;;

public class ADisk implements DiskCallback{

  //-------------------------------------------------------
  // The size of the redo log in sectors
  //-------------------------------------------------------
  public static final int REDO_LOG_SECTORS = 1024;

  SimpleLock ADisk_lock;
  SimpleLock waitLock; 
  Condition commitDone;
  Condition writebackDone;
  
  public int commitBarrierSector;
  public void setCBC(int i){ commitBarrierSector = i; }
  public int commitBarrierTid;
  public int writebackBarrierSector;
  public int writebackBarrierTid;
  
  public Disk d;
  public ActiveTransactionList atranslist;
  public WriteBackList wblist;
  public LogStatus lstatus;
  public float failprob = 0.0f;
  public Disk getDisk() { return d; }
  //-------------------------------------------------------
  //
  // Allocate an ADisk that stores its data using
  // a Disk.
  //
  // If format is true, wipe the current disk
  // and initialize data structures for an empty 
  // disk.
  //
  // Otherwise, initialize internal state, read the log, 
  // and redo any committed transactions. 
  //
  //-------------------------------------------------------
  public ADisk(boolean format)// Not done yet
  {  
	  // build lock
	  this.setFailureProb(0);
      ADisk_lock = new SimpleLock();
      waitLock = new SimpleLock();
      atranslist = new ActiveTransactionList();
      //initialize lists and logs
      lstatus = new LogStatus(this);
      wblist = new WriteBackList(this);
      lstatus = new LogStatus(this);
      
      commitDone = waitLock.newCondition();
      writebackDone = waitLock.newCondition();
      commitBarrierSector = 0;
      commitBarrierTid = 0;
      writebackBarrierSector = 0;
      writebackBarrierTid = 0;
      
      if (format == true){
    	    d = null;
    	    try{
    	      d = new Disk(this);
    	    }
    	    catch(FileNotFoundException fnf){
    	      System.out.println("Unable to open disk file");
    	      System.exit(-1);
    	    }
      } 
      else {
              // boot disk as normal, recovering if log is nonempty
//              failureRecovery();
      }
      
  }

  //-------------------------------------------------------
  //
  // Return the total number of data sectors that
  // can be used *not including space reseved for
  // the log or other data sructures*. This
  // number will be smaller than Disk.NUM_OF_SECTORS.
  //
  //-------------------------------------------------------
  public static int getNSectors()
  {
    return Disk.NUM_OF_SECTORS - ADisk.REDO_LOG_SECTORS - 1;
  } 

  //-------------------------------------------------------
  //
  // Begin a new transaction and return a transaction ID
  //
  //-------------------------------------------------------
  public TransID beginTransaction()// Not done yet
  {
	  //TransID tid = new TransID();
	  Transaction collect_trans = new Transaction(this);
	  atranslist.put(collect_trans);
	  return collect_trans.getTid();
  }

  //-------------------------------------------------------
  //
  // First issue writes to put all of the transaction's
  // writes in the log.
  //
  // Then issue a barrier to the Disk's write queue.
  //
  // Then, mark the log to indicate that the specified
  // transaction has been committed. 
  //
  // Then wait until the "commit" is safely on disk
  // (in the log).
  //
  // Then take some action to make sure that eventually
  // the updates in the log make it to their final
  // location on disk. Do not wait for these writes
  // to occur. These writes should be asynchronous.
  //
  // Note: You must ensure that (a) all writes in
  // the transaction are in the log *before* the
  // commit record is in the log and (b) the commit
  // record is in the log before this method returns.
  //
  // Throws 
  // IOException if the disk fails to complete
  // the commit or the log is full.
  //
  // IllegalArgumentException if tid does not refer
  // to an active transaction.
  // 
  //-------------------------------------------------------
  public void commitTransaction(TransID tid) 
    throws IOException, IllegalArgumentException{// Not yet complete
	  try {
		  ADisk_lock.lock();
		  // Call commit
		  atranslist.get(tid).commit();
		  Transaction temp = atranslist.remove(tid);
		  wblist.addCommitted(temp);	  
	  }
	  finally{
		  ADisk_lock.unlock();
	  }
  }
  



  //-------------------------------------------------------
  //
  // Free up the resources for this transaction without
  // committing any of the writes.
  //
  // Throws 
  // IllegalArgumentException if tid does not refer
  // to an active transaction.
  // 
  //-------------------------------------------------------
  public void abortTransaction(TransID tid) throws IllegalArgumentException, IOException{//done
	// Check that this is actually an active transaction
      atranslist.get(tid).abort();
      atranslist.remove(tid);
  }


  //-------------------------------------------------------
  //
  // Read the disk sector numbered sectorNum and place
  // the result in buffer. Note: the result of a read of a
  // sector must reflect the results of all previously
  // committed writes as well as any uncommitted writes
  // from the transaction tid. The read must not
  // reflect any writes from other active transactions
  // or writes from aborted transactions.
  //
  // Throws 
  // IOException if the disk fails to complete
  // the read.
  //
  // IllegalArgumentException if tid does not refer
  // to an active transaction or buffer is too small
  // to hold a sector.
  // 
  // IndexOutOfBoundsException if sectorNum is not
  // a valid sector number
  //
  //-------------------------------------------------------
  public void readSector(TransID tid, int sectorNum, byte buffer[])
    throws IOException, IllegalArgumentException, 
    IndexOutOfBoundsException{								// Not quite done yet
	  try{
		  ADisk_lock.lock();
//		  if (buffer==null || !atranslist.get(tid).checkRead(sectorNum, buffer) || buffer.length < Disk.SECTOR_SIZE)
//        	  throw new IllegalArgumentException();       
//          													// Check that this is a safe sector to access
//          if (sectorNum < Disk.NUM_OF_SECTORS-getNSectors() || sectorNum > Disk.NUM_OF_SECTORS)
//        	  throw new IndexOutOfBoundsException();
          
		  
//		  for(int i=0; i < buffer.length; i++){
//			  buffer[i] = 0;
//		  }
		  //int tag = (int)(tid.getTidfromTransID() % 10000);
          d.startRequest(Disk.READ, tid.getTidfromTransID(), sectorNum, buffer);
          
          wblist.checkRead(sectorNum, buffer);
          atranslist.get(tid).checkRead(sectorNum, buffer);
	  }
	  finally{
		  ADisk_lock.unlock();
	  }    
  }

  //-------------------------------------------------------
  //
  // Buffer the specified update as part of the in-memory
  // state of the specified transaction. Don't write
  // anything to disk yet.
  //  
  // Concurrency: The final value of a sector
  // must be the value written by the transaction that
  // commits the latest.
  //
  // Throws 
  // IllegalArgumentException if tid does not refer
  // to an active transaction or buffer is too small
  // to hold a sector.
  // 
  // IndexOutOfBoundsException if sectorNum is not
  // a valid sector number
  //
  //-------------------------------------------------------
  public void writeSector(TransID tid, int sectorNum, byte buffer[]){
	  atranslist.get(tid).addWrite(sectorNum, buffer);
  }
  //-------------------------------------------------------
  // Update the failure probability for testing
  //-------------------------------------------------------

  public void setFailureProb(float failprob)
  {
          this.failprob = failprob;
  }

  @Override
  public void requestDone(DiskResult result) {
	    try{
	      waitLock.lock();
		  //check for real error
		  if(result.getStatus() == DiskResult.REAL_ERROR){
	          System.out.println("UNEXPECTED ERROR: real IO error");
	          System.exit(-1);
	        }	
		  
		  //check if this is the commit barrier sector and signal appropriately 
		  if(result.getSectorNum() == commitBarrierSector && result.getTag() == commitBarrierTid && result.getOperation() == Disk.WRITE){
			  	commitBarrierSector = -1; 
			  	commitBarrierTid = -1;
			  	commitDone.signal();
			  	return;
		}
		  
		//check if this is the write back barrier sector and signal appropriately
		if(result.getTag() == writebackBarrierSector && result.getSectorNum() == writebackBarrierTid && result.getOperation() == Disk.WRITE){
		 	writebackBarrierSector = -1;
		  	writebackBarrierTid = -1;
		  	writebackDone.signal();
		  	return;
		}
	  
	 }
	 finally{
		 waitLock.unlock();
	 }
  	
  }
  
  //function to wait for commit to write 
  public void commitWait()
  {
    try{
      waitLock.lock();
      while(commitBarrierSector != -1){
    	  commitDone.awaitUninterruptibly();
      }
      return;
    }
    finally{
      waitLock.unlock();
    }
  }
    
}