import java.io.IOException;
import java.util.Arrays;

public class FlatFSUnit {
	private static FlatFS flatFS;
  	
	  public static void main(String args[]) throws IllegalArgumentException, IOException
	  {
		  
		  flatFS = new FlatFS(true);
		  int passcount = 0; 
		  int failcount = 0; 
		
		  //Test Commit Transaction
		  if(testCommitTrans())
			  passcount++;
		  else 
			  failcount++;
		  
		  //Testing abort tranaction
		  if(testAbortTrans())
			  passcount++;
		  else
			  failcount++;
		  
		  //Testing file creation and deletion in FlatFS
		  if(testCreateAndDeleteFile())
			  passcount++;
		  else
			  failcount++;
		  
		  //Testing if file metadata can be read
		  if(testReadFileMetadata())
			  passcount++;
		  else
			  failcount++;
		  
		  //Test if params are right
		  if(testGetParam())
			  passcount++;
		  else
			  failcount++;
		  
		
		
		System.out.println("\nPassed: " + passcount + " Failed: " + failcount);		
		System.exit(0);
	  }
	  
	  //////////////////////TESTS////////////////////////////////////////////////
	  
	  public static boolean testCommitTrans() {
		  boolean pass = false;
		  TransID tid = flatFS.beginTrans();
		  try {
			  	flatFS.commitTrans(tid);
			  	pass = true;
		  } 
		  catch (Exception e) {
			  e.printStackTrace();
			  pass = false;
		  }
		  if(pass)
			  System.out.println("testGetParam: Passed!");
		  else
			  System.out.println("testGetParam: Failed!");
		  return pass;
	  }
	  /////////////////////
	  public static boolean testAbortTrans() {
		  boolean pass = false;
		  TransID tid = flatFS.beginTrans();
		  try {
			  	flatFS.abortTrans(tid);
			  	pass = true;
		  } 
		  catch (Exception e) {
			  e.printStackTrace();
			  pass = false;
		  }
		  if(pass)
			  System.out.println("testGetParam: Passed!");
		  else
			  System.out.println("testGetParam: Failed!");
		  return pass;
	  }
	  /////////////////////
	  public static boolean testCreateAndDeleteFile() {
		  boolean pass = false;
		  try{
			  	TransID tid = flatFS.beginTrans();
			  	int inode = flatFS.createFile(tid);
			  	flatFS.deleteFile(tid,inode);
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
	  /////////////////////
	  public static boolean testReadFileMetadata() {
		  boolean pass = false;
		  try{
			  flatFS.getParam(FlatFS.ASK_MAX_FILE);
			  flatFS.getParam(FlatFS.ASK_FREE_FILES);
			  flatFS.getParam(FlatFS.ASK_FREE_SPACE_BLOCKS);
			  flatFS.getParam(FlatFS.ASK_FILE_METADATA_SIZE);
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
	  /////////////////////
	  public static boolean testGetParam() {
		  boolean pass = false;
		  try{
			  TransID tid = flatFS.beginTrans();
			  int tnum = flatFS.createFile(tid);
			  byte buffer[] = new byte[FlatFS.ASK_FILE_METADATA_SIZE];
			  for (int i=0;i<buffer.length;i++){
				  buffer[i]=(byte)i;}
			  flatFS.writeFileMetadata( tid, tnum, buffer);
			  byte buffer2[] =new byte[PTree.METADATA_SIZE];
			  flatFS.readFileMetadata(tid,tnum,buffer2);
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

}
