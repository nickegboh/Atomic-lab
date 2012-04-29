/*
 * FlatFS -- flat file system
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */

import java.io.IOException;
import java.io.EOFException;
public class FlatFS{

  public static final int ASK_MAX_FILE = 2423;
  public static final int ASK_FREE_SPACE_BLOCKS = 29542;
  public static final int ASK_FREE_FILES = 29545;
  public static final int ASK_FILE_METADATA_SIZE = 3502;
  
  public PTree ptree;
  public TransID tid;

  /* This function is the constructor. If doFormat == false, data 
   * stored in previous sessions must remain stored. If doFormat == true, 
   * the system should initialize the underlying disk to empty. 
   */
  public FlatFS(boolean doFormat)throws IOException		//TODO
  {
	  if(doFormat == true){
		  
	  }
	  else{
		  
	  }
  }

  /* This function begins a new transaction and returns an 
   * identifying transaction ID.
   */
  public TransID beginTrans()
  {
    return ptree.beginTrans();
  }

  /* This function commits the specified transaction. 
   */
  public void commitTrans(TransID xid)
    throws IOException, IllegalArgumentException
  {
	  ptree.commitTrans(xid);
  }

  /* This function aborts the specified transaction.  
   */
  public void abortTrans(TransID xid)
    throws IOException, IllegalArgumentException
  {
	  ptree.abortTrans(xid);
  }

  /* This function creates a new file and returns the 
   * inode number (a unique identifier for the file) 
   */
  public int createFile(TransID xid)
    throws IOException, IllegalArgumentException		//TODO
  {
    return -1;
  }

  /* This function removes the file specified by the inode 
   * number inumber. The file is deleted and the 
   * corresponding resources are reclaimed. 
   */
  public void deleteFile(TransID xid, int inumber)
    throws IOException, IllegalArgumentException		//TODO
  {
  }

  /* This function reads count bytes from the file specified by inumber  into the 
   * buffer specified by buffer. The parameter offset specifies the starting location 
   * within the file where the data should be read. Upon success, the function returns 
   * the number of bytes read (this number can be less than count if offset + count 
   * exceeds the length of the file. The method throws EOFException if offset is past 
   * the end of the file.
   */
  public int read(TransID xid, int inumber, int offset, int count, byte buffer[])
    throws IOException, IllegalArgumentException, EOFException	//TODO
  {
    return -1;
  }
    
  /* This function writes count bytes from the buffer specified by buffer into the file 
   * specified by inumber. The parameter offset specifies the starting location within 
   * the file where the data should be written. Attempting to write beyond the end of 
   * file should extend the size of the file to accommodate the new data. 
   */
  public void write(TransID xid, int inumber, int offset, int count, byte buffer[])
    throws IOException, IllegalArgumentException			//TODO
  {
  }
  
  /* This function reads getParam(ASK_FILE_METADATA_SIZE) bytes of per-file metadata 
   * for tree tnum and stores this data in the buffer beginning at buffer. This per-file 
   * metadata is an uninterpreted array of bytes that higher-level code may use to store 
   * state associated with a given file. 
   */
  public void readFileMetadata(TransID xid, int inumber, byte buffer[])
    throws IOException, IllegalArgumentException		
  {
	  ptree.readTreeMetadata(xid, inumber, buffer);
  }

  /* This function writes getParam(ASK_FILE_METADATA_SIZE) bytes of per-file metadata 
   * for file inumber from the buffer beginning at buffer. 
   */
  public void writeFileMetadata(TransID xid, int inumber, byte buffer[])
    throws IOException, IllegalArgumentException
  {
	  ptree.writeTreeMetadata(xid, inumber, buffer);
  }

  /* This function allows applications to get parameters of the file system. The parameter 
   * is one of FlatFS.ASK_MAX_FILE (to ask the maximum number of files the formatted file 
   * system supports), FlatFS.ASK_FREE_SPACE_BLOCKS (to ask how many free blocks the file 
   * system currently has), FlatFS.ASK_FREE_FILES (to ask how many free inodes the system 
   * currently has), and FlatFS.ASK_FILE_METADATA_SIZE (to ask how much space there is for 
   * per-file metadata).  It returns an integer answer to the question
   */
  public int getParam(int param)							//TODO
    throws IOException, IllegalArgumentException
  {
    return -1;
  }
    

  
  

}
