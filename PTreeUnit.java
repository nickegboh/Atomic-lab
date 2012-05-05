import java.io.IOException;
import java.util.Arrays;
//import java.util.Arrays;

public class PTreeUnit {
//	private static ADisk disk;
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
		  
		
		
		System.out.println("\nPassed: " + passcount + " Failed: " + failcount);		
		System.exit(0);
	  }
	  
	  ////////////////////////Start tests////////////////////////////////////
	  public static boolean testCommit() {
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
		  return pass; 

	  }
	  
	  /////////////
	  public static boolean testGetParam() {
		  boolean pass = false;
		  try{
			  ptree.getParam(PTree.ASK_MAX_TREES);
			  ptree.getParam(PTree.ASK_FREE_SPACE);
			  ptree.getParam(PTree.ASK_FREE_TREES);
			  pass = true;
		  }
		  catch(IOException e){
			  e.printStackTrace();
			  pass = false;
		  }
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
		  return pass;
	  }
	  ///////////////
	  public static boolean testWriteDataHard(){
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

		  try{
			  int blockId = 0;
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
		  if(pass)
			  System.out.println("testGetParam: Passed!");
		  else
			  System.out.println("testGetParam: Failed!");
		  return pass;
	  }
	  ////////////////
	  public static boolean testWriteandReadTreeMetadata(){
		  boolean pass = false;
		  try{
			  TransID tid = ptree.beginTrans();
			  int tnum = ptree.createTree(tid);
			  byte buffer[] = new byte[PTree.METADATA_SIZE];
			  for (int i=0;i<buffer.length;i++){
				  buffer[i]=(byte)i;
			  }
			  ptree.writeTreeMetadata( tid, tnum, buffer);
			  byte buffer2[] =new byte[PTree.METADATA_SIZE];
			  ptree.readTreeMetadata(tid,tnum,buffer2);
			  assert(Arrays.equals(buffer,buffer2));
			  pass = true;
		  }
		  catch(IOException e){
			  e.printStackTrace();
			  pass = false;
		  }
		  if(pass)
			  System.out.println("testGetParam: Passed!");
		  else
			  System.out.println("testGetParam: Failed!");
		  return pass;
	  }
	  ///////////////
	  	
//	  public static boolean testGetMaxBlockId() throws IllegalArgumentException, ResourceException, IOException {
//		  boolean pass = false;
//		  TransID tid = ptree.beginTrans();
//		  int tnum = ptree.createTree(tid);
//		  ptree.writeData(tid, tnum, 5, new byte[PTree.BLOCK_SIZE_BYTES]);
//		  ptree.commitTrans(tid);
//		  tid = ptree.beginTrans();
//		  ptree.writeData(tid, tnum, 417, new byte[PTree.BLOCK_SIZE_BYTES]);
//		  assert(ptree.getMaxDataBlockId(tid, tnum) == 417);
//		  if(ptree.getMaxDataBlockId(tid, tnum) == 417){
//			  System.out.println("Test Tree Creation: Passed!");
//			  pass = true;
//		  }
//		  else{
//			  System.out.println("Test Tree Creation: Failed!");
//			  pass = false;
//		  }
//		  return pass;
//	  }
	  
	  /////////////////////////END TESTS/////////////////////////////////
}
