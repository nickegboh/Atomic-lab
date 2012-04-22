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
    private static SimpleLock logLock;
    
    //start of the redo log (log at end of disk) 
	final static int redo_log_start = Disk.NUM_OF_SECTORS - ADisk.REDO_LOG_SECTORS - 1;
    
    //head - tail
    static int log_head = redo_log_start; 
    static int log_tail = redo_log_start; 
    
    //numbrt of available sectors in the log
    static int available_sectors = ADisk.REDO_LOG_SECTORS; 
    
    //static length of redo log
    static int redo_log_size = ADisk.REDO_LOG_SECTORS; 
    
    //head - tail locations in log
    static int head_location = Disk.NUM_OF_SECTORS - 1;
    	
    //
    // The write back for the specified range of
    // sectors is done. These sectors may be safely 
    // reused for future transactions. (Circular log)
    //
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
    public static boolean logWrite(byte[] transaction, TransID tid) throws IllegalArgumentException, IOException{
        try{
		logLock.lock();
		int sectors = transaction.length / Disk.SECTOR_SIZE;
		
		//assert bytes are even sector length
		assert((sectors % Disk.SECTOR_SIZE) == 0); 
		//assert this is a valid transaction
		assert(sectors >= 3);
				
		//reserve sectors for disk 
		int transaction_head = reserveLogSectors(sectors);
		
		if(transaction_head == -1)
			return false;
		
		int sectorStart = 0; 
		int sectorTail = Disk.SECTOR_SIZE - 1; 
		
		for(int i = 0; i < sectors; i++){
			byte [] thisSector = Arrays.copyOfRange(transaction, sectorStart, sectorTail);	
			ADisk.d.startRequest(Disk.WRITE, tid.getTidfromTransID(), transaction_head, thisSector);
			sectorStart += Disk.SECTOR_SIZE;
			sectorTail += Disk.SECTOR_SIZE; 
			transaction_head++; 
			transaction_head = ((transaction_head - redo_log_start) % redo_log_size) + redo_log_start; 
		}
		
	}
	finally { 
		logLock.unlock();
	}	    
	return true; 
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
    
     // 
    // Return the index of the log sector where
    // the next transaction should go.
    // Return -1 if not enough space in the redo log for number of Sectors      
    private static int reserveLogSectors(int nSectors) throws IllegalArgumentException, IOException
    {
    	if(available_sectors >= nSectors){
    		int temp = log_tail; 
    		log_tail += nSectors;
    		log_tail = ((log_tail + redo_log_size) % redo_log_size) + redo_log_size;
    		available_sectors -= nSectors; 
    		
    		//update tail on disk 
		    updateHeadTail();
    		
    		return temp;	
    	}
    	else 
    		return -1; 
    }
    
    // Update the head / tail on disk from the head tail variables.  
    private static void updateHeadTail() throws IllegalArgumentException, IOException{
    	ByteBuffer b = ByteBuffer.allocate(Disk.SECTOR_SIZE);
        b.putInt(0, log_head); 
        b.putInt(4, log_tail);
        byte[] headerBlock = b.array();
        
        ADisk.d.startRequest(Disk.WRITE, -1, head_location, headerBlock);
        
        return; 
    }
    
    //Set the head tail variables from the variables on disk 
    private void getHeadTail() throws IllegalArgumentException, IOException {
    	byte[] headerBlock = new byte[Disk.SECTOR_SIZE];
    	ADisk.d.startRequest(Disk.READ, -1, head_location, headerBlock);
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
				ADisk.d.startRequest(Disk.READ, -1, head_location, headerBlock);
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
				ADisk.d.startRequest(Disk.READ, -1, head_location, headerBlock);
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