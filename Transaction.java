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
import java.util.HashMap;
import java.util.concurrent.locks.Condition;

public class Transaction{
	///////////////////////////////////////////////////////////////////
	private TransID Tid;
    private Map<Integer, ByteBuffer> writes; // sector # to byteBuffer
    private Object[] writesCommitted;
 
    private SimpleLock mutex;
    private Status the_stat;

    private int logStart;
    private int logNSectors;

    public enum Status {
      INPROGRESS,
      COMMITTED,
      ABORTED
    }

    public final static int HDR_Tag = 5555555;
    public final static int FTR_Tag = 9185444;
    public TransID getTid() { return Tid; }
    //////////////////////////////////////////////////////////////////
    // 
    // You can modify and add to the interfaces
    //
    // Constructors
    public Transaction() {
      Tid = new TransID();
      writes = new HashMap<Integer, ByteBuffer>(); 
      mutex = new SimpleLock();
      the_stat = Status.INPROGRESS;
      //ADisk.atranslist.put(this);
    }
    
    private Transaction(int tid) {
      Tid = new TransID(tid);
      writes = new HashMap<Integer, ByteBuffer>(); 
      mutex = new SimpleLock();
      the_stat = Status.INPROGRESS;
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
            
            //Write bytes to the log
            if(!LogStatus.logWrite(toWrite, Tid)){
            	System.out.println("Redo Log Full!");
            	System.exit(-1);
            }
            
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
	      header.putInt(headlen);				//insert number of header sectors
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
    	/*ByteBuffer by_buff = ByteBuffer.wrap(buffer);

        													// first 4 bytes is the Header Tag
        int tag = by_buff.getInt();
        assert tag == Transaction.HDR_Tag;

        // next 8 bytes = Tid
        //long tid = by_buff.getLong();

        // next 4 bytes = num of sectors
        int numUpdates = by_buff.getInt();

        int headerLen = getHDRLenInSecs(numUpdates);*/
        return -1;
    }

    //
    // Parse k+2 sectors from disk (header + k update sectors + commit).
    // If this is a committed transaction, construct a
    // committed transaction and return it. Otherwise
    // throw an exception or return null.
    public static Transaction parseLogBytes(byte buffer[]){
    	/*ByteBuffer by_buff = ByteBuffer.wrap(buffer);

        int tid = by_buff.getInt();
        Transaction newTrans = new Transaction(tid);

        													// Populate the sector number list
        int numUpdates = by_buff.getInt();
        ArrayList<Integer> sectorNums = new ArrayList<Integer>();
        for(int i = 0; i < numUpdates; ++i) {
          sectorNums.add(by_buff.getInt());
        }

        													// Push position to first sector after the header
        by_buff.position(getHDRLenInSecs(numUpdates) * Disk.SECTOR_SIZE);

        													// For each sector, write with (sectorNum[i], update)
        for(int i = 0; i < numUpdates; ++i) {
          byte[] update = new byte[Disk.SECTOR_SIZE];
          by_buff.get(update);
          newTrans.addWrite(sectorNums.get(i), update);
        }

        													// read Tid from commit
        long endTid = by_buff.getLong();
        if(endTid != tid) {
          return null;
        }
        													// newTrans is valid, return it to ADisk
        return newTrans;*/
    	return null;
    }
    
}

