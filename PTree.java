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
  
  public ADisk d;
  public TransID tid;
  SimpleLock ADisk_lock;


  /* This function is the constructor. If doFormat == false, data stored 
   * in previous sessions must remain stored. If doFormat == true, the 
   * system should initialize the underlying disk to empty. 
   */
  public PTree(boolean doFormat)
  {
	  ADisk_lock = new SimpleLock();	//mutex lock
	  d = new ADisk(doFormat);

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
    return d.beginTransaction();
  }

  /*
   * This function commits the specified transaction.
   */
  public void commitTrans(TransID xid) 
    throws IOException, IllegalArgumentException
  {
	  d.commitTransaction(xid);
  }

  /* 
   * This function aborts the specified transaction.  
   */
  public void abortTrans(TransID xid) 
    throws IOException, IllegalArgumentException
  {
	  d.abortTransaction(xid);
  }

  /* This function creates a new tree and returns the 
   * TNum number (a unique identifier for the tree). 
   */
  public int createTree(TransID xid) 
    throws IOException, IllegalArgumentException, ResourceException
  {
	  int TNum = 0;
	  try{
		  ADisk_lock.lock();
		  
	  }
	  finally{
		  ADisk_lock.unlock();
	  }
    return TNum;
  }

  /* This function removes the tree specified by the tree  
   * number tnum. The tree is deleted and the corresponding 
   * resources are reclaimed.
   */
  public void deleteTree(TransID xid, int tnum) 
    throws IOException, IllegalArgumentException
  {
	  try{
		  ADisk_lock.lock();
		  
	  }
	  finally{
		  ADisk_lock.unlock();
	  }
  }

  /* This function returns the maximum ID of any data block  
   * stored in the specified tree. Note that blocks in a 
   * tree are numbered starting from 0.
   */
  public void getMaxDataBlockId(TransID xid, int tnum)
    throws IOException, IllegalArgumentException
  {
  }

  /* This function reads PTree.BLOCK_SIZE_BYTES bytes from the blockId'th 
   * block of data in the tree specified by tnum into the buffer specified 
   * by buffer.  If the specified block does not exist in the tree, the 
   * function should fill *buffer with '\0' values.
   */
  public void readData(TransID xid, int tnum, int blockId, byte buffer[])
    throws IOException, IllegalArgumentException
  {
	  try{
		  ADisk_lock.lock();
		  
	  }
	  finally{
		  ADisk_lock.unlock();
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
    throws IOException, IllegalArgumentException
  {
	  try{
		  ADisk_lock.lock();
		  
	  }
	  finally{
		  ADisk_lock.unlock();
	  }
  }

  /* This function reads PTree.METADATA_SIZE bytes of per-tree metadata for tree tnum 
   * and stores this data in the buffer beginning at buffer. This per-tree metadata 
   * is an uninterpreted array of bytes that higher-level code may use to store state 
   * associated with a given tree. 
   */
  public void readTreeMetadata(TransID xid, int tnum, byte buffer[])
    throws IOException, IllegalArgumentException
  {
	  try{
		  ADisk_lock.lock();
		  
	  }
	  finally{
		  ADisk_lock.unlock();
	  }
  }


  /* This function writes PTree.METADATA_SIZE bytes of per-tree metadata for 
   * tree tnum from the buffer beginning at buffer.
   */
  public void writeTreeMetadata(TransID xid, int tnum, byte buffer[])
    throws IOException, IllegalArgumentException
  {
	  try{
		  ADisk_lock.lock();
		  
	  }
	  finally{
		  ADisk_lock.unlock();
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
    return -1;
  }

  
}
