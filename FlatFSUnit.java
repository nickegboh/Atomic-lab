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
		  
		  //Test if params are right
		  if(testGetParam())
			  passcount++;
		  else
			  failcount++;
		  
		  //Testing if file metadata can be read
		  if(testReadFileMetadata())
			  passcount++;
		  else
			  failcount++;
		  
		  if(testReadANDWriteFile())
			  passcount++;
		  else
			  failcount++;

		
		System.out.println("\nPassed: " + passcount + " Failed: " + failcount);		
		System.exit(0);
	  }
	  
	  //////////////////////TESTS////////////////////////////////////////////////
	  
	  public static boolean testCommitTrans() throws IllegalArgumentException, IOException {
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
			  System.out.println("Test Commit Transaction: Passed!");
		  else
			  System.out.println("Test Commit Transaction: Failed!");
//		  flatFS.abortTrans(tid);
		  return pass;
	  }
	  /////////////////////
	  public static boolean testAbortTrans() throws IllegalArgumentException, IOException {
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
			  System.out.println("Test abort transaction: Passed!");
		  else
			  System.out.println("Test abort transaction: Failed!");
//		  flatFS.abortTrans(tid);
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
			  	flatFS.abortTrans(tid);
		  }
		  catch(IOException e){
			  e.printStackTrace();
			  pass = false;
		  }
		  if(pass)
			  System.out.println("file creation and deletion in FlatFS: Passed!");
		  else
			  System.out.println("file creation and deletion in FlatFS: Failed!");
		  
		  return pass;
	  } 
	  /////////////////////
	  public static boolean testReadFileMetadata() {
		  boolean pass = false;
		  try{
			  TransID tid = flatFS.beginTrans();
			  int tnum = flatFS.createFile(tid);
			  byte buffer[] = new byte[FlatFS.ASK_FILE_METADATA_SIZE];
			  for (int i=0;i<buffer.length;i++){
				  buffer[i]=(byte)i;
			  }
			  flatFS.writeFileMetadata( tid, tnum, buffer);
			  byte buffer2[] =new byte[PTree.METADATA_SIZE];
			  flatFS.readFileMetadata(tid,tnum,buffer2);
			  assert(Arrays.equals(buffer,buffer2));
			  pass = true;
			  flatFS.abortTrans(tid);
		  }
		  catch(IOException e){
			  e.printStackTrace();
			  pass = false;
		  }
		  if(pass)
			  System.out.println("testReadFileMetadata: Passed!");
		  else
			  System.out.println("testReadFileMetadata: Failed!");
		  
		  return pass;
	  }
	  /////////////////////
	  public static boolean testGetParam() {
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
	  ///////////////////////
	  public static boolean testReadANDWriteFile() throws IllegalArgumentException, IOException {
		  boolean pass = false;
		  TransID tid = flatFS.beginTrans();
		  int tnum = -1;
		  try{
			  tnum = flatFS.createFile(tid);
		  }
		  catch(IOException e){
			  e.printStackTrace();
			  pass = false;
		  }


		  byte[] buffer = new byte[PTree.BLOCK_SIZE_BYTES];
		  for(int i=0;i<buffer.length;i++){
			  buffer[i]=(byte)i;
			  if (i == 6)
				  break;
		  }
		  int blockId = 0;
		  try{
			 
			  flatFS.write(tid, tnum, blockId, 6, buffer);
			  blockId = 1000;
			  flatFS.write(tid,tnum,blockId, 6,buffer);
			  flatFS.commitTrans(tid);
			  tid = flatFS.beginTrans();
			  blockId=500;
			  flatFS.write(tid,tnum,blockId, 6,buffer);
			  pass = true;
		  }
		  catch(IOException e){
			  e.printStackTrace();
			  pass = false;
		  }
		  byte[] temp1 = new byte[PTree.BLOCK_SIZE_BYTES];
		  byte[] temp2 = new byte[PTree.BLOCK_SIZE_BYTES];
		  byte[] temp3 = new byte[PTree.BLOCK_SIZE_BYTES];
		  flatFS.read(tid, tnum, blockId, 7, temp1);
		  flatFS.read(tid, tnum, blockId, 7, temp2);
		  flatFS.read(tid, tnum, blockId, 7, temp3);
		  if(!Arrays.equals(buffer, temp1) || !Arrays.equals(buffer, temp2) || !Arrays.equals(buffer, temp3) )
			  pass = false; 
		  flatFS.commitTrans(tid);
		  if(pass)
			  System.out.println("testWriteDataHard: Passed!");
		  else
			  System.out.println("testWriteDataHard: Failed!");
		  return pass;
	  } 

}
