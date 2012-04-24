/*
 * Transaction.java
 *
 * List of active transactions.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.locks.Condition;

public class Transaction{
	///////////////////////////////////////////////////////////////////
	private TransID Tid;
    private Map<Integer, ByteBuffer> writes; // sector # to byteBuffer
    private Object[] writesCommitted;
 
    private SimpleLock mutex;
    private Status the_stat;
    
    private ADisk theDisk;

    private int logStart;
    private int logNSectors;
    
    private static byte[] sectorsForLog; // FOR DEBUGGING

    public enum Status {
      INPROGRESS,
      COMMITTED,
      ABORTED
    }

    public final static int HDR_Tag = 5555555;
    public final static int FTR_Tag = 9185444;
    public TransID getTid() { return Tid; }
    public byte[] getSectorsForLogDebug(){
        if(the_stat != Status.COMMITTED) {
            throw new IllegalArgumentException("transaction " + Tid + " already committed");
          }
    	return sectorsForLog; } 
    //////////////////////////////////////////////////////////////////
    // 
    // You can modify and add to the interfaces
    //
    // Constructors
    public Transaction(ADisk myDisk) {
      Tid = new TransID();
      writes = new HashMap<Integer, ByteBuffer>(); 
      mutex = new SimpleLock();
      the_stat = Status.INPROGRESS;
      theDisk = myDisk;
    }
    
    private Transaction(int tid, ADisk myDisk) {
      Tid = new TransID(tid);
      writes = new HashMap<Integer, ByteBuffer>(); 
      mutex = new SimpleLock();
      the_stat = Status.INPROGRESS;
      theDisk = myDisk;
    }
    // 
    // You can modify and add to the interfaces
    //

    public void addWrite(int sectorNum, byte buffer[])
    throws IllegalArgumentException, IndexOutOfBoundsException{
    	try {
            mutex.lock();
            if(the_stat != Status.INPROGRESS) {
              throw new IllegalArgumentException("transaction " + Tid + " is not in progress");
            }
            if(sectorNum >= ADisk.getNSectors()) {
              throw new IndexOutOfBoundsException("invalid sectorNum " + sectorNum);
            }
            writes.put(sectorNum, ByteBuffer.wrap(buffer));
          } 
          finally {
            mutex.unlock();
          }
    }

    //
    // Return true if this transaction has written the specified
    // sector; in that case update buffer[] with the written value.
    // Return false if this transaction has not written this sector.
    //
    public boolean checkRead(int sectorNum, byte buffer[])
    throws IllegalArgumentException, 
           IndexOutOfBoundsException
    {
    	boolean read_yet = false;
        try {
          mutex.lock();
          if(sectorNum >= ADisk.getNSectors()) {			//If invalid secNum, throw out with exception
            throw new IndexOutOfBoundsException("invalid sectorNum " + sectorNum);
          }
          // Write sector the return true
          ByteBuffer bufToReturn = writes.get(sectorNum);
          if(bufToReturn != null) {
            System.arraycopy(bufToReturn.array(), 0, buffer, 0, buffer.length);
            read_yet = true;
          }
        }
        finally {
          mutex.unlock();
        }
        return read_yet;
    }


    public void commit() throws IOException, IllegalArgumentException{
    	try {
            mutex.lock();
            
            //Ensure the transaction is in progress
            if(the_stat != Status.INPROGRESS) {					//If not in progress, throw out with exception
              throw new IllegalArgumentException("transaction " + Tid + " not in progress");
            }
            
            //Get the bytes to be written to the log
            byte[] toWrite = getSectorsForLog(); 
            this.sectorsForLog = toWrite;
            
            //Write bytes to the log
            int transaction_head = theDisk.lstatus.logWrite(toWrite, Tid);
            if(transaction_head == -1){
            	System.out.println("Redo Log Full!");
            	System.exit(-1);
            }
            this.rememberLogSectors(transaction_head, (toWrite.length / Disk.SECTOR_SIZE));
                        
            //Create static order write array
            this.writesCommitted = writes.entrySet().toArray();
            
            //Remove from active transaction list
            //ADisk.atranslist.remove(Tid);
            	
            the_stat = Status.COMMITTED;
       }
       finally {
            mutex.unlock();
       }
    }
    
    private void commitRecovery() {
    	try {
            mutex.lock();
            
            //Ensure the transaction is in progress
            if(the_stat != Status.INPROGRESS) {					//If not in progress, throw out with exception
              throw new IllegalArgumentException("transaction " + Tid + " not in progress");
            }
            
            //Get the bytes to be written to the log
            byte[] toWrite = getSectorsForLog(); 
            this.sectorsForLog = toWrite;
            
            //Create static order write array
            this.writesCommitted = writes.entrySet().toArray();
            	
            the_stat = Status.COMMITTED;
       }
       finally {
            mutex.unlock();
       }
    }

    public void abort() throws IOException, IllegalArgumentException
    {
    	try {
            mutex.lock();
            if(the_stat == Status.COMMITTED) {
              throw new IllegalArgumentException("transaction " + Tid + " already committed");
            }
          }
          finally {
        	the_stat = Status.ABORTED;
            mutex.unlock();
          }
    }
    
    // 
    // These methods help get a transaction from memory to
    // the log (on commit), get a committed transaction's writes
    // (for writeback), and get a transaction from the log
    // to memory (for recovery).

    // For a committed transaction, return a byte
    // array that can be written to some number
    // of sectors in the log in order to place
    // this transaction on disk. Note that the
    // first sector is the header, which lists
    // which sectors the transaction updates
    // and the last sector is the commit. 
    // The k sectors between should contain
    // the k writes by this transaction.
    //
    private byte[] getSectorsForLog(){
    	
    	byte[] bufToRet = null;
         
        ByteBuffer header = null;
        ByteBuffer body = null;
        ByteBuffer footer = null;
          
        // Generate header Byte Buffer (4 bytes header tag, 4 bytes for tid, 4 bytes for num of write sectors, 4 bytes number of sectors in header, 4 bytes for sector number of each sector to be written)
	      //Calculate header length
          int headlen = 4 + 4 + 4 + 4 + (4 * writes.size());
	      if((headlen % Disk.SECTOR_SIZE) != 0)
	      	  headlen = headlen / Disk.SECTOR_SIZE + 1;
	      else 
	      	  headlen = headlen / Disk.SECTOR_SIZE;
	      headlen = headlen * Disk.SECTOR_SIZE; 
	      //Insert transaction information to header
	      header = ByteBuffer.wrap(new byte[headlen]);
	      header.putInt(Transaction.HDR_Tag);	//insert the header tag
	      header.putInt(this.Tid.getTidfromTransID());				//insert the tid
	      header.putInt(this.writes.size());		//insert number of write sectors
	      header.putInt(headlen / Disk.SECTOR_SIZE);				//insert number of header sectors
	      //Calculate body length
	      int bodylen   = Disk.SECTOR_SIZE * this.writes.size();
	      //Create body byte buffer
	      body = ByteBuffer.wrap(new byte[bodylen]);
	      //Insert write sector information to header and body 
	      Set<Map.Entry<Integer,ByteBuffer>> foo = writes.entrySet();
	      for(Map.Entry<Integer,ByteBuffer> e : foo){
	        header.putInt(e.getKey());		// insert sector number to header 
	        body.put(e.getValue());			// insert sector data to body 
	      }
	      //create footer byte buffer
	      int footerlen = Disk.SECTOR_SIZE;					//calculate footer length
	      footer = ByteBuffer.wrap(new byte[footerlen]);	//create footer byte buffer
	      footer.putInt(Transaction.FTR_Tag);			 	// insert the footer tag
	      footer.putInt(this.Tid.getTidfromTransID());							//insert tid
	      //reposition byte buffers
	      header.position(0);
	      body.position(0);
	      footer.position(0);
	          
        //Create byte buffer for log for this transaction from header, body, footer
        bufToRet = new byte[header.array().length + body.array().length + footer.array().length];
        System.arraycopy(header.array(), 0, bufToRet, 0, header.array().length);
        System.arraycopy(body.array(), 0, bufToRet, header.array().length, body.array().length);
        System.arraycopy(footer.array(), 0, bufToRet, header.array().length+body.array().length, footer.array().length);
        
        return bufToRet;
    }

    //
    // You'll want to remember where a Transactions
    // log record ended up in the log so that
    // you can free this part of the log when
    // writeback is done.
    //
    public void rememberLogSectors(int start, int nSectors){
    	logStart = start;
        logNSectors = nSectors;
    }
    public int recallLogSectorStart(){
    	return logStart;
    }
    public int recallLogSectorNSectors(){
    	return logNSectors;
    }

    //
    // For a committed transaction, return
    // the number of sectors that this
    // transaction updates. Used for writeback.
    //
    public int getNUpdatedSectors(){						// Not quite done yet***//throws IllegalArgumentException 
    	if(the_stat != Status.COMMITTED) {
            throw new IllegalArgumentException("At this stage transaction yet to be committed");
    	}
    	return writesCommitted.length;
    }

    //
    // For a committed transaction, return
    // the sector number and body of the
    // ith sector to be updated. Return
    // a secNum and put the body of the
    // write in byte array. Used for writeback.
    //
    public int getUpdateI(int i, byte buffer[]){
    	Map.Entry<Integer,ByteBuffer> temp_write = (Map.Entry<Integer,ByteBuffer>)this.writesCommitted[i];
    	byte[] temp = temp_write.getValue().array();
    	for(int j = 0; j < Disk.SECTOR_SIZE; j++)
    		buffer[j] = temp[j];
    	return temp_write.getKey();
    }

    
    //
    // Parse a sector from the log that *may*
    // be a transaction header. If so, return
    // the total number of sectors in the
    // log that should be read (including the
    // header and commit) to get the full transaction.
    //
    public static int parseHeader(byte buffer[]){
    	assert(buffer.length == Disk.SECTOR_SIZE);
    	ByteBuffer temp = ByteBuffer.wrap(buffer);
    	int headerTag = temp.getInt();
    	if(headerTag != Transaction.HDR_Tag)
    		return -1;
    	int tid_temp = temp.getInt();
    	int write_length = temp.getInt();
    	int header_length = temp.getInt(); 
    	return (write_length + header_length + 1);
    }

    //
    // Parse k+2 sectors from disk (header + k update sectors + commit).
    // If this is a committed transaction, construct a
    // committed transaction and return it. Otherwise
    // throw an exception or return null.
    public Transaction parseLogBytes(byte buffer[]){
    	ByteBuffer trans_log = ByteBuffer.wrap(buffer);
    	
    	//read header info
    	int headerTag = trans_log.getInt();
    	if(headerTag != Transaction.HDR_Tag)
    		return null;
    	int tid_temp = trans_log.getInt();
    	int write_length = trans_log.getInt();
    	int header_length = trans_log.getInt();
    	
    	//read sector number's from header
    	int[] sectorNums = new int[write_length];
    	for(int i = 0; i < write_length; i++){
    		sectorNums[i] = trans_log.getInt();
    	}
    	
    	//Create new transaction
    	Transaction newTrans = new Transaction(tid_temp, this.theDisk);
    	
    	//Create writes in transactoin from log
    	int sectorStart = header_length * Disk.SECTOR_SIZE; 
		int sectorTail = sectorStart + Disk.SECTOR_SIZE; 
    	for(int i = 0; i < write_length; i++){
    		byte [] thisSector = Arrays.copyOfRange(buffer, sectorStart, sectorTail);
    		newTrans.addWrite(sectorNums[i], thisSector);
    		sectorStart += Disk.SECTOR_SIZE;
    		sectorTail += Disk.SECTOR_SIZE;
    	}
        
        // position trans_log to the footer sector
    	trans_log.position((header_length + write_length) * Disk.SECTOR_SIZE);
    	int footerTag = trans_log.getInt();
    	if(footerTag != Transaction.FTR_Tag)
    		return null;
        int endTid = trans_log.getInt();
        if(endTid != tid_temp) 
          return null;
        
        newTrans.commitRecovery();
        // newTrans is valid, return it to ADisk
        return newTrans;
    }
    
    public static Transaction parseLogBytesDebug(byte buffer[], ADisk myDisk){
    	ByteBuffer trans_log = ByteBuffer.wrap(buffer);
    	
    	//read header info
    	int headerTag = trans_log.getInt();
    	if(headerTag != Transaction.HDR_Tag)
    		return null;
    	int tid_temp = trans_log.getInt();
    	int write_length = trans_log.getInt();
    	int header_length = trans_log.getInt();
    	
    	//read sector number's from header
    	int[] sectorNums = new int[write_length];
    	for(int i = 0; i < write_length; i++){
    		sectorNums[i] = trans_log.getInt();
    	}
    	
    	//Create new transaction
    	Transaction newTrans = new Transaction(tid_temp, myDisk);
    	
    	//Create writes in transactoin from log
    	int sectorStart = header_length * Disk.SECTOR_SIZE; 
		int sectorTail = sectorStart + Disk.SECTOR_SIZE; 
    	for(int i = 0; i < write_length; i++){
    		byte [] thisSector = Arrays.copyOfRange(buffer, sectorStart, sectorTail);
    		newTrans.addWrite(sectorNums[i], thisSector);
    		sectorStart += Disk.SECTOR_SIZE;
    		sectorTail += Disk.SECTOR_SIZE;
    	}
        
        // position trans_log to the footer sector
    	trans_log.position((header_length + write_length) * Disk.SECTOR_SIZE);
    	int footerTag = trans_log.getInt();
    	if(footerTag != Transaction.FTR_Tag)
    		return null;
        int endTid = trans_log.getInt();
        if(endTid != tid_temp) 
          return null;
        
        newTrans.commitRecovery();
        // newTrans is valid, return it to ADisk
        return newTrans;
    }
    
}

