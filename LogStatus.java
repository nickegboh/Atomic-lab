import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;


/*
 * LogStatus.java
 *
 * Keep track of where head of log is (where should next
 * committed transactio go); where tail is (where
 * has write-back gotten); and where recovery
 * should start.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */
public class LogStatus{

    //LOG LOCK
    private SimpleLock logLock = new SimpleLock();
    
    //Adisk
    private ADisk theDisk; 
    
    //start of the redo log (log at end of disk) 
	final static int redo_log_start = Disk.NUM_OF_SECTORS - ADisk.REDO_LOG_SECTORS - 1;
    
    //head - tail
    private int log_head; 
    private int log_tail;
    private int recovery_head;
    private int recovery_tail; 
    
    //numbrt of available sectors in the log
    private int available_sectors = ADisk.REDO_LOG_SECTORS; 
    
    //static length of redo log
    private int redo_log_size = ADisk.REDO_LOG_SECTORS; 
    
    //head - tail locations in log
    private int head_location = Disk.NUM_OF_SECTORS - 1;
    	
    //
    // The write back for the specified range of
    // sectors is done. These sectors may be safely 
    // reused for future transactions. (Circular log)
    //
    public LogStatus(ADisk myDisk, boolean recovery) throws IllegalArgumentException, IOException{
    	theDisk = myDisk;
    	if(!recovery){
    		log_head = redo_log_start; 
        	log_tail = redo_log_start;
    	}
    	else {
    		recoveryInitializeLog();
    	}
    		
    }
    
    public void writeBackDone(int startSector, int nSectors) throws IllegalArgumentException, IOException
    {
        try{
		logLock.lock();
		assert(log_head == startSector); 
		log_head += nSectors;
		log_head = ((log_head - redo_log_start) % redo_log_size) + redo_log_start;
		available_sectors += nSectors;
		
		//update head on disk 
		updateHeadTail();
	}
	finally { 
		logLock.unlock();
	}
        
    }
    
    //
    //Write a transaction to the log.  Receives transaction in the form of an array of bytes.  
    //Returns true for success and false for fail.  
    public int logWrite(byte[] transaction, TransID tid) throws IllegalArgumentException, IOException{
        int returnval = 1;
    	try{
		logLock.lock();
		int sectors = transaction.length / Disk.SECTOR_SIZE;
		
		//assert bytes are even sector length
		assert((sectors % Disk.SECTOR_SIZE) == 0); 
		//assert this is a valid transaction
		assert(sectors >= 3);
				
		//reserve sectors for disk 
		int transaction_head = reserveLogSectors(sectors);
		returnval = transaction_head;
		
		if(transaction_head == -1)
			return returnval;
		
		int sectorStart = 0; 
		int sectorTail = Disk.SECTOR_SIZE; 
		
		for(int i = 0; i < sectors; i++){
			//if this is the end of body sectors add barrier
			if(i == sectors-1){
				theDisk.commitBarrierTid = tid.getTidfromTransID();
				theDisk.commitBarrierSector = transaction_head; 	
				theDisk.d.addBarrier();
			}
			byte [] thisSector = Arrays.copyOfRange(transaction, sectorStart, sectorTail);
			theDisk.getDisk().startRequest(Disk.WRITE, tid.getTidfromTransID(), transaction_head, thisSector);
			sectorStart += Disk.SECTOR_SIZE;
			sectorTail += Disk.SECTOR_SIZE;			
			transaction_head++; 
			transaction_head = ((transaction_head - redo_log_start) % redo_log_size) + redo_log_start; 
		}
		
		//wait for the log to finish writing the commit sector
	    theDisk.commitWait();
		
	}
	finally { 
		logLock.unlock();
	}	    
	return returnval; 
    }

    //
    // During recovery, we need to initialize the
    // LogStatus information with the sectors 
    // in the log that are in-use by committed
    // transactions with pending write-backs
    //
    public void recoveryInitializeLog() throws IllegalArgumentException, IOException
    {
    	    try{
                    logLock.lock();
				    // get head and tail info from disk
				    getHeadTail();
				    recovery_head = log_head;
				    recovery_tail = log_tail; 
				    
				    // calculate the available sectors
				    if(log_head <= log_tail) 
					    available_sectors =  redo_log_size - (log_tail - log_head);
				    else
					    available_sectors = redo_log_size - (redo_log_size - (log_head - redo_log_start) - (log_tail - redo_log_start));
	    }
	    finally { 
	    	    logLock.unlock();
	    }
    	    
    }
    
    //Recover the next write transaction from the log
    public byte[] recoverNext() throws IllegalArgumentException, IOException{
    	byte[] result;
    	try{
            logLock.lock();
	    	if(recovery_head == log_tail)
	    		return null; 
	    	byte[] nextHeader = new byte[Disk.SECTOR_SIZE];
	        theDisk.logReadTid = Disk.SECTOR_SIZE;
	        theDisk.logReadSector = recovery_head;
	        theDisk.d.startRequest(Disk.READ, Disk.SECTOR_SIZE, recovery_head, nextHeader);
	        theDisk.logReadWait();
	    	
	    	int nextLength = Transaction.parseHeader(nextHeader);
	    	result = new byte[Disk.SECTOR_SIZE * nextLength];
	    	
	    	byte[] tempByte = new byte[Disk.SECTOR_SIZE];
	    	//Read in the transaction
	    	for(int i = 0; i < nextLength; i++){
	            theDisk.logReadTid = Disk.SECTOR_SIZE;
	            theDisk.logReadSector = recovery_head;
	            theDisk.d.startRequest(Disk.READ, Disk.SECTOR_SIZE, recovery_head, tempByte);
	            theDisk.logReadWait();
	            System.arraycopy(tempByte, 0, result, i*Disk.SECTOR_SIZE, Disk.SECTOR_SIZE);
	            recovery_head++; 
	            if(recovery_head > redo_log_size + redo_log_start)
	            	recovery_head = redo_log_start; 
	    	}
    	}
	    finally { 
	        logLock.unlock();
	    }
        return result;
    	 
    }
    
     // 
    // Return the index of the log sector where
    // the next transaction should go.
    // Return -1 if not enough space in the redo log for number of Sectors      
    private int reserveLogSectors(int nSectors) throws IllegalArgumentException, IOException
    {
    	if(available_sectors >= nSectors){
    		int temp = log_tail; 
    		log_tail += nSectors;
    		log_tail = ((log_tail - redo_log_start) % redo_log_size) + redo_log_start;
    		available_sectors -= nSectors; 
    		
    		//update tail on disk 
		    updateHeadTail();
    		
    		return temp;	
    	}
    	else 
    		return -1; 
    }
    
    // Update the head / tail on disk from the head tail variables.  
    private void updateHeadTail() throws IllegalArgumentException, IOException{
    	ByteBuffer b = ByteBuffer.allocate(Disk.SECTOR_SIZE);
        b.putInt(0, log_head); 
        b.putInt(4, log_tail);
        byte[] headerBlock = b.array();
        
        theDisk.logReadTid = Disk.NUM_OF_SECTORS;
        theDisk.logReadSector = head_location;
        theDisk.getDisk().startRequest(Disk.WRITE, Disk.NUM_OF_SECTORS, head_location, headerBlock);
        theDisk.logReadWait();
        
        return; 
    }
    
    //Set the head tail variables from the variables on disk 
    private void getHeadTail() throws IllegalArgumentException, IOException {
    	byte[] headerBlock = new byte[Disk.SECTOR_SIZE];
        theDisk.logReadTid = Disk.NUM_OF_SECTORS;
        theDisk.logReadSector = head_location;
    	theDisk.getDisk().startRequest(Disk.READ, Disk.NUM_OF_SECTORS, head_location, headerBlock);
        theDisk.logReadWait();
    	
    	ByteBuffer b = ByteBuffer.allocate(Disk.SECTOR_SIZE);
    	b.put(headerBlock);
    	log_head = b.getInt(0); 
    	log_tail = b.getInt(4); 
    }
    
    
    // DEBUGGING METHODS
    	    // Get the head sector number from the head variable on disk
	    public int getHeadDisk () {
	    	int temp = -1; 
		    try{
	           logLock.lock();
				byte[] headerBlock = new byte[Disk.SECTOR_SIZE];
		        theDisk.logReadTid = Disk.NUM_OF_SECTORS;
		        theDisk.logReadSector = head_location;
				theDisk.getDisk().startRequest(Disk.READ, Disk.NUM_OF_SECTORS, head_location, headerBlock);
		        theDisk.logReadWait();
				ByteBuffer b = ByteBuffer.allocate(Disk.SECTOR_SIZE);
				b.put(headerBlock);   
				temp =  b.getInt(0);
			} catch (IllegalArgumentException e) {
				System.out.println("IllegalArgumentException getHeadDisk() LogStatus.java.\n");
                System.exit(1);
			} catch (IOException e) {
				System.out.println("IOException getHeadDisk() LogStatus.java.\n");
                System.exit(1);
			}
			finally { 
				logLock.unlock();
			}
			return temp;
	    }
	    
	    // Get the tail sector number from the tail variable on disk 
	    public int getTailDisk () {
	    	int temp = -1; 
		    try{
	           logLock.lock();
				byte[] headerBlock = new byte[Disk.SECTOR_SIZE];
		        theDisk.logReadTid = Disk.NUM_OF_SECTORS;
		        theDisk.logReadSector = head_location;
				theDisk.getDisk().startRequest(Disk.READ, Disk.NUM_OF_SECTORS, head_location, headerBlock);
		        theDisk.logReadWait();
				ByteBuffer b = ByteBuffer.allocate(Disk.SECTOR_SIZE);
				b.put(headerBlock);   
				temp =  b.getInt(4);
			} catch (IllegalArgumentException e) {
				System.out.println("IllegalArgumentException getHeadDisk() LogStatus.java.\n");
                System.exit(1);
			} catch (IOException e) {
				System.out.println("IOException getHeadDisk() LogStatus.java.\n");
                System.exit(1);
			}
			finally { 
				logLock.unlock();
			}
			return temp;
	    }
    // END DEBUGGING METHODS
    
}