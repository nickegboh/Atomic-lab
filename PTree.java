/*
 * PTree -- persistent tree
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007, 2010, 2012 Mike Dahlin
 *
 */

import java.io.IOException;
import java.io.EOFException;
import java.util.concurrent.locks.Condition;

public class PTree{
  public static final int METADATA_SIZE = 64;
  public static final int MAX_TREES = 512;
  public static final int MAX_BLOCK_ID = Integer.MAX_VALUE; 

  //
  // Arguments to getParam
  //
  public static final int ASK_FREE_SPACE = 997;
  public static final int ASK_MAX_TREES = 13425;
  public static final int ASK_FREE_TREES = 23421;

  //
  // TNode structure
  //
  public static final int TNODE_POINTERS = 8;
  public static final int BLOCK_SIZE_BYTES = 1024;
  public static final int POINTERS_PER_INTERNAL_NODE = 256;
  
  //
  // New Args we added 
  //
  public ADisk d;
  public TransID tid;
  SimpleLock Ptree_lock;
  Condition noActiveTrans;
  public boolean freeLSTInMEM;
  public boolean[] treeIDs; 
  public byte[] freeList;
  
  //so, 4 tnodes per sector = 128 sectors for TNode Array
  public static final int SIZE_OF_TNODE = 128;
  public static final int SIZE_OF_FREELIST = 2;
  public static final int SIZE_OF_TNODE_ARRAY = 128;
  // + 2 is so we never write to block 1
  public static final int SIZE_OF_PTREE_METADATA = SIZE_OF_FREELIST + SIZE_OF_TNODE_ARRAY;
  // 16384 - 1156 = 15228 available sectors... /2 = 7614
  // put the extra minus one because otherwise, odd number of sectors.
  public static final int TOT_AVAIL_BLKS = (Disk.NUM_OF_SECTORS - ADisk.REDO_LOG_SECTORS - 1 - SIZE_OF_PTREE_METADATA - 1) / 2;
  public static final byte[] zeroBuffer = new byte[BLOCK_SIZE_BYTES];


  /* This function is the constructor. If doFormat == false, data stored 
   * in previous sessions must remain stored. If doFormat == true, the 
   * system should initialize the underlying disk to empty. 
   */
  public PTree(boolean doFormat)						//TODO
  {
	  Ptree_lock = new SimpleLock();	//mutex lock
	  noActiveTrans = Ptree_lock.newCondition();
	  d = new ADisk(doFormat);
	  treeIDs = new boolean[MAX_TREES];
	  freeList = new byte[BLOCK_SIZE_BYTES];
	  freeLSTInMEM = false;
	  
	  if(doFormat == true){
		  
	  }
	  else{
		  
	  }
  }

  /* This function begins a new transaction and returns 
   * an identifying transaction ID.
   */
  public TransID beginTrans()
  {
	TransID returnTID; 
	try{
	  Ptree_lock.lock();
	  while(d.atranslist.size() > 0)
		  noActiveTrans.awaitUninterruptibly();
	  	  returnTID = d.beginTransaction();
	} 
	finally {
        Ptree_lock.unlock();
    }
	return returnTID; 
  }

  /*
   * This function commits the specified transaction.
   */
  public void commitTrans(TransID xid) 
    throws IOException, IllegalArgumentException
  {
	try{
	  Ptree_lock.lock();
	  d.commitTransaction(xid);
	  noActiveTrans.signalAll();
	} 
	finally {
        Ptree_lock.unlock();
    }	
  }

  /* 
   * This function aborts the specified transaction.  
   */
  public void abortTrans(TransID xid) 
    throws IOException, IllegalArgumentException
  {
	try{
		Ptree_lock.lock();
		d.abortTransaction(xid);
		noActiveTrans.signalAll();
	} 
	finally {
	      Ptree_lock.unlock();
    }
  }

  /* This function creates a new tree and returns the 
   * TNum number (a unique identifier for the tree). 
   */
  public int createTree(TransID xid) 				//needs to be tested
    throws IOException, IllegalArgumentException, ResourceException
  {
	  int TNum = -3290;
	  try{
		  Ptree_lock.lock();
		  Transaction newP_trans = d.atranslist.get(xid);
		  //Acquire TNum then create tree and return TNum
		  for (int i = 0; i < treeIDs.length; i++) {
              if (!treeIDs[i]) {
                  treeIDs[i] = true;
                  TNum = i;
                  break;
              }
          }
		  //If no more free ID's, then it has surpassed MAX_TREES
		  if (TNum == -3290)
              throw new ResourceException();
		  
		  //write that Tree is in use on disk (1st 4 bytes of OUR metadata --> +64 into TNode).
          //(used for recovery)
          int treeInUse = 1; //IN USE
          byte[] inUse = intToByteArray(treeInUse);

          //create temp buffer to load the TNode into
          byte[] tnodeBuffer = new byte[SIZE_OF_TNODE];
          
          //create temp buffer to hold the sector the TNode is in
          byte[] tnodeSector = new byte[Disk.SECTOR_SIZE];
          
          //Now, read the TNode from disk into temp buffer
          //sector number = 1025(log+CP) + 2 (freeList) puts
          //you at the beginning of the TNode array. Then to get to specific TNode,
          //do... + (TNum/4). /4 because there are 4 TNums in a sector.
          //This gives us the sector that the TNum is in. 

          //Check if specific sector has been written
          d.readSector(xid, SIZE_OF_FREELIST + TNum / 4, tnodeSector);
          System.arraycopy(tnodeSector, SIZE_OF_TNODE * (TNum % 4), tnodeBuffer, 0, SIZE_OF_TNODE);

          System.arraycopy(inUse, 0, tnodeBuffer, 64, 4);	//start at 64 bytes to compensate for other Metadata
          System.arraycopy(tnodeBuffer, 0, tnodeSector, SIZE_OF_TNODE * (TNum % 4), SIZE_OF_TNODE);

          //Then copy buffer back into sector and write sector to disk d, the return TNum
          newP_trans.addWrite(SIZE_OF_FREELIST + TNum / 4, tnodeSector);
          return TNum;
	  }
	  finally{
		  Ptree_lock.unlock();
	  }
  }

  /* This function removes the tree specified by the tree  
   * number tnum. The tree is deleted and the corresponding 
   * resources are reclaimed.
   */
  public void deleteTree(TransID xid, int tnum) 	//TODO
    throws IOException, IllegalArgumentException
  {
	  try{
		  Ptree_lock.lock();
		  
	  }
	  finally{
		  Ptree_lock.unlock();
	  }
  }

  /* This function returns the maximum ID of any data block  
   * stored in the specified tree. Note that blocks in a 
   * tree are numbered starting from 0.
   */
  public void getMaxDataBlockId(TransID xid, int tnum)//TODO
    throws IOException, IllegalArgumentException
  {
  }

  /* This function reads PTree.BLOCK_SIZE_BYTES bytes from the blockId'th 
   * block of data in the tree specified by tnum into the buffer specified 
   * by buffer.  If the specified block does not exist in the tree, the 
   * function should fill *buffer with '\0' values.
   */
  public void readData(TransID xid, int tnum, int blockId, byte buffer[])
    throws IOException, IllegalArgumentException	//TODO
  {
	  try{
		  Ptree_lock.lock();
		  
	  }
	  finally{
		  Ptree_lock.unlock();
	  }
  }

  /* This function writes PTREE.BLOCK_SIZE_BYTES bytes from the buffer specified 
   * by buffer into the blockId'th block of data in the tree specified by tnum. 
   * If the specified block does not exist in the tree, the function should grow 
   * the tree to include the new block. Notice that this growth may require 
   * updating multiple data structures -- the free list, the pointer to the tree 
   * root, internal tree nodes, and the data block itself -- and all of these 
   * updates must be done atomically within the transaction. 
   */
  public void writeData(TransID xid, int tnum, int blockId, byte buffer[])
    throws IOException, IllegalArgumentException	//TODO
  {
	  try{
		  Ptree_lock.lock();
		  
	  }
	  finally{
		  Ptree_lock.unlock();
	  }
  }

  /* This function reads PTree.METADATA_SIZE bytes of per-tree metadata for tree tnum 
   * and stores this data in the buffer beginning at buffer. This per-tree metadata 
   * is an uninterpreted array of bytes that higher-level code may use to store state 
   * associated with a given tree. 
   */
  public void readTreeMetadata(TransID xid, int tnum, byte buffer[])
    throws IOException, IllegalArgumentException	//TODO
  {
	  try{
		  Ptree_lock.lock();
		  
	  }
	  finally{
		  Ptree_lock.unlock();
	  }
  }


  /* This function writes PTree.METADATA_SIZE bytes of per-tree metadata for 
   * tree tnum from the buffer beginning at buffer.
   */
  public void writeTreeMetadata(TransID xid, int tnum, byte buffer[])
    throws IOException, IllegalArgumentException	//TODO
  {
	  try{
		  Ptree_lock.lock();
		  
	  }
	  finally{
		  Ptree_lock.unlock();
	  }
  }
  
  /* This function allows applications to get parameters of the persistent tree 
   * system. The parameter is one of PTree.ASK_FREE_SPACE (to ask how much free 
   * space the system currently has), PTree.ASK_MAX_TREES (to ask what is the 
   * maximum number of trees the system can support), and PTree.ASK_FREE_TREES 
   * (to ask how many free tree IDs the system currently has). It returns an 
   * integer answer to the question or throws IllegalArgumentException if param 
   * does not correspond to one of these value.. 
   */
  public int getParam(int param)					
    throws IOException, IllegalArgumentException
  {
	  try {
		  Ptree_lock.lock();
          if (param == ASK_FREE_SPACE) {
              //free blocks * bytes per block
              return numFreeBlocks() * BLOCK_SIZE_BYTES;
          } else if (param == ASK_FREE_TREES) {
              //check the TNum list
              int count = 0;
              for (int i = 0; i < treeIDs.length; i++) {
                  if (!treeIDs[i]) {
                      count++;
                  }
              }
              return count;

          } else if (param == ASK_MAX_TREES) {
              return MAX_TREES;
          } else {
              throw new IllegalArgumentException("Illegal Argument Exception.");
          }
      } 
	  finally {
          Ptree_lock.unlock();
      }
  }
  
  ///////////////////////////////HELPER FUNCTIONS//////////////////////////////////////
  public int numFreeBlocks() throws IllegalArgumentException, IOException {
      try {
          Ptree_lock.lock();
          int count = 0;
          //divided by 8, because that's how many bytes need to express them
          for (int i = 1; i <= TOT_AVAIL_BLKS; i++) {
              if (isBlockFree(i)) {
                  count++;
              }
          }
          return count;
      } finally {
          Ptree_lock.unlock();
      }
  }
  ///////////
  public boolean isBlockFree(int blockNum) throws IllegalArgumentException, IOException {
      try {
          Ptree_lock.lock();
          if (!freeLSTInMEM) {
              loadFreeListFromDisk();
          }
          byte FLblock = freeList[blockNum / 8];
          int shiftVal = blockNum % 8;
          FLblock = (byte) ((byte) (FLblock << shiftVal) & 0x80);
          // -128 == 0x80
          if (FLblock == -128) {
              return false;
          }
          return true;
      } 
      finally {
          Ptree_lock.unlock();
      }
  }
  ///////////
  public static byte[] intToByteArray(int value)
  {
        return new byte[] {(byte)(value >>> 24),(byte)(value >>> 16),(byte)(value >>> 8),(byte)value};
  }
  ///////////
  public void loadFreeListFromDisk() throws IllegalArgumentException, IOException {
      try {
          //load freeList from disk:
          Ptree_lock.lock();
          byte[] freeList1 = new byte[Disk.SECTOR_SIZE];
          byte[] freeList2 = new byte[Disk.SECTOR_SIZE];
          d.d.startRequest(Disk.READ, d.getTag(), ADisk.REDO_LOG_SECTORS + 1, freeList1);
          d.callback.waitForTag(d.setTag());
          d.d.startRequest(Disk.READ, d.getTag(), ADisk.REDO_LOG_SECTORS + 2, freeList2);
          d.callback.waitForTag(d.setTag());
          System.arraycopy(freeList1, 0, freeList, 0, Disk.SECTOR_SIZE);
          System.arraycopy(freeList2, 0, freeList, Disk.SECTOR_SIZE, Disk.SECTOR_SIZE);
          freeLSTInMEM = true;
          return;
      } finally {
          Ptree_lock.unlock();
      }
  }
  ////////////////////////////////////////////////////////////////////////////////////////
}
