import java.io.IOException;
import java.util.Arrays;
//import java.util.Arrays;

public class PTreeUnit {
	private static ADisk disk;
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
		  
		  //Testing tree creation in Ptree
		  if(testTree())
			  passcount++;
		  else
			  failcount++;
		  
//		  if(testcreat_killTree())
//			  passcount++;
//		  else
//			  failcount++;
		  
		
		
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
			  pass = true;
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
		  if(pass)
			  System.out.println("Test Tree Creation: Passed!");
		  else
			  System.out.println("Test Tree Creation: Failed!");
		  return pass;
	  }
	  
	  ///////////////
	  public static boolean testcreat_killTree(){
		  boolean pass = false;
		  TransID tid = ptree.beginTrans();
		  try{
			  int tnum = ptree.createTree(tid);
			  ptree.deleteTree(tid, tnum);
			  pass = true;
		  }
		  catch(IOException e){
			  e.printStackTrace();
			  pass = false;
		  }
		  if(pass)
			  System.out.println("Test Tree Creation: Passed!");
		  else
			  System.out.println("Test Tree Creation: Failed!");
		  return pass;
		  }
	  	
	  
	  /////////////////////////END TESTS/////////////////////////////////
}
