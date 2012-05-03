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
			ByteBuffer b = ByteBuffer.allocate(Disk.SECTOR_SIZE);
	    	b.put(temp);
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
		  for(int i = 0; i < (this.METADATA_SIZE / 8); i++)
			  b.putChar((char)0x00);
		  //reserve space for block pointers.  ie one block for every tnode pointer one pointer for each sector in block. 
		  for(int i = 0; i < (this.TNODE_POINTERS * (this.BLOCK_SIZE_BYTES / Disk.SECTOR_SIZE)); i++)
			  b.putInt(0);
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
		  int just_gotMaxID = getMaxDataBlockId(xid, tnum);
		  if(blockId > just_gotMaxID){
			  for (int i = 0; i < BLOCK_SIZE_BYTES; i++) {//If block does not exist fill *buffer with '\0' values
                  buffer[i] = zeroBuffer[i];
              }
              return;
		  }
		  if(getHeight(just_gotMaxID) == 1){//If we just the root node then read and return**
			  //freeSectors.fill(readBlock(tid, root.pointers[blockID]), buffer);
			  int TnodeSector = getTnodePointer(xid, tnum);
			  byte[] temp = new byte[Disk.SECTOR_SIZE];
			  d.readSector(xid, TnodeSector, temp);
			  System.arraycopy(temp, 0, buffer, 0, BLOCK_SIZE_BYTES);
			  return;
		  }
		  int leavesBelow = (int) Math.pow(POINTERS_PER_INTERNAL_NODE, getHeight(just_gotMaxID)-1);
		  int index = blockId / leavesBelow;
		  if(getTnodePointer(xid, index) == NULL_PTR) {//Not sure bout this**
				//freeSectors.fill(readBlock(tid, root.pointers[blockID]), buffer);
			  	int TnodeSector = getTnodePointer(xid, index);
			  	byte[] temp = new byte[Disk.SECTOR_SIZE];
			  	d.readSector(xid, TnodeSector, temp);
			  	System.arraycopy(temp, 0, buffer, 0, BLOCK_SIZE_BYTES);
				return;
		  }
		  //Now recurse
		  //readDataREC(xid, getHeight(just_gotMaxID)-1, getChild(xid, root, index), blockId%leavesBelow, buffer);
		  readDataREC(xid, getHeight(just_gotMaxID)-1, blockId%leavesBelow, buffer);
	  }
	  finally{
		  Ptree_lock.unlock();
	  }
  }
  /////////////////////////////////////////////////////////////////////////////////////////////
//  public void readDataREC(TransID tid, int height, InternalNode node, int blockID, byte[] buffer) //recursive function for readData
//  throws IllegalArgumentException, IndexOutOfBoundsException, IOException {
	public void readDataREC(TransID tid, int height, int blockID, byte[] buffer) //recursive function for readData
	  throws IllegalArgumentException, IndexOutOfBoundsException, IOException {
		assert (height >= 1);

		if (height == 1) {
			if (blockID >= POINTERS_PER_INTERNAL_NODE){
				for (int i = 0; i < BLOCK_SIZE_BYTES; i++) {//If block does not exist fill *buffer with '\0' values
	                  buffer[i] = zeroBuffer[i];
	              }
	              return;
			}
			else if (getTnodePointer(tid, blockID) == NULL_PTR){//Not sure bout this**
				//freeSectors.fill(new byte[BLOCK_SIZE_BYTES], buffer);
				int TnodeSector = getTnodePointer(tid, blockID);
			  	byte[] temp = new byte[Disk.SECTOR_SIZE];
			  	d.readSector(tid, TnodeSector, temp);
			  	System.arraycopy(temp, 0, buffer, 0, BLOCK_SIZE_BYTES);
				return;
			}
			else{
				//freeSectors.fill(readBlock(tid, node.pointers[blockID]), buffer);//Not sure bout this**
				int TnodeSector = getTnodePointer(tid, blockID);
			  	byte[] temp = new byte[Disk.SECTOR_SIZE];
			  	d.readSector(tid, TnodeSector, temp);
			  	System.arraycopy(temp, 0, buffer, 0, BLOCK_SIZE_BYTES);
				return;
			}
			//return;
		}

		int leavesBelow = (int) Math.pow(POINTERS_PER_INTERNAL_NODE, height-1);
		int index = blockID / leavesBelow;

		if(getTnodePointer(tid, index) == NULL_PTR) {
			//freeSectors.fill(new byte[BLOCK_SIZE_BYTES], buffer);//Not sure bout this**
			int TnodeSector = getTnodePointer(tid, index);
		  	byte[] temp = new byte[Disk.SECTOR_SIZE];
		  	d.readSector(tid, TnodeSector, temp);
		  	System.arraycopy(temp, 0, buffer, 0, BLOCK_SIZE_BYTES);
			return;
		}
		//readDataREC(tid, height-1, getChild(tid, node, index), blockID%leavesBelow, buffer);
		readDataREC(tid, height-1, blockID%leavesBelow, buffer);
	}
  ////////////////////////////////////////////////////////////////////////////////////////////

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
		  // first if there are no written blocks, expand height & continue
		  int just_gotMaxID = getMaxDataBlockId(xid, tnum);
		  int tempHeight = getHeight(just_gotMaxID);
		  if(tempHeight == 0){
			  while(maxBlocks(tempHeight)< blockId){
				  tempHeight++;}
		  }
		  else
			  while(maxBlocks(tempHeight) < blockId){
				  tempHeight++;
				  //........
			  }
		  //writeRoot();
		  assert(tempHeight >= 1);
			if (tempHeight == 1) {// if only block is the root
//				short block = getSectors(tid, BLOCK_SIZE_SECTORS);
//				writeBlock(tid, block, buffer);
//				root.pointers[blockID] = block;
//				writeRoot(tid, tnum, root);			
//				return;
				int TnodeSector = getTnodePointer(xid, blockId);
			  	d.writeSector(xid, TnodeSector, buffer);
				return;
			}
			int leavesBelow = (int) Math.pow(POINTERS_PER_INTERNAL_NODE, tempHeight-1);
			int index =  (blockId / leavesBelow);
//			writeVisit(tid, tempHeight-1, getChild(tid, root, index), blockId%leavesBelow, buffer);
			writeVisit(xid, tempHeight-1, blockId%leavesBelow, buffer);
	  }
	  finally{
		  Ptree_lock.unlock();
	  }
  }
  ////////////////////////////////////////////////////////////////////////////////////////////////
  //Find the maximum block ID a tree of a given height supports
	public static int maxBlocks(int height) {
		return TNODE_POINTERS * (int)Math.pow(POINTERS_PER_INTERNAL_NODE, height - 1) - 1;
	}
	/////////
	
//	public void writeVisit(TransID tid, int height, InternalNode node, int blockID, byte[] buffer) 
//		throws IllegalArgumentException, IndexOutOfBoundsException, ResourceException, IOException {
	public void writeVisit(TransID tid, int height,int blockID, byte[] buffer) 
		throws IllegalArgumentException, IndexOutOfBoundsException, ResourceException, IOException {

		assert (height >= 1);
		if(height == 1) {
			assert (blockID < POINTERS_PER_INTERNAL_NODE);
			int block;
			if (getTnodePointer(tid, blockID) != NULL_PTR)
				block = getTnodePointer(tid, blockID);
			else
				block = getSectors(tid, BLOCK_SIZE_SECTORS);//?
			
			int TnodeSector = getTnodePointer(tid, block);
		  	d.writeSector(tid, TnodeSector, buffer);
			return;
		}

		int leavesBelow = (int) Math.pow(POINTERS_PER_INTERNAL_NODE, height-1);
		int index = blockID / leavesBelow;
		writeVisit(tid, height-1, blockID%leavesBelow, buffer);
	}
  ////////////////////////////////////////////////////////////////////////////////////////////////
  
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
		  System.arraycopy(temp, 8, buffer, 0, this.METADATA_SIZE);
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
		  System.arraycopy(buffer, 0, temp, 8, this.METADATA_SIZE);		  
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
          } else if (param == ASK_FREE_TREES) {
              //check the TNum list
              int count = this.MAX_TREES - treeCount;
              return count;
          } 
  		  else if (param == ASK_MAX_TREES) {
              return MAX_TREES;
          } 
          else {
              throw new IllegalArgumentException("Illegal Argument Exception.");
          }
  }
///////////////////////////HELPER////////////////////////////////////////////////////////  
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
  private void setTnodePointer(TransID tid, int tnode, int tnodePointer) throws IllegalArgumentException, IndexOutOfBoundsException, IOException { 
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
  private int getTnodePointer(TransID tid, int tnode) throws IllegalArgumentException, IndexOutOfBoundsException, IOException{
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
  private int getFreeTnode(TransID tid) throws IllegalArgumentException, IndexOutOfBoundsException, IOException {
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
  
  // given int return byte array
  public static byte[] intToByteArray(int value)
  {
        return new byte[] {(byte)(value >>> 24),(byte)(value >>> 16),(byte)(value >>> 8),(byte)value};
  }
  
  //give byte array return int
  public static int byteArraytoInt( byte[] bytes ) {
	    int result = 0;
	    for (int i=0; i<4; i++) {
	      result = ( result << 8 ) - Byte.MIN_VALUE + (int) bytes[i];
	    }
	    return result;
  }

  
}


