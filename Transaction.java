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
	private long Tid;
    private Map<Integer, ByteBuffer> writes; // sector # to byteBuffer
    private SimpleLock mutex;
    private Status the_stat;

    private int logStart;
    private int logNSectors;

    private ByteBuffer header = null;
    private ByteBuffer body = null;
    private ByteBuffer footer = null;

    public enum Status {
      INPROGRESS,
      COMMITTED,
      ABORTED
    }

    public final static int HDR_Tag = 5555555;
    public final static int FTR_Tag = 9185444;
    public long getTid() { return Tid; }
    public ByteBuffer getHeader() { return header; }
    public ByteBuffer getBody() { return body; }
    public ByteBuffer getFooter() { return footer; }
    //////////////////////////////////////////////////////////////////
    // 
    // You can modify and add to the interfaces
    //
    // Constructors
    public Transaction() {
      Tid = new TransID().getTidfromTransID();
      writes = new HashMap<Integer, ByteBuffer>(); 
      mutex = new SimpleLock();
      the_stat = Status.INPROGRESS;
    }
    
    private Transaction(long tid) {
      Tid = tid;
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
          if(the_stat != Status.INPROGRESS) {					//If not in progress, throw out with exception 
            throw new IllegalArgumentException("transaction " + Tid + " not in progress");
          }
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

            if(the_stat != Status.INPROGRESS) {					//If not in progress, throw out with exception
              throw new IllegalArgumentException("transaction " + Tid + " not in progress");
            }

            Set<Map.Entry<Integer,ByteBuffer>> foo = writes.entrySet();

            int num_Sec_size = writes.size();
            												//Disk.SECTOR_SIZE gives you 512
            int headerlen = getHDRLenInSecs(num_Sec_size) * Disk.SECTOR_SIZE;
            int bodylen   = Disk.SECTOR_SIZE * num_Sec_size;
            int footerlen = Disk.SECTOR_SIZE;

            header = ByteBuffer.wrap(new byte[headerlen]);
            body = ByteBuffer.wrap(new byte[bodylen]);
            footer = ByteBuffer.wrap(new byte[footerlen]);

            header.putInt(Transaction.HDR_Tag);				// Attach the header tag
            header.putLong(this.Tid);
            header.putInt(this.writes.size());
            for(Map.Entry<Integer,ByteBuffer> e : foo){
              header.putInt(e.getKey());					// sector number 
              header.putInt(e.getValue().getInt());			// Take first word from sector aADisk.nd reset position
              e.getValue().position(0);
            }
            header.position(0);

            												// WRITE in body
            for(Map.Entry<Integer,ByteBuffer> e : foo){  
              e.getValue().putInt(e.getKey());				// overwrite first word with the sector Number
              body.put(e.getValue().array());
            }
            body.position(0);
            footer.putInt(Transaction.FTR_Tag);				// Attach the footer tag
            footer.putLong(this.Tid);
            footer.position(0);
          }
          finally {
        	  the_stat = Status.COMMITTED;
            mutex.unlock();
          }
    }

    public void abort() throws IOException, IllegalArgumentException
    {
    	try {
            mutex.lock();
            if(the_stat == Status.COMMITTED) {
              throw new IllegalArgumentException("transaction " + Tid + " already Done");
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
    public byte[] getSectorsForLog(){
    	byte[] bufToRet = null;
        try {
          mutex.lock();
          if(the_stat == Status.COMMITTED) {
            bufToRet = new byte[header.array().length + body.array().length + footer.array().length];
            System.arraycopy(header.array(), 0, bufToRet, 0, header.array().length);
            System.arraycopy(body.array(), 0, bufToRet, header.array().length, body.array().length);
            System.arraycopy(footer.array(), 0, bufToRet, header.array().length+body.array().length, footer.array().length);
          }
        }
        finally {
          mutex.unlock();
          return bufToRet;
        }
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
    	int num_Sec_size = writes.size();
    	if(the_stat != Status.COMMITTED) {
            throw new IllegalArgumentException("At this stage transaction yet to be committed");
    	}
    	
    	return num_Sec_size;
    }

    //
    // For a committed transaction, return
    // the sector number and body of the
    // ith sector to be updated. Return
    // a secNum and put the body of the
    // write in byte array. Used for writeback.
    //
    public int getUpdateI(int i, byte buffer[]){			// Not quite done yet***
    	int temp_hold = 0;
    	if(the_stat != Status.COMMITTED) {
            throw new IllegalArgumentException("At this stage transaction yet to be committed");
          }
    	temp_hold = buffer[i];
        return temp_hold;
    }

    
    //
    // Parse a sector from the log that *may*
    // be a transaction header. If so, return
    // the total number of sectors in the
    // log that should be read (including the
    // header and commit) to get the full transaction.
    //
    public static int parseHeader(byte buffer[]){
    	ByteBuffer by_buff = ByteBuffer.wrap(buffer);

        													// first 4 bytes is the Header Tag
        int tag = by_buff.getInt();
        assert tag == Transaction.HDR_Tag;

        // next 8 bytes = Tid
        //long tid = by_buff.getLong();

        // next 4 bytes = num of sectors
        int numUpdates = by_buff.getInt();

        int headerLen = getHDRLenInSecs(numUpdates);
        return headerLen + numUpdates + 1; 					// header + numSectors + commit
    }

    //
    // Parse k+2 sectors from disk (header + k update sectors + commit).
    // If this is a committed transaction, construct a
    // committed transaction and return it. Otherwise
    // throw an exception or return null.
    public static Transaction parseLogBytes(byte buffer[]){
    	ByteBuffer by_buff = ByteBuffer.wrap(buffer);

        long tid = by_buff.getLong();
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
        return newTrans;
    }
    
    private static int getHDRLenInSecs(int numSec){
        int temp = 4 + 8 + 4 + (8 * numSec);
        return (temp / Disk.SECTOR_SIZE) + (temp % Disk.SECTOR_SIZE == 0 ? 0 : 1);
    }
    
}

