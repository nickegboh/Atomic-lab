import java.io.IOException;
import java.util.Arrays;
//import java.util.Arrays;

public class PTreeUnit {
	//public TransID xid;
	private static PTree ptree;
	  	
	  public static void main(String args[]) throws IllegalArgumentException, IOException
	  {
		  
		  //disk = new ADisk(true);
		  ptree = new PTree(true);
		  int passcount = 0; 
		  int failcount = 0; 
		
		  //Test Commit Transaction
		  if(testCommit())
			  passcount++;
		  else 
			  failcount++;
		  
		  //Testing get param method in Ptree
		  if(testGetParam())
			  passcount++;
		  else
			  failcount++;
		  
		  //Testing tree creation, write & read data in Ptree
		  if(testTree())
			  passcount++;
		  else
			  failcount++;
		  
		  //Testing write Data more intensely 
		  if(testWriteDataHard())
			  passcount++;
		  else
			  failcount++;
		  
		  if(testWriteandReadTreeMetadata())
			  passcount++;
		  else
			  failcount++;
		  
		  if(testGetMaxBlockId())
			  passcount++;
		  else
			  failcount++;
		
		System.out.println("\nPassed: " + passcount + " Failed: " + failcount);		
		System.exit(0);
	  }
	  
	  ////////////////////////Start tests////////////////////////////////////
	  public static boolean testCommit() throws IllegalArgumentException, IOException {
		  boolean pass = false; 
		  //Transaction temp = new Transaction(disk);
		  TransID tid = ptree.beginTrans();
		  try {
			  ptree.commitTrans(tid);
			  pass = true;
		  } 
		  catch (Exception e) {
			  e.printStackTrace();
			  pass = false;
		  }
		  if(pass)
			  System.out.println("Test Commit: Passed!");
		  else
			  System.out.println("Test Commit: Failed!");
		  //ptree.abortTrans(tid);
		  return pass; 

	  }
	  
	  /////////////
	  public static boolean testGetParam() {
		  boolean pass = false;
		  int temp1 = 0;
		  int temp2 = 0;
		  int temp3 = 0;
		  try{
			  temp1 = ptree.getParam(PTree.ASK_MAX_TREES);
			  temp2 = ptree.getParam(PTree.ASK_FREE_SPACE);
			  temp3 = ptree.getParam(PTree.ASK_FREE_TREES);
			  pass = true;
		  }
		  catch(IOException e){
			  e.printStackTrace();
			  pass = false;
		  }
		  if(temp1 != 512 || temp2 != 15359 || temp3 != 512)
			  pass = false; 
		  if(pass)
			  System.out.println("testGetParam: Passed!");
		  else
			  System.out.println("testGetParam: Failed!");
		  return pass; 
	  }
	  
	  /////////////
	  public static boolean testTree() throws IllegalArgumentException, IOException{
		  boolean pass = false;
		  TransID tid = ptree.beginTrans();
		  int tnum = -1;
		  try{
			  tnum = ptree.createTree(tid);
			  //pass = true;
		  }
		  catch(IOException e){
			  e.printStackTrace();
			  pass = false;
		  }
		  int blockId = 573;
		  byte[] buffer = new byte[PTree.BLOCK_SIZE_BYTES];
		  for(int i=0; i<buffer.length; i++){
			  buffer[i] = (byte)i;
		  }
		  byte[] buffer2 = new byte[PTree.BLOCK_SIZE_BYTES];
		  
		  ptree.writeData(tid, tnum, blockId, buffer);
		  ptree.readData(tid, tnum, blockId, buffer2);
		  assert(Arrays.equals(buffer, buffer2));
		  if(Arrays.equals(buffer, buffer2)){
			  System.out.println("Test Tree Creation write, read & compare: Passed!");
			  pass = true;
		  }
		  else{
			  System.out.println("Test Tree Creation write, read & compare: Failed!");
			  pass = false;
		  }
		  ptree.abortTrans(tid);
		  return pass;
	  }
	  ///////////////
	  public static boolean testWriteDataHard() throws IllegalArgumentException, IOException{
		  boolean pass = false;
		  TransID tid = ptree.beginTrans();
		  int tnum = -1;
		  try{
			  tnum = ptree.createTree(tid);
		  }
		  catch(IOException e){
			  e.printStackTrace();
			  pass = false;
		  }


		  byte[] buffer = new byte[PTree.BLOCK_SIZE_BYTES];
		  for(int i=0;i<buffer.length;i++){
			  buffer[i]=(byte)i;
		  }
		  int blockId = 0;

		  try{
			  blockId = 0;
			  ptree.writeData(tid, tnum, blockId, buffer);
			  blockId = 1000;
			  ptree.writeData(tid,tnum,blockId,buffer);
			  ptree.commitTrans(tid);
			  tid = ptree.beginTrans();
			  blockId=500;
			  ptree.writeData(tid,tnum,blockId,buffer);
			  pass = true;
		  }
		  catch(IOException e){
			  e.printStackTrace();
			  pass = false;
		  }
		  byte[] temp1 = new byte[PTree.BLOCK_SIZE_BYTES];
		  byte[] temp2 = new byte[PTree.BLOCK_SIZE_BYTES];
		  byte[] temp3 = new byte[PTree.BLOCK_SIZE_BYTES];
		  ptree.readData(tid, tnum, blockId, temp1);
		  ptree.readData(tid, tnum, blockId, temp2);
		  ptree.readData(tid, tnum, blockId, temp3);
		  if(!Arrays.equals(buffer, temp1) || !Arrays.equals(buffer, temp2) || !Arrays.equals(buffer, temp3) )
			  pass = false; 
		  ptree.commitTrans(tid);
		  if(pass)
			  System.out.println("testWriteDataHard: Passed!");
		  else
			  System.out.println("testWriteDataHard: Failed!");
		  return pass;
	  }
	  ////////////////
	  public static boolean testWriteandReadTreeMetadata() throws IllegalArgumentException, IOException{
		  boolean pass = false;
		// Make some byte[]s
          byte [] r1 = new byte[PTree.METADATA_SIZE];
          byte [] r2 = new byte[PTree.METADATA_SIZE];
          byte [] r3 = new byte[PTree.METADATA_SIZE];
          r1[0] = 10;
          r2[0] = 20;
          r3[0] = 30;
          
          // Stuff them in three trees
          
          TransID xid = ptree.beginTrans();
          int tnum1 = ptree.createTree(xid);
          ptree.writeTreeMetadata(xid, tnum1, r1);
          ptree.commitTrans(xid);
          
          xid = ptree.beginTrans();
          int tnum2 = ptree.createTree(xid);
          ptree.writeTreeMetadata(xid, tnum2, r2);
          int tnum3 = ptree.createTree(xid);
          ptree.writeTreeMetadata(xid, tnum3, r3);
          ptree.commitTrans(xid);

          // Now read back
          byte [] a1 = new byte[PTree.METADATA_SIZE];
          byte [] a2 = new byte[PTree.METADATA_SIZE];
          byte [] a3 = new byte[PTree.METADATA_SIZE];
          xid = ptree.beginTrans();
          
          ptree.readTreeMetadata(xid, tnum1, a1);
          ptree.readTreeMetadata(xid, tnum2, a2);
          ptree.readTreeMetadata(xid, tnum3, a3);
          
          if(Arrays.equals(a1, r1) && Arrays.equals(a2, r2) && Arrays.equals(a3, r3)){
        	  ptree.commitTrans(xid);
			  System.out.println("Test Read/Write metadata works: Passed!");
			  pass = true;
          }
		  else
			  System.out.println("Test Read/Write metadata: Failed!");
		  return pass;
	  }
	  ///////////////
	  	
	  public static boolean testGetMaxBlockId() throws IllegalArgumentException, ResourceException, IOException {
		  boolean pass = false;
		  TransID tid = ptree.beginTrans();
		  int tnum = ptree.createTree(tid);
		  ptree.writeData(tid, tnum, 5, new byte[PTree.BLOCK_SIZE_BYTES]);
		  ptree.commitTrans(tid);
		  tid = ptree.beginTrans();
		  ptree.writeData(tid, tnum, 417, new byte[PTree.BLOCK_SIZE_BYTES]);
		  assert(ptree.getMaxDataBlockId(tid, tnum) == 417);
		  if(ptree.getMaxDataBlockId(tid, tnum) == 417){
			  System.out.println("Test Tree Creation: Passed!");
			  pass = true;
		  }
		  else{
			  System.out.println("Test Tree Creation: Failed!");
			  pass = false;
		  }
		  return pass;
	  }
	  
	  /////////////////////////END TESTS/////////////////////////////////
}
