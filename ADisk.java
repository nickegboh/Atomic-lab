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
  Condition readDone;
  Condition logReadDone;
  Condition wbbarrier;
  
  public int commitBarrierSector;
  public void setCBC(int i){ commitBarrierSector = i; }
  public int commitBarrierTid;
  public int writebackBarrierSector;
  public int writebackBarrierTid;
  public int readSector;
  public int readTid;
  public int logReadSector;
  public int logReadTid;
  public int wbbarrierSec;
  public int wbbarrierTag;
  public WriteBackThread writebackthread; 
	  
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
  public ADisk(boolean format)
  {  
	  // build lock
	  this.setFailureProb(0);
      ADisk_lock = new SimpleLock();
      waitLock = new SimpleLock();
      atranslist = new ActiveTransactionList();
      //initialize lists and logs
      wblist = new WriteBackList(this);
      commitDone = waitLock.newCondition();
      writebackDone = waitLock.newCondition();
      readDone = waitLock.newCondition();
      logReadDone = waitLock.newCondition();
      wbbarrier = waitLock.newCondition();
      commitBarrierSector = -1;
      commitBarrierTid = -1;
      writebackBarrierSector = -1;
      writebackBarrierTid = -1;
      readSector = -1;
      readTid = -1;
      logReadSector = -1;
      logReadTid = -1;
      wbbarrierSec = -1;
      wbbarrierTag = -1;
      
	  d = null;
	  try{
	    d = new Disk(this);
	  }
	  catch(FileNotFoundException fnf){
	    System.out.println("Unable to open disk file");
	    System.exit(-1);
	  }
	  
	  //create writeback thread
	  writebackthread = new WriteBackThread();
      try {
	      if (format == true){
	    	    lstatus = new LogStatus(this, false);
	      } 
	      else {
	    	  //RECOVERY
	    	  lstatus = new LogStatus(this, true);
	    	  byte[] next = lstatus.recoverNext();
	          while(next != null){
	        	  Transaction temp = Transaction.parseLogBytesDebug(next, this);
	        	  wblist.addCommitted(temp);	 
	        	  next = lstatus.recoverNext();
	          }     
	      }
      }
      catch(IOException e){
			System.out.println("IO exception");
			e.printStackTrace();
			System.exit(-1);
      }
      catch(IllegalArgumentException e){
			System.out.println("Illegal Argument exception");
			e.printStackTrace();
			System.exit(-1);
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
  public TransID beginTransaction()
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
  public void abortTransaction(TransID tid) throws IllegalArgumentException {//done
	// Check that this is actually an active transaction
      try {
		atranslist.get(tid).abort();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		System.out.println("IO exception");
		e.printStackTrace();
		System.exit(-1);
	}
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

		  readTid = tid.getTidfromTransID();
          readSector = sectorNum;
          d.startRequest(Disk.READ, tid.getTidfromTransID(), sectorNum, buffer);
          readWait();
          
          wblist.checkRead(sectorNum, buffer);
          Transaction temp = atranslist.get(tid);
          if(temp != null)
        	  temp.checkRead(sectorNum, buffer);
	  }
	  finally{
		  ADisk_lock.unlock();
	  }
  }
  
  
  /// Read sector DISK ONLY this is used for debugging to make sure changes were written to disk
  
  public void readSectorDiskOnly(TransID tid, int sectorNum, byte buffer[])
  throws IOException, IllegalArgumentException, 
  IndexOutOfBoundsException{								// Not quite done yet
	  try{
		  ADisk_lock.lock();

	    readTid = tid.getTidfromTransID();
	    readSector = sectorNum;
        d.startRequest(Disk.READ, tid.getTidfromTransID(), sectorNum, buffer);
        readWait();
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
		if(result.getTag() == writebackBarrierTid && result.getSectorNum() == writebackBarrierSector && result.getOperation() == Disk.WRITE){
		 	writebackBarrierSector = -1;
		  	writebackBarrierTid = -1;
		  	writebackDone.signal();
		  	return;
		}
		
		//check if this is a read request
		if(result.getTag() == readTid && result.getSectorNum() == readSector && result.getOperation() == Disk.READ){
		 	readSector = -1;
		  	readTid = -1;
		  	readDone.signal();
		  	return;
		}
				
		//check if this is a log read request
		if(result.getTag() == logReadTid && result.getSectorNum() == logReadSector){
		 	logReadSector = -1;
		  	logReadTid = -1;
		  	logReadDone.signal();
		  	return;
		}
		
		//check if this is a writeback barrier request
		if(result.getTag() == wbbarrierTag && result.getSectorNum() == wbbarrierSec && result.getOperation() == Disk.WRITE){
			wbbarrierTag = -1;
			wbbarrierSec = -1;
			wbbarrier.signal();
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
  
  //function to wait for disk read 
  public void readWait()
  {
    try{
      waitLock.lock();
      while(readSector != -1){
    	  readDone.awaitUninterruptibly();
      }
      return;
    }
    finally{
      waitLock.unlock();
    }
  }
  
  //function to wait for disk log 
  public void logReadWait()
  {
    try{
      waitLock.lock();
      while(logReadSector != -1){
    	  logReadDone.awaitUninterruptibly();
      }
      return;
    }
    finally{
      waitLock.unlock();
    }
  }
  
  //function to wait for writeback barrier
  public void wbbarrierWait()
  {
    try{
      waitLock.lock();
      while(wbbarrierTag != -1){
    	  wbbarrier.awaitUninterruptibly();
      }
      return;
    }
    finally{
      waitLock.unlock();
    }
  }

//Writeback thread!
 public class WriteBackThread extends Thread {
    
    public void run(ADisk theDisk) throws IllegalArgumentException, IOException{
       Transaction temp;
    	   temp = wblist.getNextWriteback();
    	   while(temp != null){
    		  //write next transaction to disk.
    		  int nsects = temp.getNUpdatedSectors();
    		  byte[] towrite = new byte[Disk.SECTOR_SIZE];
    		  for(int i = 0; i < nsects; i++){
    			  int secNum = temp.getUpdateI(i, towrite);
    			  //System.out.println("towrite[0] " + (char)towrite[0] + " secnum: " + secNum);
    			  wbbarrierTag = Disk.NUM_OF_SECTORS+5;
			  	  wbbarrierSec = secNum;
    			  theDisk.d.startRequest(Disk.WRITE, Disk.NUM_OF_SECTORS+5, secNum, towrite);
			  	  wbbarrierWait();			  	  
    			  /*if(i == (nsects-1)){
    				  //add barrier because can not remove from writeback list until all sectors written to disk. 
    				  theDisk.d.addBarrier();
    			  	  wbbarrierTag = Disk.NUM_OF_SECTORS+5;
    			  	  wbbarrierSec = secNum;
    			  }*/
    		  }
    		  //wbbarrierWait();
    		  wblist.removeNextWriteback();
    		  temp = wblist.getNextWriteback();
    	   }
  }
 }
    
}