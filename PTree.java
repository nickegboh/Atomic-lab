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
import java.nio.ByteBuffer;
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
  public freeBitMap freeSectors;
  public int treeCount;
  public int currentTNodeListI;
  public byte[] currentTNodeList;
  int TnodeListSectors;
  SimpleLock Ptree_lock;
  Condition noActiveTrans;
  ///////////////////////////////////
  public static final byte[] zeroBuffer = new byte[BLOCK_SIZE_BYTES];
  public static final short NULL_PTR = (short) Short.MIN_VALUE;
  //////////////////////////////////


  /* This function is the constructor. If doFormat == false, data stored 
   * in previous sessions must remain stored. If doFormat == true, the 
   * system should initialize the underlying disk to empty. 
   */
  public PTree(boolean doFormat) throws IllegalArgumentException, IOException						
  {
	  Ptree_lock = new SimpleLock();	//mutex lock
	  noActiveTrans = Ptree_lock.newCondition();
	  d = new ADisk(doFormat);
	  treeCount = 0; 
	  TnodeListSectors = 0;

	  if(doFormat == true){
		  //initialize empty free map
		  freeSectors = new freeBitMap(d.getNSectors());
		  treeCount = 0;
		  //calculate how many sectors needed to save list of tnodes
		  TnodeListSectors = (MAX_TREES * 4) / Disk.SECTOR_SIZE;
		  if(((MAX_TREES * 4) % Disk.SECTOR_SIZE) != 0)
			  TnodeListSectors++;
		  //create sector of null pointers
		  byte[] emptySector = new byte[Disk.SECTOR_SIZE];
		  for(int i = 0; i < emptySector.length; i++)
			  emptySector[i] = 0x00;
		  //write emtpy sectors to disk in first TnodeListSectors of disk.  Mark sectors as not free.
		  TransID initializeTID = d.beginTransaction();
		  for(int i = 0; i < TnodeListSectors; i++){
			d.writeSector(initializeTID, i, emptySector);
			freeSectors.markFull(i);
		  }
		  //set first sector of tnode list available from memory.
		  currentTNodeListI = 0; 
		  currentTNodeList = emptySector;
		  //reserve free map on disk after TNodeListSectors on disk.
		  for(int i = 0; i < freeSectors.reserveSectors; i++){
			  freeSectors.markFull(i+TnodeListSectors);
		  }
		  //write free map on disk
		  writeFreeMap(initializeTID);
		  
		  //commit initial tid
		  d.commitTransaction(initializeTID);
	  }
	  else{
		  treeCount = 0;
		  //calculate how many sectors needed to save list of tnodes
		  TnodeListSectors = (MAX_TREES * 4) / Disk.SECTOR_SIZE;
		  if(((MAX_TREES * 4) % Disk.SECTOR_SIZE) != 0)
			  TnodeListSectors++;
		  //create sector temp
		  byte[] temp = new byte[Disk.SECTOR_SIZE];
		  //read TnodeListSectors of disk.  get tree count
		  TransID initializeTID = d.beginTransaction();
		  int i;
		  for(i = 0; i < TnodeListSectors; i++){
			//read sector of list
			d.readSector(initializeTID, i, temp);
			//count set trees in sector
			ByteBuffer b = ByteBuffer.wrap(temp);
	    	for(int j = 0; j < (Disk.SECTOR_SIZE / 4); j++){
	    	int treeNum = b.getInt();
	    	if (treeNum != 0)
	    		treeCount++;
	    	}
		  }
		  //set last read sector available
		  currentTNodeListI = i; 
		  currentTNodeList = temp;
		  //read free map from disk and create free map
		  int reserveSectors = d.getNSectors() / (8 * Disk.SECTOR_SIZE); 
		  if(d.getNSectors() % (8 * Disk.SECTOR_SIZE) != 0)
			reserveSectors++;
		  byte[] freeMap = new byte[reserveSectors * Disk.SECTOR_SIZE];
		  temp = new byte[Disk.SECTOR_SIZE];
		  for(i = 0; i < reserveSectors; i++){
			  d.readSector(initializeTID, i+TnodeListSectors, temp);
			  System.arraycopy(temp, 0, freeMap, i*Disk.SECTOR_SIZE, Disk.SECTOR_SIZE);
		  }
		  freeSectors = new freeBitMap(d.getNSectors(), freeMap);
		  //commit initial tid
		  d.commitTransaction(initializeTID);
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
	} finally {
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
	} finally {
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
	} finally {
	      Ptree_lock.unlock();
    }
  }

  /* This function creates a new tree and returns the 
   * TNum number (a unique identifier for the tree). 
   */
  public int createTree(TransID xid) 				
    throws IOException, IllegalArgumentException, ResourceException
  {
	  int TNum = -1;
	  try{
		  Ptree_lock.lock();
		  //get transaction
		  Transaction newP_trans = d.atranslist.get(xid);
		  //get new tnode
		  TNum = getFreeTnode(xid);
		  //if no tnode availble throw resource exception
		  if(TNum == -1)
			  throw new ResourceException();
		  //increment tree counter
		  treeCount++;
		  //get sector number to write tnode
		  int secNum = freeSectors.getNextFree();
		  freeSectors.markFull(secNum);
		  //set tnode pointer on disk
		  this.setTnodePointer(xid, TNum, secNum);
		  
		  //generate and fill TNode byte
		  ByteBuffer b = ByteBuffer.allocate(Disk.SECTOR_SIZE);
		  b.putInt(TNum); // put tnode number at top of tnode sector
		  b.putInt(0); //put tree max seen id
		  //reserve space for meta data 
		  for(int i = 0; i < (METADATA_SIZE / 8 / 2); i++)
			  b.putChar((char)0x00);
		  //reserve space for block pointers.  ie one block for every tnode pointer one pointer for each sector in block. 
		  for(int i = 0; i < (TNODE_POINTERS * (BLOCK_SIZE_BYTES / Disk.SECTOR_SIZE)); i++)
			  b.putShort(this.NULL_PTR);
		  //put footer tag
		  b.putChar((char)0xFF);
		  b.putChar((char)0x00);
		  //generate byte array
		  byte[] tnodeByte = b.array();
		  //write tnode byte array to disk
		  d.writeSector(xid, secNum, tnodeByte);		  
	  }
	  finally{
		  Ptree_lock.unlock();
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
		  Ptree_lock.lock();
		  short tnodePointer = (short) this.getTnodePointer(xid, tnum);
		  this.destroyTNode(xid, tnodePointer);
		  this.setTnodePointer(xid, tnum, NULL_PTR);
		  this.treeCount--;
	  }
	  finally{
		  Ptree_lock.unlock();
	  }
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
		  Ptree_lock.lock();
		  int just_gotMaxID = getMaxDataBlockId(xid, tnum);
		  if(blockId > just_gotMaxID){
			  for (int i = 0; i < BLOCK_SIZE_BYTES; i++) {//If block does not exist fill *buffer with '\0' values
                  buffer[i] = zeroBuffer[i];
              }
              return;
		  }
		  short TnodeSector = (short)getTnodePointer(xid, tnum);
		  if(getHeight(just_gotMaxID) == 1){//If we just the root node then read and return
			  short sectorPTR_1 = getPointerTnode(xid, TnodeSector, true, blockId);
			  short sectorPTR_2 = getPointerTnode(xid, TnodeSector, false, blockId);
			  if(sectorPTR_1 == NULL_PTR || sectorPTR_2 == NULL_PTR){
				  for (int i = 0; i < BLOCK_SIZE_BYTES; i++) {//If block does not exist fill *buffer with '\0' values
	                  buffer[i] = zeroBuffer[i];
	              }
	              return;
			  }
			  getDataBlock(xid, sectorPTR_1, sectorPTR_2, buffer);
			  return;
		  }
		  //From this point on height is more than 1
		  int leavesBelow = (int) Math.pow(POINTERS_PER_INTERNAL_NODE, getHeight(just_gotMaxID)-1);
		  int pointerNum = blockId / leavesBelow;
		  short nextSEC_NUM1 = this.getPointerTnode(xid, TnodeSector, true, pointerNum);
		  short nextSEC_NUM2 = this.getPointerTnode(xid, TnodeSector, false, pointerNum);
		  
		  if(nextSEC_NUM1 == NULL_PTR || nextSEC_NUM2 == NULL_PTR){
			    for (int i = 0; i < BLOCK_SIZE_BYTES; i++) {//If block does not exist fill *buffer with '\0' values
	               buffer[i] = zeroBuffer[i];
	            }
	            return;
		  }

		  //Now recurse
		  readDataREC(xid, getHeight(just_gotMaxID)-1, blockId%leavesBelow, nextSEC_NUM1, nextSEC_NUM2, buffer);
	  }
	  finally{
		  Ptree_lock.unlock();
	  }
  }
  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  //recursive function for readData
	public void readDataREC(TransID tid, int height, int blockID, short thisSEC_NUM1, short thisSEC_NUM2, byte[] buffer) 
	  throws IllegalArgumentException, IndexOutOfBoundsException, IOException {
		assert (height >= 1);

		if (height == 1) {
			  short sectorPTR_1 = this.getPointerInode(tid, thisSEC_NUM1, thisSEC_NUM2, true, blockID);
			  short sectorPTR_2 = this.getPointerInode(tid, thisSEC_NUM1, thisSEC_NUM2, false, blockID);
			  if(sectorPTR_1 == NULL_PTR || sectorPTR_2 == NULL_PTR){
				  for (int i = 0; i < BLOCK_SIZE_BYTES; i++) {//If block does not exist fill *buffer with '\0' values
	                  buffer[i] = zeroBuffer[i];
	              }
	              return;
			  }
			  getDataBlock(tid, sectorPTR_1, sectorPTR_2, buffer);
			  return;
		}

		int leavesBelow = (int) Math.pow(POINTERS_PER_INTERNAL_NODE, height-1);
		int pointerNumI = blockID / leavesBelow;
		short nextSEC_NUM1 = this.getPointerInode(tid, thisSEC_NUM1, thisSEC_NUM2, true, pointerNumI);
		short nextSEC_NUM2 = this.getPointerInode(tid, thisSEC_NUM1, thisSEC_NUM2, false, pointerNumI);
		
		if(nextSEC_NUM1 == NULL_PTR || nextSEC_NUM2 == NULL_PTR){
		    for (int i = 0; i < BLOCK_SIZE_BYTES; i++) {//If block does not exist fill *buffer with '\0' values
               buffer[i] = zeroBuffer[i];
            }
            return;
		}
		
		readDataREC(tid, height-1, blockID%leavesBelow, nextSEC_NUM1, nextSEC_NUM2, buffer);
	}
  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
		  Ptree_lock.lock();
		  // first if there are no written blocks, expand height & continue
		  int just_gotMaxID = getMaxDataBlockId(xid, tnum);
		  int current_Height = getHeight(just_gotMaxID);
		  short TnodeSector = (short)getTnodePointer(xid, tnum);
			  //shift tree
			  //update current height
		  if(just_gotMaxID < blockId && current_Height < getHeight(blockId)){
			  int old_height = current_Height;
			  current_Height = getHeight(blockId);
			  //backup old pointers
			  short[] backupPointers= new short[TNODE_POINTERS * 2 * 2];
			  int pointerNum = 0; 
			  for(int i = 0; i < (TNODE_POINTERS * 2 * 2); i++){
				  backupPointers[i] = this.getPointerTnode(xid, TnodeSector, true, pointerNum);
				  i++;
				  backupPointers[i] = this.getPointerTnode(xid, TnodeSector, false, pointerNum);
				  pointerNum++;
			  }
			  //null old first level pointers
			  for(int i = 0; i < this.TNODE_POINTERS; i++){
				  this.setPointerTnode(xid, TnodeSector, true, i, NULL_PTR);
				  this.setPointerTnode(xid, TnodeSector, false, i, NULL_PTR);
			  }  
			  int difference = current_Height - old_height; 
			  short tempInode1 = (short)this.freeSectors.getNextFree();
			  this.freeSectors.markFull((int)tempInode1);
			  short tempInode2 = (short)this.freeSectors.getNextFree();
			  this.freeSectors.markFull((int)tempInode2);
			  this.setPointerTnode(xid, TnodeSector, true, 0, tempInode1);
			  this.setPointerTnode(xid, TnodeSector, false, 0, tempInode2);
			  this.writeDataNew(xid, tempInode1, tempInode2);
			  difference--;
			  for(; difference > 0; difference--){
				  //create new inode
				  short tempInode1_new = (short)this.freeSectors.getNextFree();
				  this.freeSectors.markFull((int)tempInode1_new);
				  short tempInode2_new = (short)this.freeSectors.getNextFree();
				  this.freeSectors.markFull((int)tempInode2_new);
				  this.setPointerInode(xid, tempInode1, tempInode2, true, 0, tempInode1_new);
				  this.setPointerInode(xid, tempInode1, tempInode2, false, 0, tempInode2_new);
				  tempInode1 = tempInode1_new;
				  tempInode2 = tempInode2_new;
				  this.writeDataNew(xid, tempInode1, tempInode2);				  
			  }
			  //restore pointers to bottom of tree
			  pointerNum = 0;
			  for(int i = 0; i < (TNODE_POINTERS * 2 * 2); i++){
				  this.setPointerInode(xid, tempInode1, tempInode2, true, pointerNum, backupPointers[i]);
				  i++;
				  this.setPointerInode(xid, tempInode1, tempInode2, false, pointerNum, backupPointers[i]);
				  pointerNum++;
			  }
		  }
		  
		  if(just_gotMaxID < blockId)
			  this.updateMaxDataBlockId(xid, tnum, blockId);
			  
		  
		  //short TnodeSector = (short)getTnodePointer(xid, tnum);
		  if(current_Height == 1){
			  int newSec1 = this.freeSectors.getNextFree();
			  this.freeSectors.markFull(newSec1);
			  int newSec2 = this.freeSectors.getNextFree();
			  this.freeSectors.markFull(newSec2);
			  setPointerTnode(xid, TnodeSector, true, blockId, (short)newSec1);
			  setPointerTnode(xid, TnodeSector, false, blockId, (short)newSec2);
			  writeDataBlock(xid, (short)newSec1, (short)newSec2, buffer);
			  return;
		  }
		  //From this point on height is more than 1
		  int leavesBelow = (int) Math.pow(POINTERS_PER_INTERNAL_NODE, current_Height-1);
		  int pointerNum = blockId / leavesBelow;
		  short nextSEC_NUM1 = this.getPointerTnode(xid, TnodeSector, true, pointerNum);
		  short nextSEC_NUM2 = this.getPointerTnode(xid, TnodeSector, false, pointerNum);
		  
		  if(nextSEC_NUM1 == this.NULL_PTR || nextSEC_NUM2 == this.NULL_PTR){
			  //create new block and adjust pointers
			  short nextSEC_NUM1_new = (short)this.freeSectors.getNextFree();
			  this.freeSectors.markFull((int)nextSEC_NUM1_new);
			  short nextSEC_NUM2_new = (short)this.freeSectors.getNextFree();
			  this.freeSectors.markFull((int)nextSEC_NUM2_new);
			  this.setPointerTnode(xid, TnodeSector, true, pointerNum, nextSEC_NUM1_new);
			  this.setPointerTnode(xid, TnodeSector, false, pointerNum, nextSEC_NUM2_new);
			  nextSEC_NUM1 = nextSEC_NUM1_new;
			  nextSEC_NUM2 = nextSEC_NUM2_new;
			  this.writeDataNew(xid, nextSEC_NUM1, nextSEC_NUM2);
		  }

		  //Now recurse
		  writeDataREC(xid, current_Height-1, blockId%leavesBelow, nextSEC_NUM1, nextSEC_NUM2, buffer);
		  
	  }
	  finally{
		  Ptree_lock.unlock();
	  }
  }
  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  //recursive function for writeData
	public void writeDataREC(TransID tid, int height,int blockID, short thisSEC_NUM1, short thisSEC_NUM2, byte[] buffer) 
		throws IllegalArgumentException, IndexOutOfBoundsException, ResourceException, IOException {

		assert (height >= 1);
		if(height == 1) {
			
			assert (blockID < POINTERS_PER_INTERNAL_NODE);
			 int newSec1 = this.freeSectors.getNextFree();
			  this.freeSectors.markFull(newSec1);
			  int newSec2 = this.freeSectors.getNextFree();
			  this.freeSectors.markFull(newSec2);
			  setPointerInode(tid, thisSEC_NUM1, thisSEC_NUM2, true, blockID, (short)newSec1);
			  setPointerInode(tid, thisSEC_NUM1, thisSEC_NUM2, false, blockID, (short)newSec2);
			  writeDataBlock(tid, (short)newSec1, (short)newSec2, buffer);
			  return;
		}
		
		int leavesBelow = (int) Math.pow(POINTERS_PER_INTERNAL_NODE, height-1);
		int pointerNumI = blockID / leavesBelow;
		short nextSEC_NUM1 = this.getPointerInode(tid, thisSEC_NUM1, thisSEC_NUM2, true, pointerNumI);
		short nextSEC_NUM2 = this.getPointerInode(tid, thisSEC_NUM1, thisSEC_NUM2, false, pointerNumI);
		
	    if(nextSEC_NUM1 == this.NULL_PTR || nextSEC_NUM2 == this.NULL_PTR){
			  //create new block and adjust pointers
			  short nextSEC_NUM1_new = (short)this.freeSectors.getNextFree();
			  this.freeSectors.markFull((int)nextSEC_NUM1_new);
			  short nextSEC_NUM2_new = (short)this.freeSectors.getNextFree();
			  this.freeSectors.markFull((int)nextSEC_NUM2_new);
			  this.setPointerInode(tid, thisSEC_NUM1, thisSEC_NUM2, true, pointerNumI, nextSEC_NUM1_new);
			  this.setPointerInode(tid, thisSEC_NUM1, thisSEC_NUM2, false, pointerNumI, nextSEC_NUM2_new);
			  nextSEC_NUM1 = nextSEC_NUM1_new;
			  nextSEC_NUM2 = nextSEC_NUM2_new;
			  this.writeDataNew(tid, nextSEC_NUM1, nextSEC_NUM2);
		  }
		
		//Now recurse again
		writeDataREC(tid, height-1, blockID%leavesBelow, nextSEC_NUM1, nextSEC_NUM2, buffer);
	}
  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  /* This function reads PTree.METADATA_SIZE bytes of per-tree metadata for tree tnum 
   * and stores this data in the buffer beginning at buffer. This per-tree metadata 
   * is an uninterpreted array of bytes that higher-level code may use to store state 
   * associated with a given tree. 
   */
  public void readTreeMetadata(TransID xid, int tnum, byte buffer[])
    throws IOException, IllegalArgumentException
  {
	  try{
		  Ptree_lock.lock();
		  int TnodeSector = getTnodePointer(xid, tnum);
		  byte[] temp = new byte[Disk.SECTOR_SIZE];
		  d.readSector(xid, TnodeSector, temp);
		  System.arraycopy(temp, 8, buffer, 0, METADATA_SIZE);
	  }
	  finally{
		  Ptree_lock.unlock();
	  }
  }


  /* This function writes PTree.METADATA_SIZE bytes of per-tree metadata for 
   * tree tnum from the buffer beginning at buffer.
   */
  public void writeTreeMetadata(TransID xid, int tnum, byte buffer[])
    throws IOException, IllegalArgumentException	
  {
	  try{
		  Ptree_lock.lock();
		  int TnodeSector = getTnodePointer(xid, tnum);
		  byte[] temp = new byte[Disk.SECTOR_SIZE];
		  d.readSector(xid, TnodeSector, temp);
		  System.arraycopy(buffer, 0, temp, 8, METADATA_SIZE);		  
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
  public int getMaxDataBlockId(TransID xid, int tnum) 
  throws IllegalArgumentException, IndexOutOfBoundsException, IOException  {
	  int MaxDataBlockId = 0;
	  try{
		  Ptree_lock.lock();
		  int TnodeSector = getTnodePointer(xid, tnum);
		  byte[] temp = new byte[Disk.SECTOR_SIZE];
		  byte[] buffer = new byte[4];
		  d.readSector(xid, TnodeSector, temp);
		  System.arraycopy(temp, 4, buffer, 0, 4);
		  MaxDataBlockId = byteArraytoInt(buffer);
	  }
	  finally{
		  Ptree_lock.unlock();
	  }
	  return MaxDataBlockId; 
  }


  /* This function writes PTree.METADATA_SIZE bytes of per-tree metadata for 
   * tree tnum from the buffer beginning at buffer.
   */
  public void updateMaxDataBlockId(TransID xid, int tnum, int newMaxBlockId) 
  throws IllegalArgumentException, IndexOutOfBoundsException, IOException
  {
	  try{
		  Ptree_lock.lock();
		  int TnodeSector = getTnodePointer(xid, tnum);
		  byte[] buffer = intToByteArray(newMaxBlockId);
		  byte[] temp = new byte[Disk.SECTOR_SIZE];
		  d.readSector(xid, TnodeSector, temp);
		  System.arraycopy(buffer, 0, temp, 4, 4);
		  d.writeSector(xid, TnodeSector, temp);
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
          if (param == ASK_FREE_SPACE) {
              //free blocks * bytes per block
              return freeSectors.getFreeSectors() * BLOCK_SIZE_BYTES;
          } 
          else if (param == ASK_FREE_TREES) {
              //check the TNum list
              int count = MAX_TREES - treeCount;
              return count;
          } 
  		  else if (param == ASK_MAX_TREES) {
              return MAX_TREES;
          } 
          else {
              throw new IllegalArgumentException("Illegal Argument Exception.");
          }
  }
/////////////////////////////////////////////////////////HELPERs////////////////////////////////////////////////////////////////////////
  public int getHeight(int maxDataBlockId){
	  int height = 0;
	  if(maxDataBlockId < TNODE_POINTERS)
		  height = 1;
	  else{
	  int i = 1;
	  while(maxDataBlockId >= (TNODE_POINTERS * Math.pow(POINTERS_PER_INTERNAL_NODE, i)))
		  i++;
	  height = i+1;
	  }
	  return height; 		  
  }
  
  private void writeFreeMap(TransID tid){
	  byte[] freeMap = freeSectors.getSectorsforWrite();
	  for(int i = 0; i < freeSectors.reserveSectors; i++){
		  byte[] toWrite = new byte[Disk.SECTOR_SIZE];
		  System.arraycopy(freeMap, i*Disk.SECTOR_SIZE, toWrite, 0, Disk.SECTOR_SIZE);
		  d.writeSector(tid, i+TnodeListSectors, toWrite);
	  }
  }
  
  //given a transaction and a tndoe number set the pointer to the tnode of the given tnode number
  private void setTnodePointer(TransID tid, int tnode, int tnodePointer) 
  	throws IllegalArgumentException, IndexOutOfBoundsException, IOException { 
	  byte[] pointer = intToByteArray(tnodePointer);
	  //calculate sector tnode is in
	  int SectorNum = (tnode * 4) / Disk.SECTOR_SIZE;
	  int SectorOffset = (tnode * 4) / Disk.SECTOR_SIZE;
	  //read appropriate sector
	  if(this.currentTNodeListI != SectorNum){
		d.readSector(tid, SectorNum, currentTNodeList);
		this.currentTNodeListI = SectorNum;
	  }
	  //change in memory for the new pointer
	  System.arraycopy(pointer, 0, currentTNodeList, SectorOffset, 4);
	  //write sector to memory
	  d.writeSector(tid, SectorNum, currentTNodeList);
  }
  
  //given a transaction and a tnode numebr return the pointer to the tnode of the given tnode number
  private int getTnodePointer(TransID tid, int tnode) 
  	throws IllegalArgumentException, IndexOutOfBoundsException, IOException{
	//calculate sector tnode is in
	int SectorNum = (tnode * 4) / Disk.SECTOR_SIZE;
	int SectorOffset = (tnode * 4) / Disk.SECTOR_SIZE;
	//read appropriate sector
    if(this.currentTNodeListI != SectorNum){
			d.readSector(tid, SectorNum, currentTNodeList);
			this.currentTNodeListI = SectorNum;
		  }
	byte[] pointer = new byte[4];
	System.arraycopy(this.currentTNodeList, SectorOffset, pointer, 0, 4);
	return byteArraytoInt(pointer);
  }
  
  //get the tnode number of the next available tnode. return -1 if no available tnodes
  private int getFreeTnode(TransID tid) 
  	throws IllegalArgumentException, IndexOutOfBoundsException, IOException {
	  for(int i = 0; i < this.TnodeListSectors; i++){
		  if(this.currentTNodeListI != i){
				d.readSector(tid, i, currentTNodeList);
				this.currentTNodeListI = i;
		  }
		  ByteBuffer b = ByteBuffer.wrap(currentTNodeList);
		  for(int j = 0; j < (Disk.SECTOR_SIZE / 4); j++){
			  int temp = b.getInt();
			  if(temp == 0)
				  return ((i * (Disk.SECTOR_SIZE / 4)) + j);
		  }
	  }
	  return -1;
  }
  
//set pointer of an interior node.
  private short getPointerInode(TransID tid, short nodePointer1, short nodePointer2, boolean firstPointer, int pointerNum) 
  	throws IllegalArgumentException, IndexOutOfBoundsException, IOException{
          byte[] inode = new byte[Disk.SECTOR_SIZE];
          byte[] result = new byte[2];
          pointerNum = pointerNum * 4;
          if(!firstPointer)
                  pointerNum = pointerNum + 2;
          if(pointerNum < Disk.SECTOR_SIZE)
                  d.readSector(tid, nodePointer1, inode);
          else {
                  d.readSector(tid, nodePointer2, inode);
                  pointerNum = pointerNum - Disk.SECTOR_SIZE;
          }
          System.arraycopy(inode, pointerNum, result, 0, 2);
         
          return byteArraytoShort(result);
  }
 
  //get pointer of an interior node
  private void setPointerInode(TransID tid, short nodePointer1, short nodePointer2, boolean firstPointer, int pointerNum, short newPointer) 
  	throws IllegalArgumentException, IndexOutOfBoundsException, IOException{
          byte[] inode = new byte[Disk.SECTOR_SIZE];
          byte[] toWrite = shortToByteArray(newPointer);
          pointerNum = pointerNum * 4;
          if(!firstPointer)
                  pointerNum = pointerNum + 2;
          if(pointerNum < Disk.SECTOR_SIZE){
                  d.readSector(tid, nodePointer1, inode);
                  System.arraycopy(toWrite, 0, inode, pointerNum, 2);
                  d.writeSector(tid, nodePointer1, inode);
          }
          else {
                  d.readSector(tid, nodePointer2, inode);
                  pointerNum = pointerNum - Disk.SECTOR_SIZE;
                  System.arraycopy(toWrite, 0, inode, pointerNum, 2);
                  d.writeSector(tid, nodePointer2, inode);
          }
          return;
  }
 
  //get pointer of a tnode
  private short getPointerTnode(TransID tid, short nodePointer, boolean firstPointer, int pointerNum) 
  	throws IllegalArgumentException, IndexOutOfBoundsException, IOException{
          byte[] inode = new byte[Disk.SECTOR_SIZE];
          byte[] result = new byte[2];
          pointerNum = pointerNum * 4;
          if(!firstPointer)
                  pointerNum = pointerNum + 2;
          //adjust pointer beyond meta data
          pointerNum = pointerNum + 8 + (PTree.METADATA_SIZE / 8);
          d.readSector(tid, nodePointer, inode);
          System.arraycopy(inode, pointerNum, result, 0, 2);
          return byteArraytoShort(result);
  }
 
  //set pointer of a tnode
  private void setPointerTnode(TransID tid, short nodePointer, boolean firstPointer, int pointerNum, short newPointer) 
  	throws IllegalArgumentException, IndexOutOfBoundsException, IOException{
          byte[] inode = new byte[Disk.SECTOR_SIZE];
          byte[] toWrite = shortToByteArray(newPointer);
          pointerNum = pointerNum * 4;
          if(!firstPointer)
                  pointerNum = pointerNum + 2;
          //adjust pointer beyond meta data
          pointerNum = pointerNum + 8 + (PTree.METADATA_SIZE / 8);
          d.readSector(tid, nodePointer, inode);
          System.arraycopy(toWrite, 0, inode, pointerNum, 2);
          d.writeSector(tid, nodePointer, inode);
          return;
  }
  
  // read a block of data given pointers to two sectors that make up a block
  private void getDataBlock(TransID tid, short sector1, short sector2, byte[] buffer) 
  	throws IllegalArgumentException, IndexOutOfBoundsException, IOException{
	  byte[] temp = new byte[Disk.SECTOR_SIZE];
	  byte[] temp2 = new byte[Disk.SECTOR_SIZE];
	  d.readSector(tid, sector1, temp);
	  d.readSector(tid, sector2, temp2);
	  
	  System.arraycopy(temp,0,buffer,0         ,temp.length);
	  System.arraycopy(temp2,0,buffer,temp.length,temp2.length);
  }
  //write data from buffer into the two sectors 
  private void writeDataBlock(TransID tid, short sector1, short sector2, byte[] buffer) 
  	throws IllegalArgumentException, IndexOutOfBoundsException, IOException{
	  byte[] temp = new byte[Disk.SECTOR_SIZE];
	  byte[] temp2 = new byte[Disk.SECTOR_SIZE];
	  
	  System.arraycopy(buffer,0,temp,0         ,temp.length);
	  System.arraycopy(buffer,temp.length,temp2,0,temp2.length);
	  
	  d.writeSector(tid, sector1, temp);
	  d.writeSector(tid, sector2, temp2);
	  
  }

  private void writeDataNew(TransID tid, short sector1, short sector2) 
	throws IllegalArgumentException, IndexOutOfBoundsException, IOException{
	  ByteBuffer b = ByteBuffer.allocate(Disk.SECTOR_SIZE*2);
	  for(int i = 0; i < (this.POINTERS_PER_INTERNAL_NODE*2); i++)
		  b.putShort(this.NULL_PTR);
	  byte[] temp = b.array();
	  d.writeSector(tid, sector1, temp);
	  d.writeSector(tid, sector2, temp);
}
  
  private void destroyTNode(TransID xid, short tempInode1) throws IllegalArgumentException, IndexOutOfBoundsException, IOException {
	  for(int i = 0; i < TNODE_POINTERS; i++){
		  short temp1 = this.getPointerTnode(xid, tempInode1, true, i);
		  short temp2 = this.getPointerTnode(xid, tempInode1, false, i);
		  if(temp1 != NULL_PTR && temp2 != NULL_PTR)
			  destroyNode(xid, temp1, temp2);
	  }
	  this.freeSectors.markEmpty(tempInode1);
  }
  
  private void destroyNode(TransID xid, short tempInode1, short  tempInode2) throws IllegalArgumentException, IndexOutOfBoundsException, IOException{
	  for(int i = 0; i < POINTERS_PER_INTERNAL_NODE; i++){
		  short temp1 = this.getPointerInode(xid, tempInode1, tempInode2, true, i);
		  short temp2 = this.getPointerInode(xid, tempInode1, tempInode2, false, i);		  
		  if(temp1 != NULL_PTR && temp2 != NULL_PTR)
			  destroyNode(xid, temp1, temp2);
	  }
	  this.freeSectors.markEmpty(tempInode1);
	  this.freeSectors.markEmpty(tempInode2);
  }
  
  ///////////////////////SUB HELPERS
  // given int return byte array
  public static byte[] intToByteArray(int value)
  {
        return new byte[] {(byte)(value >>> 24),(byte)(value >>> 16),(byte)(value >>> 8),(byte)value};
  }
  
  //give byte array return int
  public static int byteArraytoInt( byte[] bytes ) {
	  ByteBuffer b = ByteBuffer.wrap(bytes); 
	  return b.getInt();
  }
  public static byte[] shortToByteArray(short value)
  {
        return new byte[] {(byte)(value >>> 8),(byte)value};
  }
  
  //give byte array return int
  public static short byteArraytoShort( byte[] bytes ) {
	  ByteBuffer b = ByteBuffer.wrap(bytes); 
	  return b.getShort();
  }

  /////////////////////////////////////////END HELPERS//////////////////////////////////////////////////////////////////////////
}
